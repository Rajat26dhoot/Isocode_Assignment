package com.companya.customs.controller;

import com.companya.customs.entity.Shipment;
import com.companya.customs.entity.User;
import com.companya.customs.repository.ShipmentRepository;
import com.companya.customs.service.ExportService;
import com.companya.customs.service.ShipmentQueryService;
import com.companya.customs.dto.ShipmentDtos.ShipmentDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/shipments") @RequiredArgsConstructor
public class ExportController {
    private final ExportService exports; private final ShipmentRepository shipments; private final ShipmentQueryService queries;
    @PostMapping("/{id}/approve") @PreAuthorize("hasRole('MANAGER')") public ShipmentDetail approve(@PathVariable Long id, @AuthenticationPrincipal User manager) { exports.approve(id, manager); return queries.get(id); }
    @GetMapping("/{id}/export") @PreAuthorize("hasAnyRole('EXECUTIVE','MANAGER')") public ResponseEntity<ByteArrayResource> export(@PathVariable Long id, @RequestParam(defaultValue = "csv") String format, @AuthenticationPrincipal User user) { ExportService.ExportFile file = exports.export(id, format, user); return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(file.fileName()).build().toString()).body(new ByteArrayResource(file.content())); }
}
