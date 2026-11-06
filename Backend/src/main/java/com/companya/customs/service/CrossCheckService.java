package com.companya.customs.service;

import com.companya.customs.entity.*;
import com.companya.customs.exception.ApiExceptions;
import com.companya.customs.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class CrossCheckService {
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    private final ShipmentRepository shipments; private final ShipmentDocumentRepository documents; private final ExtractedFieldRepository fields;
    private final LineItemRepository lineItems; private final CrossCheckDiscrepancyRepository discrepancies;

    @Transactional
    public void runForShipment(Long shipmentId) {
        Shipment shipment = shipments.findById(shipmentId).orElseThrow(() -> new ApiExceptions.NotFound("Shipment not found: " + shipmentId));
        Map<Enums.DocumentType, ShipmentDocument> docs = documents.findByShipmentId(shipmentId).stream().collect(Collectors.toMap(ShipmentDocument::getDocType, Function.identity(), (a,b) -> a));
        checkInvoiceTotal(shipment, docs.get(Enums.DocumentType.INVOICE));
        checkNumeric(shipment, Enums.RuleCode.GROSS_WEIGHT_MISMATCH, "gross_weight_kg", docs, true);
        checkNumeric(shipment, Enums.RuleCode.NET_WEIGHT_MISMATCH, "net_weight_kg", docs, true);
        checkNumeric(shipment, Enums.RuleCode.PACKAGE_COUNT_MISMATCH, "total_packages", docs, false);
        checkPartyNames(shipment, docs);
    }

    private void checkInvoiceTotal(Shipment shipment, ShipmentDocument invoice) {
        if (invoice == null) { clear(shipment, Enums.RuleCode.INVOICE_TOTAL_VS_LINE_SUM); return; }
        BigDecimal declared = numeric(invoice, Enums.FieldKey.invoice_total); BigDecimal sum = lineItems.findByDocumentIdOrderByItemNumber(invoice.getId()).stream()
                .map(item -> item.getLineTotal() != null ? item.getLineTotal() : nz(item.getQuantity()).multiply(nz(item.getUnitPrice())))
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (declared != null && declared.subtract(sum).abs().compareTo(TOLERANCE) > 0) upsert(shipment, Enums.RuleCode.INVOICE_TOTAL_VS_LINE_SUM, Enums.Severity.CRITICAL, declared.toPlainString(), sum.toPlainString(), null, "delta=" + declared.subtract(sum).setScale(2, RoundingMode.HALF_UP).toPlainString()); else clear(shipment, Enums.RuleCode.INVOICE_TOTAL_VS_LINE_SUM);
    }

    private void checkNumeric(Shipment shipment, Enums.RuleCode rule, String key, Map<Enums.DocumentType, ShipmentDocument> docs, boolean decimal) {
        BigDecimal invoice = value(docs.get(Enums.DocumentType.INVOICE), key); BigDecimal packing = value(docs.get(Enums.DocumentType.PACKING_LIST), key); BigDecimal bol = value(docs.get(Enums.DocumentType.BILL_OF_LADING), key);
        List<BigDecimal> values = List.of(invoice, packing, bol).stream().filter(Objects::nonNull).toList();
        boolean mismatch = values.size() >= 2 && values.stream().anyMatch(v -> v.subtract(values.get(0)).abs().compareTo(decimal ? TOLERANCE : BigDecimal.ZERO) > 0);
        if (mismatch) {
            BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO), max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            upsert(shipment, rule, decimal ? Enums.Severity.WARNING : Enums.Severity.CRITICAL, print(invoice), print(packing), print(bol), "delta=" + max.subtract(min).stripTrailingZeros().toPlainString());
        } else clear(shipment, rule);
    }

    private void checkPartyNames(Shipment shipment, Map<Enums.DocumentType, ShipmentDocument> docs) {
        boolean mismatch = false; String mismatchDetail = null;
        for (Enums.FieldKey key : List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name)) {
            Map<Enums.DocumentType, String> values = new EnumMap<>(Enums.DocumentType.class);
            docs.forEach((type, doc) -> { String value = text(doc, key); if (value != null && !value.isBlank()) values.put(type, normalize(value)); });
            if (values.size() >= 2 && new HashSet<>(values.values()).size() > 1) { mismatch = true; mismatchDetail = (mismatchDetail == null ? "" : mismatchDetail + "; ") + key + "=" + values; }
        }
        if (mismatch) upsert(shipment, Enums.RuleCode.PARTY_NAME_MISMATCH, Enums.Severity.WARNING, "see detail", "see detail", "see detail", "normalized values differ: " + mismatchDetail); else clear(shipment, Enums.RuleCode.PARTY_NAME_MISMATCH);
    }

    private void upsert(Shipment s, Enums.RuleCode rule, Enums.Severity severity, String invoice, String packing, String bol, String delta) {
        discrepancies.deleteByShipmentIdAndRuleCode(s.getId(), rule);
        discrepancies.save(CrossCheckDiscrepancy.builder().shipment(s).ruleCode(rule).severity(severity).invoiceValue(invoice).packingListValue(packing).bolValue(bol).expectedOrDelta(delta).status(Enums.DiscrepancyStatus.OPEN).build());
    }
    private void clear(Shipment s, Enums.RuleCode rule) { discrepancies.deleteByShipmentIdAndRuleCode(s.getId(), rule); }
    private BigDecimal numeric(ShipmentDocument doc, Enums.FieldKey key) { String value = text(doc, key); if (value == null) return null; try { return new BigDecimal(value.replaceAll("[^0-9.\\-]", "")); } catch (NumberFormatException e) { return null; } }
    private BigDecimal value(ShipmentDocument doc, String key) { return doc == null ? null : numeric(doc, Enums.FieldKey.valueOf(key)); }
    private String text(ShipmentDocument doc, Enums.FieldKey key) { return doc == null ? null : fields.findByDocumentIdAndFieldKey(doc.getId(), key).map(ExtractedField::getCurrentValue).orElse(null); }
    private String print(BigDecimal value) { return value == null ? null : value.stripTrailingZeros().toPlainString(); }
    private BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String normalize(String input) { return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
}
