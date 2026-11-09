package com.companya.customs.controller;

import com.companya.customs.entity.Shipment;
import com.companya.customs.entity.User;
import com.companya.customs.repository.ShipmentRepository;
import com.companya.customs.service.ExportService;
import com.companya.customs.service.ShipmentQueryService;
import com.companya.customs.dto.ShipmentDtos.ShipmentDetail;
import com.companya.customs.dto.ShipmentDtos.RejectionRequest;
import com.companya.customs.service.ShipmentAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController @RequestMapping("/api/shipments") @RequiredArgsConstructor
public class ExportController {
    private final ExportService exports; private final ShipmentRepository shipments; private final ShipmentQueryService queries; private final ShipmentAccessService access;
    @PostMapping("/{id}/approve") @PreAuthorize("hasRole('MANAGER')") public ShipmentDetail approve(@PathVariable Long id, @AuthenticationPrincipal User manager) { exports.approve(id, manager); return queries.get(id); }
    @PostMapping("/{id}/reject") @PreAuthorize("hasRole('MANAGER')") public ShipmentDetail reject(@PathVariable Long id, @Valid @RequestBody RejectionRequest request, @AuthenticationPrincipal User manager) { exports.reject(id, manager, request.reason()); return queries.get(id); }
    @GetMapping("/{id}/export") @PreAuthorize("hasAnyRole('EXECUTIVE','MANAGER')") public ResponseEntity<ByteArrayResource> export(@PathVariable Long id, @RequestParam(defaultValue = "csv") String format, @AuthenticationPrincipal User user) { access.requireShipment(id,user); if (!format.equalsIgnoreCase("csv") && !format.equalsIgnoreCase("xlsx")) throw new com.companya.customs.exception.ApiExceptions.Validation("Export format must be csv or xlsx"); ExportService.ExportFile file = exports.export(id, format, user); return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString()).body(new ByteArrayResource(file.content())); }
}
