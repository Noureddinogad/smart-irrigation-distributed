package core.dto;

import java.io.Serializable;

public class DeviceSummaryRowDTO implements Serializable {
    public String device;
    public Boolean online;
    public Long secondsSinceLastSeen;

    public Integer soil;
    public Integer waterTank;
    public Boolean raining;
    public Boolean pump;
    public Double tempC;
    public Double humidity;
    public String createdUtc;

    public ModeDTO mode;
    public Boolean manualPump;
    public Integer recentAlertCount; // alerts since sinceUtc
}
