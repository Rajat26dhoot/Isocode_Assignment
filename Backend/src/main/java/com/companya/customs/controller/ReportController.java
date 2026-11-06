package com.companya.customs.controller;

import com.companya.customs.dto.ShipmentDtos.AuditLogResponse;
import com.companya.customs.entity.ExtractedField;
import com.companya.customs.entity.FieldAuditLog;
import com.companya.customs.repository.ExtractedFieldRepository;
import com.companya.customs.repository.FieldAuditLogRepository;
import com.companya.customs.service.AccuracyService;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {
    private final AccuracyService accuracy;
    private final FieldAuditLogRepository auditLogs;
    private final ExtractedFieldRepository fields;

    @GetMapping("/supplier-accuracy")
    public List<AccuracyService.SupplierAccuracy> supplierAccuracy() {
        return accuracy.supplierAccuracy();
    }

    @GetMapping("/audit-logs")
    @Transactional(readOnly = true)
    public List<AuditLogResponse> auditLogs() {
        return auditLogs.findAllByOrderByChangedAtDesc().stream().map(this::toResponse).toList();
    }

    private AuditLogResponse toResponse(FieldAuditLog log) {
        String document = fields.findById(log.getExtractedFieldId())
                .map(ExtractedField::getDocument)
                .map(doc -> doc.getDocType().name())
                .orElse("INVOICE");
        return new AuditLogResponse(
                log.getId(),
                log.getChangedAt(),
                log.getChangedByUser().getFullName(),
                document,
                log.getFieldKey().name(),
                log.getOldValue(),
                log.getNewValue(),
                log.getChangeReason()
        );
    }
}
