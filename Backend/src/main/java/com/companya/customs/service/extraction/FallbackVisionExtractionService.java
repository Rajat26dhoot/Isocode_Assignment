package com.companya.customs.service.extraction;

import com.companya.customs.entity.Enums;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Deterministic parser for text-backed PDFs, OCR sidecars, and key:value fixture uploads. */
@Service("fallbackVisionExtractionService")
public class FallbackVisionExtractionService implements VisionExtractionService {
    private static final Pattern KV = Pattern.compile("(?im)^\\s*([a-z][a-z _-]+)\\s*[:=]\\s*(.+?)\\s*$");
    @Override public ExtractionResult extract(byte[] content, String fileName, Enums.DocumentType type) {
        String text = new String(content, StandardCharsets.UTF_8); Map<String, String> values = new HashMap<>();
        Matcher matcher = KV.matcher(text); while (matcher.find()) values.put(normalize(matcher.group(1)), matcher.group(2).trim());
        List<FieldValue> fields = new ArrayList<>();
        add(fields, Enums.FieldKey.exporter_name, values, "exporter name", "Exporter Name", .90f);
        add(fields, Enums.FieldKey.importer_name, values, "importer name", "Importer Name", .90f);
        add(fields, Enums.FieldKey.invoice_number, values, "invoice number", "Invoice Number", .88f);
        add(fields, Enums.FieldKey.invoice_date, values, "invoice date", "Invoice Date", .88f);
        add(fields, Enums.FieldKey.currency, values, "currency", "Currency", .95f);
        add(fields, Enums.FieldKey.incoterm, values, "incoterm", "Incoterm", .88f);
        add(fields, Enums.FieldKey.total_packages, values, "total packages", "Total Packages", .90f);
        add(fields, Enums.FieldKey.gross_weight_kg, values, "gross weight kg", "Gross Weight (kg)", .90f);
        add(fields, Enums.FieldKey.net_weight_kg, values, "net weight kg", "Net Weight (kg)", .90f);
        add(fields, Enums.FieldKey.container_number, values, "container number", "Container Number", .90f);
        add(fields, Enums.FieldKey.invoice_total, values, "invoice total", "Invoice Total", .90f);
        List<LineValue> lines = new ArrayList<>(); String description = value(values, "description", "item description");
        if (description != null) lines.add(new LineValue(1, description, value(values, "hsn code", "hsn"), decimal(values, "quantity"), decimal(values, "unit price"), value(values, "currency"), decimal(values, "line total"), decimal(values, "gross weight kg"), new BigDecimal("0.90")));
        boolean unreadable = text.isBlank() && content.length < 50;
        return new ExtractionResult(fields, lines, 1, unreadable);
    }
    private void add(List<FieldValue> out, Enums.FieldKey key, Map<String,String> values, String lookup, String label, float confidence) { String value = value(values, lookup); if (value != null) out.add(new FieldValue(key, label, value, BigDecimal.valueOf(confidence), 1)); }
    private String value(Map<String,String> values, String... keys) { for (String key : keys) if (values.containsKey(normalize(key))) return values.get(normalize(key)); return null; }
    private BigDecimal decimal(Map<String,String> values, String key) { String value = value(values, key); if (value == null) return null; try { return new BigDecimal(value.replaceAll("[^0-9.\\-]", "")); } catch (NumberFormatException e) { return null; } }
    private String normalize(String value) { return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
}
