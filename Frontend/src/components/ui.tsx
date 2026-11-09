import type { ReactNode } from 'react'
import { Check, ChevronDown, CircleAlert, Info, LoaderCircle } from 'lucide-react'

export function Badge({ children, tone = 'slate', dot = false }: { children: ReactNode; tone?: 'slate' | 'amber' | 'red' | 'green' | 'blue'; dot?: boolean }) {
  const tones = { slate: 'border-slate-200 bg-slate-50 text-slate-600', amber: 'border-amber-200 bg-amber-50 text-amber-800', red: 'border-rose-200 bg-rose-50 text-rose-700', green: 'border-emerald-200 bg-emerald-50 text-emerald-700', blue: 'border-blue-200 bg-blue-50 text-blue-700' }
  return <span className={`inline-flex items-center gap-1.5 rounded-md border px-2 py-1 text-[10px] font-semibold uppercase tracking-wide ${tones[tone]}`}>{dot && <span className="h-1.5 w-1.5 rounded-full bg-current" />}{children}</span>
}

export function StatusBadge({ status }: { status: string }) {
  const map: Record<string, ['slate' | 'amber' | 'red' | 'green' | 'blue', string]> = { DOCUMENTS_PENDING: ['slate', 'Documents pending'], NEEDS_REVIEW: ['amber', 'Review required'], VALIDATION_FAILED: ['red', 'Validation failed'], PENDING_APPROVAL: ['blue', 'Pending approval'], READY_FOR_APPROVAL: ['blue', 'Ready for approval'], APPROVED: ['green', 'Approved'], EXPORTED: ['green', 'Exported'], REJECTED: ['red', 'Rejected'], EXTRACTING: ['blue', 'Processing'], DRAFT: ['slate', 'Draft'] }
  const [tone, label] = map[status] ?? ['slate', status.replace(/_/g, ' ')]
  return <Badge tone={tone} dot>{label}</Badge>
}

export function Button({ children, variant = 'quiet', icon, className = '', ...props }: { children: ReactNode; variant?: 'quiet' | 'primary' | 'danger'; icon?: ReactNode; className?: string } & React.ButtonHTMLAttributes<HTMLButtonElement>) {
  const classes = variant === 'primary' ? 'primary-btn' : variant === 'danger' ? 'danger-btn' : 'quiet-btn'
  return <button className={`${classes} ${className}`} {...props}>{icon}{children}</button>
}

export function Card({ children, className = '' }: { children: ReactNode; className?: string }) { return <section className={`panel ${className}`}>{children}</section> }
export function SectionTitle({ eyebrow, title, description, action }: { eyebrow?: string; title: string; description?: string; action?: ReactNode }) { return <div className="flex items-start justify-between gap-4"><div>{eyebrow && <div className="eyebrow mb-1.5">{eyebrow}</div>}<h2 className="text-lg font-semibold tracking-tight text-slate-950">{title}</h2>{description && <p className="mt-1 text-sm text-slate-500">{description}</p>}</div>{action}</div> }
export function ConfidenceBadge({ score, confirmed = false, onConfirm }: { score: number; confirmed?: boolean; onConfirm?: () => void }) {
  if (confirmed) return <Badge tone="green" dot>Confirmed</Badge>
  if (score >= 0.85) return <span className="text-xs text-slate-500 numeric">{Math.round(score * 100)}%</span>
  return <button onClick={onConfirm} className="inline-flex items-center gap-1.5 rounded-md border border-amber-200 bg-amber-50 px-2 py-1 text-[10px] font-semibold uppercase tracking-wide text-amber-800 hover:bg-amber-100"><CircleAlert size={12} /> {Math.round(score * 100)}% · Confirm</button>
}
export function EmptyState({ icon, title, description }: { icon?: ReactNode; title: string; description: string }) { return <div className="flex flex-col items-center justify-center py-14 text-center">{icon && <div className="mb-3 rounded-xl bg-slate-100 p-3 text-slate-500">{icon}</div>}<h3 className="text-sm font-semibold text-slate-800">{title}</h3><p className="mt-1 max-w-sm text-xs leading-5 text-slate-500">{description}</p></div> }
export function ProgressBar({ value, tone = 'slate' }: { value: number; tone?: 'slate' | 'amber' | 'green' }) { return <div className="h-1.5 overflow-hidden rounded-full bg-slate-100"><div className={`h-full rounded-full ${tone === 'green' ? 'bg-emerald-500' : tone === 'amber' ? 'bg-amber-500' : 'bg-slate-800'}`} style={{ width: `${value}%` }} /></div> }
export function SelectChevron() { return <ChevronDown size={14} className="pointer-events-none text-slate-400" /> }
export function Spinner() { return <LoaderCircle size={15} className="animate-spin" /> }
export function CheckIcon() { return <Check size={14} /> }
export function InfoIcon() { return <Info size={14} /> }
