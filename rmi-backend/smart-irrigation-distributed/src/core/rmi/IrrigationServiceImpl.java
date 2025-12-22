package core.rmi;

import core.db.Db;
import core.db.IrrigationDao;
import core.dto.AlertDTO;
import core.dto.DeviceStatusDTO;
import core.dto.DeviceSummaryDTO;
import core.dto.ModeDTO;
import core.dto.PumpDecisionDTO;
import core.dto.ReadingDTO;
import core.logic.PumpLogic;
import core.state.DeviceState;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class IrrigationServiceImpl extends UnicastRemoteObject implements IrrigationService {

    private final IrrigationDao dao;

    // device -> runtime cached state (loaded once from DB on first contact)
    private final ConcurrentHashMap<String, DeviceState> devices = new ConcurrentHashMap<>();

    // Anti-spam cooldown for some alerts: device|type -> last emitted ms
    private final ConcurrentHashMap<String, Long> lastAlertMs = new ConcurrentHashMap<>();

    // ✅ Tank-low latch (prevents spam while still low)
    private final ConcurrentHashMap<String, Boolean> tankLowLatched = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Integer> lastTankSeen = new ConcurrentHashMap<>();

    public IrrigationServiceImpl(IrrigationDao dao) throws RemoteException {
        super();
        this.dao = dao;
    }

    /** Default fallback (no DB). */
    private DeviceState defaultState() {
        DeviceState st = new DeviceState();
        st.mode = ModeDTO.AUTO;
        st.manualPumpCmd = false;
        st.lastAutoCmd = false;
        return st;
    }

    /**
     * load state from DB on first contact.
     * IMPORTANT: if device is blank -> don't cache and don't touch DB.
     */
    private DeviceState stateFor(String device) {
        if (device == null || device.isBlank()) return defaultState();

        return devices.computeIfAbsent(device, d -> {
            try {
                DeviceState st = dao.loadOrCreateState(d);
                System.out.println("[RMI] Loaded state from DB device=" + d
                        + " mode=" + st.mode
                        + " manual=" + st.manualPumpCmd
                        + " lastAuto=" + st.lastAutoCmd);
                return st;
            } catch (Exception e) {
                System.err.println("[DB] loadOrCreateState failed for device=" + d + " : " + e.getMessage());
                return defaultState();
            }
        });
    }

    /** Persist current cached state to DB */
    private void persistState(String device, DeviceState st) {
        if (device == null || device.isBlank()) return;
        if (st == null) return;

        try {
            dao.upsertState(device, st.mode, st.manualPumpCmd, st.lastAutoCmd);
        } catch (Exception e) {
            System.err.println("[DB] upsertState failed for device=" + device + " : " + e.getMessage());
        }
    }

    private boolean shouldEmitAlert(String device, String type, long cooldownMs) {
        if (device == null || device.isBlank()) return false;
        if (type == null || type.isBlank()) return false;

        String key = device + "|" + type;
        long now = System.currentTimeMillis();
        Long last = lastAlertMs.get(key);

        if (last != null && (now - last) < cooldownMs) return false;

        lastAlertMs.put(key, now);
        return true;
    }

    /* ===================== CORE DECISION ===================== */

    @Override
    public PumpDecisionDTO pushReading(ReadingDTO r) throws RemoteException {

        // Persist raw reading + last_seen_utc
        try {
            dao.insertReading(r);
            if (r != null && r.device != null && !r.device.isBlank()) {
                dao.touchLastSeen(r.device);
            }
        } catch (Exception e) {
            System.err.println("[DB] insertReading/touchLastSeen failed: " + e.getMessage());
        }

        // Alerts
        try {
            if (r != null && r.device != null && !r.device.isBlank()) {

                // 1) SENSOR_MISSING (cooldown 60s)
                if (r.soil == null || r.waterTank == null || r.raining == null) {
                    if (shouldEmitAlert(r.device, "SENSOR_MISSING", 60_000)) {
                        dao.insertAlert(
                                r.device,
                                "SENSOR_MISSING",
                                "WARN",
                                "Missing critical sensor field(s): soil/water_tank/raining"
                        );
                    }
                }

                // ✅ 2) TANK_LOW (NO SPAM): fire once when crossing into low, reset after recovery
                if (r.waterTank != null) {
                    int lowThreshold = 10;   // alert when <= 10
                    int recoverLevel = 15;   // reset latch when >= 15

                    boolean latched = tankLowLatched.getOrDefault(r.device, false);
                    Integer prevTank = lastTankSeen.get(r.device);

                    // reset latch if recovered
                    if (r.waterTank >= recoverLevel) {
                        latched = false;
                    }

                    // fire only once per low episode
                    if (!latched && r.waterTank <= lowThreshold) {
                        // Only on crossing (or first observation)
                        if (prevTank == null || prevTank > lowThreshold) {
                            dao.insertAlert(
                                    r.device,
                                    "TANK_LOW",
                                    "WARN",
                                    "Water tank is low: " + r.waterTank + "%"
                            );
                        }
                        latched = true;
                    }

                    tankLowLatched.put(r.device, latched);
                    lastTankSeen.put(r.device, r.waterTank);
                }

                // 3) RAINING (cooldown 30s)
                if (Boolean.TRUE.equals(r.raining)) {
                    if (shouldEmitAlert(r.device, "RAINING", 30_000)) {
                        dao.insertAlert(
                                r.device,
                                "RAINING",
                                "INFO",
                                "Rain detected"
                        );
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[DB] insertAlert failed: " + e.getMessage());
        }

        String device = (r != null ? r.device : null);
        DeviceState st = stateFor(device);

        boolean cmd;
        String reason;

        if (st.mode == ModeDTO.MANUAL) {
            cmd = st.manualPumpCmd;
            reason = "MANUAL mode -> manualPumpCmd=" + cmd;
        } else {
            cmd = PumpLogic.decidePumpCmd(r, st.lastAutoCmd);
            st.lastAutoCmd = cmd;
            reason = "AUTO mode -> decided pump_cmd=" + cmd;

            // persist last_auto_cmd updates
            persistState(device, st);
        }

        // decision log
        insertPumpDecision(device, st.mode, cmd, reason);

        PumpDecisionDTO out = new PumpDecisionDTO();
        out.device = device;
        out.pumpCmd = cmd;
        out.reason = reason;

        System.out.println("[RMI] pushReading device=" + device
                + " mode=" + st.mode
                + " -> pump_cmd=" + cmd
                + " (" + reason + ")");

        return out;
    }

    /* ===================== MODE CONTROL ===================== */

    @Override
    public void setMode(String device, ModeDTO mode) throws RemoteException {
        if (device == null || device.isBlank()) return;
        if (mode == null) return;

        DeviceState st = stateFor(device);
        st.mode = mode;

        persistState(device, st);
        insertControlEvent(device, "SET_MODE", mode, null, "CONTROL_API");

        System.out.println("[RMI] setMode device=" + device + " -> " + mode);
    }

    @Override
    public ModeDTO getMode(String device) throws RemoteException {
        if (device == null || device.isBlank()) return ModeDTO.AUTO;
        return stateFor(device).mode;
    }

    /* ===================== MANUAL PUMP ===================== */

    @Override
    public void setManualPump(String device, boolean on) throws RemoteException {
        if (device == null || device.isBlank()) return;

        DeviceState st = stateFor(device);
        st.manualPumpCmd = on;

        persistState(device, st);
        insertControlEvent(device, "SET_MANUAL_PUMP", null, on, "CONTROL_API");

        System.out.println("[RMI] setManualPump device=" + device + " -> " + on);
    }

    @Override
    public boolean getManualPump(String device) throws RemoteException {
        if (device == null || device.isBlank()) return false;
        return stateFor(device).manualPumpCmd;
    }

    /* ===================== DB HELPERS (logs) ===================== */

    private void insertPumpDecision(String device, ModeDTO mode, boolean cmd, String reason) {
        if (device == null || device.isBlank()) return;

        String sql = """
            INSERT INTO dbo.pump_decisions
            (device_id, mode, pump_cmd, reason)
            VALUES (?, ?, ?, ?)
        """;

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, device);
            ps.setString(2, mode != null ? mode.name() : ModeDTO.AUTO.name());
            ps.setBoolean(3, cmd);
            ps.setString(4, reason);
            ps.executeUpdate();

        } catch (Exception e) {
            System.err.println("[DB] pump_decisions insert failed: " + e.getMessage());
        }
    }

    private void insertControlEvent(String device, String type, ModeDTO mode, Boolean manualPump, String source) {
        if (device == null || device.isBlank()) return;

        String sql = """
            INSERT INTO dbo.control_events
            (device_id, event_type, mode, manual_pump, source)
            VALUES (?, ?, ?, ?, ?)
        """;

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, device);
            ps.setString(2, type);
            ps.setString(3, mode != null ? mode.name() : null);

            if (manualPump != null) ps.setBoolean(4, manualPump);
            else ps.setNull(4, java.sql.Types.BIT);

            ps.setString(5, (source != null && !source.isBlank()) ? source : "UNKNOWN");

            ps.executeUpdate();

        } catch (Exception e) {
            System.err.println("[DB] control_events insert failed: " + e.getMessage());
        }
    }

    /* ===================== READ APIs ===================== */

    private ReadingDTO mapReadingRow(ResultSet rs) throws Exception {
        ReadingDTO r = new ReadingDTO();

        r.device = rs.getString("device_id");

        int soilVal = rs.getInt("soil");
        r.soil = rs.wasNull() ? null : soilVal;

        int wtVal = rs.getInt("water_tank");
        r.waterTank = rs.wasNull() ? null : wtVal;

        boolean rainingVal = rs.getBoolean("raining");
        r.raining = rs.wasNull() ? null : rainingVal;

        boolean pumpVal = rs.getBoolean("pump_reported");
        r.pump = rs.wasNull() ? null : pumpVal;

        Object t = rs.getObject("temp_c");
        r.tempC = (t == null) ? null : ((Number) t).doubleValue();

        Object h = rs.getObject("humidity");
        r.humidity = (h == null) ? null : ((Number) h).doubleValue();

        r.createdUtc = rs.getTimestamp("created_utc").toInstant().toString();

        return r;
    }

    @Override
    public ReadingDTO getLatest(String device) throws RemoteException {
        if (device == null || device.isBlank()) return null;

        String sql = """
            SELECT TOP 1
              device_id, soil, water_tank, raining, pump_reported, temp_c, humidity, created_utc
            FROM dbo.readings
            WHERE device_id = ?
            ORDER BY created_utc DESC
        """;

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, device);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return mapReadingRow(rs);
            }

        } catch (Exception e) {
            throw new RemoteException("getLatest failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<ReadingDTO> getHistory(String device, String fromUtc, String toUtc, int limit) throws RemoteException {
        if (device == null || device.isBlank()) return List.of();
        if (fromUtc == null || toUtc == null) return List.of();

        if (limit <= 0) limit = 200;
        if (limit > 5000) limit = 5000;

        Timestamp fromTs = Timestamp.from(OffsetDateTime.parse(fromUtc).toInstant());
        Timestamp toTs   = Timestamp.from(OffsetDateTime.parse(toUtc).toInstant());

        String sql = """
            SELECT TOP (?)
              device_id, soil, water_tank, raining, pump_reported, temp_c, humidity, created_utc
            FROM dbo.readings
            WHERE device_id = ?
              AND created_utc >= ?
              AND created_utc <= ?
            ORDER BY created_utc ASC
        """;

        try (Connection c = Db.get();
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setInt(1, limit);
            ps.setString(2, device);
            ps.setTimestamp(3, fromTs);
            ps.setTimestamp(4, toTs);

            List<ReadingDTO> out = new ArrayList<>();
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) out.add(mapReadingRow(rs));
            }
            return out;

        } catch (Exception e) {
            throw new RemoteException("getHistory failed: " + e.getMessage(), e);
        }
    }

    /* ===================== ALERTS ===================== */

    @Override
    public List<AlertDTO> getAlerts(String device, String sinceUtc, int limit) throws RemoteException {
        try {
            return dao.getAlerts(device, sinceUtc, limit);
        } catch (Exception e) {
            throw new RemoteException("getAlerts failed: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> listDevices() throws RemoteException {
        try {
            return dao.listDevices();
        } catch (Exception e) {
            throw new RemoteException("listDevices failed: " + e.getMessage(), e);
        }
    }

    @Override
    public DeviceStatusDTO getStatus(String device, int offlineSec) throws RemoteException {
        try {
            if (offlineSec <= 0) offlineSec = 20;
            return dao.getStatus(device, offlineSec);
        } catch (Exception e) {
            throw new RemoteException("getStatus failed: " + e.getMessage(), e);
        }
    }


    @Override
    public List<DeviceStatusDTO> listStatus(int offlineSec) throws RemoteException {
        try {
            return dao.listStatus(offlineSec);
        } catch (Exception e) {
            throw new RemoteException("listStatus failed: " + e.getMessage(), e);
        }
    }

    @Override
    public DeviceSummaryDTO getSummary(String device, int offlineSec, String sinceUtc, int alertLimit) throws RemoteException {
        if (device == null || device.isBlank()) return null;

        try {
            if (offlineSec <= 0) offlineSec = 20;
            if (alertLimit <= 0) alertLimit = 10;

            if (sinceUtc == null || sinceUtc.isBlank()) {
                sinceUtc = "1970-01-01T00:00:00Z";
            }

            DeviceSummaryDTO s = new DeviceSummaryDTO();
            s.device = device;

            s.latest = getLatest(device);
            s.mode = getMode(device);
            s.manualPump = getManualPump(device);
            s.status = getStatus(device, offlineSec);
            s.alerts = dao.getAlerts(device, sinceUtc, alertLimit);

            return s;
        } catch (Exception e) {
            throw new RemoteException("getSummary failed: " + e.getMessage(), e);
        }
    }
    @Override
    public List<core.dto.DeviceSummaryRowDTO> listSummaries(int offlineSec, String sinceUtc) throws RemoteException {
        if (offlineSec <= 0) offlineSec = 20;
        if (sinceUtc == null || sinceUtc.isBlank()) sinceUtc = "1970-01-01T00:00:00Z";

        try {
            List<String> devices = dao.listDevices();
            List<core.dto.DeviceSummaryRowDTO> out = new ArrayList<>();

            for (String d : devices) {
                if (d == null || d.isBlank()) continue;

                var row = new core.dto.DeviceSummaryRowDTO();
                row.device = d;

                // status
                DeviceStatusDTO st = getStatus(d, offlineSec);
                row.online = st.online;
                row.secondsSinceLastSeen = st.secondsSinceLastSeen;

                // latest
                ReadingDTO latest = getLatest(d);
                if (latest != null) {
                    row.soil = latest.soil;
                    row.waterTank = latest.waterTank;
                    row.raining = latest.raining;
                    row.pump = latest.pump;
                    row.tempC = latest.tempC;
                    row.humidity = latest.humidity;
                    row.createdUtc = latest.createdUtc;
                }

                // control state
                row.mode = getMode(d);
                row.manualPump = getManualPump(d);

                // alerts count since sinceUtc (limit huge number)
                List<AlertDTO> alerts = dao.getAlerts(d, sinceUtc, 1000);
                row.recentAlertCount = alerts == null ? 0 : alerts.size();

                out.add(row);
            }

            return out;

        } catch (Exception e) {
            throw new RemoteException("listSummaries failed: " + e.getMessage(), e);
        }
    }

}
