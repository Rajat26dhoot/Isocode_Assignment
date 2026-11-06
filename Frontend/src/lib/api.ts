import type { AuditLog, Role, Shipment, SupplierAccuracy } from '../types/domain'

const API_URL = (import.meta.env.VITE_API_URL ?? 'http://localhost:8080/api').replace(/\/$/, '')

class ApiClient {
  private token = localStorage.getItem('customs.jwt')
  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    const response = await fetch(`${API_URL}${path}`, { ...init, headers: { 'Content-Type': 'application/json', ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}), ...(init.headers ?? {}) } })
    if (!response.ok) throw new Error(await response.text())
    return response.json() as Promise<T>
  }
  setToken(token: string) { this.token = token; localStorage.setItem('customs.jwt', token) }
  async login(email: string, password: string) { const result = await this.request<{ token: string; role: Role }>('/auth/login', { method: 'POST', body: JSON.stringify({ email, password }) }); this.setToken(result.token); return result }
  getShipments() { return this.request<Shipment[]>('/shipments') }
  getShipment(id: number) { return this.request<Shipment>(`/shipments/${id}`) }
  editField(fieldId: number, currentValue: string, changeReason: string) { return this.request(`/shipments/fields/${fieldId}`, { method: 'PATCH', body: JSON.stringify({ currentValue, changeReason }) }) }
  markUnreadable(documentId: number) { return this.request(`/shipments/documents/${documentId}/unreadable`, { method: 'POST' }) }
  verifyDocument(documentId: number) { return this.request(`/shipments/documents/${documentId}/verify`, { method: 'POST' }) }
  resolveDiscrepancy(id: number, resolutionNote: string, managerOverride = false) { return this.request(`/shipments/discrepancies/${id}`, { method: 'PATCH', body: JSON.stringify({ resolutionNote, managerOverride }) }) }
  approve(id: number) { return this.request(`/shipments/${id}/approve`, { method: 'POST' }) }
  getAccuracy() { return this.request<SupplierAccuracy[]>('/reports/supplier-accuracy') }
  getAuditLogs(): Promise<AuditLog[]> { return Promise.resolve([]) }
  async exportFile(id: number, format: 'csv' | 'xlsx') { const response = await fetch(`${API_URL}/shipments/${id}/export?format=${format}`, { headers: this.token ? { Authorization: `Bearer ${this.token}` } : {} }); if (!response.ok) throw new Error(await response.text()); return response.blob() }
}

export const api = new ApiClient()
