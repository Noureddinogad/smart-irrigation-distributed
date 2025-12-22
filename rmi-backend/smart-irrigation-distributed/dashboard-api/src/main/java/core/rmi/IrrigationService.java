package core.rmi;

import core.dto.AlertDTO;
import core.dto.ModeDTO;
import core.dto.PumpDecisionDTO;
import core.dto.ReadingDTO;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.List;
import core.dto.DeviceStatusDTO;
import java.util.List;
import core.dto.DeviceSummaryDTO;
public interface IrrigationService extends Remote {

    // called by gateway on every POST
    PumpDecisionDTO pushReading(ReadingDTO r) throws RemoteException;

    // read APIs
    ReadingDTO getLatest(String device) throws RemoteException;

    List<ReadingDTO> getHistory(String device, String fromUtc, String toUtc, int limit)
            throws RemoteException;

    // control API
    void setMode(String device, ModeDTO mode) throws RemoteException;
    ModeDTO getMode(String device) throws RemoteException;

    void setManualPump(String device, boolean on) throws RemoteException;
    boolean getManualPump(String device) throws RemoteException;

    // alerts API
    List<AlertDTO> getAlerts(String device, String sinceUtc, int limit) throws RemoteException;

    // devices API ✅ (needed for /api/devices)
    List<String> listDevices() throws RemoteException;
    DeviceStatusDTO getStatus(String device, int offlineSec) throws RemoteException;
    List<DeviceStatusDTO> listStatus(int offlineSec) throws RemoteException;
    DeviceSummaryDTO getSummary(String device, int offlineSec, String sinceUtc, int alertLimit)
            throws java.rmi.RemoteException;
    java.util.List<core.dto.DeviceSummaryRowDTO> listSummaries(int offlineSec, String sinceUtc) throws java.rmi.RemoteException;


}
