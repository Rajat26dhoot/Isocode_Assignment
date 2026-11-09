package com.companya.customs.service;

import com.companya.customs.entity.*;
import com.companya.customs.exception.ApiExceptions;
import com.companya.customs.repository.*;
import com.companya.customs.service.extraction.VisionExtractionService;
import com.companya.customs.service.extraction.OpenAiVisionExtractionService;
import com.companya.customs.service.extraction.GeminiVisionExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ExtractionService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExtractionService.class);
    @Value("${app.extraction.confidence-threshold:0.85}") private java.math.BigDecimal confidenceThreshold;
    @Value("${app.extraction.provider:fallback}") private String providerName;
    private final ShipmentRepository shipments; private final ShipmentDocumentRepository documents; private final ExtractedFieldRepository fields; private final LineItemRepository lineItems; private final CrossCheckService crossChecks; private final SupplierRepository suppliers; private final VisionExtractionService fallback; private final Optional<OpenAiVisionExtractionService> openAiProvider; private final Optional<GeminiVisionExtractionService> geminiProvider; private final FieldAuditLogRepository auditLogs;
    public ExtractionService(ShipmentRepository shipments, ShipmentDocumentRepository documents, ExtractedFieldRepository fields, LineItemRepository lineItems, CrossCheckService crossChecks, SupplierRepository suppliers, @Qualifier("fallbackVisionExtractionService") VisionExtractionService fallback, Optional<OpenAiVisionExtractionService> openAiProvider, Optional<GeminiVisionExtractionService> geminiProvider, FieldAuditLogRepository auditLogs) { this.shipments = shipments; this.documents = documents; this.fields = fields; this.lineItems = lineItems; this.crossChecks = crossChecks; this.suppliers = suppliers; this.fallback = fallback; this.openAiProvider = openAiProvider; this.geminiProvider = geminiProvider; this.auditLogs = auditLogs; }
    @Transactional
    public ShipmentDocument extract(Long shipmentId, Enums.DocumentType type, String fileName, byte[] content, User user) {
        return extract(shipmentId, type, fileName, content, user, false);
    }

    @Transactional
    public ShipmentDocument retry(Long documentId, User user) {
        ShipmentDocument previous = documents.findById(documentId).orElseThrow(() -> new ApiExceptions.NotFound("Document not found: " + documentId));
        if (previous.getExtractionStatus() != Enums.ExtractionStatus.FAILED || previous.getFileContent() == null || previous.getFileContent().length == 0) throw new ApiExceptions.Validation("Only failed extractions with stored source files can be retried");
        return extract(previous.getShipment().getId(), previous.getDocType(), previous.getFileName(), previous.getFileContent(), user, true);
    }

    private ShipmentDocument extract(Long shipmentId, Enums.DocumentType type, String fileName, byte[] content, User user, boolean retry) {
        Shipment shipment = shipments.findById(shipmentId).orElseThrow(() -> new ApiExceptions.NotFound("Shipment not found: " + shipmentId));
        VisionExtractionService provider = configuredProvider(); shipment.setStatus(Enums.ShipmentStatus.EXTRACTING);
        if (content == null || content.length == 0 || content.length > 25 * 1024 * 1024) throw new ApiExceptions.Validation("Document must be non-empty and no larger than 25 MB");
        String lowerName = Objects.requireNonNullElse(fileName, "").toLowerCase(Locale.ROOT);
        if (!(lowerName.endsWith(".pdf") || lowerName.endsWith(".jpg") || lowerName.endsWith(".jpeg") || lowerName.endsWith(".png"))) throw new ApiExceptions.Validation("Only PDF, JPG, and PNG documents are supported");
        boolean signatureValid = lowerName.endsWith(".pdf") ? content.length >= 5 && new String(content, 0, 5, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-")
                : lowerName.endsWith(".png") ? content.length >= 8 && (content[0] & 255) == 137 && content[1] == 80 && content[2] == 78 && content[3] == 71
                : content.length >= 3 && (content[0] & 255) == 255 && (content[1] & 255) == 216 && (content[2] & 255) == 255;
        if (!signatureValid) throw new ApiExceptions.Validation("The file extension does not match a readable PDF, JPG, or PNG document");
        ShipmentDocument duplicate = retry ? null : documents.findByShipmentId(shipmentId).stream().filter(d -> d.getDocType() == type && d.getFileContent() != null && java.security.MessageDigest.isEqual(hash(d.getFileContent()), hash(content))).findFirst().orElse(null);
        if (duplicate != null) return duplicate;
        fileName = safeFileName(fileName);
        ShipmentDocument document = ShipmentDocument.builder().shipment(shipment).docType(type).fileName(fileName).build();
        document.setFileName(fileName); document.setExtractionStatus(Enums.ExtractionStatus.PROCESSING); documents.save(document);
        document.setFileContent(content);
        document.setContentType(contentType(fileName));
        record(shipment, document, user, "DOCUMENT_UPLOAD", "Document upload stored");
        record(shipment, document, user, "EXTRACTION_ATTEMPT", "Extraction attempted using configured provider");
        VisionExtractionService.ExtractionResult result;
        boolean providerFallbackUsed = false;
        try {
            result = provider.extract(content, fileName, type);
        } catch (RuntimeException providerFailure) {
            if (provider != fallback) {
                providerFallbackUsed = true;
                log.warn("Extraction provider '{}' failed for shipment {} and document type {}; using local extraction fallback. Cause: {}",
                        providerName, shipmentId, type, rootMessage(providerFailure));
                record(shipment, document, user, "EXTRACTION_PROVIDER_FALLBACK",
                        "Configured provider was unavailable; local extraction fallback used and manual review is required");
                try {
                    result = fallback.extract(content, fileName, type);
                } catch (RuntimeException fallbackFailure) {
                    log.error("Local extraction fallback also failed for shipment {} and document type {}",
                            shipmentId, type, fallbackFailure);
                    result = new VisionExtractionService.ExtractionResult(List.of(), List.of(), 1, true);
                    document.setExtractionStatus(Enums.ExtractionStatus.FAILED);
                    record(shipment, document, user, "EXTRACTION_FAILED",
                            "Provider and local fallback failed; manual review is required");
                }
            } else {
                log.error("Extraction provider '{}' failed for shipment {} and document type {}",
                        providerName, shipmentId, type, providerFailure);
                result = new VisionExtractionService.ExtractionResult(List.of(), List.of(), 1, true);
                document.setExtractionStatus(Enums.ExtractionStatus.FAILED);
                record(shipment, document, user, "EXTRACTION_FAILED", "Provider failed; manual review is required");
            }
        }
        List<VisionExtractionService.FieldValue> extractedFields = result.fields();
        if (result.unreadable() && extractedFields.isEmpty()) extractedFields = manualFields(type);
        Set<Enums.FieldKey> seen = new HashSet<>();
        extractedFields.forEach(item -> { seen.add(item.key()); java.math.BigDecimal confidence = item.confidence() == null ? java.math.BigDecimal.ZERO : item.confidence().max(java.math.BigDecimal.ZERO).min(java.math.BigDecimal.ONE); fields.save(ExtractedField.builder().document(document).fieldKey(item.key()).fieldLabel(item.label()).aiExtractedValue(item.value()).currentValue(item.value()).confidenceScore(confidence).isLowConfidence(confidence.compareTo(confidenceThreshold) < 0).pageNumber(item.pageNumber()).build()); if (!item.value().isBlank()) { Supplier supplier = shipment.getSupplier(); supplier.setTotalFieldsExtracted(supplier.getTotalFieldsExtracted() + 1); suppliers.save(supplier); } });
        for (Enums.FieldKey key : requiredKeys(type)) if (!seen.contains(key)) fields.save(ExtractedField.builder().document(document).fieldKey(key).fieldLabel(label(key)).aiExtractedValue("").currentValue("").confidenceScore(java.math.BigDecimal.ZERO).isLowConfidence(true).pageNumber(1).build());
        result.lineItems().forEach(item -> lineItems.save(LineItem.builder().document(document).itemNumber(item.itemNumber()).description(item.description()).hsnCode(item.hsnCode()).quantity(item.quantity()).unitPrice(item.unitPrice()).currency(item.currency()).lineTotal(item.lineTotal()).grossWeightKg(item.grossWeightKg()).confidenceScore(item.confidence()).build()));
        document.setPageCount(result.pageCount()); document.setUnreadable(result.unreadable()); if (document.getExtractionStatus() != Enums.ExtractionStatus.FAILED) document.setExtractionStatus(Enums.ExtractionStatus.COMPLETED); shipment.setStatus(providerFallbackUsed || result.unreadable() ? Enums.ShipmentStatus.NEEDS_REVIEW : Enums.ShipmentStatus.EXTRACTING); documents.save(document); shipments.save(shipment); crossChecks.runForShipment(shipmentId); return document;
    }

    private String rootMessage(Throwable failure) {
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        String message = root.getMessage();
        return message == null || message.isBlank() ? root.getClass().getSimpleName() : message;
    }

    private VisionExtractionService configuredProvider() {
        return switch (providerName.toLowerCase(Locale.ROOT)) {
            case "openai" -> openAiProvider.orElseThrow(() -> new IllegalStateException("OpenAI extraction provider is not configured"));
            case "gemini" -> geminiProvider.orElseThrow(() -> new IllegalStateException("Gemini extraction provider is not configured"));
            default -> fallback;
        };
    }

    private String contentType(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
    private byte[] hash(byte[] bytes) { try { return java.security.MessageDigest.getInstance("SHA-256").digest(bytes); } catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); } }
    private String safeFileName(String fileName) { String safe = Objects.requireNonNullElse(fileName, "document").replaceAll("[\\r\\n\\\\/\\\\\\\\\\\"]", "_"); return safe.length() > 255 ? safe.substring(safe.length() - 255) : safe; }
    private void record(Shipment shipment, ShipmentDocument document, User user, String action, String reason) { auditLogs.save(FieldAuditLog.builder().shipment(shipment).documentId(document.getId()).fieldName(document.getDocType().name()).actionType(action).changedByUser(user).changeReason(reason).build()); }

    private List<VisionExtractionService.FieldValue> manualFields(Enums.DocumentType type) {
        List<Enums.FieldKey> keys = switch (type) {
            case INVOICE -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name, Enums.FieldKey.invoice_number, Enums.FieldKey.invoice_date, Enums.FieldKey.currency, Enums.FieldKey.incoterm, Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg, Enums.FieldKey.invoice_total);
            case PACKING_LIST -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name, Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg, Enums.FieldKey.net_weight_kg);
            case BILL_OF_LADING -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name, Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg, Enums.FieldKey.container_number);
        };
        return keys.stream().map(key -> new VisionExtractionService.FieldValue(key, label(key), "", java.math.BigDecimal.ZERO, 1)).toList();
    }

    private List<Enums.FieldKey> requiredKeys(Enums.DocumentType type) { return switch (type) {
        case INVOICE -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name, Enums.FieldKey.invoice_number, Enums.FieldKey.invoice_date, Enums.FieldKey.currency, Enums.FieldKey.invoice_total);
        case PACKING_LIST -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg);
        case BILL_OF_LADING -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name, Enums.FieldKey.bl_number, Enums.FieldKey.port_of_loading, Enums.FieldKey.port_of_discharge, Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg, Enums.FieldKey.container_number);
    }; }

    private String label(Enums.FieldKey key) {
        return switch (key) {
            case exporter_name -> "Exporter Name"; case importer_name -> "Importer Name";
            case invoice_number -> "Invoice Number"; case invoice_date -> "Invoice Date";
            case bl_number -> "B/L Number"; case port_of_loading -> "Port of Loading"; case port_of_discharge -> "Port of Discharge";
            case currency -> "Currency"; case incoterm -> "Incoterm"; case total_packages -> "Total Packages";
            case gross_weight_kg -> "Gross Weight (kg)"; case net_weight_kg -> "Net Weight (kg)";
            case container_number -> "Container Number"; case invoice_total -> "Invoice Total";
        };
    }
}
