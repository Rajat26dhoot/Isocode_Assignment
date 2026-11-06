package com.companya.customs.service.extraction;

import com.companya.customs.entity.Enums;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.util.*;

/** Optional OpenAI vision adapter. The prompt requests JSON only and sends no persistence or training opt-in headers. */
@Service("openAiVisionExtractionService")
@ConditionalOnProperty(name = "app.extraction.provider", havingValue = "openai")
public class OpenAiVisionExtractionService implements VisionExtractionService {
    private final RestClient client; private final ObjectMapper mapper; private final String model;
    public OpenAiVisionExtractionService(@Value("${app.openai.endpoint}") String endpoint, @Value("${app.openai.api-key}") String apiKey, @Value("${app.openai.model}") String model, ObjectMapper mapper) {
        this.client = RestClient.builder().baseUrl(endpoint).defaultHeader("Authorization", "Bearer " + apiKey).defaultHeader("X-OpenAI-Data-Usage-Policy", " Zero Data Retention").build(); this.mapper = mapper; this.model = model;
    }
    @Override public ExtractionResult extract(byte[] content, String fileName, Enums.DocumentType type) {
        String data = Base64.getEncoder().encodeToString(content);
        Map<String,Object> body = Map.of("model", model, "temperature", 0, "response_format", Map.of("type", "json_object"), "messages", List.of(Map.of("role", "user", "content", List.of(Map.of("type", "text", "text", prompt(type)), Map.of("type", "image_url", "image_url", Map.of("url", "data:application/octet-stream;base64," + data))))));
        try {
            Map<?,?> response = client.post().contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
            String json = String.valueOf(((Map<?,?>)((List<?>)response.get("choices")).get(0)).get("message"));
            return new FallbackVisionExtractionService().extract(json.getBytes(), fileName, type);
        } catch (Exception ex) { throw new IllegalStateException("Vision extraction provider failed", ex); }
    }
    private String prompt(Enums.DocumentType type) { return "Extract this " + type + " into key:value text. Include exporter name, importer name, invoice number, invoice date, currency, incoterm, total packages, gross weight kg, net weight kg, container number, invoice total, description, hsn code, quantity, unit price, line total, and confidence values. Return only JSON."; }
}
