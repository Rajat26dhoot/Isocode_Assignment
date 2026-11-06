package com.companya.customs.service;

import com.companya.customs.entity.*;
import com.companya.customs.exception.ApiExceptions;
import com.companya.customs.repository.*;
import com.companya.customs.service.extraction.VisionExtractionService;
import com.companya.customs.service.extraction.OpenAiVisionExtractionService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class ExtractionService {
    private final ShipmentRepository shipments; private final ShipmentDocumentRepository documents; private final ExtractedFieldRepository fields; private final LineItemRepository lineItems; private final CrossCheckService crossChecks; private final SupplierRepository suppliers; private final VisionExtractionService fallback; private final Optional<OpenAiVisionExtractionService> openAiProvider;
    public ExtractionService(ShipmentRepository shipments, ShipmentDocumentRepository documents, ExtractedFieldRepository fields, LineItemRepository lineItems, CrossCheckService crossChecks, SupplierRepository suppliers, @Qualifier("fallbackVisionExtractionService") VisionExtractionService fallback, Optional<OpenAiVisionExtractionService> openAiProvider) { this.shipments = shipments; this.documents = documents; this.fields = fields; this.lineItems = lineItems; this.crossChecks = crossChecks; this.suppliers = suppliers; this.fallback = fallback; this.openAiProvider = openAiProvider; }
    @Transactional
    public ShipmentDocument extract(Long shipmentId, Enums.DocumentType type, String fileName, byte[] content) {
        Shipment shipment = shipments.findById(shipmentId).orElseThrow(() -> new ApiExceptions.NotFound("Shipment not found: " + shipmentId));
        VisionExtractionService provider = openAiProvider.map(item -> (VisionExtractionService) item).orElse(fallback); shipment.setStatus(Enums.ShipmentStatus.EXTRACTING);
        ShipmentDocument document = documents.findByShipmentIdAndDocType(shipmentId, type).orElseGet(() -> ShipmentDocument.builder().shipment(shipment).docType(type).fileName(fileName).build());
        document.setFileName(fileName); document.setExtractionStatus(Enums.ExtractionStatus.PROCESSING); documents.save(document);
        VisionExtractionService.ExtractionResult result = provider.extract(content, fileName, type);
        fields.deleteAll(fields.findByDocumentId(document.getId())); lineItems.deleteAll(lineItems.findByDocumentIdOrderByItemNumber(document.getId()));
        result.fields().forEach(item -> { fields.save(ExtractedField.builder().document(document).fieldKey(item.key()).fieldLabel(item.label()).aiExtractedValue(item.value()).currentValue(item.value()).confidenceScore(item.confidence()).isLowConfidence(item.confidence().compareTo(new java.math.BigDecimal("0.850")) < 0).pageNumber(item.pageNumber()).build()); Supplier supplier = shipment.getSupplier(); supplier.setTotalFieldsExtracted(supplier.getTotalFieldsExtracted() + 1); suppliers.save(supplier); });
        result.lineItems().forEach(item -> lineItems.save(LineItem.builder().document(document).itemNumber(item.itemNumber()).description(item.description()).hsnCode(item.hsnCode()).quantity(item.quantity()).unitPrice(item.unitPrice()).currency(item.currency()).lineTotal(item.lineTotal()).grossWeightKg(item.grossWeightKg()).confidenceScore(item.confidence()).build()));
        document.setPageCount(result.pageCount()); document.setUnreadable(result.unreadable()); document.setExtractionStatus(Enums.ExtractionStatus.COMPLETED); shipment.setStatus(Enums.ShipmentStatus.NEEDS_REVIEW); documents.save(document); shipments.save(shipment); crossChecks.runForShipment(shipmentId); return document;
    }
}
