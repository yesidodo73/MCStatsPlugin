package com.yesidodo.mcstats.plugin;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

public final class McStatsApiClient {
    private final HttpClient client;
    private final URI eventsEndpoint;
    private final URI telemetryEndpoint;
    private final Duration timeout;
    private final String serverId;
    private final String apiKey;
    private final String secret;

    public McStatsApiClient(String baseUrl, int timeoutMs, String serverId, String apiKey, String secret) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        String normalizedBaseUrl = baseUrl.replaceAll("/+$", "");
        this.eventsEndpoint = URI.create(normalizedBaseUrl + "/v1/events/batch");
        this.telemetryEndpoint = URI.create(normalizedBaseUrl + "/v1/telemetry/batch");
        this.timeout = Duration.ofMillis(timeoutMs);
        this.serverId = serverId;
        this.apiKey = apiKey;
        this.secret = secret == null ? "" : secret;
    }

    public boolean sendEventsBatch(List<StatEvent> events) {
        if (events.isEmpty()) {
            return true;
        }

        try {
            return postJson(eventsEndpoint, toEventsJson(events));
        } catch (Exception ignored) {
            return false;
        }
    }

    public boolean sendTelemetryBatch(List<TelemetrySample> samples) {
        if (samples.isEmpty()) {
            return true;
        }

        try {
            return postJson(telemetryEndpoint, toTelemetryJson(samples));
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean postJson(URI endpoint, String body) throws Exception {
        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-MCStats-ServerId", serverId)
                .header("X-MCStats-ApiKey", apiKey)
                .header("X-Idempotency-Key", UUID.randomUUID().toString());

        if (!secret.isBlank()) {
            String ts = String.valueOf(Instant.now().getEpochSecond());
            String signature = sign(ts + "\n" + body, secret);
            reqBuilder.header("X-MCStats-Timestamp", ts);
            reqBuilder.header("X-MCStats-Signature", signature);
        }

        HttpRequest request = reqBuilder
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
        return response.statusCode() >= 200 && response.statusCode() < 300;
    }

    private static String toEventsJson(List<StatEvent> events) {
        StringBuilder sb = new StringBuilder(64 + (events.size() * 90));
        sb.append("{\"events\":[");
        for (int i = 0; i < events.size(); i++) {
            StatEvent e = events.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"uuid\":\"")
                    .append(e.uuid())
                    .append("\",\"metric\":\"")
                    .append(escapeJson(e.metric()))
                    .append("\",\"delta\":")
                    .append(e.delta())
                    .append(",\"timestampUtc\":")
                    .append(e.timestampUtc())
                    .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String toTelemetryJson(List<TelemetrySample> samples) {
        StringBuilder sb = new StringBuilder(80 + (samples.size() * 220));
        sb.append("{\"samples\":[");
        for (int i = 0; i < samples.size(); i++) {
            TelemetrySample s = samples.get(i);
            if (i > 0) {
                sb.append(',');
            }

            sb.append('{')
                    .append("\"serverId\":\"").append(escapeJson(s.serverId())).append('"')
                    .append(",\"timestampUtc\":").append(s.timestampUtc());

            appendNullable(sb, "tps", s.tps());
            appendNullable(sb, "mspt", s.mspt());
            appendNullable(sb, "cpuUsagePercent", s.cpuUsagePercent());
            appendNullable(sb, "ramUsedMb", s.ramUsedMb());
            appendNullable(sb, "ramTotalMb", s.ramTotalMb());
            appendNullable(sb, "networkRxKbps", s.networkRxKbps());
            appendNullable(sb, "networkTxKbps", s.networkTxKbps());
            appendNullable(sb, "diskReadKbps", s.diskReadKbps());
            appendNullable(sb, "diskWriteKbps", s.diskWriteKbps());
            appendNullable(sb, "gcCollectionsPerMinute", s.gcCollectionsPerMinute());
            appendNullable(sb, "threadCount", s.threadCount());
            appendNullable(sb, "onlinePlayers", s.onlinePlayers());
            appendNullable(sb, "pingP50Ms", s.pingP50Ms());
            appendNullable(sb, "pingP95Ms", s.pingP95Ms());
            appendNullable(sb, "pingP99Ms", s.pingP99Ms());

            sb.append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void appendNullable(StringBuilder sb, String key, Number value) {
        if (value != null) {
            sb.append(",\"").append(key).append("\":").append(value);
        }
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signature = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(signature);
    }
}
