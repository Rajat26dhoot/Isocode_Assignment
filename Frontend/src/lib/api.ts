import type { AuditLog, DocumentType, Role, Shipment, SupplierAccuracy } from '../types/domain'

const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api').replace(/\/$/, '')

export interface LoginResult {
  token: string
  email: string
  role: Role
  fullName: string
}

export interface CreateShipmentPayload {
  importerName: string
  supplierName: string
  supplierCountry?: string
  importerContact?: string
  unreadableDocTypes?: DocumentType[]
}

class ApiClient {
  private token = sessionStorage.getItem('customs.jwt')

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const headers: Record<string, string> = {
      ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}),
      ...(init.headers as Record<string, string> | undefined ?? {}),
    }
    if (init.body && !(init.body instanceof FormData) && !headers['Content-Type']) {
      headers['Content-Type'] = 'application/json'
    }
    const response = await fetch(`${API_URL}${path}`, { ...init, headers })
    if (!response.ok) {
      const text = await response.text()
      let message = text || `Request failed (${response.status})`
      try { message = JSON.parse(text).message || message } catch { /* response is plain text */ }
      throw new Error(message)
    }
    if (response.status === 204) return undefined as T
    const contentType = response.headers.get('content-type') ?? ''
    if (!contentType.includes('application/json')) return undefined as T
    return response.json() as Promise<T>
  }

  setToken(token: string | null) {
    this.token = token
    if (token) sessionStorage.setItem('customs.jwt', token)
    else {
      sessionStorage.removeItem('customs.jwt')
      localStorage.removeItem('customs.role')
      localStorage.removeItem('customs.user')
    }
  }

  setSessionIdentity(role: Role, fullName: string) {
    sessionStorage.setItem('customs.role', role)
    sessionStorage.setItem('customs.user', fullName)
  }

  getRole(): Role { return (sessionStorage.getItem('customs.role') as Role | null) ?? 'ROLE_EXECUTIVE' }
  getUserName() { return sessionStorage.getItem('customs.user') ?? '' }

  hasToken() {
    return Boolean(this.token)
  }

  async login(email: string, password: string) {
    const result = await this.request<LoginResult>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    })
    this.setToken(result.token)
    this.setSessionIdentity(result.role, result.fullName)
    return result
  }

  logout() { const token = this.token; this.setToken(null); if (token) void fetch(`${API_URL}/auth/logout`, { method: 'POST', headers: { Authorization: `Bearer ${token}` } }).catch(() => undefined) }

  getShipments() {
    return this.request<Shipment[]>('/shipments')
  }

  getShipment(id: number) {
    return this.request<Shipment>(`/shipments/${id}`)
  }

  getDocumentContent(documentId: number) {
    return fetch(`${API_URL}/shipments/documents/${documentId}/content`, { headers: this.token ? { Authorization: `Bearer ${this.token}` } : {} }).then(async response => {
      if (!response.ok) throw new Error(await response.text() || `Request failed (${response.status})`)
      return response.blob()
    })
  }

  createShipment(payload: CreateShipmentPayload) {
    return this.request<Shipment>('/shipments', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
  }

  async extractDocument(shipmentId: number, docType: DocumentType, file: File) {
    const form = new FormData()
    form.append('file', file)
    return this.request<Shipment>(`/shipments/${shipmentId}/documents/extract?docType=${docType}`, {
      method: 'POST',
      body: form,
    })
  }
  retryExtraction(documentId: number) { return this.request<Shipment>(`/shipments/documents/${documentId}/retry`, { method: 'POST' }) }

  createUnreadableDocument(shipmentId: number, docType: DocumentType) {
    return this.request<Shipment>(`/shipments/${shipmentId}/documents/unreadable?docType=${docType}`, {
      method: 'POST',
    })
  }

  editField(fieldId: number, currentValue: string, changeReason: string) {
    return this.request(`/shipments/fields/${fieldId}`, {
      method: 'PATCH',
      body: JSON.stringify({ currentValue, changeReason }),
    })
  }

  editLineItem(id: number, update: { description: string; hsnCode: string; quantity: number; unitPrice: number; currency: string; lineTotal: number }) {
    return this.request(`/shipments/line-items/${id}`, { method: 'PATCH', body: JSON.stringify(update) })
  }
  createLineItem(documentId: number, item: { description: string; hsnCode: string; quantity: number; unitPrice: number; currency: string; lineTotal: number }) {
    return this.request(`/shipments/documents/${documentId}/line-items`, { method: 'POST', body: JSON.stringify(item) })
  }

  markUnreadable(documentId: number) {
    return this.request<Shipment>(`/shipments/documents/${documentId}/unreadable`, { method: 'POST' })
  }

  verifyDocument(documentId: number) {
    return this.request<Shipment>(`/shipments/documents/${documentId}/verify`, { method: 'POST' })
  }

  resolveDiscrepancy(id: number, resolutionNote: string, managerOverride = false) {
    return this.request<Shipment>(`/shipments/discrepancies/${id}`, {
      method: 'PATCH',
      body: JSON.stringify({ resolutionNote, managerOverride }),
    })
  }

  runCrossCheck(id: number) {
    return this.request<Shipment>(`/shipments/${id}/cross-check`, { method: 'POST' })
  }

  approve(id: number) {
    return this.request<Shipment>(`/shipments/${id}/approve`, { method: 'POST' })
  }

  submit(id: number) { return this.request<Shipment>(`/shipments/${id}/submit`, { method: 'POST' }) }
  reject(id: number, reason: string) { return this.request<Shipment>(`/shipments/${id}/reject`, { method: 'POST', body: JSON.stringify({ reason }) }) }

  getAccuracy() {
    return this.request<SupplierAccuracy[]>('/reports/supplier-accuracy')
  }

  getAuditLogs() {
    return this.request<Array<{
      id: number
      timestamp: string
      shipment: string
      user: string
      document: DocumentType | string
      field: string
      actionType: string
      aiValue: string
      correctedValue: string
      reason: string
    }>>('/reports/audit-logs')
  }

  async exportFile(id: number, format: 'csv' | 'xlsx') {
    const response = await fetch(`${API_URL}/shipments/${id}/export?format=${format}`, {
      headers: this.token ? { Authorization: `Bearer ${this.token}` } : {},
    })
    if (!response.ok) throw new Error(await response.text())
    return response.blob()
  }
}

export const api = new ApiClient()

export function mapAuditLogs(raw: Awaited<ReturnType<ApiClient['getAuditLogs']>>): AuditLog[] {
  return raw.map(item => ({
    id: item.id,
    timestamp: formatTimestamp(item.timestamp),
    shipment: item.shipment,
    user: item.user,
    document: (item.document as DocumentType) || 'INVOICE',
    field: humanizeField(item.field),
    actionType: item.actionType,
    aiValue: item.aiValue ?? '',
    correctedValue: item.correctedValue ?? '',
    reason: item.reason,
  }))
}

function humanizeField(field: string) {
  return field.replaceAll('_', ' ').replace(/\b\w/g, char => char.toUpperCase())
}

function formatTimestamp(value: string) {
  try {
    return new Date(value).toLocaleString('en-GB', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    })
  } catch {
    return value
  }
}
