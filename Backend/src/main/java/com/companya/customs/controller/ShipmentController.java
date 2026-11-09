package com.companya.customs.controller;

import com.companya.customs.dto.ShipmentDtos.*;
import com.companya.customs.entity.*;
import com.companya.customs.repository.*;
import com.companya.customs.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
public class ShipmentController {
    private final ShipmentQueryService queries;
    private final ShipmentService shipmentService;
    private final ExtractionService extraction;
    private final AuditService audit;
    private final CrossCheckService crossChecks;
    private final CrossCheckDiscrepancyRepository discrepancies;
    private final ShipmentAccessService access;
    private final ExportService workflow;
    private final LineItemService lineItemService;

    @GetMapping
    public List<ShipmentSummary> list(@AuthenticationPrincipal User user) {
        return queries.list(user);
    }

    @GetMapping("/{id}")
    public ShipmentDetail get(@PathVariable Long id, @AuthenticationPrincipal User user) {
        access.requireShipment(id, user);
        return queries.get(id);
    }

    @GetMapping("/documents/{documentId}/content")
    public ResponseEntity<byte[]> documentContent(@PathVariable Long documentId, @AuthenticationPrincipal User user) {
        ShipmentDocument document = access.requireDocument(documentId, user);
        if (document.getFileContent() == null || document.getFileContent().length == 0) {
            throw new com.companya.customs.exception.ApiExceptions.NotFound("Document file content is unavailable");
        }
        MediaType type = MediaType.APPLICATION_OCTET_STREAM;
        try { type = MediaType.parseMediaType(document.getContentType()); } catch (Exception ignored) { }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + document.getFileName().replace("\"", "") + "\"").contentType(type).body(document.getFileContent());
    }

    @PostMapping
    @PreAuthorize("hasRole('EXECUTIVE')")
    public ShipmentDetail create(@Valid @RequestBody CreateShipmentRequest request, @AuthenticationPrincipal User user) {
        Shipment shipment = shipmentService.create(request, user);
        return queries.get(shipment.getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('EXECUTIVE')")
    public ShipmentDetail update(@PathVariable Long id, @Valid @RequestBody ShipmentUpdateRequest request, @AuthenticationPrincipal User user) {
        shipmentService.update(id, request, user);
        return queries.get(id);
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('EXECUTIVE')")
    public ShipmentDetail submit(@PathVariable Long id, @AuthenticationPrincipal User user) { workflow.submit(id, user); return queries.get(id); }

    @PostMapping("/{id}/documents/extract")
    @PreAuthorize("hasRole('EXECUTIVE')")
    public ShipmentDetail extract(@PathVariable Long id, @RequestParam Enums.DocumentType docType, @RequestPart MultipartFile file, @AuthenticationPrincipal User user) throws IOException {
        access.requireShipment(id, user);
        extraction.extract(id, docType, file.getOriginalFilename(), file.getBytes(), user);
        return queries.get(id);
    }

    @PostMapping("/documents/{documentId}/retry")
    @PreAuthorize("hasRole('EXECUTIVE')")
    public ShipmentDetail retryExtraction(@PathVariable Long documentId, @AuthenticationPrincipal User user) {
        ShipmentDocument document = access.requireDocument(documentId, user);
        extraction.retry(documentId, user);
        return queries.get(document.getShipment().getId());
    }

    @PostMapping("/{id}/documents/unreadable")
    @PreAuthorize("hasRole('EXECUTIVE')")
    public ShipmentDetail createUnreadable(@PathVariable Long id, @RequestParam Enums.DocumentType docType, @AuthenticationPrincipal User user) {
        access.requireShipment(id, user);
        ShipmentDocument document = shipmentService.createUnreadablePlaceholder(id, docType);
        audit.logAction(document.getShipment(), document.getId(), document.getDocType().name(), "DOCUMENT_MANUAL_ENTRY_CREATED", null, document.getFileName(), user, "Unreadable source marked for manual entry");
        return queries.get(id);
    }

    @PatchMapping("/fields/{fieldId}")
    @PreAuthorize("hasRole('EXECUTIVE')")
    public FieldResponse editField(@PathVariable Long fieldId, @Valid @RequestBody FieldEditRequest request, @AuthenticationPrincipal User user) {
        access.requireField(fieldId, user);
        ExtractedField field = audit.editField(fieldId, request, user);
        return new FieldResponse(field.getId(), field.getDocument().getDocType().name(), field.getFieldKey(), field.getFieldLabel(), field.getAiExtractedValue(), field.getCurrentValue(), field.getConfidenceScore(), field.isLowConfidence(), field.isHumanVerified(), field.isWasEdited(), field.getPageNumber());
    }

    @PatchMapping("/line-items/{lineItemId}")
    @PreAuthorize("hasRole('EXECUTIVE')")
    public LineItemResponse editLineItem(@PathVariable Long lineItemId, @RequestBody LineItemEditRequest request, @AuthenticationPrincipal User user) {
        access.requireLineItem(lineItemId, user);
        LineItem item = audit.editLineItem(lineItemId, request, user);
        return new LineItemResponse(item.getId(), item.getItemNumber(), item.getDescription(), item.getHsnCode(), item.getQuantity(), item.getUnitPrice(), item.getCurrency(), item.getLineTotal(), item.getGrossWeightKg(), item.getConfidenceScore(), item.isHumanVerified());
    }

    @PostMapping("/documents/{documentId}/line-items")
    @PreAuthorize("hasRole('EXECUTIVE')")
    public LineItemResponse createLineItem(@PathVariable Long documentId, @Valid @RequestBody LineItemCreateRequest request, @AuthenticationPrincipal User user) {
        LineItem item = lineItemService.create(documentId, request, user);
        return new LineItemResponse(item.getId(), item.getItemNumber(), item.getDescription(), item.getHsnCode(), item.getQuantity(), item.getUnitPrice(), item.getCurrency(), item.getLineTotal(), item.getGrossWeightKg(), item.getConfidenceScore(), item.isHumanVerified());
    }

    @PostMapping("/documents/{documentId}/verify")
    @PreAuthorize("hasRole('EXECUTIVE')")
    public ShipmentDetail verifyDocument(@PathVariable Long documentId, @AuthenticationPrincipal User user) {
        access.requireDocument(documentId, user);
        ShipmentDocument document = audit.verifyDocument(documentId, user);
        return queries.get(document.getShipment().getId());
    }

    @PostMapping("/documents/{documentId}/unreadable")
    @PreAuthorize("hasRole('EXECUTIVE')")
    public ShipmentDetail markUnreadable(@PathVariable Long documentId, @AuthenticationPrincipal User user) {
        access.requireDocument(documentId, user);
        ShipmentDocument document = audit.markUnreadable(documentId, user);
        return queries.get(document.getShipment().getId());
    }

    @PostMapping("/{id}/cross-check")
    @PreAuthorize("hasAnyRole('EXECUTIVE','MANAGER')")
    public ShipmentDetail runCrossCheck(@PathVariable Long id, @AuthenticationPrincipal User user) {
        access.requireShipment(id, user);
        crossChecks.runForShipment(id);
        return queries.get(id);
    }

    @PatchMapping("/discrepancies/{discrepancyId}")
    @PreAuthorize("hasAnyRole('EXECUTIVE','MANAGER')")
    public ShipmentDetail resolve(@PathVariable Long discrepancyId, @Valid @RequestBody DiscrepancyResolutionRequest request, @AuthenticationPrincipal User user) {
        CrossCheckDiscrepancy d = discrepancies.findById(discrepancyId)
                .orElseThrow(() -> new com.companya.customs.exception.ApiExceptions.NotFound("Discrepancy not found: " + discrepancyId));
        access.requireShipment(d.getShipment().getId(), user);
        if (d.getStatus() != Enums.DiscrepancyStatus.OPEN) throw new com.companya.customs.exception.ApiExceptions.Validation("This discrepancy is already resolved");
        if (request.managerOverride() && user.getRole() != Enums.Role.ROLE_MANAGER) {
            throw new com.companya.customs.exception.ApiExceptions.Validation("Only a manager can override a discrepancy");
        }
        d.setStatus(request.managerOverride() ? Enums.DiscrepancyStatus.MANAGER_OVERRIDDEN : Enums.DiscrepancyStatus.RESOLVED);
        d.setResolutionNote(request.resolutionNote());
        d.setResolvedByUser(user);
        d.setResolvedAt(java.time.Instant.now());
        discrepancies.save(d);
        audit.logAction(d.getShipment(), null, d.getRuleCode().name(), request.managerOverride() ? "VALIDATION_MANAGER_OVERRIDE" : "VALIDATION_RESOLVED", d.getStatus().name(), request.resolutionNote(), user, request.resolutionNote());
        return queries.get(d.getShipment().getId());
    }
}
