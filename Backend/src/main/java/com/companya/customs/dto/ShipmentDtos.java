package com.companya.customs.dto;

import com.companya.customs.entity.Enums;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class ShipmentDtos {
    private ShipmentDtos() { }
    public record ShipmentSummary(Long id, String shipmentCode, String importerName, String supplier, Enums.ShipmentStatus status, Instant updatedAt) { }
    public record FieldResponse(Long id, String documentType, Enums.FieldKey fieldKey, String fieldLabel, String aiExtractedValue, String currentValue, BigDecimal confidenceScore, boolean lowConfidence, boolean humanVerified, boolean wasEdited, Integer pageNumber) { }
    public record LineItemResponse(Long id, Integer itemNumber, String description, String hsnCode, BigDecimal quantity, BigDecimal unitPrice, String currency, BigDecimal lineTotal, BigDecimal grossWeightKg, BigDecimal confidenceScore, boolean humanVerified) { }
    public record DocumentResponse(Long id, Enums.DocumentType docType, String fileName, Integer pageCount, boolean unreadable, boolean humanVerified, Enums.ExtractionStatus extractionStatus, List<FieldResponse> fields, List<LineItemResponse> lineItems) { }
    public record DiscrepancyResponse(Long id, Enums.RuleCode ruleCode, Enums.Severity severity, String invoiceValue, String packingListValue, String bolValue, String expectedOrDelta, Enums.DiscrepancyStatus status, String resolutionNote) { }
    public record ShipmentDetail(Long id, String shipmentCode, String importerName, String supplier, Enums.ShipmentStatus status, Instant updatedAt, List<DocumentResponse> documents, List<DiscrepancyResponse> discrepancies, String rejectionReason) { }
    public record ShipmentUpdateRequest(@NotBlank @Size(max=255) String importerName, @NotBlank @Size(max=255) String supplierName, @Size(max=100) String supplierCountry) { }
    public record RejectionRequest(@NotBlank @Size(max=2000) String reason) { }
    public record CreateShipmentRequest(
            @NotBlank @Size(max=255) String importerName,
            @NotBlank @Size(max=255) String supplierName,
            @Size(max=100) String supplierCountry,
            @Size(max=1000) String importerContact,
            List<Enums.DocumentType> unreadableDocTypes
    ) { }
    public record FieldEditRequest(@NotNull @Size(max=4000) String currentValue, @NotBlank @Size(max=1000) String changeReason) { }
    public record LineItemEditRequest(@Size(max=1000) String description, @Size(max=64) String hsnCode, @DecimalMin(value="0", inclusive=false) BigDecimal quantity, @DecimalMin("0") BigDecimal unitPrice, @Size(max=8) String currency, @DecimalMin("0") BigDecimal lineTotal, @DecimalMin("0") BigDecimal grossWeightKg) { }
    public record LineItemCreateRequest(@NotBlank @Size(max=1000) String description, @Size(max=64) String hsnCode, @NotNull @DecimalMin(value="0", inclusive=false) BigDecimal quantity, @DecimalMin("0") BigDecimal unitPrice, @Size(max=8) String currency, @DecimalMin("0") BigDecimal lineTotal) { }
    public record DiscrepancyResolutionRequest(@NotBlank String resolutionNote, boolean managerOverride) { }
    public record AuditLogResponse(Long id, Instant timestamp, String shipment, Long documentId, String user, String document, String field, String actionType, String aiValue, String correctedValue, String reason) { }
}
