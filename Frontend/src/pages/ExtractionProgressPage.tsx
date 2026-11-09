import { useEffect, useRef, useState } from 'react'
import { ArrowRight, Check, CircleAlert, FileSearch, LoaderCircle, Network } from 'lucide-react'
import { useNavigate, useParams } from 'react-router-dom'
import { Badge, Button, Card, ProgressBar, SectionTitle } from '../components/ui'
import { useApp } from '../lib/AppContext'
import { api } from '../lib/api'
import type { DocumentType } from '../types/domain'

const order: DocumentType[] = ['INVOICE', 'PACKING_LIST', 'BILL_OF_LADING']
const labels: Record<DocumentType, string> = { INVOICE: 'Commercial Invoice', PACKING_LIST: 'Packing List', BILL_OF_LADING: 'Bill of Lading' }

function failedDocumentNames(documents: ReturnType<typeof useApp>['shipments'][number]['documents']) {
  return order.flatMap(type => {
    const latest = documents.filter(document => document.docType === type).sort((a, b) => b.id - a.id)[0]
    return latest?.extractionStatus === 'FAILED' ? [labels[type]] : []
  })
}

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
        setActive(4)
        const failures = failedDocumentNames(shipment.documents)
        if (failures.length) setError(`Extraction failed for ${failures.join(', ')}. The uploaded file is saved; retry extraction from the review workspace or enter the fields manually.`)
        else if (!complete) setError('No source documents are uploaded yet. Continue to review and add documents when they are available.')
        setDone(true)
        return
      }

      try {
        const failures: string[] = []
        for (let index = 0; index < order.length; index++) {
          if (cancelled) return
          const type = order[index]
          setActive(index)
          if (packet.files[type]) {
            const result = await api.extractDocument(shipment.id, type, packet.files[type]!)
            const latest = result.documents.filter(document => document.docType === type).sort((a, b) => b.id - a.id)[0]
            if (latest?.extractionStatus === 'FAILED') failures.push(labels[type])
          } else if (packet.unreadable.includes(type)) {
            await api.createUnreadableDocument(shipment.id, type)
          }
        }
        if (cancelled) return
        setActive(3)
        await api.runCrossCheck(shipment.id)
        await refreshShipment(shipment.id)
        if (failures.length) setError(`Extraction failed for ${failures.join(', ')}. The uploaded file is saved; retry extraction from the review workspace or enter the fields manually.`)
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
  const hasDocuments = shipment.documents.length > 0
  const failedNames = failedDocumentNames(shipment.documents)
  const hasExtractionFailures = failedNames.length > 0
  const documentStatus = (type: DocumentType, index: number): 'complete' | 'failed' | 'active' | 'waiting' => {
    const latest = shipment.documents.filter(document => document.docType === type).sort((a, b) => b.id - a.id)[0]
    if (latest?.extractionStatus === 'FAILED') return 'failed'
    if (latest?.extractionStatus === 'COMPLETED') return 'complete'
    return !done && active === index ? 'active' : 'waiting'
  }
  const stages: { label: string; detail: string; icon: React.ReactNode; status: 'complete' | 'failed' | 'active' | 'waiting' }[] = [
    { label: labels.INVOICE, detail: 'Parties, totals, line items', icon: <FileSearch size={16} />, status: documentStatus('INVOICE', 0) },
    { label: labels.PACKING_LIST, detail: 'Packages and weights', icon: <FileSearch size={16} />, status: documentStatus('PACKING_LIST', 1) },
    { label: labels.BILL_OF_LADING, detail: 'Container and consignee details', icon: <FileSearch size={16} />, status: documentStatus('BILL_OF_LADING', 2) },
    { label: 'Cross-document validation', detail: done && hasDocuments ? (openCount > 0 ? `${openCount} finding(s) need review` : 'Completed') : hasDocuments ? 'Comparing matching fields' : 'Waiting for source documents', icon: <Network size={16} />, status: done && hasDocuments ? 'complete' : !done && hasDocuments ? 'active' : 'waiting' },
  ]

  return (
    <div className="mx-auto max-w-[800px] pt-8">
      <div className="mb-8 text-center">
        <div className="mx-auto mb-3 flex h-11 w-11 items-center justify-center rounded-xl bg-slate-900 text-white">
          <LoaderCircle size={22} className={!done ? 'animate-spin' : ''} />
        </div>
        <SectionTitle
          title={done ? hasDocuments ? hasExtractionFailures ? 'Extraction needs attention' : 'Extraction complete' : 'Shipment created' : 'Reading your documents'}
          description={done ? hasDocuments ? hasExtractionFailures ? `Some files in ${shipment.shipmentCode} could not be processed.` : `The packet for ${shipment.shipmentCode} is ready for human review.` : 'Upload the required documents from the review workspace when they are available.' : 'The configured extraction provider is processing uploaded files.'}
        />
        <div className="mt-4 flex items-center justify-center gap-2">
          <Badge tone="blue" dot>Packet {shipment.shipmentCode}</Badge>
          {done && hasExtractionFailures && <Badge tone="amber" dot>Extraction failed</Badge>}
          {done && openCount > 0 && <Badge tone="amber" dot>{openCount} mismatch needs review</Badge>}
          {done && hasDocuments && !hasExtractionFailures && openCount === 0 && <Badge tone="green" dot>Cross-check complete</Badge>}
        </div>
      </div>
      <Card className="p-6">
        <div className="mb-6 flex items-center justify-between">
          <div>
            <div className="text-xs font-semibold text-slate-800">Extraction pipeline</div>
            <div className="mt-1 text-xs text-slate-400">Provider settings are controlled by deployment configuration.</div>
          </div>
            <div className="numeric text-sm font-semibold text-slate-700">{Math.round(stages.filter(stage => stage.status === 'complete').length / stages.length * 100)}%</div>
        </div>
        <ProgressBar value={stages.filter(stage => stage.status === 'complete').length / stages.length * 100} tone={done && hasDocuments && !hasExtractionFailures ? 'green' : 'slate'} />
        <div className="mt-6 divide-y divide-slate-100">
          {stages.map((stage, index) => (
            <div key={stage.label} className="flex items-center gap-4 py-4">
              <div className={`flex h-8 w-8 items-center justify-center rounded-full ${stage.status === 'complete' ? 'bg-emerald-100 text-emerald-700' : stage.status === 'failed' ? 'bg-rose-100 text-rose-700' : stage.status === 'active' ? 'bg-slate-900 text-white' : 'bg-slate-100 text-slate-400'}`}>
                {stage.status === 'complete' ? <Check size={15} /> : stage.status === 'failed' ? <CircleAlert size={15} /> : stage.status === 'active' ? <LoaderCircle size={15} className="animate-spin" /> : stage.icon}
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
                    {stage.status === 'failed' ? 'Failed' : stage.status === 'complete' ? 'Complete' : stage.status === 'active' ? 'Processing' : 'Queued'}
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
