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
    public record ShipmentDetail(Long id, String shipmentCode, String importerName, String supplier, Enums.ShipmentStatus status, Instant updatedAt, List<DocumentResponse> documents, List<DiscrepancyResponse> discrepancies) { }
    public record CreateShipmentRequest(
            @NotBlank String importerName,
            @NotBlank String supplierName,
            String supplierCountry,
            String importerContact,
            List<Enums.DocumentType> unreadableDocTypes
    ) { }
    public record FieldEditRequest(@NotNull String currentValue, @NotBlank String changeReason) { }
    public record LineItemEditRequest(String description, String hsnCode, BigDecimal quantity, BigDecimal unitPrice, String currency, BigDecimal lineTotal, BigDecimal grossWeightKg) { }
    public record DiscrepancyResolutionRequest(@NotBlank String resolutionNote, boolean managerOverride) { }
    public record AuditLogResponse(Long id, Instant timestamp, String user, String document, String field, String aiValue, String correctedValue, String reason) { }
}
