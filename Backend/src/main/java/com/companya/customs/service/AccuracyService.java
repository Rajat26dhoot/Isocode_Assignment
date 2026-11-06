package com.companya.customs.service;

import com.companya.customs.entity.Supplier;
import com.companya.customs.repository.SupplierRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service @RequiredArgsConstructor
public class AccuracyService {
    private final SupplierRepository suppliers;
    public List<SupplierAccuracy> supplierAccuracy() { return suppliers.findAll().stream().map(s -> { long extracted = s.getTotalFieldsExtracted(), corrected = s.getTotalFieldsCorrected(); BigDecimal accuracy = extracted == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(extracted - corrected).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(extracted), 2, RoundingMode.HALF_UP); return new SupplierAccuracy(s.getId(), s.getName(), extracted, corrected, accuracy); }).toList(); }
    public record SupplierAccuracy(Long supplierId, String supplierName, long totalFieldsExtracted, long totalFieldsCorrected, BigDecimal accuracyPercentage) { }
}
