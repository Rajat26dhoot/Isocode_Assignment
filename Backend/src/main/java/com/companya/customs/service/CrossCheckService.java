package com.companya.customs.service;

import com.companya.customs.entity.*;
import com.companya.customs.exception.ApiExceptions;
import com.companya.customs.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.math.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class CrossCheckService {
    private static final BigDecimal TOLERANCE = new BigDecimal("0.01");
    @Value("${app.extraction.confidence-threshold:0.85}") private BigDecimal confidenceThreshold;
    private final ShipmentRepository shipments; private final ShipmentDocumentRepository documents; private final ExtractedFieldRepository fields;
    private final LineItemRepository lineItems; private final CrossCheckDiscrepancyRepository discrepancies;

    @Transactional
    public void runForShipment(Long shipmentId) {
        Shipment shipment = shipments.findById(shipmentId).orElseThrow(() -> new ApiExceptions.NotFound("Shipment not found: " + shipmentId));
        Map<Enums.DocumentType, ShipmentDocument> docs = documents.findByShipmentId(shipmentId).stream().collect(Collectors.toMap(ShipmentDocument::getDocType, Function.identity(), (a,b) -> a.getId() > b.getId() ? a : b));
        checkInvoiceTotal(shipment, docs.get(Enums.DocumentType.INVOICE));
        checkNumeric(shipment, Enums.RuleCode.GROSS_WEIGHT_MISMATCH, "gross_weight_kg", docs, true, true);
        checkNumeric(shipment, Enums.RuleCode.NET_WEIGHT_MISMATCH, "net_weight_kg", docs, true, true);
        checkNumeric(shipment, Enums.RuleCode.PACKAGE_COUNT_MISMATCH, "total_packages", docs, false, false);
        checkPartyNames(shipment, docs);
        checkLineArithmetic(shipment, docs.get(Enums.DocumentType.INVOICE));
        checkCurrency(shipment, docs);
        checkContainers(shipment, docs);
        checkLineConsistency(shipment, docs);
        refreshWorkflowStatus(shipment, docs);
    }

    private void refreshWorkflowStatus(Shipment shipment, Map<Enums.DocumentType, ShipmentDocument> docs) {
        if (List.of(Enums.ShipmentStatus.PENDING_APPROVAL, Enums.ShipmentStatus.APPROVED, Enums.ShipmentStatus.EXPORTED, Enums.ShipmentStatus.REJECTED).contains(shipment.getStatus())) return;
        if (docs.size() < Enums.DocumentType.values().length) { shipment.setStatus(Enums.ShipmentStatus.DOCUMENTS_PENDING); return; }
        List<CrossCheckDiscrepancy> open = discrepancies.findByShipmentId(shipment.getId()).stream().filter(d -> d.getStatus() == Enums.DiscrepancyStatus.OPEN).toList();
        if (open.stream().anyMatch(d -> d.getSeverity() == Enums.Severity.CRITICAL)) { shipment.setStatus(Enums.ShipmentStatus.VALIDATION_FAILED); return; }
        Set<Long> activeIds = docs.values().stream().map(ShipmentDocument::getId).collect(Collectors.toSet());
        boolean requiresReview = fields.findByDocumentShipmentId(shipment.getId()).stream().filter(f -> activeIds.contains(f.getDocument().getId())).anyMatch(f -> !f.isHumanVerified() && (f.isLowConfidence() || f.getCurrentValue() == null || f.getCurrentValue().isBlank()))
            || docs.values().stream().anyMatch(d -> d.isUnreadable() && !d.isHumanVerified()) || !open.isEmpty();
        ShipmentDocument invoice = docs.get(Enums.DocumentType.INVOICE);
        if (invoice != null) {
            List<LineItem> invoiceItems = lineItems.findByDocumentIdOrderByItemNumber(invoice.getId());
            if (invoiceItems.isEmpty() || invoiceItems.stream().anyMatch(i -> !i.isHumanVerified() && (i.getConfidenceScore() == null || i.getConfidenceScore().compareTo(confidenceThreshold) < 0))) requiresReview = true;
        }
        shipment.setStatus(requiresReview ? Enums.ShipmentStatus.NEEDS_REVIEW : Enums.ShipmentStatus.READY_FOR_APPROVAL);
    }

    private void checkInvoiceTotal(Shipment shipment, ShipmentDocument invoice) {
        if (invoice == null) { clear(shipment, Enums.RuleCode.INVOICE_TOTAL_VS_LINE_SUM); return; }
        List<LineItem> items = lineItems.findByDocumentIdOrderByItemNumber(invoice.getId());
        if (items.isEmpty() || items.stream().anyMatch(item -> item.getLineTotal() == null || item.getQuantity() == null || item.getUnitPrice() == null)) { clear(shipment, Enums.RuleCode.INVOICE_TOTAL_VS_LINE_SUM); return; }
        BigDecimal declared = numeric(invoice, Enums.FieldKey.invoice_total); BigDecimal sum = items.stream()
                .map(LineItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (declared != null && declared.subtract(sum).abs().compareTo(TOLERANCE) > 0) upsert(shipment, Enums.RuleCode.INVOICE_TOTAL_VS_LINE_SUM, Enums.Severity.CRITICAL, declared.toPlainString(), sum.toPlainString(), null, "delta=" + declared.subtract(sum).setScale(2, RoundingMode.HALF_UP).toPlainString()); else clear(shipment, Enums.RuleCode.INVOICE_TOTAL_VS_LINE_SUM);
    }

    private void checkNumeric(Shipment shipment, Enums.RuleCode rule, String key, Map<Enums.DocumentType, ShipmentDocument> docs, boolean decimal, boolean packingAndBolOnly) {
        BigDecimal invoice = packingAndBolOnly ? null : value(docs.get(Enums.DocumentType.INVOICE), key); BigDecimal packing = value(docs.get(Enums.DocumentType.PACKING_LIST), key); BigDecimal bol = value(docs.get(Enums.DocumentType.BILL_OF_LADING), key);
        if (key.equals("gross_weight_kg") || key.equals("net_weight_kg")) { packing = weightKg(docs.get(Enums.DocumentType.PACKING_LIST), key); bol = weightKg(docs.get(Enums.DocumentType.BILL_OF_LADING), key); }
        List<BigDecimal> values = java.util.stream.Stream.of(invoice, packing, bol).filter(Objects::nonNull).toList();
        boolean mismatch = values.size() >= 2 && values.stream().anyMatch(v -> v.subtract(values.get(0)).abs().compareTo(decimal ? TOLERANCE : BigDecimal.ZERO) > 0);
        if (mismatch) {
            BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO), max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
            upsert(shipment, rule, decimal ? Enums.Severity.WARNING : Enums.Severity.CRITICAL, print(invoice), print(packing), print(bol), "delta=" + max.subtract(min).stripTrailingZeros().toPlainString());
        } else clear(shipment, rule);
    }

    private void checkLineArithmetic(Shipment shipment, ShipmentDocument invoice) {
        if (invoice == null) { clear(shipment, Enums.RuleCode.LINE_ARITHMETIC); return; }
        List<String> errors = lineItems.findByDocumentIdOrderByItemNumber(invoice.getId()).stream()
            .filter(i -> i.getQuantity() != null && i.getUnitPrice() != null && i.getLineTotal() != null)
            .filter(i -> i.getQuantity().multiply(i.getUnitPrice()).subtract(i.getLineTotal()).abs().compareTo(TOLERANCE) > 0)
            .map(i -> "line " + i.getItemNumber() + ": quantity * unit price does not equal line total (" + i.getQuantity().toPlainString() + " * " + i.getUnitPrice().toPlainString() + " vs " + i.getLineTotal().toPlainString() + ")").toList();
        if (errors.isEmpty()) clear(shipment, Enums.RuleCode.LINE_ARITHMETIC);
        else upsert(shipment, Enums.RuleCode.LINE_ARITHMETIC, Enums.Severity.CRITICAL, null, null, null, String.join("; ", errors));
    }

    private void checkCurrency(Shipment shipment, Map<Enums.DocumentType, ShipmentDocument> docs) {
        Set<String> currencies = new TreeSet<>();
        for (ShipmentDocument doc : docs.values()) {
            String declared = text(doc, Enums.FieldKey.currency);
            if (declared != null && !declared.isBlank()) currencies.add(declared.trim().toUpperCase(Locale.ROOT));
            lineItems.findByDocumentIdOrderByItemNumber(doc.getId()).stream().map(LineItem::getCurrency).filter(Objects::nonNull).filter(s -> !s.isBlank()).map(s -> s.trim().toUpperCase(Locale.ROOT)).forEach(currencies::add);
        }
        if (currencies.size() > 1) upsert(shipment, Enums.RuleCode.CURRENCY_MISMATCH, Enums.Severity.CRITICAL, null, null, null, "Currency values differ: " + currencies);
        else clear(shipment, Enums.RuleCode.CURRENCY_MISMATCH);
    }

    private void checkContainers(Shipment shipment, Map<Enums.DocumentType, ShipmentDocument> docs) {
        Map<Enums.DocumentType,String> values = new EnumMap<>(Enums.DocumentType.class);
        docs.forEach((type, doc) -> { String v = text(doc, Enums.FieldKey.container_number); if (v != null && !v.isBlank()) values.put(type, v.trim()); });
        boolean mismatch = values.values().stream().map(this::normalize).collect(Collectors.toSet()).size() > 1;
        if (mismatch) upsert(shipment, Enums.RuleCode.CONTAINER_MISMATCH, Enums.Severity.WARNING, values.get(Enums.DocumentType.INVOICE), values.get(Enums.DocumentType.PACKING_LIST), values.get(Enums.DocumentType.BILL_OF_LADING), "Container identifiers differ: " + values);
        else clear(shipment, Enums.RuleCode.CONTAINER_MISMATCH);
    }

    private void checkLineConsistency(Shipment shipment, Map<Enums.DocumentType, ShipmentDocument> docs) {
        ShipmentDocument invoice = docs.get(Enums.DocumentType.INVOICE), packing = docs.get(Enums.DocumentType.PACKING_LIST);
        if (invoice == null || packing == null) { clear(shipment, Enums.RuleCode.ITEM_OR_QUANTITY_MISMATCH); return; }
        List<LineItem> a = lineItems.findByDocumentIdOrderByItemNumber(invoice.getId()), b = lineItems.findByDocumentIdOrderByItemNumber(packing.getId());
        if (a.isEmpty() && b.isEmpty()) { upsert(shipment, Enums.RuleCode.ITEM_OR_QUANTITY_MISMATCH, Enums.Severity.CRITICAL, "missing", "missing", null, "No comparable item quantities were extracted or entered for the invoice and packing list"); return; }
        if (a.isEmpty() || b.isEmpty()) { upsert(shipment, Enums.RuleCode.ITEM_OR_QUANTITY_MISMATCH, Enums.Severity.CRITICAL, a.isEmpty() ? "missing" : "present", b.isEmpty() ? "missing" : "present", null, a.isEmpty() ? "Invoice line items are missing" : "Packing list line items are missing"); return; }
        BigDecimal invoiceQty = a.stream().map(LineItem::getQuantity).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal packingQty = b.stream().map(LineItem::getQuantity).filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        boolean quantityMismatch = invoiceQty.compareTo(packingQty) != 0;
        boolean itemMismatch = a.stream().map(LineItem::getDescription).filter(Objects::nonNull).map(this::normalize).collect(Collectors.toSet()).stream().anyMatch(desc -> b.stream().map(LineItem::getDescription).filter(Objects::nonNull).map(this::normalize).noneMatch(desc::equals));
        if (quantityMismatch || itemMismatch) upsert(shipment, Enums.RuleCode.ITEM_OR_QUANTITY_MISMATCH, Enums.Severity.CRITICAL, invoiceQty.toPlainString(), packingQty.toPlainString(), null, (quantityMismatch ? "Invoice quantity total " + invoiceQty + " differs from packing list " + packingQty : "Item descriptions differ between invoice and packing list"));
        else clear(shipment, Enums.RuleCode.ITEM_OR_QUANTITY_MISMATCH);
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
    private BigDecimal weightKg(ShipmentDocument doc, String key) {
        String raw = text(doc, Enums.FieldKey.valueOf(key)); if (raw == null) return null;
        BigDecimal amount = numeric(doc, Enums.FieldKey.valueOf(key)); if (amount == null) return null;
        String value = raw.toLowerCase(Locale.ROOT);
        if (value.matches(".*(\\blb\\b|\\blbs\\b|pound|pounds).*")) return amount.multiply(new BigDecimal("0.45359237")).setScale(3, RoundingMode.HALF_UP);
        if (value.matches(".*(\\bg\\b|gram|grams).*")) return amount.divide(new BigDecimal("1000"), 3, RoundingMode.HALF_UP);
        if (value.matches(".*(\\bt\\b|mt\\b|tonne|tonnes|metric ton).*")) return amount.multiply(new BigDecimal("1000")).setScale(3, RoundingMode.HALF_UP);
        return amount;
    }
    private String text(ShipmentDocument doc, Enums.FieldKey key) { return doc == null ? null : fields.findByDocumentIdAndFieldKey(doc.getId(), key).map(ExtractedField::getCurrentValue).orElse(null); }
    private String print(BigDecimal value) { return value == null ? null : value.stripTrailingZeros().toPlainString(); }
    private String print(String value) { return value; }
    private BigDecimal nz(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    private String normalize(String input) { return input.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
}
