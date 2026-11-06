import { useRef, useState } from 'react'
import { ArrowRight, Check, FileText, ImagePlus, Info, UploadCloud, X } from 'lucide-react'
import { useNavigate } from 'react-router-dom'
import { Badge, Button, Card, SectionTitle } from '../components/ui'
import type { DocumentType } from '../types/domain'
import { useApp } from '../lib/AppContext'

const slots: { type: DocumentType; label: string; subtitle: string }[] = [
  { type: 'INVOICE', label: 'Commercial Invoice', subtitle: 'Invoice number, value, Incoterm, line items' },
  { type: 'PACKING_LIST', label: 'Packing List', subtitle: 'Packages, gross & net weight, dimensions' },
  { type: 'BILL_OF_LADING', label: 'Bill of Lading', subtitle: 'Container, consignee, vessel and port details' },
]

export function NewShipmentPage() {
  const navigate = useNavigate()
  const { createShipment } = useApp()
  const [files, setFiles] = useState<Partial<Record<DocumentType, File>>>({})
  const [unreadable, setUnreadable] = useState<Partial<Record<DocumentType, boolean>>>({})
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState('')
  const [importerName, setImporterName] = useState('')
  const [supplierName, setSupplierName] = useState('')
  const [supplierCountry, setSupplierCountry] = useState('')
  const [importerContact, setImporterContact] = useState('')
  const inputRefs = useRef<Record<string, HTMLInputElement | null>>({})

  const choose = (type: DocumentType, file?: File) => {
    if (!file) return
    setFiles(current => ({ ...current, [type]: file }))
    setUnreadable(current => {
      const next = { ...current }
      delete next[type]
      return next
    })
  }

  const onDrop = (event: React.DragEvent, type: DocumentType) => {
    event.preventDefault()
    choose(type, event.dataTransfer.files[0])
  }

  const ready = slots.every(slot => Boolean(files[slot.type] || unreadable[slot.type])) && Boolean(importerName.trim() && supplierName.trim())

  const begin = async () => {
    setSubmitting(true)
    setError('')
    try {
      const id = await createShipment(files, unreadable, { importerName: importerName.trim(), supplierName: supplierName.trim(), supplierCountry: supplierCountry.trim(), importerContact: importerContact.trim() })
      navigate(`/shipments/${id}/extract`)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Unable to create shipment')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="mx-auto max-w-[1160px]">
      <div className="mb-7 flex items-end justify-between">
        <SectionTitle eyebrow="Create shipment" title="Start a document packet" description="Upload the three source documents to begin AI extraction and reconciliation." />
        <div className="flex items-center gap-2">
          <Badge tone="blue" dot>AI extraction ready</Badge>
          <span className="text-xs text-slate-400">PDF, JPG, PNG · max 25 MB</span>
        </div>
      </div>
      <Card className="p-6">
        <div className="mb-6 grid grid-cols-2 gap-4">
          <label className="text-xs font-medium text-slate-600">Importer name<input required value={importerName} onChange={event => setImporterName(event.target.value)} className="field-input mt-1" /></label>
          <label className="text-xs font-medium text-slate-600">Supplier name<input required value={supplierName} onChange={event => setSupplierName(event.target.value)} className="field-input mt-1" /></label>
          <label className="text-xs font-medium text-slate-600">Supplier country<input value={supplierCountry} onChange={event => setSupplierCountry(event.target.value)} className="field-input mt-1" /></label>
          <label className="text-xs font-medium text-slate-600">Importer contact<input value={importerContact} onChange={event => setImporterContact(event.target.value)} className="field-input mt-1" /></label>
        </div>
        <div className="mb-6 flex items-center gap-3 rounded-lg border border-blue-100 bg-blue-50/60 px-4 py-3 text-xs text-blue-800">
          <Info size={15} />
          <span>Upload scans as-is. Bilingual documents and skewed scans are accepted; unreadable documents can be flagged for manual entry without blocking the rest of the packet.</span>
        </div>
        <div className="grid grid-cols-3 gap-4">
          {slots.map(slot => (
            <div
              key={slot.type}
              onDragOver={event => event.preventDefault()}
              onDrop={event => onDrop(event, slot.type)}
              className={`relative rounded-xl border-2 border-dashed p-5 transition ${files[slot.type] ? 'border-emerald-300 bg-emerald-50/30' : unreadable[slot.type] ? 'border-amber-300 bg-amber-50/30' : 'border-slate-200 bg-slate-50/40 hover:border-slate-300 hover:bg-white'}`}
            >
              <input
                ref={element => { inputRefs.current[slot.type] = element }}
                onChange={event => choose(slot.type, event.target.files?.[0])}
                type="file"
                accept=".pdf,.png,.jpg,.jpeg,.txt"
                className="hidden"
              />
              <div className="flex items-start justify-between">
                <div className={`flex h-9 w-9 items-center justify-center rounded-lg ${files[slot.type] ? 'bg-emerald-100 text-emerald-700' : unreadable[slot.type] ? 'bg-amber-100 text-amber-700' : 'bg-white text-slate-500 shadow-sm'}`}>
                  {files[slot.type] ? <Check size={18} /> : unreadable[slot.type] ? <ImagePlus size={18} /> : <FileText size={18} />}
                </div>
                {(files[slot.type] || unreadable[slot.type]) && (
                  <button
                    onClick={() => {
                      setFiles(current => {
                        const next = { ...current }
                        delete next[slot.type]
                        return next
                      })
                      setUnreadable(current => {
                        const next = { ...current }
                        delete next[slot.type]
                        return next
                      })
                    }}
                    className="text-slate-400 hover:text-slate-700"
                  >
                    <X size={15} />
                  </button>
                )}
              </div>
              <div className="mt-5 text-sm font-semibold text-slate-900">{slot.label}</div>
              <div className="mt-1 min-h-8 text-xs leading-4 text-slate-500">{slot.subtitle}</div>
              {files[slot.type] ? (
                <div className="mt-4 truncate rounded-lg bg-emerald-100/70 px-2.5 py-2 text-xs font-medium text-emerald-800">{files[slot.type]?.name}</div>
              ) : unreadable[slot.type] ? (
                <div className="mt-4 rounded-lg bg-amber-100/70 px-2.5 py-2 text-xs font-medium text-amber-800">Flagged for manual entry</div>
              ) : (
                <div className="mt-4 flex gap-2">
                  <Button onClick={() => inputRefs.current[slot.type]?.click()} icon={<UploadCloud size={14} />}>Choose file</Button>
                  <button
                    onClick={() => setUnreadable(current => ({ ...current, [slot.type]: true }))}
                    className="px-2 text-[10px] font-semibold text-slate-400 underline underline-offset-2 hover:text-slate-700"
                  >
                    Unreadable?
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
        <div className="mt-7 flex items-center justify-between border-t border-slate-200 pt-5">
          <div>
            <div className="text-xs font-semibold text-slate-700">{Object.keys(files).length + Object.keys(unreadable).length} of 3 document slots ready</div>
            <div className="mt-1 text-xs text-slate-400">You can add missing documents from the review workspace.</div>
            {error && <div className="mt-2 text-xs text-rose-600">{error}</div>}
          </div>
          <Button variant="primary" disabled={!ready || submitting} onClick={() => void begin()} icon={<ArrowRight size={15} />}>
            {submitting ? 'Creating…' : 'Begin AI extraction'}
          </Button>
        </div>
      </Card>
      <div className="mt-5 grid grid-cols-3 gap-4">
        <Tip icon={<UploadCloud size={16} />} title="Drop to upload" text="Drag a file anywhere onto its matching document slot." />
        <Tip icon={<ImagePlus size={16} />} title="Scan-friendly" text="Skewed scans and bilingual source pages are supported." />
        <Tip icon={<Info size={16} />} title="Human in the loop" text="Low-confidence fields are always surfaced before export." />
      </div>
    </div>
  )
}

function Tip({ icon, title, text }: { icon: React.ReactNode; title: string; text: string }) {
  return (
    <div className="flex gap-3 rounded-xl border border-slate-200 bg-white p-4">
      <div className="text-slate-400">{icon}</div>
      <div>
        <div className="text-xs font-semibold text-slate-700">{title}</div>
        <div className="mt-1 text-[11px] leading-4 text-slate-400">{text}</div>
      </div>
    </div>
  )
}
