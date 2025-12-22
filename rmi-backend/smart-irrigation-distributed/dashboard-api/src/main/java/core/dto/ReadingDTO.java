package core.dto;

import java.io.Serializable;

public class ReadingDTO implements Serializable {
    public String device;
    public Integer soil;        // 0..100
    public Integer waterTank;   // 0..100
    public Boolean raining;
    public Boolean pump;        // reported state (what ESP thinks it is)
    public Double tempC;        // nullable
    public Double humidity;     // nullable
    public String createdUtc;

}
