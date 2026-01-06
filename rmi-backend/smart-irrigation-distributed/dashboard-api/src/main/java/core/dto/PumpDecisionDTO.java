package core.dto;

import java.io.Serializable;

public class PumpDecisionDTO implements Serializable {
    public String device;
    public boolean pumpCmd;     // what server wants
    public String reason;       // debug string for now (prof-friendly)
}
