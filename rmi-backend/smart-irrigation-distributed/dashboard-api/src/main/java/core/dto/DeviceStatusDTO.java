package core.dto;

import java.io.Serializable;

public class DeviceStatusDTO implements Serializable {
    public String device;
    public String lastSeenUtc;          // ISO string or null
    public boolean online;
    public long secondsSinceLastSeen;   // -1 if unknown
}
