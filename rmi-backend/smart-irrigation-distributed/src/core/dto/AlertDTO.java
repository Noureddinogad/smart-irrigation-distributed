package core.dto;

import java.io.Serializable;

public class AlertDTO implements Serializable {
    public Long id;
    public String device;
    public String type;       // OFFLINE, TANK_LOW, RAINING, SENSOR_MISSING ...
    public String severity;   // INFO, WARN, CRIT
    public String message;
    public String createdUtc;
}
