package gateway.http;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import core.dto.AlertDTO;
import core.dto.ModeDTO;
import core.dto.PumpDecisionDTO;
import core.dto.ReadingDTO;
import gateway.Client.RmiClient;
import gateway.model.SensorReading;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class Esp32HttpServer {

    private static RmiClient rmiClient = null;

    public static void setRmiClient(RmiClient client) {
        rmiClient = client;
    }

    public static void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);

        // ESP ingestion + control
        server.createContext("/api/readings", new ReadingsHandler());
        server.createContext("/control/mode", new ControlModeHandler());
        server.createContext("/control/pump", new ControlPumpHandler());

        // Read APIs for mobile/dashboard
        server.createContext("/readings/latest", new LatestHandler());
        server.createContext("/readings/history", new HistoryHandler());

        // Alerts SSE
        server.createContext("/alerts/stream", new AlertsSseHandler());

        server.setExecutor(null);

        System.out.println("✅ Java HTTP Gateway running");
        System.out.println("   POST http://0.0.0.0:" + port + "/api/readings");
        System.out.println("   POST http://0.0.0.0:" + port + "/control/mode");
        System.out.println("   GET  http://0.0.0.0:" + port + "/control/mode?device=esp32-01");
        System.out.println("   POST http://0.0.0.0:" + port + "/control/pump");
        System.out.println("   GET  http://0.0.0.0:" + port + "/readings/latest?device=esp32-01");
        System.out.println("   GET  http://0.0.0.0:" + port + "/readings/history?device=esp32-01&from=...&to=...&limit=...");
        System.out.println("   GET  http://0.0.0.0:" + port + "/alerts/stream?device=esp32-01&since=2025-12-19T15:00:00Z");
        System.out.println("   RMI forwarding: " + (rmiClient != null ? "ON" : "OFF"));

        server.start();
    }

    // ======================================================================
    // HANDLERS
    // ======================================================================

    static class ReadingsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                send(exchange, 405, "{\"error\":\"Only POST allowed\"}");
                return;
            }

            String body = readAll(exchange.getRequestBody());
            SensorReading r = parseSensorReading(body);

            printLikeTerminal(r);

            boolean pumpCmd = false; // FAIL-SAFE

            if (rmiClient != null) {
                try {
                    ReadingDTO dto = toDto(r);

                    PumpDecisionDTO decision = rmiClient.call(svc -> svc.pushReading(dto)); // reconnect+retry
                    pumpCmd = decision.pumpCmd;

                    System.out.println("[GW→RMI] pump_cmd=" + pumpCmd + " reason=" + decision.reason);

                } catch (Exception e) {
                    System.out.println("[GW→RMI] ERROR: " + e.getMessage());
                    pumpCmd = false;
                }
            } else {
                System.out.println("[GW→RMI] RMI OFF -> pump_cmd=false");
            }

            send(exchange, 200, "{\"status\":\"ok\",\"pump_cmd\":" + pumpCmd + "}");
        }
    }

    static class LatestHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;

            if (rmiClient == null) {
                send(exchange, 503, "{\"error\":\"RMI not configured\"}");
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                send(exchange, 405, "{\"error\":\"Only GET allowed\"}");
                return;
            }

            try {
                String device = queryParam(exchange, "device");
                if (device == null || device.isBlank()) {
                    send(exchange, 400, "{\"error\":\"Missing device\"}");
                    return;
                }

                ReadingDTO r = rmiClient.call(svc -> svc.getLatest(device));
                if (r == null) {
                    send(exchange, 200, "{\"device\":\"" + escape(device) + "\",\"latest\":null}");
                    return;
                }

                send(exchange, 200, toJson(r));

            } catch (Exception e) {
                send(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    static class HistoryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;

            if (rmiClient == null) {
                send(exchange, 503, "{\"error\":\"RMI not configured\"}");
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                send(exchange, 405, "{\"error\":\"Only GET allowed\"}");
                return;
            }

            try {
                String device = queryParam(exchange, "device");
                String from = queryParam(exchange, "from");
                String to = queryParam(exchange, "to");
                String limitStr = queryParam(exchange, "limit");

                if (device == null || device.isBlank() || from == null || to == null) {
                    send(exchange, 400, "{\"error\":\"Required: device, from, to\"}");
                    return;
                }

                int limit = 200;
                if (limitStr != null && !limitStr.isBlank()) {
                    try { limit = Integer.parseInt(limitStr); } catch (Exception ignored) {}
                }
                final int limitFinal = limit;

                List<ReadingDTO> list = rmiClient.call(svc -> svc.getHistory(device, from, to, limitFinal));

                StringBuilder sb = new StringBuilder();
                sb.append("{\"device\":\"").append(escape(device)).append("\",\"count\":").append(list.size()).append(",\"items\":[");
                for (int i = 0; i < list.size(); i++) {
                    if (i > 0) sb.append(",");
                    sb.append(toJson(list.get(i)));
                }
                sb.append("]}");

                send(exchange, 200, sb.toString());

            } catch (Exception e) {
                send(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    static class ControlModeHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;

            if (rmiClient == null) {
                send(exchange, 503, "{\"error\":\"RMI not configured\"}");
                return;
            }

            try {
                // GET /control/mode?device=esp32-01
                if (exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                    String device = queryParam(exchange, "device");
                    if (device == null || device.isBlank()) {
                        send(exchange, 400, "{\"error\":\"Missing device\"}");
                        return;
                    }

                    ModeDTO mode = rmiClient.call(svc -> svc.getMode(device));
                    send(exchange, 200,
                            "{\"device\":\"" + escape(device) + "\",\"mode\":\"" + mode + "\"}");
                    return;
                }

                // POST /control/mode  body: {"device":"esp32-01","mode":"MANUAL"}
                if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                    send(exchange, 405, "{\"error\":\"Only GET/POST allowed\"}");
                    return;
                }

                String body = readAll(exchange.getRequestBody());
                String device = getJsonString(body, "device");
                String modeStr = getJsonString(body, "mode");

                if (device == null || device.isBlank() || modeStr == null || modeStr.isBlank()) {
                    send(exchange, 400, "{\"error\":\"Expected JSON: {device, mode}\"}");
                    return;
                }

                ModeDTO mode = ModeDTO.valueOf(modeStr.toUpperCase());

                rmiClient.call(svc -> { svc.setMode(device, mode); return null; });

                send(exchange, 200,
                        "{\"status\":\"ok\",\"device\":\"" + escape(device) + "\",\"mode\":\"" + mode + "\"}");

            } catch (IllegalArgumentException badEnum) {
                send(exchange, 400, "{\"error\":\"mode must be AUTO or MANUAL\"}");
            } catch (Exception e) {
                send(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    static class ControlPumpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;

            if (rmiClient == null) {
                send(exchange, 503, "{\"error\":\"RMI not configured\"}");
                return;
            }

            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                send(exchange, 405, "{\"error\":\"Only POST allowed\"}");
                return;
            }

            try {
                // POST /control/pump  body: {"device":"esp32-01","on":true}
                String body = readAll(exchange.getRequestBody());
                String device = getJsonString(body, "device");
                Boolean on = getJsonBool(body, "on");

                if (device == null || device.isBlank() || on == null) {
                    send(exchange, 400, "{\"error\":\"Expected JSON: {device, on:true|false}\"}");
                    return;
                }

                rmiClient.call(svc -> { svc.setManualPump(device, on); return null; });

                send(exchange, 200,
                        "{\"status\":\"ok\",\"device\":\"" + escape(device) + "\",\"on\":" + on + "}");

            } catch (Exception e) {
                send(exchange, 500, "{\"error\":\"" + escape(e.getMessage()) + "\"}");
            }
        }
    }

    static class AlertsSseHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (handleCorsPreflight(exchange)) return;

            if (rmiClient == null) {
                send(exchange, 503, "{\"error\":\"RMI not configured\"}");
                return;
            }
            if (!exchange.getRequestMethod().equalsIgnoreCase("GET")) {
                send(exchange, 405, "{\"error\":\"Only GET allowed\"}");
                return;
            }

            String device = queryParam(exchange, "device");
            if (device == null || device.isBlank()) {
                send(exchange, 400, "{\"error\":\"Missing device\"}");
                return;
            }

            // optional cursor
            String sinceParam = queryParam(exchange, "since");
            String cursorUtc = (sinceParam != null && !sinceParam.isBlank())
                    ? sinceParam
                    : "1970-01-01T00:00:00Z";

            Headers h = exchange.getResponseHeaders();
            addCorsHeaders(exchange);
            h.set("Content-Type", "text/event-stream; charset=utf-8");
            h.set("Cache-Control", "no-cache");
            h.set("Connection", "keep-alive");

            exchange.sendResponseHeaders(200, 0);

            try (OutputStream os = exchange.getResponseBody()) {

                // open stream
                os.write((": connected\n\n").getBytes(StandardCharsets.UTF_8));
                os.flush();

                while (true) {
                    final String cursorSnapshot = cursorUtc; // ✅ effectively final for lambda

                    List<AlertDTO> list = rmiClient.call(svc -> svc.getAlerts(device, cursorSnapshot, 200));

                    String lastSeenUtc = null;

                    for (AlertDTO a : list) {
                        if (a == null) continue;

                        if (a.createdUtc != null) lastSeenUtc = a.createdUtc;

                        String msg = "event: alert\n" +
                                "data: " + toJson(a) + "\n\n";

                        os.write(msg.getBytes(StandardCharsets.UTF_8));
                    }

                    os.flush();

                    // advance cursor safely:
                    // if we got alerts, move to (lastSeenUtc + 1ms) to avoid duplicates with same timestamp
                    if (lastSeenUtc != null) {
                        try {
                            Instant inst = Instant.parse(lastSeenUtc);
                            cursorUtc = inst.plusMillis(1).toString();
                        } catch (Exception ignored) {
                            // if parse fails, just keep lastSeenUtc (still works, maybe duplicates)
                            cursorUtc = lastSeenUtc;
                        }
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

            } catch (Exception e) {
                System.out.println("[SSE] alerts stream closed: " + e.getMessage());
            }
        }
    }

    // ======================================================================
    // TRANSLATION (Gateway model -> Core DTO)
    // ======================================================================

    private static ReadingDTO toDto(SensorReading r) {
        ReadingDTO dto = new ReadingDTO();
        dto.device = r.device;
        dto.soil = r.soil;
        dto.waterTank = r.waterTank;
        dto.raining = r.raining;
        dto.pump = r.pump;
        dto.tempC = r.tempC;
        dto.humidity = r.humidity;
        return dto;
    }

    // ======================================================================
    // JSON BUILDERS
    // ======================================================================

    private static String toJson(ReadingDTO r) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"device\":\"").append(escape(r.device)).append("\"");
        sb.append(",\"soil\":").append(r.soil == null ? "null" : r.soil);
        sb.append(",\"water_tank\":").append(r.waterTank == null ? "null" : r.waterTank);
        sb.append(",\"raining\":").append(r.raining == null ? "null" : r.raining);
        sb.append(",\"pump\":").append(r.pump == null ? "null" : r.pump);
        sb.append(",\"temp_c\":").append(r.tempC == null ? "null" : r.tempC);
        sb.append(",\"humidity\":").append(r.humidity == null ? "null" : r.humidity);
        sb.append(",\"created_utc\":").append(r.createdUtc == null ? "null" : "\"" + escape(r.createdUtc) + "\"");
        sb.append("}");
        return sb.toString();
    }

    private static String toJson(AlertDTO a) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"id\":").append(a.id == null ? "null" : a.id);
        sb.append(",\"device\":\"").append(escape(a.device)).append("\"");
        sb.append(",\"type\":\"").append(escape(a.type)).append("\"");
        sb.append(",\"severity\":\"").append(escape(a.severity)).append("\"");
        sb.append(",\"message\":\"").append(escape(a.message)).append("\"");
        sb.append(",\"created_utc\":").append(a.createdUtc == null ? "null" : "\"" + escape(a.createdUtc) + "\"");
        sb.append("}");
        return sb.toString();
    }

    // ======================================================================
    // PRINTING
    // ======================================================================

    private static void printLikeTerminal(SensorReading r) {
        String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
        String pumpTxt = (r.pump != null && r.pump) ? "ON" : "OFF";

        System.out.println();
        System.out.println("✅  New ESP32 Reading");
        System.out.println("──────────────────────────────");
        System.out.printf("🆔  Device    : %s%n", safe(r.device));
        System.out.printf("🌱  Soil      : %s %%\n", safeInt(r.soil));
        System.out.printf("💧  Water Tank: %s %%\n", safeInt(r.waterTank));
        System.out.printf("🌧️  Raining   : %s%n", r.raining);
        System.out.printf("🔌  Pump(rep) : %s%n", pumpTxt);
        System.out.printf("🌡️  Temp      : %s °C%n", r.tempC);
        System.out.printf("💦  Humidity  : %s %%\n", r.humidity);
        System.out.printf("🕒  Time      : %s%n", time);
        System.out.println("──────────────────────────────");
    }

    private static String safe(String s) { return s != null ? s : "null"; }
    private static String safeInt(Integer n) { return n != null ? n.toString() : "null"; }

    // ======================================================================
    // HTTP HELPERS (+ CORS)
    // ======================================================================

    private static boolean handleCorsPreflight(HttpExchange ex) throws IOException {
        if (ex.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            addCorsHeaders(ex);
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return true;
        }
        return false;
    }

    private static void addCorsHeaders(HttpExchange ex) {
        Headers h = ex.getResponseHeaders();
        h.set("Access-Control-Allow-Origin", "*");
        h.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        h.set("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void send(HttpExchange ex, int code, String text) throws IOException {
        addCorsHeaders(ex);
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String readAll(InputStream is) throws IOException {
        return new String(is.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String queryParam(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getQuery();
        if (q == null) return null;
        for (String part : q.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].equals(key)) {
                return urlDecode(kv[1]);
            }
        }
        return null;
    }

    private static String urlDecode(String s) {
        try {
            return URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    // ======================================================================
    // MINIMAL JSON PARSING (NO LIBS, FIXED SCHEMA)
    // ======================================================================

    private static SensorReading parseSensorReading(String json) {
        SensorReading r = new SensorReading();
        r.device     = getJsonString(json, "device");
        r.soil       = getJsonInt(json, "soil");
        r.waterTank  = getJsonInt(json, "water_tank");
        r.raining    = getJsonBool(json, "raining");
        r.pump       = getJsonBool(json, "pump");
        r.tempC      = getJsonDouble(json, "temp_c");
        r.humidity   = getJsonDouble(json, "humidity");
        return r;
    }

    private static String getJsonString(String json, String key) {
        return getJsonRawValue(json, key);
    }

    private static Integer getJsonInt(String json, String key) {
        String v = getJsonRawValue(json, key);
        if (v == null || v.equals("null")) return null;
        try { return Integer.parseInt(v); } catch (Exception e) { return null; }
    }

    private static Double getJsonDouble(String json, String key) {
        String v = getJsonRawValue(json, key);
        if (v == null || v.equals("null")) return null;
        try { return Double.parseDouble(v); } catch (Exception e) { return null; }
    }

    private static Boolean getJsonBool(String json, String key) {
        String v = getJsonRawValue(json, key);
        if (v == null || v.equals("null")) return null;
        if (v.equalsIgnoreCase("true")) return true;
        if (v.equalsIgnoreCase("false")) return false;
        return null;
    }

    private static String getJsonRawValue(String json, String key) {
        String pattern = "\"" + key + "\"";
        int i = json.indexOf(pattern);
        if (i < 0) return null;

        int colon = json.indexOf(":", i);
        if (colon < 0) return null;

        int start = colon + 1;
        while (start < json.length() && Character.isWhitespace(json.charAt(start))) start++;

        if (start < json.length() && json.charAt(start) == '"') {
            int endQuote = json.indexOf('"', start + 1);
            if (endQuote < 0) return null;
            return json.substring(start + 1, endQuote);
        }

        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}') end++;
        return json.substring(start, end).trim();
    }
}
