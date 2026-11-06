import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, mapAuditLogs } from './api'
import { demoAuditLogs, demoShipments, demoSuppliers } from '../data/demoData'
import type { AuditLog, DocumentType, Role, Shipment, SupplierAccuracy } from '../types/domain'

interface PendingPacket {
  files: Partial<Record<DocumentType, File>>
  unreadable: DocumentType[]
}

interface AppContextValue {
  role: Role
  setRole: (role: Role) => void
  userName: string
  shipments: Shipment[]
  suppliers: SupplierAccuracy[]
  auditLogs: AuditLog[]
  liveApi: boolean
  loading: boolean
  apiError: string | null
  pendingPackets: Record<number, PendingPacket>
  confirmField: (shipmentId: number, fieldId: number, value?: string) => Promise<void>
  markDocumentUnreadable: (shipmentId: number, documentId: number) => Promise<void>
  verifyDocument: (shipmentId: number, documentId: number) => Promise<void>
  resolveDiscrepancy: (shipmentId: number, discrepancyId: number, note?: string) => Promise<void>
  approveShipment: (shipmentId: number) => Promise<void>
  refreshShipment: (shipmentId: number) => Promise<void>
  refreshAll: () => Promise<void>
  createShipment: (files: Partial<Record<DocumentType, File>>, unreadable: Partial<Record<DocumentType, boolean>>) => Promise<number>
  takePendingPacket: (shipmentId: number) => PendingPacket | null
}

const Context = createContext<AppContextValue | null>(null)

