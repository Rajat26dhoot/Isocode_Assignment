import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, mapAuditLogs } from './api'
import type { AuditLog, DocumentType, Role, Shipment, SupplierAccuracy } from '../types/domain'

interface PendingPacket {
  files: Partial<Record<DocumentType, File>>
  unreadable: DocumentType[]
}

interface AppContextValue {
  role: Role
  setRole: (role: Role) => void
  authenticated: boolean
  signIn: (email: string, password: string) => Promise<void>
  signOut: () => void
  userName: string
  shipments: Shipment[]
  suppliers: SupplierAccuracy[]
  auditLogs: AuditLog[]
  liveApi: boolean
  loading: boolean
  apiError: string | null
  pendingPackets: Record<number, PendingPacket>
  confirmField: (shipmentId: number, fieldId: number, value?: string) => Promise<void>
  saveLineItem: (shipmentId: number, itemId: number, values: { description: string; hsnCode: string; quantity: number; unitPrice: number; currency: string; lineTotal: number }) => Promise<void>
  createLineItem: (shipmentId: number, documentId: number, values: { description: string; hsnCode: string; quantity: number; unitPrice: number; currency: string; lineTotal: number }) => Promise<void>
  markDocumentUnreadable: (shipmentId: number, documentId: number) => Promise<void>
  verifyDocument: (shipmentId: number, documentId: number) => Promise<void>
  retryExtraction: (shipmentId: number, documentId: number) => Promise<void>
  resolveDiscrepancy: (shipmentId: number, discrepancyId: number, note?: string) => Promise<void>
  approveShipment: (shipmentId: number) => Promise<void>
  submitShipment: (shipmentId: number) => Promise<void>
  rejectShipment: (shipmentId: number, reason: string) => Promise<void>
  refreshShipment: (shipmentId: number) => Promise<void>
  refreshAll: () => Promise<void>
  createShipment: (files: Partial<Record<DocumentType, File>>, unreadable: Partial<Record<DocumentType, boolean>>, details: { importerName: string; supplierName: string; supplierCountry?: string; importerContact?: string }) => Promise<number>
  takePendingPacket: (shipmentId: number) => PendingPacket | null
}

const Context = createContext<AppContextValue | null>(null)

function normalizeShipment(raw: Shipment): Shipment {
  return {
    ...raw,
    updatedAt: raw.updatedAt ?? new Date().toISOString(),
    documents: (raw.documents ?? []).map(document => ({
      ...document,
      fields: document.fields ?? [],
      lineItems: (document.lineItems ?? []).map(item => ({
        ...item,
        quantity: Number(item.quantity),
        unitPrice: Number(item.unitPrice),
        lineTotal: Number(item.lineTotal),
        confidenceScore: Number(item.confidenceScore),
        grossWeightKg: item.grossWeightKg == null ? undefined : Number(item.grossWeightKg),
      })),
    })),
    discrepancies: raw.discrepancies ?? [],
  }
}

function normalizeAccuracy(rows: SupplierAccuracy[]): SupplierAccuracy[] {
  return rows.map(row => ({
    ...row,
    accuracyPercentage: Number(row.accuracyPercentage),
  }))
}

