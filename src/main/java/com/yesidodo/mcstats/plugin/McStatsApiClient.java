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

public final class McStatsApiClient {
    private final HttpClient client;
    private final URI endpoint;
    private final Duration timeout;
    private final String secret;

    public McStatsApiClient(String baseUrl, int timeoutMs, String secret) {
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/v1/events/batch");
        this.timeout = Duration.ofMillis(timeoutMs);
        this.secret = secret == null ? "" : secret;
    }

    public boolean sendBatch(List<StatEvent> events) {
        if (events.isEmpty()) {
            return true;
        }

        try {
            String body = toJson(events);
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Content-Type", "application/json");

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
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String toJson(List<StatEvent> events) {
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
