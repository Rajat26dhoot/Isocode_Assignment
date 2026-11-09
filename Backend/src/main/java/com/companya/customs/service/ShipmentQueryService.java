package com.companya.customs.service;

import com.companya.customs.dto.ShipmentDtos.*;
import com.companya.customs.entity.*;
import com.companya.customs.exception.ApiExceptions;
import com.companya.customs.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShipmentQueryService {
    private final ShipmentRepository shipments;
    private final ShipmentDocumentRepository documents;
    private final ExtractedFieldRepository fields;
    private final LineItemRepository lineItems;
    private final CrossCheckDiscrepancyRepository discrepancies;

    @Transactional(readOnly = true)
    public List<ShipmentSummary> list(User user) {
        List<Shipment> rows = user.getRole() == Enums.Role.ROLE_MANAGER ? shipments.findAll() : shipments.findByAssignedExecutiveId(user.getId());
        return rows.stream()
                .sorted(Comparator.comparing(Shipment::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(s -> new ShipmentSummary(s.getId(), s.getShipmentCode(), s.getImporterName(), s.getSupplier().getName(), s.getStatus(), s.getUpdatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ShipmentDetail get(Long id) {
        Shipment shipment = shipments.findById(id).orElseThrow(() -> new ApiExceptions.NotFound("Shipment not found: " + id));
        List<DocumentResponse> docs = documents.findByShipmentId(id).stream().sorted(Comparator.comparing(ShipmentDocument::getId)).map(this::document).toList();
        List<DiscrepancyResponse> checks = discrepancies.findByShipmentId(id).stream()
                .map(d -> new DiscrepancyResponse(d.getId(), d.getRuleCode(), d.getSeverity(), d.getInvoiceValue(), d.getPackingListValue(), d.getBolValue(), d.getExpectedOrDelta(), d.getStatus(), d.getResolutionNote()))
                .toList();
        return new ShipmentDetail(shipment.getId(), shipment.getShipmentCode(), shipment.getImporterName(), shipment.getSupplier().getName(), shipment.getStatus(), shipment.getUpdatedAt(), docs, checks, shipment.getRejectionReason());
    }

    @Transactional(readOnly = true)
    public ShipmentDocument document(Long documentId) {
        return documents.findById(documentId).orElseThrow(() -> new ApiExceptions.NotFound("Document not found: " + documentId));
    }

    private DocumentResponse document(ShipmentDocument doc) {
        return new DocumentResponse(
                doc.getId(),
                doc.getDocType(),
                doc.getFileName(),
                doc.getPageCount(),
                doc.isUnreadable(),
                doc.isHumanVerified(),
                doc.getExtractionStatus(),
                fields.findByDocumentId(doc.getId()).stream()
                        .map(f -> new FieldResponse(f.getId(), doc.getDocType().name(), f.getFieldKey(), f.getFieldLabel(), f.getAiExtractedValue(), f.getCurrentValue(), f.getConfidenceScore(), f.isLowConfidence(), f.isHumanVerified(), f.isWasEdited(), f.getPageNumber()))
                        .toList(),
                lineItems.findByDocumentIdOrderByItemNumber(doc.getId()).stream()
                        .map(i -> new LineItemResponse(i.getId(), i.getItemNumber(), i.getDescription(), i.getHsnCode(), i.getQuantity(), i.getUnitPrice(), i.getCurrency(), i.getLineTotal(), i.getGrossWeightKg(), i.getConfidenceScore(), i.isHumanVerified()))
                        .toList()
        );
    }
}
