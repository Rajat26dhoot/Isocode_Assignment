package com.companya.customs.service.extraction;

import com.companya.customs.entity.Enums;
import java.math.BigDecimal;
import java.util.List;

public interface VisionExtractionService {
    ExtractionResult extract(byte[] content, String fileName, Enums.DocumentType documentType);
    record ExtractionResult(List<FieldValue> fields, List<LineValue> lineItems, int pageCount, boolean unreadable) { }
    record FieldValue(Enums.FieldKey key, String label, String value, BigDecimal confidence, int pageNumber) { }
    record LineValue(int itemNumber, String description, String hsnCode, BigDecimal quantity, BigDecimal unitPrice, String currency, BigDecimal lineTotal, BigDecimal grossWeightKg, BigDecimal confidence) { }
}
