package com.companya.customs.config;

import com.companya.customs.entity.*;
import com.companya.customs.repository.*;
import com.companya.customs.service.CrossCheckService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;

@Configuration @RequiredArgsConstructor
public class DataSeeder {
    private final UserRepository users; private final SupplierRepository suppliers; private final ShipmentRepository shipments; private final ShipmentDocumentRepository documents; private final ExtractedFieldRepository fields; private final LineItemRepository lineItems; private final PasswordEncoder encoder; private final CrossCheckService crossChecks;
    @Bean CommandLineRunner seedData() { return args -> seed(); }
    @Transactional
    public void seed() {
        if (users.count() > 0) return;
        User exec = users.save(User.builder().fullName("Company A Executive").email("exec@companya.in").passwordHash(encoder.encode("password123")).role(Enums.Role.ROLE_EXECUTIVE).build());
        User manager = users.save(User.builder().fullName("Company A Manager").email("manager@companya.in").passwordHash(encoder.encode("password123")).role(Enums.Role.ROLE_MANAGER).build());
        Supplier china = suppliers.save(Supplier.builder().name("Company B (China)").country("China").build()); Supplier sweden = suppliers.save(Supplier.builder().name("Company C (Sweden)").country("Sweden").build());
        Shipment s1 = shipments.save(Shipment.builder().shipmentCode("SHP-24811").importerName("Company A").importerContactEncrypted("+91-9876543210").supplier(china).assignedExecutive(exec).status(Enums.ShipmentStatus.NEEDS_REVIEW).build());
        Shipment s2 = shipments.save(Shipment.builder().shipmentCode("SHP-24812").importerName("Company A").importerContactEncrypted("+91-9876543211").supplier(sweden).assignedExecutive(exec).status(Enums.ShipmentStatus.NEEDS_REVIEW).build());
        seedShipment1(s1); seedShipment2(s2); crossChecks.runForShipment(s1.getId()); crossChecks.runForShipment(s2.getId());
    }
    private void seedShipment1(Shipment shipment) {
        ShipmentDocument invoice = doc(shipment, Enums.DocumentType.INVOICE, "SHP-24811-invoice.pdf"); ShipmentDocument packing = doc(shipment, Enums.DocumentType.PACKING_LIST, "SHP-24811-packing-list.pdf"); ShipmentDocument bol = doc(shipment, Enums.DocumentType.BILL_OF_LADING, "SHP-24811-bol.pdf");
        field(invoice, Enums.FieldKey.invoice_total, "Invoice Total", "16320.00", ".92"); field(invoice, Enums.FieldKey.currency, "Currency", "USD", ".96"); field(invoice, Enums.FieldKey.total_packages, "Total Packages", "120", ".95"); field(invoice, Enums.FieldKey.gross_weight_kg, "Gross Weight (kg)", "3120", ".92"); field(invoice, Enums.FieldKey.incoterm, "Incoterm", "FOB Shenzhen", ".78"); field(invoice, Enums.FieldKey.exporter_name, "Exporter Name", "Company B", ".91"); field(invoice, Enums.FieldKey.importer_name, "Importer Name", "Company A", ".91");
        field(packing, Enums.FieldKey.total_packages, "Total Packages", "120", ".95"); field(packing, Enums.FieldKey.gross_weight_kg, "Gross Weight (kg)", "3165", ".93"); field(packing, Enums.FieldKey.exporter_name, "Exporter Name", "Company B", ".91"); field(packing, Enums.FieldKey.importer_name, "Importer Name", "Company A", ".91");
        field(bol, Enums.FieldKey.total_packages, "Total Packages", "120", ".95"); field(bol, Enums.FieldKey.gross_weight_kg, "Gross Weight (kg)", "3165", ".91"); field(bol, Enums.FieldKey.container_number, "Container Number", "MSCU8492014", ".81"); field(bol, Enums.FieldKey.exporter_name, "Exporter Name", "Company B", ".91"); field(bol, Enums.FieldKey.importer_name, "Importer Name", "Company A", ".91");
        item(invoice, "LED panel light 40W", "94051100", "2400", "6.80", "USD", "16320.00", ".96");
    }
    private void seedShipment2(Shipment shipment) {
        ShipmentDocument invoice = doc(shipment, Enums.DocumentType.INVOICE, "SHP-24812-invoice.pdf"); ShipmentDocument packing = doc(shipment, Enums.DocumentType.PACKING_LIST, "SHP-24812-packing-list.pdf"); ShipmentDocument bol = doc(shipment, Enums.DocumentType.BILL_OF_LADING, "SHP-24812-bol.pdf");
        for (ShipmentDocument document : List.of(invoice, packing, bol)) { field(document, Enums.FieldKey.total_packages, "Total Packages", "18", ".95"); field(document, Enums.FieldKey.gross_weight_kg, "Gross Weight (kg)", "1460", ".95"); field(document, Enums.FieldKey.exporter_name, "Exporter Name", "Company C", ".95"); field(document, Enums.FieldKey.importer_name, "Importer Name", "Company A", ".95"); }
        field(invoice, Enums.FieldKey.invoice_total, "Invoice Total", "7650.00", ".95"); field(invoice, Enums.FieldKey.currency, "Currency", "EUR", ".95"); field(invoice, Enums.FieldKey.incoterm, "Incoterm", "CIF Nhava Sheva", ".95"); item(invoice, "Steel ball valve DN50", "84818030", "180", "42.50", "EUR", "7650.00", ".96"); field(bol, Enums.FieldKey.container_number, "Container Seal", "SEAL-C-24812", ".74");
    }
    private ShipmentDocument doc(Shipment shipment, Enums.DocumentType type, String fileName) { return documents.save(ShipmentDocument.builder().shipment(shipment).docType(type).fileName(fileName).pageCount(1).extractionStatus(Enums.ExtractionStatus.COMPLETED).build()); }
    private void field(ShipmentDocument document, Enums.FieldKey key, String label, String value, String confidence) { BigDecimal score = new BigDecimal(confidence); fields.save(ExtractedField.builder().document(document).fieldKey(key).fieldLabel(label).aiExtractedValue(value).currentValue(value).confidenceScore(score).isLowConfidence(score.compareTo(new BigDecimal(".85")) < 0).pageNumber(1).build()); document.getShipment().getSupplier().setTotalFieldsExtracted(document.getShipment().getSupplier().getTotalFieldsExtracted() + 1); suppliers.save(document.getShipment().getSupplier()); }
    private void item(ShipmentDocument document, String description, String hsn, String quantity, String price, String currency, String total, String confidence) { lineItems.save(LineItem.builder().document(document).itemNumber(1).description(description).hsnCode(hsn).quantity(new BigDecimal(quantity)).unitPrice(new BigDecimal(price)).currency(currency).lineTotal(new BigDecimal(total)).confidenceScore(new BigDecimal(confidence)).build()); }
}
