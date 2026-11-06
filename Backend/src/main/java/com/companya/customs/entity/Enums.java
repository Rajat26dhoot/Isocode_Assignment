package com.companya.customs.entity;

public final class Enums {
    private Enums() { }
    public enum Role { ROLE_EXECUTIVE, ROLE_MANAGER }
    public enum ShipmentStatus { DRAFT, EXTRACTING, NEEDS_REVIEW, READY_FOR_APPROVAL, APPROVED, EXPORTED }
    public enum DocumentType { INVOICE, PACKING_LIST, BILL_OF_LADING }
    public enum ExtractionStatus { PENDING, PROCESSING, COMPLETED, FAILED }
    public enum FieldKey { exporter_name, importer_name, invoice_number, invoice_date, currency, incoterm, total_packages, gross_weight_kg, net_weight_kg, container_number, invoice_total }
    public enum RuleCode { INVOICE_TOTAL_VS_LINE_SUM, GROSS_WEIGHT_MISMATCH, NET_WEIGHT_MISMATCH, PACKAGE_COUNT_MISMATCH, PARTY_NAME_MISMATCH }
    public enum Severity { CRITICAL, WARNING }
    public enum DiscrepancyStatus { OPEN, RESOLVED, MANAGER_OVERRIDDEN }
}
