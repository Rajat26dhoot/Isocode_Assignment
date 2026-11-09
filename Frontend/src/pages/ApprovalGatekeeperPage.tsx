import { useState } from 'react'
import { ArrowLeft, Check, CheckCircle2, Download, FileSpreadsheet, LockKeyhole, ShieldCheck, XCircle } from 'lucide-react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Badge, Button, Card, ProgressBar, SectionTitle, StatusBadge } from '../components/ui'
import { useApp } from '../lib/AppContext'
import { api } from '../lib/api'
import type { DocumentType, FieldKey } from '../types/domain'

const required: Record<DocumentType, FieldKey[]> = {
  INVOICE: ['exporter_name', 'importer_name', 'invoice_number', 'invoice_date', 'currency', 'invoice_total'],
  PACKING_LIST: ['exporter_name', 'total_packages', 'gross_weight_kg'],
  BILL_OF_LADING: ['exporter_name', 'importer_name', 'bl_number', 'port_of_loading', 'port_of_discharge', 'total_packages', 'gross_weight_kg', 'container_number'],
}

export function ApprovalGatekeeperPage() {
  const { id } = useParams(); const navigate = useNavigate()
  const { shipments, role, approveShipment, rejectShipment, liveApi } = useApp()
  const shipment = shipments.find(item => item.id === Number(id))
  const [error, setError] = useState(''); const [busy, setBusy] = useState(false)
  if (!shipment) return <div className="mx-auto max-w-[940px] pt-4 text-sm text-slate-500">Shipment not found.</div>
  const missingDocs = (Object.keys(required) as DocumentType[]).filter(type => !shipment.documents.some(d => d.docType === type))
  const missingFields = shipment.documents.flatMap(doc => required[doc.docType].filter(key => !doc.fields.some(f => f.fieldKey === key && f.currentValue.trim())).map(key => `${doc.docType}: ${key.replaceAll('_', ' ')}`))
  const low = shipment.documents.flatMap(document => document.fields).filter(field => field.lowConfidence && !field.humanVerified)
  const unreadable = shipment.documents.filter(document => document.unreadable && !document.humanVerified)
  const open = shipment.discrepancies.filter(item => item.status === 'OPEN')
  const checks = [missingDocs.length === 0 && missingFields.length === 0, low.length === 0 && unreadable.length === 0, open.length === 0]
  const ready = checks.every(Boolean)
  const download = async (format: 'csv' | 'xlsx') => { try { const blob = await api.exportFile(shipment.id, format); const url = URL.createObjectURL(blob); const link = document.createElement('a'); link.href = url; link.download = `${shipment.shipmentCode}.${format}`; link.click(); URL.revokeObjectURL(url); setError('') } catch (err) { setError(err instanceof Error ? err.message : 'Export failed') } }
  const run = async (action: () => Promise<void>) => { setBusy(true); setError(''); try { await action() } catch (err) { setError(err instanceof Error ? err.message : 'Request failed') } finally { setBusy(false) } }
  const approve = () => run(() => approveShipment(shipment.id))
  const reject = () => { const reason = window.prompt('Enter the reason for rejecting this shipment.'); if (reason?.trim()) void run(() => rejectShipment(shipment.id, reason.trim())) }
  const items = [
    { ok: checks[0], title: 'Required documents and fields', detail: checks[0] ? 'All three document types and required values are present.' : `${missingDocs.length} document(s) and ${missingFields.length} required field(s) missing.` },
    { ok: checks[1], title: 'Human review', detail: checks[1] ? 'Low-confidence fields and unreadable pages have been reviewed.' : `${low.length} low-confidence field(s), ${unreadable.length} unreadable document(s) unverified.` },
    { ok: checks[2], title: 'Cross-document validation', detail: checks[2] ? 'No open validation findings.' : `${open.length} finding(s) need resolution or an explicit review note.` },
  ]
  return <div className="mx-auto max-w-[940px] pt-4">
    <div className="mb-7 flex items-center gap-3"><Link to={`/shipments/${shipment.id}/review`} className="rounded-lg p-2 text-slate-400 hover:bg-white hover:text-slate-700"><ArrowLeft size={17} /></Link><SectionTitle eyebrow="Manager control" title="Approval & export gatekeeper" description={`Review findings and workflow history for ${shipment.shipmentCode}.`} /></div>
    {shipment.rejectionReason && shipment.status === 'REJECTED' && <div className="mb-4 rounded-lg border border-rose-200 bg-rose-50 p-4 text-sm text-rose-900"><strong>Rejected:</strong> {shipment.rejectionReason}</div>}
    <div className={`mb-5 flex items-center justify-between rounded-xl border px-5 py-4 ${ready ? 'border-emerald-200 bg-emerald-50/60' : 'border-amber-200 bg-amber-50/60'}`}><div className="flex items-center gap-3">{ready ? <CheckCircle2 size={22} className="text-emerald-600" /> : <LockKeyhole size={22} className="text-amber-600" />}<div><div className="text-sm font-semibold text-slate-900">{ready ? 'Workflow checks are clear' : 'Resolve required checks before approval'}</div><div className="mt-1 text-xs text-slate-600">Approval is available only after the executive submits this shipment.</div></div></div><Badge tone={ready ? 'green' : 'amber'}>{checks.filter(Boolean).length}/3 checks passed</Badge></div>
    <div className="grid grid-cols-[1fr_290px] gap-5"><Card className="p-5"><div className="mb-5 flex items-center justify-between"><div><div className="text-xs font-semibold text-slate-800">Review checklist</div><div className="mt-1 text-[11px] text-slate-400">The backend enforces these gates during submission and approval.</div></div><div className="numeric text-sm font-semibold text-slate-700">{Math.round(checks.filter(Boolean).length / 3 * 100)}%</div></div><ProgressBar value={checks.filter(Boolean).length / 3 * 100} tone={ready ? 'green' : 'amber'} /><div className="mt-5 space-y-3">{items.map(item => <CheckRow key={item.title} {...item} action={!item.ok ? <Button onClick={() => navigate(`/shipments/${shipment.id}/review`)}>Review</Button> : undefined} />)}</div>{open.map(item => <div key={item.id} className="mt-3 rounded-lg border border-amber-200 bg-amber-50 p-3 text-xs text-amber-900"><strong>{item.ruleCode.replaceAll('_', ' ')}</strong><div className="mt-1">Invoice: {item.invoiceValue ?? '—'} · Packing list: {item.packingListValue ?? '—'} · B/L: {item.bolValue ?? '—'}</div><div className="mt-1">{item.expectedOrDelta}</div></div>)}</Card>
      <Card className="h-fit p-5"><div className="flex h-9 w-9 items-center justify-center rounded-lg bg-indigo-100 text-indigo-700"><ShieldCheck size={18} /></div><div className="mt-4 text-sm font-semibold text-slate-900">Manager decision</div><div className="mt-3 rounded-lg bg-slate-50 p-3"><div className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">Current status</div><div className="mt-2"><StatusBadge status={shipment.status} /></div></div>{role === 'ROLE_MANAGER' && shipment.status === 'PENDING_APPROVAL' ? <><Button variant="primary" disabled={!ready || busy} onClick={() => void approve()} className="mt-4 w-full" icon={<Check size={14} />}>{busy ? 'Saving…' : 'Approve shipment'}</Button><Button variant="danger" disabled={busy} onClick={reject} className="mt-2 w-full" icon={<XCircle size={14} />}>Reject with reason</Button></> : <p className="mt-4 text-xs leading-5 text-slate-500">A manager can decide after the executive submits the packet.</p>}{error && <div className="mt-3 text-xs text-rose-700">{error}</div>}</Card></div>
    {(shipment.status === 'APPROVED' || shipment.status === 'EXPORTED') && <Card className="mt-5 p-5"><div className="flex items-center justify-between"><div><div className="text-sm font-semibold text-slate-900">Configured export</div><div className="mt-1 text-xs text-slate-500">Generic shipment data CSV/XLSX. No filing-software layout is configured.</div></div><div className="flex gap-2"><Button onClick={() => void download('csv')} icon={<Download size={14} />}>Download CSV</Button><Button variant="primary" onClick={() => void download('xlsx')} icon={<FileSpreadsheet size={14} />}>Download Excel</Button></div></div>{error && <div className="mt-3 text-xs text-rose-700">{error}</div>}</Card>}
  </div>
}

function CheckRow({ ok, title, detail, action }: { ok: boolean; title: string; detail: string; action?: React.ReactNode }) { return <div className="flex items-center gap-3 rounded-lg border border-slate-200 p-3"><div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-full ${ok ? 'bg-emerald-100 text-emerald-700' : 'bg-rose-100 text-rose-700'}`}>{ok ? <Check size={14} /> : <XCircle size={15} />}</div><div className="min-w-0 flex-1"><div className="text-xs font-semibold text-slate-800">{title}</div><div className="mt-0.5 text-[11px] text-slate-500">{detail}</div></div>{action}</div> }
