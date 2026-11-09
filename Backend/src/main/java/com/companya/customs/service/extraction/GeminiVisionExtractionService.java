package com.companya.customs.service.extraction;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.companya.customs.entity.Enums;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.SocketTimeoutException;
import java.util.*;

/** Gemini vision adapter using the free Gemini Developer API tier. */
@Service("geminiVisionExtractionService")
@ConditionalOnProperty(name = "app.extraction.provider", havingValue = "gemini")
public class GeminiVisionExtractionService implements VisionExtractionService {
    private final RestClient client;
    private final ObjectMapper mapper;
    private final String model;

    public GeminiVisionExtractionService(
            @Value("${app.gemini.endpoint}") String endpoint,
            @Value("${app.gemini.api-key}") String apiKey,
            @Value("${app.gemini.model}") String model,
            @Value("${app.gemini.read-timeout-ms:45000}") int readTimeoutMillis,
            ObjectMapper mapper) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("GEMINI_API_KEY is required when EXTRACTION_PROVIDER=gemini");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(readTimeoutMillis);
        this.client = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(endpoint)
                .defaultHeader("x-goog-api-key", apiKey)
                .defaultHeader("Api-Revision", "2026-05-20")
                .build();
        this.mapper = mapper;
        this.model = model;
    }

    @Override
    public ExtractionResult extract(byte[] content, String fileName, Enums.DocumentType type) {
        String lower = Optional.ofNullable(fileName).orElse("").toLowerCase(Locale.ROOT);
        String mime = mimeType(lower);
        String contentType = mime.equals("application/pdf") ? "document" : "image";
        Map<String, Object> mediaPart = new LinkedHashMap<>();
        mediaPart.put("type", contentType);
        mediaPart.put("mime_type", mime);
        // Gemini's Interactions API accepts resolution for images, but rejects
        // it for document/PDF input with: Unknown parameter 'resolution'.
        if (!mime.equals("application/pdf")) mediaPart.put("resolution", "high");
        mediaPart.put("data", Base64.getEncoder().encodeToString(content));
        Map<String, Object> body = Map.of(
                "model", model,
                "input", List.of(
                        Map.of("type", "text", "text", prompt(type)),
                        mediaPart
                ),
                "response_format", Map.of("type", "text", "mime_type", "application/json"),
                "generation_config", Map.of(
                        "thinking_level", "minimal",
                        "temperature", 0,
                        "max_output_tokens", 4096
                ),
                "store", false
        );

        try {
            String rawResponse = callWithRetry(body);
            if (rawResponse == null || rawResponse.isBlank()) {
                throw new IllegalStateException("Gemini interaction returned an empty response");
            }
            JsonNode response = mapper.readTree(rawResponse);
            if (response == null || response.isNull()) {
                throw new IllegalStateException("Gemini interaction returned an empty JSON response");
            }
            String responseText = responseText(response);
            if (responseText.isBlank()) {
                throw new IllegalStateException("Gemini interaction returned no text output (status: "
                        + response.path("status").asText("unknown") + ")");
            }

            JsonNode root = mapper.readTree(responseText);
            List<FieldValue> fields = new ArrayList<>();
            for (Enums.FieldKey key : Enums.FieldKey.values()) {
                JsonNode value = root.path(key.name());
                if (!value.isMissingNode() && !value.isNull() && !value.asText().isBlank()) {
                    JsonNode confidence = root.path("confidence").path(key.name());
                    BigDecimal score = confidence.isNumber()
                            ? confidence.decimalValue().max(BigDecimal.ZERO).min(BigDecimal.ONE)
                            : BigDecimal.ZERO;
                    fields.add(new FieldValue(key, label(key), value.asText(), score, 1));
                }
            }

            List<LineValue> lines = new ArrayList<>();
            JsonNode items = root.path("line_items");
            if (items.isArray()) {
                for (int i = 0; i < items.size(); i++) {
                    JsonNode item = items.get(i);
                    lines.add(new LineValue(
                            i + 1,
                            item.path("description").asText(""),
                            item.path("hsn_code").asText(""),
                            decimal(item.path("quantity")),
                            decimal(item.path("unit_price")),
                            item.path("currency").asText(""),
                            decimal(item.path("line_total")),
                            decimal(item.path("gross_weight_kg")),
                            decimal(item.path("confidence"))
                    ));
                }
            }
            return new ExtractionResult(fields, lines, pageCount(content, lower), fields.isEmpty() && lines.isEmpty());
        } catch (Exception ex) {
            throw new IllegalStateException("Gemini vision extraction failed", ex);
        }
    }

    private String callWithRetry(Map<String, Object> body) {
        for (int attempt = 0; ; attempt++) {
            try {
                return client.post()
                        .uri("/v1beta/interactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        // Some Gemini-compatible endpoints return valid JSON with
                        // application/octet-stream. Read text first so the response
                        // is not rejected by Map message conversion before parsing.
                        .body(String.class);
            } catch (RestClientException ex) {
                if (attempt >= 2 || !isRetryable(ex)) throw ex;
                pause(attempt);
            }
        }
    }

    private String responseText(JsonNode response) {
        StringJoiner text = new StringJoiner("\n");
        JsonNode steps = response.path("steps");
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                if (!step.path("type").asText().equals("model_output")) continue;
                JsonNode content = step.path("content");
                if (!content.isArray()) continue;
                for (JsonNode part : content) {
                    if (part.path("type").asText().equals("text") && !part.path("text").asText().isBlank()) {
                        text.add(part.path("text").asText());
                    }
                }
            }
        }
        return text.toString();
    }

    private boolean isRetryable(RestClientException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            // A slow model response is unlikely to improve by replaying the same
            // large image request; let the local fallback handle it immediately.
            if (cause instanceof SocketTimeoutException) return false;
            if (cause instanceof HttpStatusCodeException status
                    && status.getStatusCode().is5xxServerError()) {
                return true;
            }
            if (cause instanceof IOException) return true;
        }
        return false;
    }

    private void pause(int attempt) {
        try {
            Thread.sleep(250L << attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Gemini extraction retry interrupted", ex);
        }
    }

    private String mimeType(String fileName) {
        if (fileName.endsWith(".pdf")) return "application/pdf";
        if (fileName.endsWith(".png")) return "image/png";
        return "image/jpeg";
    }

    private BigDecimal decimal(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull() || value.asText().isBlank()) return null;
        try {
            return value.decimalValue();
        } catch (Exception ignored) {
            try {
                String normalized = value.asText().replaceAll("[^0-9.\\-]", "");
                return normalized.isBlank() ? null : new BigDecimal(normalized);
            } catch (Exception ignoredAgain) {
                return null;
            }
        }
    }

    private int pageCount(byte[] content, String fileName) {
        if (!fileName.endsWith(".pdf")) return 1;
        try (PDDocument pdf = Loader.loadPDF(content)) {
            return Math.max(1, pdf.getNumberOfPages());
        } catch (Exception ignored) {
            return 1;
        }
    }

    private String label(Enums.FieldKey key) {
        String words = key.name().replace('_', ' ');
        return Arrays.stream(words.split(" "))
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private String prompt(Enums.DocumentType type) {
        return "Read this " + type + " document carefully and extract only text that is visible. "
                + "Return JSON only, never guess. Root keys may include: exporter_name, importer_name, "
                + "invoice_number, invoice_date, bl_number, port_of_loading, port_of_discharge, currency, "
                + "incoterm, total_packages, gross_weight_kg, net_weight_kg, container_number, invoice_total. "
                + "Also return confidence as an object mapping every returned field key to a number from 0 to 1. "
                + "Return line_items as an array with description, hsn_code, quantity, unit_price, currency, "
                + "line_total, gross_weight_kg, and confidence as a number from 0 to 1. "
                + "Use empty strings or omit values that are not legible.";
    }
}
