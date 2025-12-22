package com.irrigation.dashboardapi.web;

import core.dto.AlertDTO;
import core.dto.ModeDTO;
import core.dto.ReadingDTO;
import core.rmi.RmiClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import core.dto.DeviceStatusDTO;
import core.dto.DeviceSummaryDTO;



@RestController
public class DeviceController {

    private final RmiClient rmi;

    public DeviceController(RmiClient rmi) {
        this.rmi = rmi;
    }

    // ✅ List devices
    @GetMapping("/api/devices")
    public List<String> devices() throws Exception {
        return rmi.call(svc -> svc.listDevices());
    }

    // ✅ Latest reading for one device
    @GetMapping("/api/devices/{device}/latest")
    public ReadingDTO latest(@PathVariable String device) throws Exception {
        return rmi.call(svc -> svc.getLatest(device));
    }

    // ✅ History readings for charts
    @GetMapping("/api/devices/{device}/history")
    public List<ReadingDTO> history(
            @PathVariable String device,
            @RequestParam String fromUtc,
            @RequestParam String toUtc,
            @RequestParam(defaultValue = "200") int limit
    ) throws Exception {
        return rmi.call(svc -> svc.getHistory(device, fromUtc, toUtc, limit));
    }

    // =========================
    // ✅ CONTROL: MODE
    // =========================

    @GetMapping("/api/devices/{device}/mode")
    public ModeDTO getMode(@PathVariable String device) throws Exception {
        return rmi.call(svc -> svc.getMode(device));
    }

    @PostMapping("/api/devices/{device}/mode")
    public ModeDTO setMode(@PathVariable String device, @RequestBody ModeRequest body) throws Exception {
        if (body == null || body.mode == null) {
            throw new IllegalArgumentException("mode is required (AUTO or MANUAL)");
        }

        ModeDTO mode;
        try {
            mode = ModeDTO.valueOf(body.mode.trim().toUpperCase());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid mode. Use AUTO or MANUAL");
        }

        rmi.call(svc -> { svc.setMode(device, mode); return null; });
        return rmi.call(svc -> svc.getMode(device)); // return the saved value
    }

    public static class ModeRequest {
        public String mode; // "AUTO" or "MANUAL"
    }

    // =========================
    // ✅ CONTROL: MANUAL PUMP
    // =========================

    @GetMapping("/api/devices/{device}/manual-pump")
    public boolean getManualPump(@PathVariable String device) throws Exception {
        return rmi.call(svc -> svc.getManualPump(device));
    }

    @PostMapping("/api/devices/{device}/manual-pump")
    public boolean setManualPump(@PathVariable String device, @RequestBody ManualPumpRequest body) throws Exception {
        if (body == null) {
            throw new IllegalArgumentException("Body required: {\"on\": true/false}");
        }
        rmi.call(svc -> { svc.setManualPump(device, body.on); return null; });
        return rmi.call(svc -> svc.getManualPump(device)); // return the saved value
    }

    public static class ManualPumpRequest {
        public boolean on;
    }

    // =========================
    // ✅ ALERTS
    // =========================

    @GetMapping("/api/devices/{device}/alerts")
    public List<AlertDTO> alerts(
            @PathVariable String device,
            @RequestParam(defaultValue = "1970-01-01T00:00:00Z") String sinceUtc,
            @RequestParam(defaultValue = "200") int limit
    ) throws Exception {
        return rmi.call(svc -> svc.getAlerts(device, sinceUtc, limit));
    }
    // ✅ Status for one device
    @GetMapping("/api/devices/{device}/status")
    public DeviceStatusDTO status(
            @PathVariable String device,
            @RequestParam(defaultValue = "20") int offlineSec
    ) throws Exception {
        return rmi.call(svc -> svc.getStatus(device, offlineSec));
    }

    // ✅ Status for all devices
    @GetMapping("/api/devices/status")
    public List<DeviceStatusDTO> statusAll(
            @RequestParam(defaultValue = "20") int offlineSec
    ) throws Exception {
        return rmi.call(svc -> svc.listStatus(offlineSec));
    }
    // ✅ Full dashboard summary (single call)
    @GetMapping("/api/devices/{device}/summary")
    public DeviceSummaryDTO summary(
            @PathVariable String device,
            @RequestParam(defaultValue = "20") int offlineSec,
            @RequestParam(required = false) String sinceUtc,
            @RequestParam(defaultValue = "10") int alertLimit
    ) throws Exception {
        String finalSince = (sinceUtc == null || sinceUtc.isBlank())
                ? "1970-01-01T00:00:00Z"
                : sinceUtc;

        return rmi.call(svc -> svc.getSummary(device, offlineSec, finalSince, alertLimit));
    }
    @GetMapping("/api/devices/summary")
    public List<core.dto.DeviceSummaryRowDTO> summaries(
            @RequestParam(defaultValue = "20") int offlineSec,
            @RequestParam(defaultValue = "1970-01-01T00:00:00Z") String sinceUtc
    ) throws Exception {
        return rmi.call(svc -> svc.listSummaries(offlineSec, sinceUtc));
    }




}
