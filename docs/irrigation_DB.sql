USE IrrigationDB;
GO

/* =========================
   1) DEVICES
   ========================= */
CREATE TABLE dbo.devices (
  device_id      VARCHAR(64) NOT NULL PRIMARY KEY,
  created_utc    DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  last_seen_utc  DATETIME2(3) NULL
);
GO

/* =========================
   2) DEVICE STATE
   ========================= */
CREATE TABLE dbo.device_state (
  device_id        VARCHAR(64) NOT NULL PRIMARY KEY,
  mode             VARCHAR(10) NOT NULL,           -- AUTO / MANUAL
  manual_pump_cmd  BIT NOT NULL,
  last_auto_cmd    BIT NOT NULL,
  updated_utc      DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  CONSTRAINT FK_device_state_device
    FOREIGN KEY (device_id) REFERENCES dbo.devices(device_id)
);
GO

/* =========================
   3) SENSOR READINGS
   ========================= */
CREATE TABLE dbo.readings (
  id            BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
  device_id     VARCHAR(64) NOT NULL,
  soil          INT NULL,
  water_tank    INT NULL,
  raining       BIT NULL,
  pump_reported BIT NULL,
  temp_c        FLOAT NULL,
  humidity      FLOAT NULL,
  created_utc   DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  CONSTRAINT FK_readings_device
    FOREIGN KEY (device_id) REFERENCES dbo.devices(device_id)
);
GO

/* =========================
   4) PUMP DECISIONS (RMI BRAIN LOG)
   ========================= */
CREATE TABLE dbo.pump_decisions (
  id          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
  device_id   VARCHAR(64) NOT NULL,
  mode        VARCHAR(10) NOT NULL,
  pump_cmd    BIT NOT NULL,
  reason      NVARCHAR(200) NOT NULL,
  created_utc DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  CONSTRAINT FK_pump_decisions_device
    FOREIGN KEY (device_id) REFERENCES dbo.devices(device_id)
);
GO

/* =========================
   5) CONTROL EVENTS (MODE / MANUAL)
   ========================= */
CREATE TABLE dbo.control_events (
  id           BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
  device_id    VARCHAR(64) NOT NULL,
  event_type   VARCHAR(20) NOT NULL,     -- SET_MODE / SET_MANUAL_PUMP
  mode         VARCHAR(10) NULL,
  manual_pump  BIT NULL,
  source       VARCHAR(30) NOT NULL,
  created_utc  DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  CONSTRAINT FK_control_events_device
    FOREIGN KEY (device_id) REFERENCES dbo.devices(device_id)
);
GO

/* =========================
   6) ALERTS
   ========================= */
CREATE TABLE dbo.alerts (
  id          BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
  device_id   VARCHAR(64) NOT NULL,
  alert_type  VARCHAR(30) NOT NULL,   -- OFFLINE, TANK_LOW, RAINING, SENSOR_MISSING
  severity    VARCHAR(10) NOT NULL,   -- INFO, WARN, CRIT
  message     NVARCHAR(200) NOT NULL,
  created_utc DATETIME2(3) NOT NULL DEFAULT SYSUTCDATETIME(),
  CONSTRAINT FK_alerts_device
    FOREIGN KEY (device_id) REFERENCES dbo.devices(device_id)
);
GO
