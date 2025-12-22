package core.logic;

import core.dto.ReadingDTO;

public class PumpLogic {

    // TEMP defaults for MVP (we will later make them configurable via RMI + DB)
    private static final int MOISTURE_ON  = 30;
    private static final int MOISTURE_OFF = 40;
    private static final int TANK_LOW     = 10;

    // Simple stateless decision for now (no mode yet)
    public static boolean decidePumpCmd(ReadingDTO r, boolean currentCmd) {
        if (r == null) return false;
        if (r.device == null || r.device.isBlank()) return false;

        // missing critical inputs => fail safe OFF
        if (r.soil == null || r.waterTank == null || r.raining == null) return false;

        boolean tankLow = r.waterTank < TANK_LOW;

        // hard stops
        if (r.raining || tankLow) return false;

        // hysteresis using currentCmd
        if (!currentCmd && r.soil < MOISTURE_ON) return true;
        if (currentCmd && r.soil > MOISTURE_OFF) return false;

        return currentCmd;
    }
}
