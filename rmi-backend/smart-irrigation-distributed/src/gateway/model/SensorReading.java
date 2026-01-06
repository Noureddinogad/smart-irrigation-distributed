package gateway.model;

import java.io.Serializable;

public class SensorReading implements Serializable {
    public String device;
    public Integer soil;
    public Integer waterTank;
    public Boolean raining;
    public Boolean pump;
    public Double tempC;
    public Double humidity;
}