const credentials: Record<Role, { email: string; password: string; name: string }> = {
  ROLE_EXECUTIVE: { email: 'exec@companya.in', password: 'password123', name: 'Rajat Mehta' },
  ROLE_MANAGER: { email: 'manager@companya.in', password: 'password123', name: 'Mira Shah' },
}

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
  const liveApi = Boolean(import.meta.env.VITE_API_URL)
  const [role, setRoleState] = useState<Role>('ROLE_EXECUTIVE')
  const [userName, setUserName] = useState(credentials.ROLE_EXECUTIVE.name)
  const [shipments, setShipments] = useState<Shipment[]>(liveApi ? [] : structuredClone(demoShipments))
  const [suppliers, setSuppliers] = useState<SupplierAccuracy[]>(liveApi ? [] : demoSuppliers)
  const [auditLogs, setAuditLogs] = useState<AuditLog[]>(liveApi ? [] : demoAuditLogs)
  const [loading, setLoading] = useState(liveApi)
  const [apiError, setApiError] = useState<string | null>(null)
  const [pendingPackets, setPendingPackets] = useState<Record<number, PendingPacket>>({})

  const upsertShipment = useCallback((remote: Shipment) => {
    const normalized = normalizeShipment(remote)
    setShipments(current => {
      const exists = current.some(item => item.id === normalized.id)
      return exists ? current.map(item => item.id === normalized.id ? normalized : item) : [normalized, ...current]
    })
  }, [])

  const refreshAll = useCallback(async () => {
    if (!liveApi) return
    const [remoteShipments, remoteSuppliers, remoteAudit] = await Promise.all([
      api.getShipments(),
      api.getAccuracy(),
      api.getAuditLogs().catch(() => []),
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
  }, [liveApi])

  const bootstrap = useCallback(async (nextRole: Role) => {
    if (!liveApi) {
      setRoleState(nextRole)
      setUserName(credentials[nextRole].name)
      return
    }
    setLoading(true)
    setApiError(null)
    try {
      const creds = credentials[nextRole]
      const login = await api.login(creds.email, creds.password)
      setRoleState(login.role)
      setUserName(login.fullName || credentials[nextRole].name)
      await refreshAll()
    } catch (error) {
      setApiError(error instanceof Error ? error.message : 'Unable to connect to API')
      setShipments(structuredClone(demoShipments))
      setSuppliers(demoSuppliers)
      setAuditLogs(demoAuditLogs)
    } finally {
      setLoading(false)
    }
  }, [liveApi, refreshAll])

  useEffect(() => {
    void bootstrap('ROLE_EXECUTIVE')
  }, [bootstrap])

  const setRole = (nextRole: Role) => {
    void bootstrap(nextRole)
  }

  const refreshShipment = useCallback(async (shipmentId: number) => {
    if (!liveApi) return
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
    if (!liveApi) return
    try {
      await api.editField(fieldId, nextValue, 'Human verification from review workspace')
      await refreshShipment(shipmentId)
      const remoteAudit = await api.getAuditLogs().catch(() => [])
      setAuditLogs(mapAuditLogs(remoteAudit))
      setSuppliers(normalizeAccuracy(await api.getAccuracy()))
    } catch {
      /* keep optimistic UI */
    }
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
    if (!liveApi) return
    try {
      upsertShipment(await api.markUnreadable(documentId))
    } catch {
      /* keep optimistic UI */
    }
  }

  const verifyDocument = async (shipmentId: number, documentId: number) => {
    setShipments(current => current.map(shipment => shipment.id !== shipmentId ? shipment : {
      ...shipment,
      documents: shipment.documents.map(document => document.id !== documentId ? document : { ...document, humanVerified: true }),
    }))
    if (!liveApi) return
    try {
      upsertShipment(await api.verifyDocument(documentId))
    } catch {
      /* keep optimistic UI */
    }
  }

  const resolveDiscrepancy = async (shipmentId: number, discrepancyId: number, note = 'Reviewed and resolved by documentation executive') => {
    setShipments(current => current.map(shipment => shipment.id !== shipmentId ? shipment : {
      ...shipment,
      discrepancies: shipment.discrepancies.map(item => item.id !== discrepancyId ? item : {
        ...item,
        status: role === 'ROLE_MANAGER' ? 'MANAGER_OVERRIDDEN' : 'RESOLVED',
        resolutionNote: note,
      }),
    }))
    if (!liveApi) return
    try {
      upsertShipment(await api.resolveDiscrepancy(discrepancyId, note, role === 'ROLE_MANAGER'))
    } catch {
      /* keep optimistic UI */
    }
  }

  const approveShipment = async (shipmentId: number) => {
    setShipments(current => current.map(shipment => shipment.id !== shipmentId ? shipment : { ...shipment, status: 'APPROVED' }))
    if (!liveApi) return
    try {
      upsertShipment(await api.approve(shipmentId))
    } catch (error) {
      setApiError(error instanceof Error ? error.message : 'Approval failed')
      await refreshShipment(shipmentId)
    }
  }

  const createShipment = async (files: Partial<Record<DocumentType, File>>, unreadable: Partial<Record<DocumentType, boolean>>) => {
    const unreadableDocTypes = (Object.keys(unreadable) as DocumentType[]).filter(type => unreadable[type])
    if (!liveApi) {
      const nextId = Math.max(...shipments.map(item => item.id), 24810) + 1
      const source = structuredClone(shipments[0] ?? demoShipments[0])
      const created: Shipment = {
        ...source,
        id: nextId,
        shipmentCode: `SHP-${nextId}`,
        status: 'EXTRACTING',
        updatedAt: new Date().toISOString(),
        supplier: 'New supplier · pending',
        discrepancies: [],
        documents: source.documents.map(document => ({ ...document, id: document.id + nextId })),
      }
      setShipments(current => [created, ...current])
      setPendingPackets(current => ({ ...current, [nextId]: { files, unreadable: unreadableDocTypes } }))
      return nextId
    }

    const created = normalizeShipment(await api.createShipment({
      importerName: 'Company A',
      supplierName: 'New supplier · pending',
      supplierCountry: 'Pending',
      importerContact: '+91-9000000000',
      unreadableDocTypes,
    }))
    upsertShipment(created)
    setPendingPackets(current => ({ ...current, [created.id]: { files, unreadable: unreadableDocTypes } }))
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
    userName,
    shipments,
    suppliers,
    auditLogs,
    liveApi,
    loading,
    apiError,
    pendingPackets,
    confirmField,
    markDocumentUnreadable,
    verifyDocument,
    resolveDiscrepancy,
    approveShipment,
    refreshShipment,
    refreshAll,
    createShipment,
    takePendingPacket,
  }), [role, userName, shipments, suppliers, auditLogs, liveApi, loading, apiError, pendingPackets, refreshShipment, refreshAll])

  return <Context.Provider value={value}>{children}</Context.Provider>
}

export function useApp() {
  const value = useContext(Context)
  if (!value) throw new Error('useApp must be used inside AppProvider')
  return value
}
