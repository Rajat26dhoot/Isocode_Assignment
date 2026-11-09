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
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

/** Optional OpenAI vision adapter. Responses are requested without server-side storage. */
@Service("openAiVisionExtractionService")
@ConditionalOnProperty(name = "app.extraction.provider", havingValue = "openai")
public class OpenAiVisionExtractionService implements VisionExtractionService {
    private final RestClient client; private final ObjectMapper mapper; private final String model;
    public OpenAiVisionExtractionService(@Value("${app.openai.endpoint}") String endpoint, @Value("${app.openai.api-key}") String apiKey, @Value("${app.openai.model}") String model, ObjectMapper mapper) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalStateException("OPENAI_API_KEY is required when EXTRACTION_PROVIDER=openai");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory(); factory.setConnectTimeout(10_000); factory.setReadTimeout(120_000);
        this.client = RestClient.builder().requestFactory(factory).baseUrl(endpoint).defaultHeader("Authorization", "Bearer " + apiKey).build(); this.mapper = mapper; this.model = model;
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
            Map<?,?> response = callWithRetry(body);
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
                    BigDecimal score = confidence.isNumber() ? confidence.decimalValue().max(BigDecimal.ZERO).min(BigDecimal.ONE) : BigDecimal.ZERO;
                    fields.add(new FieldValue(key, label, node.asText(), score, 1));
                }
            }
            List<LineValue> lines = new ArrayList<>();
            JsonNode items = root.path("line_items");
            if (items.isArray()) for (int i = 0; i < items.size(); i++) {
                JsonNode item = items.get(i);
                JsonNode confidence = item.path("confidence");
                BigDecimal score = confidence.isNumber() ? confidence.decimalValue().max(BigDecimal.ZERO).min(BigDecimal.ONE) : BigDecimal.ZERO;
                lines.add(new LineValue(i + 1, item.path("description").asText(""), item.path("hsn_code").asText(""), decimal(item.path("quantity")), decimal(item.path("unit_price")), item.path("currency").asText(""), decimal(item.path("line_total")), decimal(item.path("gross_weight_kg")), score));
            }
            boolean unreadable = fields.isEmpty() && lines.isEmpty();
            return new ExtractionResult(fields, lines, pageCount(content, lower), unreadable);
        } catch (Exception ex) { throw new IllegalStateException("Vision extraction provider failed", ex); }
    }
    private Map<?,?> callWithRetry(Map<String,Object> body) {
        for (int attempt = 0; ; attempt++) {
            try { return client.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class); }
            catch (HttpStatusCodeException ex) { if (attempt >= 2 || (ex.getStatusCode().value() != 429 && ex.getStatusCode().value() < 500)) throw ex; pause(attempt); }
            catch (ResourceAccessException ex) { if (attempt >= 2) throw ex; pause(attempt); }
        }
    }
    private void pause(int attempt) { try { Thread.sleep(250L << attempt); } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException("Extraction retry interrupted", ex); } }
    private BigDecimal decimal(JsonNode value) { if (value == null || value.isMissingNode() || value.isNull() || value.asText().isBlank()) return null; try { return value.decimalValue(); } catch (Exception ignored) { try { String normalized = value.asText().replaceAll("[^0-9.\\-]", ""); return normalized.isBlank() ? null : new BigDecimal(normalized); } catch (Exception e) { return null; } } }
    private int pageCount(byte[] content, String fileName) { if (!fileName.endsWith(".pdf")) return 1; try (PDDocument pdf = Loader.loadPDF(content)) { return Math.max(1, pdf.getNumberOfPages()); } catch (Exception ignored) { return 1; } }
    private String label(Enums.FieldKey key) { String words = key.name().replace('_', ' '); return Arrays.stream(words.split(" ")).map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1)).collect(java.util.stream.Collectors.joining(" ")); }
    private String prompt(Enums.DocumentType type) { return "Read this " + type + " carefully. Return a JSON object only. Include these root keys where visible: exporter_name, importer_name, invoice_number, invoice_date, bl_number, port_of_loading, port_of_discharge, currency, incoterm, total_packages, gross_weight_kg, net_weight_kg, container_number, invoice_total. Include confidence as an object mapping each returned field key to a number from 0 to 1. Include line_items as an array of objects with description, hsn_code exactly as printed, quantity, unit_price, currency, line_total, gross_weight_kg, and a confidence object with a score for every returned value. Use empty string or omit fields that are not legible; never guess."; }
}
