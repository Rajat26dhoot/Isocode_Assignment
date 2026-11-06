import { useEffect, useRef, useState } from 'react'
import { ArrowRight, Check, CircleAlert, FileSearch, LoaderCircle, Network } from 'lucide-react'
import { useNavigate, useParams } from 'react-router-dom'
import { Badge, Button, Card, ProgressBar, SectionTitle } from '../components/ui'
import { useApp } from '../lib/AppContext'
import { api } from '../lib/api'
import type { DocumentType } from '../types/domain'

const order: DocumentType[] = ['INVOICE', 'PACKING_LIST', 'BILL_OF_LADING']

export function ExtractionProgressPage() {
  const { id } = useParams()
  const navigate = useNavigate()
  const { shipments, liveApi, takePendingPacket, refreshShipment } = useApp()
  const shipmentId = Number(id)
  const shipment = shipments.find(item => item.id === shipmentId) ?? shipments[0]
  const [active, setActive] = useState(0)
  const [error, setError] = useState('')
  const [done, setDone] = useState(false)
  const started = useRef(false)

  useEffect(() => {
    if (!shipment || started.current) return
    started.current = true
    let cancelled = false

    const run = async () => {
      const packet = takePendingPacket(shipment.id)
      if (!liveApi || !packet) {
        const complete = shipment.documents.some(document => document.extractionStatus === 'COMPLETED' || document.unreadable)
        setActive(complete ? 4 : 0)
        if (!complete) setError('No processed source documents were found. Return to the shipment list and upload the packet again.')
        setDone(true)
        return
      }

      try {
        for (let index = 0; index < order.length; index++) {
          if (cancelled) return
          const type = order[index]
          setActive(index)
          if (packet.files[type]) {
            await api.extractDocument(shipment.id, type, packet.files[type]!)
          } else if (packet.unreadable.includes(type)) {
            await api.createUnreadableDocument(shipment.id, type)
          }
        }
        if (cancelled) return
        setActive(3)
        await api.runCrossCheck(shipment.id)
        await refreshShipment(shipment.id)
        setActive(4)
        setDone(true)
      } catch (err) {
        if (cancelled) return
        setError(err instanceof Error ? err.message : 'Extraction failed')
        setDone(true)
        setActive(4)
      }
    }

    void run()
    return () => { cancelled = true }
  }, [shipment, liveApi, takePendingPacket, refreshShipment])

  if (!shipment) {
    return <div className="mx-auto max-w-[800px] pt-8 text-center text-sm text-slate-500">Shipment not found.</div>
  }

  const openCount = shipment.discrepancies.filter(item => item.status === 'OPEN').length
  const stages: { label: string; detail: string; icon: React.ReactNode; status: 'complete' | 'active' | 'waiting' }[] = [
    { label: 'Commercial Invoice', detail: 'Parties, totals, line items', icon: <FileSearch size={16} />, status: active > 0 ? 'complete' : 'active' },
    { label: 'Packing List', detail: 'Packages and weights', icon: <FileSearch size={16} />, status: active > 1 ? 'complete' : active === 1 ? 'active' : 'waiting' },
    { label: 'Bill of Lading', detail: 'Container and consignee details', icon: <FileSearch size={16} />, status: active > 2 ? 'complete' : active === 2 ? 'active' : 'waiting' },
    { label: '3-way reconciliation', detail: done ? (openCount > 0 ? `${openCount} discrepancy found` : 'All checks passed') : 'Comparing matching fields', icon: <Network size={16} />, status: active > 3 ? 'complete' : active === 3 ? 'active' : 'waiting' },
  ]

  return (
    <div className="mx-auto max-w-[800px] pt-8">
      <div className="mb-8 text-center">
        <div className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-xl bg-slate-900 text-white">
          <LoaderCircle size={22} className={!done ? 'animate-spin' : ''} />
        </div>
        <SectionTitle
          title={done ? 'Extraction complete' : 'Reading your documents'}
          description={done ? `The packet for ${shipment.shipmentCode} is ready for human review.` : 'AI is extracting fields and checking agreement across the source documents.'}
        />
        <div className="mt-4 flex items-center justify-center gap-2">
          <Badge tone="blue" dot>Packet {shipment.shipmentCode}</Badge>
          {done && openCount > 0 && <Badge tone="amber" dot>{openCount} mismatch needs review</Badge>}
          {done && openCount === 0 && <Badge tone="green" dot>Cross-check passed</Badge>}
        </div>
      </div>
      <Card className="p-6">
        <div className="mb-6 flex items-center justify-between">
          <div>
            <div className="text-xs font-semibold text-slate-800">Extraction pipeline</div>
            <div className="mt-1 text-xs text-slate-400">Secure processing · zero data retention</div>
          </div>
          <div className="numeric text-sm font-semibold text-slate-700">{Math.min(100, active * 25)}%</div>
        </div>
        <ProgressBar value={Math.min(100, active * 25)} tone={done ? 'green' : 'slate'} />
        <div className="mt-6 divide-y divide-slate-100">
          {stages.map((stage, index) => (
            <div key={stage.label} className="flex items-center gap-4 py-4">
              <div className={`flex h-8 w-8 items-center justify-center rounded-full ${stage.status === 'complete' ? 'bg-emerald-100 text-emerald-700' : stage.status === 'active' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-400'}`}>
                {stage.status === 'complete' ? <Check size={15} /> : stage.status === 'active' ? <LoaderCircle size={15} className="animate-spin" /> : stage.icon}
              </div>
              <div className="flex-1">
                <div className="text-sm font-semibold text-slate-800">{stage.label}</div>
                <div className="mt-0.5 text-xs text-slate-400">{stage.detail}</div>
              </div>
              <div className="text-right">
                {stage.status === 'complete' && index === 3 && openCount > 0 ? (
                  <div className="flex items-center gap-1.5 text-xs font-semibold text-amber-700"><CircleAlert size={14} /> Review needed</div>
                ) : (
                  <span className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">
                    {stage.status === 'complete' ? 'Complete' : stage.status === 'active' ? 'Processing' : 'Queued'}
                  </span>
                )}
              </div>
            </div>
          ))}
        </div>
        {error && <div className="mt-4 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-xs text-rose-700">{error}</div>}
        <div className="mt-6 flex justify-end border-t border-slate-200 pt-5">
          <Button variant="primary" onClick={() => navigate(`/shipments/${shipment.id}/review`)} disabled={!done} icon={<ArrowRight size={15} />}>
            Open review workspace
          </Button>
        </div>
      </Card>
    </div>
  )
}
