import type { AuditLog, Shipment, SupplierAccuracy } from '../types/domain'

const field = (id: number, documentType: 'INVOICE' | 'PACKING_LIST' | 'BILL_OF_LADING', fieldKey: any, fieldLabel: string, value: string, confidence = 0.94, humanVerified = false) => ({ id, documentType, fieldKey, fieldLabel, aiExtractedValue: value, currentValue: value, confidenceScore: confidence, lowConfidence: confidence < 0.85, humanVerified, wasEdited: false, pageNumber: 1 })
const line = (id: number, description: string, hsnCode: string, quantity: number, unitPrice: number, currency: string, lineTotal: number, confidence = 0.96) => ({ id, itemNumber: 1, description, hsnCode, quantity, unitPrice, currency, lineTotal, confidenceScore: confidence, humanVerified: false })

export const demoShipments: Shipment[] = [
  {
    id: 24811, shipmentCode: 'SHP-24811', importerName: 'Company A', supplier: 'Company B (China)', status: 'NEEDS_REVIEW', updatedAt: '2026-11-05T09:32:00Z',
    documents: [
      { id: 101, docType: 'INVOICE', fileName: 'SHP-24811-invoice.pdf', pageCount: 2, unreadable: false, humanVerified: false, extractionStatus: 'COMPLETED', fields: [field(1001, 'INVOICE', 'invoice_total', 'Invoice Total', '16,320.00'), field(1002, 'INVOICE', 'currency', 'Currency', 'USD'), field(1003, 'INVOICE', 'total_packages', 'Total Packages', '120'), field(1004, 'INVOICE', 'gross_weight_kg', 'Gross Weight (kg)', '3120'), field(1005, 'INVOICE', 'incoterm', 'Incoterm', 'FOB Shenzhen', 0.78), field(1006, 'INVOICE', 'exporter_name', 'Exporter Name', 'Company B'), field(1007, 'INVOICE', 'importer_name', 'Importer Name', 'Company A')], lineItems: [line(1101, 'LED panel light 40W', '94051100', 2400, 6.8, 'USD', 16320)] },
      { id: 102, docType: 'PACKING_LIST', fileName: 'SHP-24811-packing-list.pdf', pageCount: 1, unreadable: false, humanVerified: false, extractionStatus: 'COMPLETED', fields: [field(1010, 'PACKING_LIST', 'total_packages', 'Total Packages', '120'), field(1011, 'PACKING_LIST', 'gross_weight_kg', 'Gross Weight (kg)', '3165'), field(1012, 'PACKING_LIST', 'exporter_name', 'Exporter Name', 'Company B'), field(1013, 'PACKING_LIST', 'importer_name', 'Importer Name', 'Company A')], lineItems: [line(1102, 'LED panel light 40W', '—', 2400, 0, 'USD', 0)] },
      { id: 103, docType: 'BILL_OF_LADING', fileName: 'SHP-24811-bol.pdf', pageCount: 1, unreadable: false, humanVerified: false, extractionStatus: 'COMPLETED', fields: [field(1020, 'BILL_OF_LADING', 'total_packages', 'Total Packages', '120'), field(1021, 'BILL_OF_LADING', 'gross_weight_kg', 'Gross Weight (kg)', '3165'), field(1022, 'BILL_OF_LADING', 'container_number', 'Container Number', 'MSCU8492014', 0.81), field(1023, 'BILL_OF_LADING', 'exporter_name', 'Exporter Name', 'Company B'), field(1024, 'BILL_OF_LADING', 'importer_name', 'Importer Name', 'Company A')], lineItems: [] },
    ], discrepancies: [{ id: 9001, ruleCode: 'GROSS_WEIGHT_MISMATCH', severity: 'WARNING', invoiceValue: '3120', packingListValue: '3165', bolValue: '3165', expectedOrDelta: 'delta=45 kg', status: 'OPEN' }],
  },
  {
    id: 24812, shipmentCode: 'SHP-24812', importerName: 'Company A', supplier: 'Company C (Sweden)', status: 'NEEDS_REVIEW', updatedAt: '2026-11-05T08:18:00Z', documents: [
      { id: 201, docType: 'INVOICE', fileName: 'SHP-24812-invoice.pdf', pageCount: 1, unreadable: false, humanVerified: false, extractionStatus: 'COMPLETED', fields: [field(2001, 'INVOICE', 'invoice_total', 'Invoice Total', '7,650.00'), field(2002, 'INVOICE', 'currency', 'Currency', 'EUR'), field(2003, 'INVOICE', 'total_packages', 'Total Packages', '18'), field(2004, 'INVOICE', 'gross_weight_kg', 'Gross Weight (kg)', '1460'), field(2005, 'INVOICE', 'incoterm', 'Incoterm', 'CIF Nhava Sheva')], lineItems: [line(2101, 'Steel ball valve DN50', '84818030', 180, 42.5, 'EUR', 7650)] },
      { id: 202, docType: 'PACKING_LIST', fileName: 'SHP-24812-packing-list.pdf', pageCount: 1, unreadable: false, humanVerified: false, extractionStatus: 'COMPLETED', fields: [field(2010, 'PACKING_LIST', 'total_packages', 'Total Packages', '18'), field(2011, 'PACKING_LIST', 'gross_weight_kg', 'Gross Weight (kg)', '1460')], lineItems: [] },
      { id: 203, docType: 'BILL_OF_LADING', fileName: 'SHP-24812-bol.pdf', pageCount: 1, unreadable: false, humanVerified: false, extractionStatus: 'COMPLETED', fields: [field(2020, 'BILL_OF_LADING', 'total_packages', 'Total Packages', '18'), field(2021, 'BILL_OF_LADING', 'gross_weight_kg', 'Gross Weight (kg)', '1460'), field(2022, 'BILL_OF_LADING', 'container_number', 'Container Seal', 'SEAL-C-24812', 0.74)], lineItems: [] },
    ], discrepancies: [],
  },
]

export const demoSuppliers: SupplierAccuracy[] = [
  { supplierId: 1, supplierName: 'Company B (China)', totalFieldsExtracted: 15, totalFieldsCorrected: 3, accuracyPercentage: 80 },
  { supplierId: 2, supplierName: 'Company C (Sweden)', totalFieldsExtracted: 12, totalFieldsCorrected: 1, accuracyPercentage: 91.67 },
]

export const demoAuditLogs: AuditLog[] = [
  { id: 1, timestamp: '05 Nov 2026, 09:41', user: 'Rajat Mehta', document: 'INVOICE', field: 'Incoterm', aiValue: 'FOB Shenzen', correctedValue: 'FOB Shenzhen', reason: 'Corrected supplier city spelling' },
  { id: 2, timestamp: '05 Nov 2026, 09:38', user: 'Rajat Mehta', document: 'INVOICE', field: 'Gross Weight (kg)', aiValue: '3,120', correctedValue: '3,165', reason: 'Verified against packing list' },
  { id: 3, timestamp: '04 Nov 2026, 16:12', user: 'Mira Shah', document: 'BILL_OF_LADING', field: 'Container Number', aiValue: 'MSCU8492014', correctedValue: 'MSCU8492014', reason: 'Human verification' },
]
