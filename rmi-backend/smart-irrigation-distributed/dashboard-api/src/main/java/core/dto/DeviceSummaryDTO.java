package core.dto;

import java.io.Serializable;
import java.util.List;

public class DeviceSummaryDTO implements Serializable {
    public String device;

    public ReadingDTO latest;
    public ModeDTO mode;
    public Boolean manualPump;

    public DeviceStatusDTO status;

    public List<AlertDTO> alerts;
}
