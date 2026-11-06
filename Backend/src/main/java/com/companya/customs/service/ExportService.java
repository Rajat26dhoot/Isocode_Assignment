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
import java.io.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service @RequiredArgsConstructor
public class ExportService {
    private final ShipmentRepository shipments; private final ShipmentDocumentRepository documents; private final ExtractedFieldRepository fields; private final CrossCheckDiscrepancyRepository discrepancies; private final UserRepository users;
    @Transactional public Shipment approve(Long shipmentId, User manager) { Shipment shipment = get(shipmentId); enforceGate(shipment); shipment.setStatus(Enums.ShipmentStatus.APPROVED); shipment.setApprovedByManager(manager); shipment.setApprovedAt(Instant.now()); return shipments.save(shipment); }
    @Transactional public ExportFile export(Long shipmentId, String format, User user) {
        Shipment shipment = get(shipmentId);
        enforceGate(shipment);
        if (shipment.getStatus() != Enums.ShipmentStatus.APPROVED && shipment.getStatus() != Enums.ShipmentStatus.EXPORTED) {
            throw new ApiExceptions.ExportBlocked("Shipment must be APPROVED before export");
        }
        byte[] bytes = "xlsx".equalsIgnoreCase(format) ? xlsx(shipment) : csv(shipment);
        shipment.setStatus(Enums.ShipmentStatus.EXPORTED);
        shipments.save(shipment);
        return new ExportFile(bytes, "xlsx".equalsIgnoreCase(format) ? "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" : "text/csv", shipment.getShipmentCode() + "." + ("xlsx".equalsIgnoreCase(format) ? "xlsx" : "csv"));
    }
    public void enforceGate(Shipment shipment) {
        List<String> reasons = new ArrayList<>();
        fields.findByDocumentShipmentId(shipment.getId()).stream().filter(f -> f.getConfidenceScore().compareTo(new BigDecimal("0.850")) < 0 && !f.isHumanVerified()).forEach(f -> reasons.add("Low-confidence field " + f.getFieldKey() + " on " + f.getDocument().getDocType() + " must be human verified"));
        documents.findByShipmentId(shipment.getId()).stream().filter(d -> d.isUnreadable() && !d.isHumanVerified()).forEach(d -> reasons.add("Unreadable document " + d.getFileName() + " must be human verified"));
        discrepancies.findByShipmentId(shipment.getId()).stream().filter(d -> d.getStatus() == Enums.DiscrepancyStatus.OPEN).forEach(d -> reasons.add("Open discrepancy " + d.getRuleCode() + ": " + d.getExpectedOrDelta()));
        if (!reasons.isEmpty()) throw new ApiExceptions.ExportBlocked("Shipment blocked: " + String.join("; ", reasons));
    }
    private Shipment get(Long id) { return shipments.findById(id).orElseThrow(() -> new ApiExceptions.NotFound("Shipment not found: " + id)); }
    private byte[] csv(Shipment shipment) { try (StringWriter writer = new StringWriter()) { CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader("Shipment Code", "Document Type", "Field / Item", "Value", "Confidence", "Human Verified").build()); documents.findByShipmentId(shipment.getId()).forEach(doc -> { fields.findByDocumentId(doc.getId()).forEach(f -> print(printer, shipment.getShipmentCode(), doc.getDocType().name(), f.getFieldKey().name(), f.getCurrentValue(), f.getConfidenceScore(), f.isHumanVerified())); }); printer.flush(); return writer.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8); } catch (IOException e) { throw new IllegalStateException("Unable to create CSV", e); } }
    private void print(CSVPrinter p, Object... values) { try { p.printRecord(values); } catch (IOException e) { throw new IllegalStateException(e); } }
    private byte[] xlsx(Shipment shipment) { try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) { Sheet sheet = workbook.createSheet("Customs Filing"); String[] headers = {"Shipment Code", "Document Type", "Field / Item", "Value", "Confidence", "Human Verified"}; Row header = sheet.createRow(0); for (int i=0;i<headers.length;i++) header.createCell(i).setCellValue(headers[i]); int row=1; for (ShipmentDocument doc : documents.findByShipmentId(shipment.getId())) for (ExtractedField f : fields.findByDocumentId(doc.getId())) { Row data = sheet.createRow(row++); data.createCell(0).setCellValue(shipment.getShipmentCode()); data.createCell(1).setCellValue(doc.getDocType().name()); data.createCell(2).setCellValue(f.getFieldKey().name()); data.createCell(3).setCellValue(Optional.ofNullable(f.getCurrentValue()).orElse("")); data.createCell(4).setCellValue(f.getConfidenceScore().doubleValue()); data.createCell(5).setCellValue(f.isHumanVerified()); } for (int i=0;i<headers.length;i++) sheet.autoSizeColumn(i); workbook.write(output); return output.toByteArray(); } catch (IOException e) { throw new IllegalStateException("Unable to create Excel export", e); } }
    public record ExportFile(byte[] content, String contentType, String fileName) { }
}
