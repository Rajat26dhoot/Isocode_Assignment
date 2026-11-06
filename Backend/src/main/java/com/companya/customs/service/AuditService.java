package com.companya.customs.service;

import com.companya.customs.dto.ShipmentDtos;
import com.companya.customs.entity.*;
import com.companya.customs.exception.ApiExceptions;
import com.companya.customs.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;

@Service @RequiredArgsConstructor
public class AuditService {
    private final ExtractedFieldRepository fields; private final LineItemRepository lineItems; private final FieldAuditLogRepository auditLogs; private final SupplierRepository suppliers; private final ShipmentDocumentRepository documents; private final CrossCheckService crossChecks;
    @Transactional public ShipmentDocument verifyDocument(Long documentId) { ShipmentDocument document = documents.findById(documentId).orElseThrow(() -> new ApiExceptions.NotFound("Document not found: " + documentId)); document.setHumanVerified(true); return documents.save(document); }
    @Transactional public ShipmentDocument markUnreadable(Long documentId) { ShipmentDocument document = documents.findById(documentId).orElseThrow(() -> new ApiExceptions.NotFound("Document not found: " + documentId)); document.setUnreadable(true); document.setHumanVerified(false); document.setExtractionStatus(Enums.ExtractionStatus.FAILED); fields.findByDocumentId(documentId).forEach(field -> { field.setCurrentValue(""); field.setLowConfidence(true); field.setHumanVerified(false); fields.save(field); }); return documents.save(document); }
    @Transactional
    public ExtractedField editField(Long fieldId, ShipmentDtos.FieldEditRequest request, User user) {
        ExtractedField field = fields.findById(fieldId).orElseThrow(() -> new ApiExceptions.NotFound("Extracted field not found: " + fieldId));
        String oldValue = field.getCurrentValue(); String newValue = request.currentValue(); field.setCurrentValue(newValue); field.setHumanVerified(true); field.setVerifiedByUser(user); field.setVerifiedAt(Instant.now());
        if (!java.util.Objects.equals(oldValue, newValue)) {
            field.setWasEdited(!java.util.Objects.equals(field.getAiExtractedValue(), newValue));
            auditLogs.save(FieldAuditLog.builder().shipment(field.getDocument().getShipment()).extractedFieldId(field.getId()).fieldKey(field.getFieldKey()).oldValue(oldValue).newValue(newValue).changedByUser(user).changeReason(request.changeReason()).build());
            if (field.isWasEdited()) { Supplier supplier = field.getDocument().getShipment().getSupplier(); supplier.setTotalFieldsCorrected(supplier.getTotalFieldsCorrected() + 1); suppliers.save(supplier); }
        }
        ExtractedField saved = fields.save(field); crossChecks.runForShipment(field.getDocument().getShipment().getId()); return saved;
    }
    @Transactional
    public LineItem editLineItem(Long lineItemId, ShipmentDtos.LineItemEditRequest request, User user) {
        LineItem item = lineItems.findById(lineItemId).orElseThrow(() -> new ApiExceptions.NotFound("Line item not found: " + lineItemId));
        if (request.description() != null) item.setDescription(request.description()); if (request.hsnCode() != null) item.setHsnCode(request.hsnCode()); if (request.quantity() != null) item.setQuantity(request.quantity()); if (request.unitPrice() != null) item.setUnitPrice(request.unitPrice()); if (request.currency() != null) item.setCurrency(request.currency()); if (request.lineTotal() != null) item.setLineTotal(request.lineTotal()); if (request.grossWeightKg() != null) item.setGrossWeightKg(request.grossWeightKg()); item.setHumanVerified(true);
        LineItem saved = lineItems.save(item); crossChecks.runForShipment(item.getDocument().getShipment().getId()); return saved;
    }
}
