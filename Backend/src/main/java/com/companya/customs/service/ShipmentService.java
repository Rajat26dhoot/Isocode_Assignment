package com.companya.customs.service;

import com.companya.customs.dto.ShipmentDtos.CreateShipmentRequest;
import com.companya.customs.dto.ShipmentDtos.ShipmentUpdateRequest;
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
    private final ShipmentAccessService access;
    private final AuditService audit;

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
                .status(Enums.ShipmentStatus.DOCUMENTS_PENDING)
                .build());

        audit.logAction(shipment, null, "Shipment", "SHIPMENT_CREATED", null, shipment.getShipmentCode(), executive, "Shipment created");
        return shipment;
    }

    @Transactional
    public Shipment update(Long id, ShipmentUpdateRequest request, User executive) {
        Shipment shipment = access.requireShipment(id, executive);
        if (shipment.getStatus() == Enums.ShipmentStatus.PENDING_APPROVAL || shipment.getStatus() == Enums.ShipmentStatus.APPROVED || shipment.getStatus() == Enums.ShipmentStatus.EXPORTED)
            throw new com.companya.customs.exception.ApiExceptions.Validation("This shipment cannot be edited in its current status");
        String previous = shipment.getImporterName() + " / " + shipment.getSupplier().getName();
        shipment.setImporterName(request.importerName().trim());
        Supplier supplier = suppliers.findByNameIgnoreCase(request.supplierName().trim()).orElseGet(() -> suppliers.save(Supplier.builder().name(request.supplierName().trim()).country(request.supplierCountry() == null || request.supplierCountry().isBlank() ? "Unknown" : request.supplierCountry().trim()).build()));
        shipment.setSupplier(supplier);
        audit.logAction(shipment, null, "Shipment details", "SHIPMENT_UPDATED", previous, shipment.getImporterName() + " / " + supplier.getName(), executive, "Shipment details updated");
        return shipments.save(shipment);
    }

    @Transactional
    public ShipmentDocument createUnreadablePlaceholder(Long shipmentId, Enums.DocumentType type) {
        Shipment shipment = shipments.findById(shipmentId)
                .orElseThrow(() -> new com.companya.customs.exception.ApiExceptions.NotFound("Shipment not found: " + shipmentId));
        return createUnreadablePlaceholder(shipment, type);
    }

    private ShipmentDocument createUnreadablePlaceholder(Shipment shipment, Enums.DocumentType type) {
        ShipmentDocument document = ShipmentDocument.builder()
                        .shipment(shipment)
                        .docType(type)
                        .fileName(type.name().toLowerCase() + "-manual-entry.txt")
                        .build();
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
        String code;
        do { code = "SHP-" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(java.util.Locale.ROOT); }
        while (shipments.findByShipmentCode(code).isPresent());
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
                    Enums.FieldKey.bl_number, Enums.FieldKey.port_of_loading, Enums.FieldKey.port_of_discharge,
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
            case bl_number -> "B/L Number";
            case port_of_loading -> "Port of Loading";
            case port_of_discharge -> "Port of Discharge";
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
