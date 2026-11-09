package com.companya.customs.service;

import com.companya.customs.entity.*;
import com.companya.customs.exception.ApiExceptions;
import com.companya.customs.repository.*;
import lombok.RequiredArgsConstructor;
import org.apache.commons.csv.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.io.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class ExportService {
    @Value("${app.extraction.confidence-threshold:0.85}") private BigDecimal confidenceThreshold;
    private final ShipmentRepository shipments; private final ShipmentDocumentRepository documents; private final ExtractedFieldRepository fields; private final CrossCheckDiscrepancyRepository discrepancies; private final UserRepository users; private final LineItemRepository lineItems; private final FieldAuditLogRepository auditLogs;
    @Transactional public Shipment submit(Long shipmentId, User executive) { Shipment shipment = get(shipmentId); if (shipment.getAssignedExecutive() == null || !shipment.getAssignedExecutive().getId().equals(executive.getId())) throw new ApiExceptions.NotFound("Shipment not found: " + shipmentId); enforceGate(shipment); String previous = shipment.getStatus().name(); shipment.setStatus(Enums.ShipmentStatus.PENDING_APPROVAL); shipment.setSubmittedAt(Instant.now()); audit(shipment, executive, "SUBMITTED", previous, "PENDING_APPROVAL", "Executive submitted shipment for manager approval"); return shipments.save(shipment); }
    @Transactional public Shipment approve(Long shipmentId, User manager) { Shipment shipment = get(shipmentId); if (shipment.getStatus() != Enums.ShipmentStatus.PENDING_APPROVAL) throw new ApiExceptions.ExportBlocked("Only a submitted shipment can be approved"); enforceGate(shipment); String previous = shipment.getStatus().name(); shipment.setStatus(Enums.ShipmentStatus.APPROVED); shipment.setApprovedByManager(manager); shipment.setApprovedAt(Instant.now()); shipment.setRejectionReason(null); audit(shipment, manager, "APPROVED", previous, "APPROVED", "Manager approved shipment"); return shipments.save(shipment); }
    @Transactional public Shipment reject(Long shipmentId, User manager, String reason) { Shipment shipment = get(shipmentId); if (shipment.getStatus() != Enums.ShipmentStatus.PENDING_APPROVAL) throw new ApiExceptions.ExportBlocked("Only a submitted shipment can be rejected"); String previous = shipment.getStatus().name(); shipment.setStatus(Enums.ShipmentStatus.REJECTED); shipment.setRejectionReason(reason.trim()); shipment.setApprovedByManager(manager); shipment.setApprovedAt(Instant.now()); audit(shipment, manager, "REJECTED", previous, "REJECTED", reason.trim()); return shipments.save(shipment); }
    @Transactional public ExportFile export(Long shipmentId, String format, User user) {
        Shipment shipment = get(shipmentId);
        enforceGate(shipment);
        if (shipment.getStatus() != Enums.ShipmentStatus.APPROVED && shipment.getStatus() != Enums.ShipmentStatus.EXPORTED) {
            throw new ApiExceptions.ExportBlocked("Shipment must be APPROVED before export");
        }
        byte[] bytes = "xlsx".equalsIgnoreCase(format) ? xlsx(shipment) : csv(shipment);
        audit(shipment, user, "xlsx".equalsIgnoreCase(format) ? "XLSX_EXPORT" : "CSV_EXPORT", shipment.getStatus().name(), Enums.ShipmentStatus.EXPORTED.name(), "Shipment export generated");
        shipment.setStatus(Enums.ShipmentStatus.EXPORTED);
        shipments.save(shipment);
        return new ExportFile(bytes, "xlsx".equalsIgnoreCase(format) ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "text/csv", shipment.getShipmentCode() + "." + ("xlsx".equalsIgnoreCase(format) ? "xlsx" : "csv"));
    }
    public void enforceGate(Shipment shipment) {
        List<String> reasons = new ArrayList<>();
        List<ShipmentDocument> allDocuments = documents.findByShipmentId(shipment.getId());
        Map<Enums.DocumentType, ShipmentDocument> latestDocuments = allDocuments.stream().collect(java.util.stream.Collectors.toMap(ShipmentDocument::getDocType, d -> d, (a,b) -> a.getId() > b.getId() ? a : b));
        List<ShipmentDocument> shipmentDocs = new ArrayList<>(latestDocuments.values());
        for (Enums.DocumentType type : Enums.DocumentType.values()) if (shipmentDocs.stream().noneMatch(d -> d.getDocType() == type)) reasons.add("Required document is missing: " + type);
        Set<Long> activeDocumentIds = shipmentDocs.stream().map(ShipmentDocument::getId).collect(java.util.stream.Collectors.toSet());
        fields.findByDocumentShipmentId(shipment.getId()).stream().filter(f -> activeDocumentIds.contains(f.getDocument().getId())).filter(f -> f.getConfidenceScore().compareTo(confidenceThreshold) < 0 && !f.isHumanVerified()).forEach(f -> reasons.add("Low-confidence field " + f.getFieldKey() + " on " + f.getDocument().getDocType() + " must be human verified"));
        shipmentDocs.stream().filter(d -> d.isUnreadable() && !d.isHumanVerified()).forEach(d -> reasons.add("Unreadable document " + d.getFileName() + " must be human verified"));
        for (ShipmentDocument doc : shipmentDocs) {
            List<Enums.FieldKey> required = switch (doc.getDocType()) {
                case INVOICE -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name, Enums.FieldKey.invoice_number, Enums.FieldKey.invoice_date, Enums.FieldKey.currency, Enums.FieldKey.invoice_total);
                case PACKING_LIST -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg);
                case BILL_OF_LADING -> List.of(Enums.FieldKey.exporter_name, Enums.FieldKey.importer_name, Enums.FieldKey.bl_number, Enums.FieldKey.port_of_loading, Enums.FieldKey.port_of_discharge, Enums.FieldKey.total_packages, Enums.FieldKey.gross_weight_kg, Enums.FieldKey.container_number);
            };
            for (Enums.FieldKey key : required) {
                ExtractedField field = fields.findByDocumentIdAndFieldKey(doc.getId(), key).orElse(null);
                if (field == null || field.getCurrentValue() == null || field.getCurrentValue().isBlank()) reasons.add("Required field " + key + " is missing on " + doc.getDocType());
            }
            if (doc.getDocType() == Enums.DocumentType.INVOICE) {
                List<LineItem> invoiceItems = lineItems.findByDocumentIdOrderByItemNumber(doc.getId());
                if (invoiceItems.isEmpty()) reasons.add("Invoice must contain at least one verified line item");
                for (LineItem item : invoiceItems) {
                    if (item.getDescription() == null || item.getDescription().isBlank() || item.getQuantity() == null || item.getUnitPrice() == null || item.getLineTotal() == null)
                        reasons.add("Line item " + item.getItemNumber() + " on the invoice has missing values");
                    if (!item.isHumanVerified() && (item.getConfidenceScore() == null || item.getConfidenceScore().compareTo(confidenceThreshold) < 0)) reasons.add("Line item " + item.getItemNumber() + " on the invoice requires human verification");
                }
            }
            if (doc.getExtractionStatus() == Enums.ExtractionStatus.PROCESSING) reasons.add("Extraction is still processing for " + doc.getFileName());
        }
        discrepancies.findByShipmentId(shipment.getId()).stream().filter(d -> d.getStatus() == Enums.DiscrepancyStatus.OPEN).forEach(d -> reasons.add("Open discrepancy " + d.getRuleCode() + ": " + d.getExpectedOrDelta()));
        if (!reasons.isEmpty()) throw new ApiExceptions.ExportBlocked("Shipment blocked: " + String.join("; ", reasons));
    }
    private Shipment get(Long id) { return shipments.findById(id).orElseThrow(() -> new ApiExceptions.NotFound("Shipment not found: " + id)); }
    private void audit(Shipment shipment, User user, String action, String previous, String next, String note) { auditLogs.save(FieldAuditLog.builder().shipment(shipment).fieldName("Shipment workflow").actionType(action).oldValue(previous).newValue(next).changedByUser(user).changeReason(note).build()); }
    private byte[] csv(Shipment shipment) { try (StringWriter writer = new StringWriter()) { CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader("Shipment Code", "Document Type", "Field / Item", "Value", "Confidence", "Human Verified").build()); currentDocuments(shipment).forEach(doc -> { fields.findByDocumentId(doc.getId()).forEach(f -> print(printer, shipment.getShipmentCode(), doc.getDocType().name(), f.getFieldKey().name(), f.getCurrentValue(), f.getConfidenceScore(), f.isHumanVerified())); lineItems.findByDocumentIdOrderByItemNumber(doc.getId()).forEach(i -> print(printer, shipment.getShipmentCode(), doc.getDocType().name(), "Line item " + i.getItemNumber(), lineItemValue(i), i.getConfidenceScore(), i.isHumanVerified())); }); printer.flush(); return writer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8); } catch (IOException e) { throw new IllegalStateException("Unable to create CSV", e); } }
    private void print(CSVPrinter p, Object... values) { try { p.printRecord(values); } catch (IOException e) { throw new IllegalStateException(e); } }
    private byte[] xlsx(Shipment shipment) { try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) { Sheet sheet = workbook.createSheet("Shipment Data"); String[] headers = {"Shipment Code", "Document Type", "Field / Item", "Value", "Confidence", "Human Verified"}; Row header = sheet.createRow(0); for (int i=0;i<headers.length;i++) header.createCell(i).setCellValue(headers[i]); int row=1; for (ShipmentDocument doc : currentDocuments(shipment)) { for (ExtractedField f : fields.findByDocumentId(doc.getId())) { Row data = sheet.createRow(row++); data.createCell(0).setCellValue(shipment.getShipmentCode()); data.createCell(1).setCellValue(doc.getDocType().name()); data.createCell(2).setCellValue(f.getFieldKey().name()); data.createCell(3).setCellValue(Optional.ofNullable(f.getCurrentValue()).orElse("")); data.createCell(4).setCellValue(f.getConfidenceScore().doubleValue()); data.createCell(5).setCellValue(f.isHumanVerified()); } for (LineItem item : lineItems.findByDocumentIdOrderByItemNumber(doc.getId())) { Row data = sheet.createRow(row++); data.createCell(0).setCellValue(shipment.getShipmentCode()); data.createCell(1).setCellValue(doc.getDocType().name()); data.createCell(2).setCellValue("Line item " + item.getItemNumber()); data.createCell(3).setCellValue(lineItemValue(item)); data.createCell(4).setCellValue(item.getConfidenceScore() == null ? 0 : item.getConfidenceScore().doubleValue()); data.createCell(5).setCellValue(item.isHumanVerified()); } } for (int i=0;i<headers.length;i++) sheet.autoSizeColumn(i); workbook.write(output); return output.toByteArray(); } catch (IOException e) { throw new IllegalStateException("Unable to create Excel export", e); } }
    private List<ShipmentDocument> currentDocuments(Shipment shipment) { return documents.findByShipmentId(shipment.getId()).stream().collect(java.util.stream.Collectors.toMap(ShipmentDocument::getDocType, d -> d, (a,b) -> a.getId() > b.getId() ? a : b)).values().stream().sorted(java.util.Comparator.comparing(d -> d.getDocType().name())).toList(); }
    private String lineItemValue(LineItem item) { return String.join(" | ", Optional.ofNullable(item.getDescription()).orElse(""), Optional.ofNullable(item.getHsnCode()).orElse(""), Optional.ofNullable(item.getQuantity()).map(BigDecimal::toPlainString).orElse(""), Optional.ofNullable(item.getUnitPrice()).map(BigDecimal::toPlainString).orElse(""), Optional.ofNullable(item.getCurrency()).orElse(""), Optional.ofNullable(item.getLineTotal()).map(BigDecimal::toPlainString).orElse("")); }
    public record ExportFile(byte[] content, String contentType, String fileName) { }
}
