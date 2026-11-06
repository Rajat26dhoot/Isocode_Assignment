package com.companya.customs.service.extraction;

import com.companya.customs.entity.Enums;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.util.*;

/** Optional OpenAI vision adapter. Responses are requested without server-side storage. */
@Service("openAiVisionExtractionService")
@ConditionalOnProperty(name = "app.extraction.provider", havingValue = "openai")
public class OpenAiVisionExtractionService implements VisionExtractionService {
    private final RestClient client; private final ObjectMapper mapper; private final String model;
    public OpenAiVisionExtractionService(@Value("${app.openai.endpoint}") String endpoint, @Value("${app.openai.api-key}") String apiKey, @Value("${app.openai.model}") String model, ObjectMapper mapper) {
        this.client = RestClient.builder().baseUrl(endpoint).defaultHeader("Authorization", "Bearer " + apiKey).build(); this.mapper = mapper; this.model = model;
    }
    @Override public ExtractionResult extract(byte[] content, String fileName, Enums.DocumentType type) {
        String data = Base64.getEncoder().encodeToString(content);
        String lower = Optional.ofNullable(fileName).orElse("").toLowerCase(Locale.ROOT);
        String mime = lower.endsWith(".pdf") ? "application/pdf" : lower.endsWith(".png") ? "image/png" : lower.endsWith(".webp") ? "image/webp" : lower.endsWith(".txt") ? "text/plain" : "image/jpeg";
        Map<String, Object> filePart = mime.equals("text/plain")
                ? Map.of("type", "input_text", "text", new String(content, java.nio.charset.StandardCharsets.UTF_8))
                : mime.equals("application/pdf")
                ? Map.of("type", "input_file", "filename", Optional.ofNullable(fileName).orElse("document.pdf"), "file_data", "data:" + mime + ";base64," + data, "detail", "high")
                : Map.of("type", "input_image", "image_url", "data:" + mime + ";base64," + data, "detail", "high");
        Map<String,Object> body = Map.of("model", model, "store", false, "input", List.of(Map.of("role", "user", "content", List.of(Map.of("type", "input_text", "text", prompt(type)), filePart))), "text", Map.of("format", Map.of("type", "json_object")));
        try {
            Map<?,?> response = client.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
            JsonNode outputs = mapper.valueToTree(response).path("output");
            String responseText = null;
            for (JsonNode output : outputs) for (JsonNode part : output.path("content")) if (part.path("type").asText().equals("output_text")) responseText = part.path("text").asText();
            if (responseText == null || responseText.isBlank()) throw new IllegalStateException("Vision provider returned no text content");
            JsonNode root = mapper.readTree(responseText);
            List<FieldValue> fields = new ArrayList<>();
            for (Enums.FieldKey key : Enums.FieldKey.values()) {
                JsonNode node = root.path(key.name());
                if (!node.isMissingNode() && !node.isNull() && !node.asText().isBlank()) {
                    String label = label(key);
                    JsonNode confidence = root.path("confidence").path(key.name());
                    BigDecimal score = confidence.isNumber() ? confidence.decimalValue() : new BigDecimal("0.75");
                    fields.add(new FieldValue(key, label, node.asText(), score, 1));
                }
            }
            List<LineValue> lines = new ArrayList<>();
            JsonNode items = root.path("line_items");
            if (items.isArray()) for (int i = 0; i < items.size(); i++) {
                JsonNode item = items.get(i);
                lines.add(new LineValue(i + 1, item.path("description").asText(""), item.path("hsn_code").asText(""), decimal(item.path("quantity")), decimal(item.path("unit_price")), item.path("currency").asText(""), decimal(item.path("line_total")), decimal(item.path("gross_weight_kg")), new BigDecimal("0.75")));
            }
            boolean unreadable = fields.isEmpty() && lines.isEmpty();
            return new ExtractionResult(fields, lines, 1, unreadable);
        } catch (Exception ex) { throw new IllegalStateException("Vision extraction provider failed", ex); }
    }
    private BigDecimal decimal(JsonNode value) { if (value == null || value.isNull() || value.asText().isBlank()) return BigDecimal.ZERO; try { return value.decimalValue(); } catch (Exception ignored) { try { return new BigDecimal(value.asText().replaceAll("[^0-9.\\-]", "")); } catch (Exception e) { return BigDecimal.ZERO; } } }
    private String label(Enums.FieldKey key) { String words = key.name().replace('_', ' '); return Arrays.stream(words.split(" ")).map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1)).collect(java.util.stream.Collectors.joining(" ")); }
    private String prompt(Enums.DocumentType type) { return "Read this " + type + " carefully. Return a JSON object only. Include these root keys where visible: exporter_name, importer_name, invoice_number, invoice_date, currency, incoterm, total_packages, gross_weight_kg, net_weight_kg, container_number, invoice_total. Include confidence as an object mapping each returned field key to a number from 0 to 1. Include line_items as an array of objects with description, hsn_code, quantity, unit_price, currency, line_total, gross_weight_kg. Use empty string or omit fields that are not legible; never guess."; }
}
