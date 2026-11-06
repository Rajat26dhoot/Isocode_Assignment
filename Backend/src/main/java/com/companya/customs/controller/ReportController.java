package com.companya.customs.controller;

import com.companya.customs.service.AccuracyService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequestMapping("/api/reports") @RequiredArgsConstructor
public class ReportController {
    private final AccuracyService accuracy;
    @GetMapping("/supplier-accuracy") public List<AccuracyService.SupplierAccuracy> supplierAccuracy() { return accuracy.supplierAccuracy(); }
}
