import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { BarChart3, Boxes, ChevronDown, Database, FilePlus2, Info, LockKeyhole, ShieldCheck, Sparkles, X } from 'lucide-react'
import { useState } from 'react'
import { useApp } from '../lib/AppContext'
import { Badge, Button } from '../components/ui'
import type { Role } from '../types/domain'

const nav = [{ to: '/shipments', label: 'Shipment queue', icon: Boxes }, { to: '/shipments/new', label: 'New shipment', icon: FilePlus2 }, { to: '/analytics', label: 'Accuracy & audit', icon: BarChart3 }]

export function AppLayout() {
  const { role, userName, liveApi, loading, apiError, authenticated, signIn } = useApp()
  const location = useLocation()
  const [notesOpen, setNotesOpen] = useState(false)
  const [switching, setSwitching] = useState(false)
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [authError, setAuthError] = useState('')
  const submitLogin = async (event: React.FormEvent) => {
    event.preventDefault(); setAuthError('')
    try { await signIn(email, password); setPassword(''); setSwitching(false) }
    catch (error) { setAuthError(error instanceof Error ? error.message : 'Sign in failed') }
  }
  if (!authenticated) return <div className="flex min-h-screen items-center justify-center bg-slate-50 p-6"><form onSubmit={event => void submitLogin(event)} className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-6 shadow-panel"><div className="mb-5"><div className="text-lg font-semibold text-slate-950">Sign in to Customs Operations</div><p className="mt-1 text-xs text-slate-500">Use your documentation workspace account.</p></div><label className="mb-3 block text-xs font-medium text-slate-600">Email<input type="email" required value={email} onChange={event => setEmail(event.target.value)} className="field-input mt-1" /></label><label className="block text-xs font-medium text-slate-600">Password<input type="password" required value={password} onChange={event => setPassword(event.target.value)} className="field-input mt-1" /></label>{(authError || apiError) && <div className="mt-3 text-xs text-rose-600">{authError || apiError}</div>}<Button variant="primary" disabled={loading} className="mt-5 w-full">{loading ? 'Signing in…' : 'Sign in'}</Button></form></div>
  const pageTitle = location.pathname.includes('/review')
    ? 'Review workspace'
    : location.pathname.includes('/approval')
      ? 'Approval gatekeeper'
      : location.pathname.includes('/extract')
        ? 'Extraction'
        : location.pathname.includes('/analytics')
          ? 'Accuracy & audit'
          : location.pathname.includes('/new')
            ? 'New shipment'
            : 'Shipment queue'

  return (
    <div className="flex min-h-screen bg-slate-50">
      <aside className="fixed inset-y-0 left-0 z-20 flex w-[232px] flex-col border-r border-slate-200 bg-white">
        <div className="flex h-[73px] items-center gap-3 border-b border-slate-200 px-5">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-slate-900 text-white"><Sparkles size={16} /></div>
          <div>
            <div className="text-sm font-semibold tracking-tight">Company A</div>
            <div className="text-[10px] font-medium uppercase tracking-[0.15em] text-slate-400">Customs operations</div>
          </div>
        </div>
        <div className="px-3 pt-5">
          <div className="eyebrow px-2 pb-2">Workspace</div>
          {nav.map(item => {
            const Icon = item.icon
            return (
              <NavLink key={item.to} to={item.to} className={({ isActive }) => `mb-1 flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition ${isActive ? 'bg-slate-100 text-slate-950' : 'text-slate-500 hover:bg-slate-50 hover:text-slate-800'}`}>
                <Icon size={16} strokeWidth={1.8} />{item.label}
              </NavLink>
            )
          })}
        </div>
        <div className="mt-auto border-t border-slate-200 p-4">
          <div className="mb-3 flex items-center gap-2">
            <ShieldCheck size={15} className="text-emerald-600" />
            <span className="text-xs font-semibold text-slate-700">DPDP Act 2023</span>
            <Badge tone="green">Compliant</Badge>
          </div>
          <div className="rounded-lg bg-slate-50 p-3">
            <div className="text-[10px] font-semibold uppercase tracking-wider text-slate-400">Workspace region</div>
            <div className="mt-1 text-xs font-medium text-slate-700">Nhava Sheva · India</div>
            <div className="mt-0.5 text-[10px] text-slate-400">Backend database · {loading ? 'syncing…' : apiError ? 'connection issue' : 'synced'}</div>
          </div>
        </div>
      </aside>
      <main className="ml-[232px] min-h-screen flex-1">
        <header className="sticky top-0 z-10 flex h-[73px] items-center justify-between border-b border-slate-200 bg-white/90 px-8 backdrop-blur">
          <div>
            <div className="text-[10px] font-semibold uppercase tracking-[0.16em] text-slate-400">Company A / {pageTitle}</div>
            <div className="mt-1 text-sm font-semibold text-slate-900">Documentation control room</div>
          </div>
          <div className="flex items-center gap-3">
            <button title="System notes and production limits" onClick={() => setNotesOpen(true)} className="flex h-8 w-8 items-center justify-center rounded-lg border border-slate-200 text-slate-400 transition hover:border-slate-300 hover:bg-slate-50 hover:text-slate-700">
              <Info size={15} />
            </button>
            <Badge tone={liveApi && !apiError ? 'green' : apiError ? 'amber' : 'blue'} dot>
              {liveApi && !apiError ? 'Backend connected' : 'Backend unavailable'}
            </Badge>
            <button onClick={() => { setSwitching(true); setAuthError('') }} className="group flex items-center gap-2.5 rounded-lg border border-slate-200 bg-white px-3 py-2 text-left transition hover:border-slate-300 hover:bg-slate-50">
              <div className={`flex h-7 w-7 items-center justify-center rounded-full text-[11px] font-bold ${role === 'ROLE_MANAGER' ? 'bg-indigo-100 text-indigo-700' : 'bg-amber-100 text-amber-700'}`}>
                {role === 'ROLE_MANAGER' ? 'MS' : 'RM'}
              </div>
              <div>
                <div className="text-xs font-semibold text-slate-800">{role === 'ROLE_MANAGER' ? 'Documentation Manager' : 'Documentation Executive'}</div>
                <div className="text-[10px] text-slate-400">{userName} · switch role</div>
              </div>
              <ChevronDown size={14} className="text-slate-400 transition group-hover:text-slate-700" />
            </button>
          </div>
        </header>
        <div className="px-8 py-7"><Outlet /></div>
        {notesOpen && <SystemNotesModal onClose={() => setNotesOpen(false)} />}
        {switching && <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/30 p-6"><form onSubmit={event => void submitLogin(event)} className="w-full max-w-sm rounded-xl border border-slate-200 bg-white p-6 shadow-2xl"><div className="mb-4 text-sm font-semibold text-slate-900">Sign in as another role</div><label className="mb-3 block text-xs font-medium text-slate-600">Email<input type="email" required value={email} onChange={event => setEmail(event.target.value)} className="field-input mt-1" /></label><label className="block text-xs font-medium text-slate-600">Password<input type="password" required value={password} onChange={event => setPassword(event.target.value)} className="field-input mt-1" /></label>{authError && <div className="mt-3 text-xs text-rose-600">{authError}</div>}<div className="mt-5 flex justify-end gap-2"><Button onClick={() => setSwitching(false)}>Cancel</Button><Button variant="primary" disabled={loading}>{loading ? 'Signing in…' : 'Sign in'}</Button></div></form></div>}
      </main>
    </div>
  )
}

function SystemNotesModal({ onClose }: { onClose: () => void }) {
  return <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/30 p-6 backdrop-blur-sm"><div className="w-full max-w-[620px] overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl"><div className="flex items-start justify-between border-b border-slate-200 px-6 py-5"><div><div className="eyebrow mb-1">Architecture transparency</div><h2 className="text-lg font-semibold tracking-tight text-slate-950">System notes & production limits</h2><p className="mt-1 text-xs text-slate-500">The safeguards and boundaries behind this workspace.</p></div><button onClick={onClose} className="rounded-lg p-2 text-slate-400 hover:bg-slate-100 hover:text-slate-700"><X size={17} /></button></div><div className="grid gap-3 p-5"><LimitNote icon={<Sparkles size={16} />} title="Vision AI failure modes" text="Handwritten stamps, low-contrast seals, cropped signatures, and severe skew can produce low confidence or unreadable results. The reviewer must confirm, edit, or route the page to manual entry before export." tone="indigo" /><LimitNote icon={<ShieldCheck size={16} />} title="DPDP Act 2023 encryption scope" text="Importer contact data is encrypted at rest with AES-256-GCM through the backend attribute converter. Document bytes and extracted business fields remain subject to your storage, retention, and access-control policies." tone="green" /><LimitNote icon={<Database size={16} />} title="Aiven MySQL connection limits" text="The backend deliberately caps HikariCP at 5 connections with 2 idle connections and a 20-second timeout to respect Aiven free-tier limits. Scale the pool only with an explicit database plan change." tone="blue" /><LimitNote icon={<LockKeyhole size={16} />} title="Customs portal filing is out of scope" text="The system stops at a validated Icegate / filing-software checklist export in CSV or Excel. Direct portal automation is intentionally excluded to keep credentials, final submission authority, and customs declarations under human control." tone="amber" /></div><div className="flex items-center justify-between border-t border-slate-200 bg-slate-50 px-6 py-4"><div className="text-[10px] text-slate-400">Last reviewed · 05 Nov 2026 · Company A platform team</div><button onClick={onClose} className="primary-btn">Got it</button></div></div></div>
}

function LimitNote({ icon, title, text, tone }: { icon: React.ReactNode; title: string; text: string; tone: 'indigo' | 'green' | 'blue' | 'amber' }) {
  const colors = { indigo: 'bg-indigo-50 text-indigo-700', green: 'bg-emerald-50 text-emerald-700', blue: 'bg-blue-50 text-blue-700', amber: 'bg-amber-50 text-amber-700' }
  return <div className="flex gap-3 rounded-xl border border-slate-200 p-3.5"><div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${colors[tone]}`}>{icon}</div><div><div className="text-xs font-semibold text-slate-800">{title}</div><div className="mt-1 text-[11px] leading-5 text-slate-500">{text}</div></div></div>
}
