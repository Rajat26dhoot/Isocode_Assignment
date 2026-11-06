import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import { api } from './api'
import { demoAuditLogs, demoShipments, demoSuppliers } from '../data/demoData'
import type { AuditLog, Role, Shipment, SupplierAccuracy } from '../types/domain'

interface AppContextValue {
  role: Role
  setRole: (role: Role) => void
  userName: string
  shipments: Shipment[]
  suppliers: SupplierAccuracy[]
  auditLogs: AuditLog[]
  liveApi: boolean
  confirmField: (shipmentId: number, fieldId: number, value?: string) => void
  markDocumentUnreadable: (shipmentId: number, documentId: number) => void
  verifyDocument: (shipmentId: number, documentId: number) => void
  resolveDiscrepancy: (shipmentId: number, discrepancyId: number, note?: string) => void
  approveShipment: (shipmentId: number) => void
  refreshShipment: (shipmentId: number) => void
  createShipment: () => number
}

const Context = createContext<AppContextValue | null>(null)

export function AppProvider({ children }: { children: ReactNode }) {
  const [role, setRole] = useState<Role>('ROLE_EXECUTIVE')
  const [shipments, setShipments] = useState<Shipment[]>(structuredClone(demoShipments))
  const [suppliers, setSuppliers] = useState<SupplierAccuracy[]>(demoSuppliers)
  const [auditLogs] = useState<AuditLog[]>(demoAuditLogs)
  const [liveApi] = useState(Boolean(import.meta.env.VITE_API_URL))

  const confirmField = (shipmentId: number, fieldId: number, value?: string) => {
    setShipments(current => current.map(shipment => {
      if (shipment.id !== shipmentId) return shipment
      return { ...shipment, documents: shipment.documents.map(document => ({ ...document, fields: document.fields.map(field => field.id !== fieldId ? field : { ...field, currentValue: value ?? field.currentValue, humanVerified: true, lowConfidence: false, wasEdited: value !== undefined && value !== field.aiExtractedValue }) })) }
    }))
    api.editField(fieldId, value ?? '', 'Human verification from review workspace').catch(() => undefined)
  }

  const markDocumentUnreadable = (shipmentId: number, documentId: number) => {
    setShipments(current => current.map(shipment => shipment.id !== shipmentId ? shipment : { ...shipment, documents: shipment.documents.map(document => document.id !== documentId ? document : { ...document, unreadable: true, humanVerified: false, extractionStatus: 'FAILED', fields: document.fields.map(field => ({ ...field, currentValue: '', lowConfidence: true, humanVerified: false })) }) }))
    api.markUnreadable(documentId).catch(() => undefined)
  }

  const verifyDocument = (shipmentId: number, documentId: number) => {
    setShipments(current => current.map(shipment => shipment.id !== shipmentId ? shipment : { ...shipment, documents: shipment.documents.map(document => document.id !== documentId ? document : { ...document, humanVerified: true }) }))
    api.verifyDocument(documentId).catch(() => undefined)
  }

  const resolveDiscrepancy = (shipmentId: number, discrepancyId: number, note = 'Reviewed and resolved by documentation executive') => {
    setShipments(current => current.map(shipment => shipment.id !== shipmentId ? shipment : { ...shipment, discrepancies: shipment.discrepancies.map(item => item.id !== discrepancyId ? item : { ...item, status: role === 'ROLE_MANAGER' ? 'MANAGER_OVERRIDDEN' : 'RESOLVED', resolutionNote: note }) }))
    api.resolveDiscrepancy(discrepancyId, note, role === 'ROLE_MANAGER').catch(() => undefined)
  }

  const approveShipment = (shipmentId: number) => {
    setShipments(current => current.map(shipment => shipment.id !== shipmentId ? shipment : { ...shipment, status: 'APPROVED' }))
    api.approve(shipmentId).catch(() => undefined)
  }

  const refreshShipment = (shipmentId: number) => {
    api.getShipment(shipmentId).then(remote => setShipments(current => current.map(shipment => shipment.id === shipmentId ? remote : shipment))).catch(() => undefined)
  }

  const createShipment = () => {
    const nextId = Math.max(...shipments.map(item => item.id)) + 1
    const source = structuredClone(shipments[0])
    const created: Shipment = { ...source, id: nextId, shipmentCode: `SHP-${nextId}`, status: 'EXTRACTING', updatedAt: new Date().toISOString(), supplier: 'New supplier · pending', discrepancies: [] }
    setShipments(current => [created, ...current])
    return nextId
  }

  const value = useMemo(() => ({ role, setRole, userName: role === 'ROLE_MANAGER' ? 'Mira Shah' : 'Rajat Mehta', shipments, suppliers, auditLogs, liveApi, confirmField, markDocumentUnreadable, verifyDocument, resolveDiscrepancy, approveShipment, refreshShipment, createShipment }), [role, shipments, suppliers, auditLogs, liveApi])
  return <Context.Provider value={value}>{children}</Context.Provider>
}

export function useApp() { const value = useContext(Context); if (!value) throw new Error('useApp must be used inside AppProvider'); return value }
