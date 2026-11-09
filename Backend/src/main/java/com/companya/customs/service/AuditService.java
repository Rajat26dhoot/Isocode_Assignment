package com.companya.customs.service;

import com.companya.customs.dto.ShipmentDtos;
import com.companya.customs.entity.*;
import com.companya.customs.exception.ApiExceptions;
import com.companya.customs.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;

@Service @RequiredArgsConstructor
public class AuditService {
    private final ExtractedFieldRepository fields; private final LineItemRepository lineItems; private final FieldAuditLogRepository auditLogs; private final SupplierRepository suppliers; private final ShipmentDocumentRepository documents; private final CrossCheckService crossChecks;
    @Transactional public ShipmentDocument verifyDocument(Long documentId, User user) {
        ShipmentDocument document = documents.findById(documentId).orElseThrow(() -> new ApiExceptions.NotFound("Document not found: " + documentId));
        List<Enums.FieldKey> required = switch (document.getDocType()) {
            case INVOICE -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name, Enums.FieldKey.invoice_number, Enums.FieldKey.invoice_date, Enums.FieldKey.currency, Enums.FieldKey.invoice_total);
            case PACKING_LIST -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg);
            case BILL_OF_LADING -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name, Enums.FieldKey.bl_number, Enums.FieldKey.port_of_loading, Enums.FieldKey.port_of_discharge, Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg, Enums.FieldKey.container_number);
        };
        for (Enums.FieldKey key : required) if (fields.findByDocumentIdAndFieldKey(documentId, key).map(ExtractedField::getCurrentValue).filter(v -> !v.isBlank()).isEmpty()) throw new com.companya.customs.exception.ApiExceptions.Validation("Required field " + key + " must be entered before verifying this document");
        document.setHumanVerified(true); logAction(document.getShipment(), document.getId(), document.getDocType().name(), "DOCUMENT_VERIFIED", "false", "true", user, "Document manually verified"); return documents.save(document);
    }
    @Transactional public ShipmentDocument markUnreadable(Long documentId, User user) { ShipmentDocument document = documents.findById(documentId).orElseThrow(() -> new ApiExceptions.NotFound("Document not found: " + documentId)); document.setUnreadable(true); document.setHumanVerified(false); document.setExtractionStatus(Enums.ExtractionStatus.FAILED); fields.findByDocumentId(documentId).forEach(field -> { field.setLowConfidence(true); field.setHumanVerified(false); fields.save(field); }); logAction(document.getShipment(), document.getId(), document.getDocType().name(), "DOCUMENT_MARKED_UNREADABLE", "false", "true", user, "Document marked unreadable for manual entry"); return documents.save(document); }

    public void logAction(Shipment shipment, Long documentId, String fieldName, String action, String oldValue, String newValue, User user, String reason) { auditLogs.save(FieldAuditLog.builder().shipment(shipment).documentId(documentId).fieldName(fieldName).actionType(action).oldValue(oldValue).newValue(newValue).changedByUser(user).changeReason(reason).build()); }
    @Transactional
    public ExtractedField editField(Long fieldId, ShipmentDtos.FieldEditRequest request, User user) {
        ExtractedField field = fields.findById(fieldId).orElseThrow(() -> new ApiExceptions.NotFound("Extracted field not found: " + fieldId));
        if (List.of(Enums.ShipmentStatus.PENDING_APPROVAL, Enums.ShipmentStatus.APPROVED, Enums.ShipmentStatus.EXPORTED).contains(field.getDocument().getShipment().getStatus())) throw new com.companya.customs.exception.ApiExceptions.Validation("This shipment cannot be edited in its current status");
        String oldValue = field.getCurrentValue(); String newValue = request.currentValue(); field.setCurrentValue(newValue); field.setHumanVerified(true); field.setVerifiedByUser(user); field.setVerifiedAt(Instant.now());
        boolean changed = !java.util.Objects.equals(oldValue, newValue);
        if (changed) {
            field.setWasEdited(!java.util.Objects.equals(field.getAiExtractedValue(), newValue));
            if (field.isWasEdited()) { Supplier supplier = field.getDocument().getShipment().getSupplier(); supplier.setTotalFieldsCorrected(supplier.getTotalFieldsCorrected() + 1); suppliers.save(supplier); }
        }
        auditLogs.save(FieldAuditLog.builder().shipment(field.getDocument().getShipment()).documentId(field.getDocument().getId()).extractedFieldId(field.getId()).fieldKey(field.getFieldKey()).fieldName(field.getFieldLabel()).actionType(changed ? "MANUAL_CORRECTION" : "FIELD_CONFIRMED").oldValue(oldValue).newValue(newValue).changedByUser(user).changeReason(request.changeReason()).build());
        ExtractedField saved = fields.save(field); crossChecks.runForShipment(field.getDocument().getShipment().getId()); return saved;
    }
    @Transactional
    public LineItem editLineItem(Long lineItemId, ShipmentDtos.LineItemEditRequest request, User user) {
        LineItem item = lineItems.findById(lineItemId).orElseThrow(() -> new ApiExceptions.NotFound("Line item not found: " + lineItemId));
        if (List.of(Enums.ShipmentStatus.PENDING_APPROVAL, Enums.ShipmentStatus.APPROVED, Enums.ShipmentStatus.EXPORTED).contains(item.getDocument().getShipment().getStatus())) throw new com.companya.customs.exception.ApiExceptions.Validation("This shipment cannot be edited in its current status");
        String old = lineSnapshot(item);
        if (request.description() != null) item.setDescription(request.description()); if (request.hsnCode() != null) item.setHsnCode(request.hsnCode()); if (request.quantity() != null) item.setQuantity(request.quantity()); if (request.unitPrice() != null) item.setUnitPrice(request.unitPrice()); if (request.currency() != null) item.setCurrency(normalizeCurrency(request.currency(), item.getDocument())); if (request.lineTotal() != null) item.setLineTotal(request.lineTotal()); if (request.grossWeightKg() != null) item.setGrossWeightKg(request.grossWeightKg()); item.setHumanVerified(true);
        logAction(item.getDocument().getShipment(), item.getDocument().getId(), "Invoice line item " + item.getItemNumber(), old.equals(lineSnapshot(item)) ? "LINE_ITEM_CONFIRMED" : "MANUAL_CORRECTION", old, lineSnapshot(item), user, "Manual line item review");
        LineItem saved = lineItems.save(item); crossChecks.runForShipment(item.getDocument().getShipment().getId()); return saved;
    }

    private String lineSnapshot(LineItem item) { return String.join(" | ", java.util.Objects.toString(item.getDescription(), ""), java.util.Objects.toString(item.getHsnCode(), ""), java.util.Objects.toString(item.getQuantity(), ""), java.util.Objects.toString(item.getUnitPrice(), ""), java.util.Objects.toString(item.getCurrency(), ""), java.util.Objects.toString(item.getLineTotal(), "")); }
    private String normalizeCurrency(String raw, ShipmentDocument document) {
        String normalized = raw.trim().toUpperCase(java.util.Locale.ROOT);
        if (normalized.matches("[A-Z]{3}")) return normalized;
        String declared = fields.findByDocumentIdAndFieldKey(document.getId(), Enums.FieldKey.currency)
                .map(ExtractedField::getCurrentValue)
                .map(value -> value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT))
                .orElse("");
        if (declared.matches("[A-Z]{3}")) return declared;
        return switch (normalized) {
            case "$", "US$" -> "USD";
            case "€" -> "EUR";
            case "£" -> "GBP";
            case "¥", "￥" -> "JPY";
            default -> throw new com.companya.customs.exception.ApiExceptions.Validation("Currency must be a three-letter code");
        };
    }
}
