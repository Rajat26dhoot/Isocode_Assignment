package com.companya.customs.service;

import com.companya.customs.dto.ShipmentDtos.CreateShipmentRequest;
import com.companya.customs.entity.*;
import com.companya.customs.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ShipmentService {
    private final ShipmentRepository shipments;
    private final SupplierRepository suppliers;
    private final ShipmentDocumentRepository documents;
    private final ExtractedFieldRepository fields;

    @Transactional
    public Shipment create(CreateShipmentRequest request, User executive) {
        Supplier supplier = suppliers.findByNameIgnoreCase(request.supplierName())
                .orElseGet(() -> suppliers.save(Supplier.builder()
                        .name(request.supplierName())
                        .country(request.supplierCountry() == null || request.supplierCountry().isBlank() ? "Unknown" : request.supplierCountry())
                        .build()));

        Shipment shipment = shipments.save(Shipment.builder()
                .shipmentCode(nextCode())
                .importerName(request.importerName())
                .importerContactEncrypted(request.importerContact())
                .supplier(supplier)
                .assignedExecutive(executive)
                .status(Enums.ShipmentStatus.EXTRACTING)
                .build());

        if (request.unreadableDocTypes() != null) {
            for (Enums.DocumentType type : request.unreadableDocTypes()) {
                createUnreadablePlaceholder(shipment, type);
            }
        }
        return shipment;
    }

    @Transactional
    public ShipmentDocument createUnreadablePlaceholder(Long shipmentId, Enums.DocumentType type) {
        Shipment shipment = shipments.findById(shipmentId)
                .orElseThrow(() -> new com.companya.customs.exception.ApiExceptions.NotFound("Shipment not found: " + shipmentId));
        return createUnreadablePlaceholder(shipment, type);
    }

    private ShipmentDocument createUnreadablePlaceholder(Shipment shipment, Enums.DocumentType type) {
        ShipmentDocument document = documents.findByShipmentIdAndDocType(shipment.getId(), type)
                .orElseGet(() -> ShipmentDocument.builder()
                        .shipment(shipment)
                        .docType(type)
                        .fileName(type.name().toLowerCase() + "-manual-entry.txt")
                        .build());
        document.setUnreadable(true);
        document.setHumanVerified(false);
        document.setPageCount(1);
        document.setExtractionStatus(Enums.ExtractionStatus.FAILED);
        documents.save(document);
        fields.deleteAll(fields.findByDocumentId(document.getId()));
        for (Enums.FieldKey key : manualKeys(type)) {
            fields.save(ExtractedField.builder()
                    .document(document)
                    .fieldKey(key)
                    .fieldLabel(labelFor(key))
                    .aiExtractedValue("")
                    .currentValue("")
                    .confidenceScore(BigDecimal.ZERO)
                    .isLowConfidence(true)
                    .pageNumber(1)
                    .build());
        }
        return document;
    }

    private String nextCode() {
        long next = 24811 + shipments.count();
        String code;
        do {
            code = "SHP-" + next++;
        } while (shipments.findByShipmentCode(code).isPresent());
        return code;
    }

    private List<Enums.FieldKey> manualKeys(Enums.DocumentType type) {
        return switch (type) {
            case INVOICE -> List.of(
                    Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name, Enums.FieldKey.invoice_number,
                    Enums.FieldKey.invoice_date, Enums.FieldKey.currency, Enums.FieldKey.incoterm,
                    Enums.FieldKey.invoice_total, Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg
            );
            case PACKING_LIST -> List.of(
                    Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name,
                    Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg, Enums.FieldKey.net_weight_kg
            );
            case BILL_OF_LADING -> List.of(
                    Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name,
                    Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg, Enums.FieldKey.container_number
            );
        };
    }

    private String labelFor(Enums.FieldKey key) {
        return switch (key) {
            case exporter_name -> "Exporter Name";
            case importer_name -> "Importer Name";
            case invoice_number -> "Invoice Number";
            case invoice_date -> "Invoice Date";
            case currency -> "Currency";
            case incoterm -> "Incoterm";
            case total_packages -> "Total Packages";
            case gross_weight_kg -> "Gross Weight (kg)";
            case net_weight_kg -> "Net Weight (kg)";
            case container_number -> "Container Number";
            case invoice_total -> "Invoice Total";
        };
    }
}
