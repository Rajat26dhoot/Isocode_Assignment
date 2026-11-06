package com.companya.customs.controller;

import com.companya.customs.dto.ShipmentDtos.*;
import com.companya.customs.entity.*;
import com.companya.customs.repository.*;
import com.companya.customs.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@RestController @RequestMapping("/api/shipments") @RequiredArgsConstructor
public class ShipmentController {
    private final ShipmentQueryService queries; private final ExtractionService extraction; private final AuditService audit; private final CrossCheckService crossChecks; private final CrossCheckDiscrepancyRepository discrepancies;
    @GetMapping public List<ShipmentSummary> list() { return queries.list(); }
    @GetMapping("/{id}") public ShipmentDetail get(@PathVariable Long id) { return queries.get(id); }
    @PostMapping("/{id}/documents/extract") @PreAuthorize("hasAnyRole('EXECUTIVE','MANAGER')") public ShipmentDetail extract(@PathVariable Long id, @RequestParam Enums.DocumentType docType, @RequestPart MultipartFile file) throws IOException { extraction.extract(id, docType, file.getOriginalFilename(), file.getBytes()); return queries.get(id); }
    @PatchMapping("/fields/{fieldId}") @PreAuthorize("hasAnyRole('EXECUTIVE','MANAGER')") public FieldResponse editField(@PathVariable Long fieldId, @Valid @RequestBody FieldEditRequest request, @AuthenticationPrincipal User user) { ExtractedField field = audit.editField(fieldId, request, user); return new FieldResponse(field.getId(), field.getDocument().getDocType().name(), field.getFieldKey(), field.getFieldLabel(), field.getAiExtractedValue(), field.getCurrentValue(), field.getConfidenceScore(), field.isLowConfidence(), field.isHumanVerified(), field.isWasEdited(), field.getPageNumber()); }
    @PatchMapping("/line-items/{lineItemId}") @PreAuthorize("hasAnyRole('EXECUTIVE','MANAGER')") public LineItemResponse editLineItem(@PathVariable Long lineItemId, @RequestBody LineItemEditRequest request, @AuthenticationPrincipal User user) { LineItem item = audit.editLineItem(lineItemId, request, user); return new LineItemResponse(item.getId(), item.getItemNumber(), item.getDescription(), item.getHsnCode(), item.getQuantity(), item.getUnitPrice(), item.getCurrency(), item.getLineTotal(), item.getGrossWeightKg(), item.getConfidenceScore(), item.isHumanVerified()); }
    @PostMapping("/documents/{documentId}/verify") @PreAuthorize("hasAnyRole('EXECUTIVE','MANAGER')") public ShipmentDetail verifyDocument(@PathVariable Long documentId) { ShipmentDocument document = audit.verifyDocument(documentId); return queries.get(document.getShipment().getId()); }
    @PostMapping("/documents/{documentId}/unreadable") @PreAuthorize("hasAnyRole('EXECUTIVE','MANAGER')") public ShipmentDetail markUnreadable(@PathVariable Long documentId) { ShipmentDocument document = audit.markUnreadable(documentId); return queries.get(document.getShipment().getId()); }
    @PostMapping("/{id}/cross-check") @PreAuthorize("hasAnyRole('EXECUTIVE','MANAGER')") public ShipmentDetail runCrossCheck(@PathVariable Long id) { crossChecks.runForShipment(id); return queries.get(id); }
    @PatchMapping("/discrepancies/{discrepancyId}") @PreAuthorize("hasAnyRole('EXECUTIVE','MANAGER')") public ShipmentDetail resolve(@PathVariable Long discrepancyId, @Valid @RequestBody DiscrepancyResolutionRequest request, @AuthenticationPrincipal User user) { CrossCheckDiscrepancy d = discrepancies.findById(discrepancyId).orElseThrow(() -> new com.companya.customs.exception.ApiExceptions.NotFound("Discrepancy not found: " + discrepancyId)); if (request.managerOverride() && user.getRole() != Enums.Role.ROLE_MANAGER) throw new com.companya.customs.exception.ApiExceptions.Validation("Only a manager can override a discrepancy"); d.setStatus(request.managerOverride() ? Enums.DiscrepancyStatus.MANAGER_OVERRIDDEN : Enums.DiscrepancyStatus.RESOLVED); d.setResolutionNote(request.resolutionNote()); d.setResolvedByUser(user); d.setResolvedAt(java.time.Instant.now()); discrepancies.save(d); return queries.get(d.getShipment().getId()); }
}
