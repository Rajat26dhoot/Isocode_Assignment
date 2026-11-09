export type Role = 'ROLE_EXECUTIVE' | 'ROLE_MANAGER'
export type ShipmentStatus = 'DRAFT' | 'DOCUMENTS_PENDING' | 'EXTRACTING' | 'NEEDS_REVIEW' | 'VALIDATION_FAILED' | 'PENDING_APPROVAL' | 'READY_FOR_APPROVAL' | 'APPROVED' | 'REJECTED' | 'EXPORTED'
export type DocumentType = 'INVOICE' | 'PACKING_LIST' | 'BILL_OF_LADING'
export type RuleCode = 'INVOICE_TOTAL_VS_LINE_SUM' | 'LINE_ARITHMETIC' | 'GROSS_WEIGHT_MISMATCH' | 'NET_WEIGHT_MISMATCH' | 'PACKAGE_COUNT_MISMATCH' | 'PARTY_NAME_MISMATCH' | 'CURRENCY_MISMATCH' | 'CONTAINER_MISMATCH' | 'ITEM_OR_QUANTITY_MISMATCH'

export type FieldKey = 'exporter_name' | 'importer_name' | 'invoice_number' | 'invoice_date' | 'bl_number' | 'port_of_loading' | 'port_of_discharge' | 'currency' | 'incoterm' | 'total_packages' | 'gross_weight_kg' | 'net_weight_kg' | 'container_number' | 'invoice_total'

export interface Field {
  id: number
  documentType: DocumentType
  fieldKey: FieldKey
  fieldLabel: string
  aiExtractedValue: string
  currentValue: string
  confidenceScore: number
  lowConfidence: boolean
  humanVerified: boolean
  wasEdited: boolean
  pageNumber: number
}

export interface LineItem {
  id: number
  itemNumber: number
  description: string
  hsnCode: string
  quantity: number
  unitPrice: number
  currency: string
  lineTotal: number
  grossWeightKg?: number
  confidenceScore: number
  humanVerified: boolean
}

export interface ShipmentDocument {
  id: number
  docType: DocumentType
  fileName: string
  pageCount: number
  unreadable: boolean
  humanVerified: boolean
  extractionStatus: 'PENDING' | 'PROCESSING' | 'COMPLETED' | 'FAILED'
  fields: Field[]
  lineItems: LineItem[]
}

export interface Discrepancy {
  id: number
  ruleCode: RuleCode
  severity: 'CRITICAL' | 'WARNING'
  invoiceValue?: string
  packingListValue?: string
  bolValue?: string
  expectedOrDelta: string
  status: 'OPEN' | 'RESOLVED' | 'MANAGER_OVERRIDDEN'
  resolutionNote?: string
}

export interface Shipment {
  id: number
  shipmentCode: string
  importerName: string
  supplier: string
  status: ShipmentStatus
  updatedAt: string
  documents: ShipmentDocument[]
  discrepancies: Discrepancy[]
  rejectionReason?: string
}

export interface SupplierAccuracy {
  supplierId: number
  supplierName: string
  totalFieldsExtracted: number
  totalFieldsCorrected: number
  accuracyPercentage: number
}

export interface AuditLog {
  id: number
  timestamp: string
  shipment: string
  user: string
  document: DocumentType
  field: string
  actionType: string
  aiValue: string
  correctedValue: string
  reason: string
}
