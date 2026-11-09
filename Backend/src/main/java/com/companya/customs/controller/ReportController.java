package com.companya.customs.controller;

import com.companya.customs.dto.ShipmentDtos.AuditLogResponse;
import com.companya.customs.entity.FieldAuditLog;
import com.companya.customs.repository.FieldAuditLogRepository;
import com.companya.customs.repository.ShipmentDocumentRepository;
import com.companya.customs.service.AccuracyService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final AccuracyService accuracy;
    private final FieldAuditLogRepository auditLogs;
    private final ShipmentDocumentRepository documents;

    @GetMapping("/supplier-accuracy")
    @PreAuthorize("hasRole('MANAGER')")
    public List<AccuracyService.SupplierAccuracy> supplierAccuracy() {
        return accuracy.supplierAccuracy();
    }

    @GetMapping("/audit-logs")
    @PreAuthorize("hasRole('MANAGER')")
    @Transactional(readOnly = true)
    public List<AuditLogResponse> auditLogs() {
        return auditLogs.findAllByOrderByChangedAtDesc().stream().map(this::toResponse).toList();
    }

    private AuditLogResponse toResponse(FieldAuditLog log) {
        String document = log.getDocumentId() == null ? "Shipment" : documents.findById(log.getDocumentId()).map(doc -> doc.getDocType().name()).orElse("Document");
        return new AuditLogResponse(
                log.getId(),
                log.getChangedAt(),
                log.getShipment().getShipmentCode(),
                log.getDocumentId(),
                log.getChangedByUser().getFullName(),
                document,
                log.getFieldName() != null ? log.getFieldName() : log.getFieldKey() == null ? log.getActionType() : log.getFieldKey().name(),
                log.getActionType() == null ? "AUDIT" : log.getActionType(),
                log.getOldValue(),
                log.getNewValue(),
                log.getChangeReason()
        );
    }
}
