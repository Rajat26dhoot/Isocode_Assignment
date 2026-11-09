package com.companya.customs.service;

import com.companya.customs.entity.ExtractedField;
import com.companya.customs.repository.ExtractedFieldRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service @RequiredArgsConstructor
public class AccuracyService {
    private final ExtractedFieldRepository fields;
    @Transactional(readOnly = true)
    public List<SupplierAccuracy> supplierAccuracy() {
        return fields.findAll().stream().filter(ExtractedField::isHumanVerified)
            .filter(f -> f.getAiExtractedValue() != null && !f.getAiExtractedValue().isBlank())
            .filter(f -> f.getCurrentValue() != null && !f.getCurrentValue().isBlank())
            .collect(java.util.stream.Collectors.groupingBy(f -> f.getDocument().getShipment().getSupplier()))
            .entrySet().stream().map(entry -> {
                var supplier = entry.getKey(); var verified = entry.getValue();
                long corrected = verified.stream().filter(f -> !java.util.Objects.equals(f.getAiExtractedValue(), f.getCurrentValue())).count();
                long accurate = verified.size() - corrected;
                BigDecimal accuracy = BigDecimal.valueOf(accurate).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(verified.size()), 2, RoundingMode.HALF_UP);
                return new SupplierAccuracy(supplier.getId(), supplier.getName(), verified.size(), corrected, accuracy);
            }).toList();
    }
    public record SupplierAccuracy(Long supplierId, String supplierName, long totalFieldsExtracted, long totalFieldsCorrected, BigDecimal accuracyPercentage) { }
}
