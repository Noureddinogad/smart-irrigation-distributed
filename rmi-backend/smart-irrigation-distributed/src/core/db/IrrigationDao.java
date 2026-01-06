package core.db;

import core.dto.AlertDTO;
import core.dto.DeviceStatusDTO;
import core.dto.ModeDTO;
import core.dto.ReadingDTO;
import core.state.DeviceState;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public class IrrigationDao {

    // 1) Ensure device exists in dbo.devices (safe for first time)
    public void ensureDeviceExists(String deviceId) throws Exception {
        if (deviceId == null || deviceId.isBlank()) return;

        final String sql =
                "IF NOT EXISTS (SELECT 1 FROM dbo.devices WHERE device_id = ?) " +
                        "INSERT INTO dbo.devices(device_id) VALUES (?)";

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            ps.setString(2, deviceId);
            ps.executeUpdate();
        }
    }

    // 2) Update last_seen_utc (called on every pushReading)
    public void touchLastSeen(String deviceId) throws Exception {
        if (deviceId == null || deviceId.isBlank()) return;

        ensureDeviceExists(deviceId);

        final String sql =
                "UPDATE dbo.devices SET last_seen_utc = SYSUTCDATETIME() WHERE device_id = ?";

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            ps.executeUpdate();
        }
    }

    // 3) Load state from dbo.device_state; if missing, create default row
    public DeviceState loadOrCreateState(String deviceId) throws Exception {
        if (deviceId == null || deviceId.isBlank()) {
            return defaultState();
        }

        ensureDeviceExists(deviceId);

        final String q =
                "SELECT mode, manual_pump_cmd, last_auto_cmd " +
                        "FROM dbo.device_state WHERE device_id = ?";

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(q)) {

            ps.setString(1, deviceId);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    DeviceState st = new DeviceState();
                    st.mode = parseMode(rs.getString("mode"));
                    st.manualPumpCmd = rs.getBoolean("manual_pump_cmd");
                    st.lastAutoCmd = rs.getBoolean("last_auto_cmd");
                    return st;
                }
            }
        }

        DeviceState def = defaultState();
        upsertState(deviceId, def.mode, def.manualPumpCmd, def.lastAutoCmd);
        return def;
    }

    // 4) Upsert state (works for insert or update)
    public void upsertState(String deviceId, ModeDTO mode, boolean manualPumpCmd, boolean lastAutoCmd) throws Exception {
        if (deviceId == null || deviceId.isBlank()) return;
        if (mode == null) mode = ModeDTO.AUTO;

        ensureDeviceExists(deviceId);

        final String sql =
                "MERGE dbo.device_state AS t " +
                        "USING (SELECT ? AS device_id) AS s " +
                        "ON t.device_id = s.device_id " +
                        "WHEN MATCHED THEN UPDATE SET " +
                        "  mode = ?, manual_pump_cmd = ?, last_auto_cmd = ?, updated_utc = SYSUTCDATETIME() " +
                        "WHEN NOT MATCHED THEN INSERT (device_id, mode, manual_pump_cmd, last_auto_cmd) " +
                        "  VALUES (?, ?, ?, ?);";

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int i = 1;
            ps.setString(i++, deviceId);
            ps.setString(i++, mode.name());
            ps.setBoolean(i++, manualPumpCmd);
            ps.setBoolean(i++, lastAutoCmd);

            ps.setString(i++, deviceId);
            ps.setString(i++, mode.name());
            ps.setBoolean(i++, manualPumpCmd);
            ps.setBoolean(i++, lastAutoCmd);

            ps.executeUpdate();
        }
    }

    // 5) Insert raw sensor reading into dbo.readings
    public void insertReading(ReadingDTO r) throws Exception {
        if (r == null || r.device == null || r.device.isBlank()) return;

        ensureDeviceExists(r.device);

        final String sql = """
            INSERT INTO dbo.readings
            (device_id, soil, water_tank, raining, pump_reported, temp_c, humidity)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            int i = 1;
            ps.setString(i++, r.device);

            if (r.soil != null) ps.setInt(i++, r.soil);
            else ps.setNull(i++, java.sql.Types.INTEGER);

            if (r.waterTank != null) ps.setInt(i++, r.waterTank);
            else ps.setNull(i++, java.sql.Types.INTEGER);

            if (r.raining != null) ps.setBoolean(i++, r.raining);
            else ps.setNull(i++, java.sql.Types.BIT);

            if (r.pump != null) ps.setBoolean(i++, r.pump);
            else ps.setNull(i++, java.sql.Types.BIT);

            if (r.tempC != null) ps.setDouble(i++, r.tempC);
            else ps.setNull(i++, java.sql.Types.FLOAT);

            if (r.humidity != null) ps.setDouble(i++, r.humidity);
            else ps.setNull(i++, java.sql.Types.FLOAT);

            ps.executeUpdate();
        }
    }

    // 6) Insert an alert row into dbo.alerts
    public void insertAlert(String deviceId, String alertType, String severity, String message) throws Exception {
        if (deviceId == null || deviceId.isBlank()) return;
        if (alertType == null || alertType.isBlank()) return;
        if (severity == null || severity.isBlank()) severity = "INFO";
        if (message == null) message = "";

        ensureDeviceExists(deviceId);

        final String sql = """
            INSERT INTO dbo.alerts (device_id, alert_type, severity, message)
            VALUES (?, ?, ?, ?)
        """;

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, deviceId);
            ps.setString(2, alertType);
            ps.setString(3, severity);
            ps.setString(4, message);
            ps.executeUpdate();
        }
    }

    // 7) Read alerts since a UTC time (ORDER ASC)
    public List<AlertDTO> getAlerts(String deviceId, String sinceUtc, int limit) throws Exception {
        if (deviceId == null || deviceId.isBlank()) return List.of();
        if (sinceUtc == null || sinceUtc.isBlank()) sinceUtc = "1970-01-01T00:00:00Z";

        if (limit <= 0) limit = 200;
        if (limit > 2000) limit = 2000;

        Timestamp sinceTs = Timestamp.from(OffsetDateTime.parse(sinceUtc).toInstant());

        final String sql = """
            SELECT TOP (?)
              id, device_id, alert_type, severity, message, created_utc
            FROM dbo.alerts
            WHERE device_id = ?
              AND created_utc > ?
            ORDER BY created_utc ASC
        """;

        List<AlertDTO> out = new ArrayList<>();

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, limit);
            ps.setString(2, deviceId);
            ps.setTimestamp(3, sinceTs);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    AlertDTO a = new AlertDTO();

                    long idVal = rs.getLong("id");
                    a.id = rs.wasNull() ? null : idVal;

                    a.device = rs.getString("device_id");
                    a.type = rs.getString("alert_type");
                    a.severity = rs.getString("severity");
                    a.message = rs.getString("message");
                    a.createdUtc = rs.getTimestamp("created_utc").toInstant().toString();

                    out.add(a);
                }
            }
        }

        return out;
    }

    // 8) List all known devices
    public List<String> listDevices() throws Exception {
        final String sql = """
            SELECT device_id
            FROM dbo.devices
            ORDER BY device_id ASC
        """;

        List<String> out = new ArrayList<>();

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                out.add(rs.getString("device_id"));
            }
        }

        return out;
    }

    // =========================
    // ✅ FIXED STATUS (NO TIMEZONE BUGS)
    // =========================

    // Used by IrrigationServiceImpl.getStatus(...)
    public DeviceStatusDTO getStatus(String deviceId, int offlineSec) throws Exception {
        if (deviceId == null || deviceId.isBlank()) return null;
        if (offlineSec <= 0) offlineSec = 20;

        final String sql = """
            SELECT
              device_id,
              last_seen_utc,
              DATEDIFF(SECOND, last_seen_utc, SYSUTCDATETIME()) AS diff_sec
            FROM dbo.devices
            WHERE device_id = ?
        """;

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, deviceId);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;

                Timestamp last = rs.getTimestamp("last_seen_utc");
                Integer diff = (Integer) rs.getObject("diff_sec"); // can be null

                DeviceStatusDTO s = new DeviceStatusDTO();
                s.device = rs.getString("device_id");

                if (last == null || diff == null) {
                    s.lastSeenUtc = null;
                    s.secondsSinceLastSeen = -1;
                    s.online = false;
                } else {
                    s.lastSeenUtc = last.toInstant().toString();
                    s.secondsSinceLastSeen = diff.longValue();
                    s.online = s.secondsSinceLastSeen <= offlineSec;
                }

                return s;
            }
        }
    }

    // Used by IrrigationServiceImpl.listStatus(...)
    public List<DeviceStatusDTO> listStatus(int offlineSec) throws Exception {
        if (offlineSec <= 0) offlineSec = 20;

        final String sql = """
            SELECT
              device_id,
              last_seen_utc,
              DATEDIFF(SECOND, last_seen_utc, SYSUTCDATETIME()) AS diff_sec
            FROM dbo.devices
            ORDER BY device_id
        """;

        List<DeviceStatusDTO> out = new ArrayList<>();

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                Timestamp last = rs.getTimestamp("last_seen_utc");
                Integer diff = (Integer) rs.getObject("diff_sec");

                DeviceStatusDTO s = new DeviceStatusDTO();
                s.device = rs.getString("device_id");

                if (last == null || diff == null) {
                    s.lastSeenUtc = null;
                    s.secondsSinceLastSeen = -1;
                    s.online = false;
                } else {
                    s.lastSeenUtc = last.toInstant().toString();
                    s.secondsSinceLastSeen = diff.longValue();
                    s.online = s.secondsSinceLastSeen <= offlineSec;
                }

                out.add(s);
            }
        }

        return out;
    }

    // Keep these helpers
    private DeviceState defaultState() {
        DeviceState st = new DeviceState();
        st.mode = ModeDTO.AUTO;
        st.manualPumpCmd = false;
        st.lastAutoCmd = false;
        return st;
    }

    private ModeDTO parseMode(String s) {
        if (s == null) return ModeDTO.AUTO;
        return "MANUAL".equalsIgnoreCase(s) ? ModeDTO.MANUAL : ModeDTO.AUTO;
    }
}
