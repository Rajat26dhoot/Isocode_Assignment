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
        document.setFileContent(content);
        document.setContentType(contentType(fileName));
        VisionExtractionService.ExtractionResult result = provider.extract(content, fileName, type);
        fields.deleteAll(fields.findByDocumentId(document.getId())); lineItems.deleteAll(lineItems.findByDocumentIdOrderByItemNumber(document.getId()));
        List<VisionExtractionService.FieldValue> extractedFields = result.fields();
        if (result.unreadable() && extractedFields.isEmpty()) extractedFields = manualFields(type);
        extractedFields.forEach(item -> { fields.save(ExtractedField.builder().document(document).fieldKey(item.key()).fieldLabel(item.label()).aiExtractedValue(item.value()).currentValue(item.value()).confidenceScore(item.confidence()).isLowConfidence(item.confidence().compareTo(new java.math.BigDecimal("0.850")) < 0).pageNumber(item.pageNumber()).build()); if (!item.value().isBlank()) { Supplier supplier = shipment.getSupplier(); supplier.setTotalFieldsExtracted(supplier.getTotalFieldsExtracted() + 1); suppliers.save(supplier); } });
        result.lineItems().forEach(item -> lineItems.save(LineItem.builder().document(document).itemNumber(item.itemNumber()).description(item.description()).hsnCode(item.hsnCode()).quantity(item.quantity()).unitPrice(item.unitPrice()).currency(item.currency()).lineTotal(item.lineTotal()).grossWeightKg(item.grossWeightKg()).confidenceScore(item.confidence()).build()));
        document.setPageCount(result.pageCount()); document.setUnreadable(result.unreadable()); document.setExtractionStatus(Enums.ExtractionStatus.COMPLETED); shipment.setStatus(Enums.ShipmentStatus.NEEDS_REVIEW); documents.save(document); shipments.save(shipment); crossChecks.runForShipment(shipmentId); return document;
    }

    private String contentType(String fileName) {
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf")) return "application/pdf";
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private List<VisionExtractionService.FieldValue> manualFields(Enums.DocumentType type) {
        List<Enums.FieldKey> keys = switch (type) {
            case INVOICE -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name, Enums.FieldKey.invoice_number, Enums.FieldKey.invoice_date, Enums.FieldKey.currency, Enums.FieldKey.incoterm, Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg, Enums.FieldKey.invoice_total);
            case PACKING_LIST -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name, Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg, Enums.FieldKey.net_weight_kg);
            case BILL_OF_LADING -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name, Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg, Enums.FieldKey.container_number);
        };
        return keys.stream().map(key -> new VisionExtractionService.FieldValue(key, label(key), "", java.math.BigDecimal.ZERO, 1)).toList();
    }

    private String label(Enums.FieldKey key) {
        return switch (key) {
            case exporter_name -> "Exporter Name"; case importer_name -> "Importer Name";
            case invoice_number -> "Invoice Number"; case invoice_date -> "Invoice Date";
            case currency -> "Currency"; case incoterm -> "Incoterm"; case total_packages -> "Total Packages";
            case gross_weight_kg -> "Gross Weight (kg)"; case net_weight_kg -> "Net Weight (kg)";
            case container_number -> "Container Number"; case invoice_total -> "Invoice Total";
        };
    }
}