export function AppProvider({ children }: { children: ReactNode }) {
  const liveApi = true
  const [role, setRoleState] = useState<Role>(api.getRole())
  const [userName, setUserName] = useState(api.getUserName())
  const [authenticated, setAuthenticated] = useState(api.hasToken())
  const [shipments, setShipments] = useState<Shipment[]>([])
  const [suppliers, setSuppliers] = useState<SupplierAccuracy[]>([])
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>([])
  const [loading, setLoading] = useState(true)
  const [apiError, setApiError] = useState<string | null>(null)
  const [pendingPackets, setPendingPackets] = useState<Record<number, PendingPacket>>({})

  const upsertShipment = useCallback((remote: Shipment) => {
    const normalized = normalizeShipment(remote)
    setShipments(current => {
      const exists = current.some(item => item.id === normalized.id)
      return exists ? current.map(item => item.id === normalized.id ? normalized : item) : [normalized, ...current]
    })
  }, [])

  const refreshAll = useCallback(async (forRole: Role = role) => {
    const [remoteShipments, remoteSuppliers, remoteAudit] = await Promise.all([
      api.getShipments(),
      forRole === 'ROLE_MANAGER' ? api.getAccuracy() : Promise.resolve([]),
      forRole === 'ROLE_MANAGER' ? api.getAuditLogs().catch(() => []) : Promise.resolve([]),
    ])
    const detailed = await Promise.all(remoteShipments.map(async summary => {
      try {
        return normalizeShipment(await api.getShipment(summary.id))
      } catch {
        return normalizeShipment({ ...summary, documents: [], discrepancies: [] } as Shipment)
      }
    }))
    setShipments(detailed)
    setSuppliers(normalizeAccuracy(remoteSuppliers))
    setAuditLogs(mapAuditLogs(remoteAudit))
  }, [liveApi, role])

  const signIn = useCallback(async (email: string, password: string) => {
    setLoading(true)
    setApiError(null)
    try {
      const login = await api.login(email, password)
      setAuthenticated(true)
      setRoleState(login.role)
      setUserName(login.fullName || login.email)
      await refreshAll(login.role)
    } catch (error) {
      api.setToken(null)
      setAuthenticated(false)
      setApiError(error instanceof Error ? error.message : 'Unable to connect to API')
      setShipments([])
      setSuppliers([])
      setAuditLogs([])
      throw error
    } finally {
      setLoading(false)
    }
  }, [refreshAll])

  const signOut = () => { api.logout(); setAuthenticated(false); setRoleState('ROLE_EXECUTIVE'); setUserName(''); setShipments([]); setSuppliers([]); setAuditLogs([]); setApiError(null) }

  useEffect(() => {
    if (!api.hasToken()) { setLoading(false); return }
    setLoading(true)
    void refreshAll().catch(error => { api.setToken(null); setAuthenticated(false); setApiError(error instanceof Error ? error.message : 'Unable to connect to API') }).finally(() => setLoading(false))
  }, [refreshAll])

  const setRole = (nextRole: Role) => setRoleState(nextRole)

  const refreshShipment = useCallback(async (shipmentId: number) => {
    const remote = normalizeShipment(await api.getShipment(shipmentId))
    upsertShipment(remote)
  }, [liveApi, upsertShipment])

  const confirmField = async (shipmentId: number, fieldId: number, value?: string) => {
    const shipment = shipments.find(item => item.id === shipmentId)
    const field = shipment?.documents.flatMap(document => document.fields).find(item => item.id === fieldId)
    const nextValue = value ?? field?.currentValue ?? ''
    setShipments(current => current.map(item => {
      if (item.id !== shipmentId) return item
      return {
        ...item,
        documents: item.documents.map(document => ({
          ...document,
          fields: document.fields.map(entry => entry.id !== fieldId ? entry : {
            ...entry,
            currentValue: nextValue,
            humanVerified: true,
            lowConfidence: false,
            wasEdited: nextValue !== entry.aiExtractedValue,
          }),
        })),
      }
    }))
    try {
      await api.editField(fieldId, nextValue, 'Human verification from review workspace')
      await refreshShipment(shipmentId)
      const remoteAudit = await api.getAuditLogs().catch(() => [])
      setAuditLogs(mapAuditLogs(remoteAudit))
      setSuppliers(normalizeAccuracy(await api.getAccuracy()))
    } catch (error) {
      await refreshShipment(shipmentId)
      throw error
    }
  }

  const saveLineItem = async (shipmentId: number, itemId: number, values: { description: string; hsnCode: string; quantity: number; unitPrice: number; currency: string; lineTotal: number }) => {
    await api.editLineItem(itemId, values)
    await refreshShipment(shipmentId)
  }
  const createLineItem = async (shipmentId: number, documentId: number, values: { description: string; hsnCode: string; quantity: number; unitPrice: number; currency: string; lineTotal: number }) => {
    await api.createLineItem(documentId, values)
    await refreshShipment(shipmentId)
  }

  const markDocumentUnreadable = async (shipmentId: number, documentId: number) => {
    setShipments(current => current.map(shipment => shipment.id !== shipmentId ? shipment : {
      ...shipment,
      documents: shipment.documents.map(document => document.id !== documentId ? document : {
        ...document,
        unreadable: true,
        humanVerified: false,
        extractionStatus: 'FAILED',
        fields: document.fields.map(field => ({ ...field, currentValue: '', lowConfidence: true, humanVerified: false })),
      }),
    }))
    try {
      upsertShipment(await api.markUnreadable(documentId))
    } catch (error) {
      await refreshShipment(shipmentId)
      throw error
    }
  }

  const verifyDocument = async (shipmentId: number, documentId: number) => {
    setShipments(current => current.map(shipment => shipment.id !== shipmentId ? shipment : {
      ...shipment,
      documents: shipment.documents.map(document => document.id !== documentId ? document : { ...document, humanVerified: true }),
    }))
    try {
      upsertShipment(await api.verifyDocument(documentId))
    } catch (error) {
      await refreshShipment(shipmentId)
      throw error
    }
  }

  const retryExtraction = async (shipmentId: number, documentId: number) => { upsertShipment(await api.retryExtraction(documentId)); await refreshShipment(shipmentId) }

  const resolveDiscrepancy = async (shipmentId: number, discrepancyId: number, note = 'Reviewed and resolved by documentation executive') => {
    setShipments(current => current.map(shipment => shipment.id !== shipmentId ? shipment : {
      ...shipment,
      discrepancies: shipment.discrepancies.map(item => item.id !== discrepancyId ? item : {
        ...item,
        status: role === 'ROLE_MANAGER' ? 'MANAGER_OVERRIDDEN' : 'RESOLVED',
        resolutionNote: note,
      }),
    }))
    try {
      upsertShipment(await api.resolveDiscrepancy(discrepancyId, note, role === 'ROLE_MANAGER'))
    } catch (error) {
      await refreshShipment(shipmentId)
      throw error
    }
  }

  const approveShipment = async (shipmentId: number) => {
    upsertShipment(await api.approve(shipmentId))
  }

  const submitShipment = async (shipmentId: number) => { upsertShipment(await api.submit(shipmentId)) }
  const rejectShipment = async (shipmentId: number, reason: string) => { upsertShipment(await api.reject(shipmentId, reason)) }

  const createShipment = async (files: Partial<Record<DocumentType, File>>, unreadable: Partial<Record<DocumentType, boolean>>, details: { importerName: string; supplierName: string; supplierCountry?: string; importerContact?: string }) => {
    const created = normalizeShipment(await api.createShipment(details))
    upsertShipment(created)
    let processed = created
    const failures: string[] = []
    for (const docType of ['INVOICE', 'PACKING_LIST', 'BILL_OF_LADING'] as DocumentType[]) {
      const file = files[docType]
      try {
        if (file) processed = normalizeShipment(await api.extractDocument(created.id, docType, file))
        else if (unreadable[docType]) processed = normalizeShipment(await api.createUnreadableDocument(created.id, docType))
      } catch (error) { failures.push(`${docType}: ${error instanceof Error ? error.message : 'upload failed'}`) }
    }
    try { processed = normalizeShipment(await api.runCrossCheck(created.id)) }
    catch (error) { failures.push(`Validation: ${error instanceof Error ? error.message : 'could not run'}`) }
    upsertShipment(processed)
    if (failures.length) setApiError(`Shipment created. Some documents need attention: ${failures.join('; ')}`)
    return created.id
  }

  const takePendingPacket = (shipmentId: number) => {
    const packet = pendingPackets[shipmentId] ?? null
    if (packet) {
      setPendingPackets(current => {
        const next = { ...current }
        delete next[shipmentId]
        return next
      })
    }
    return packet
  }

  const value = useMemo(() => ({
    role,
    setRole,
    authenticated,
    signIn,
    signOut,
    userName,
    shipments,
    suppliers,
    auditLogs,
    liveApi,
    loading,
    apiError,
    pendingPackets,
    confirmField,
    saveLineItem,
    createLineItem,
    markDocumentUnreadable,
    verifyDocument,
    retryExtraction,
    resolveDiscrepancy,
    approveShipment,
    submitShipment,
    rejectShipment,
    refreshShipment,
    refreshAll,
    createShipment,
    takePendingPacket,
  }), [role, userName, authenticated, signIn, shipments, suppliers, auditLogs, liveApi, loading, apiError, pendingPackets, refreshShipment, refreshAll])

  return <Context.Provider value={value}>{children}</Context.Provider>
}

export function useApp() {
  const value = useContext(Context)
  if (!value) throw new Error('useApp must be used inside AppProvider')
  return value
}
