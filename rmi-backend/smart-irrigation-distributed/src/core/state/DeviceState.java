package core.state;

import core.dto.ModeDTO;

import java.io.Serializable;

public class DeviceState implements Serializable {
    public ModeDTO mode = ModeDTO.AUTO;
    public boolean manualPumpCmd = false; // used only when MANUAL
    public boolean lastAutoCmd = false;   // hysteresis memory in AUTO
}
