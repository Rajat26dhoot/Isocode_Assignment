import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import './index.css'
import { AppProvider } from './lib/AppContext'
import { AppLayout } from './layouts/AppLayout'
import { ShipmentQueuePage } from './pages/ShipmentQueuePage'
import { NewShipmentPage } from './pages/NewShipmentPage'
import { ExtractionProgressPage } from './pages/ExtractionProgressPage'
import { ReviewWorkspacePage } from './pages/ReviewWorkspacePage'
import { ApprovalGatekeeperPage } from './pages/ApprovalGatekeeperPage'
import { AnalyticsPage } from './pages/AnalyticsPage'

createRoot(document.getElementById('root')!).render(<StrictMode><AppProvider><BrowserRouter><Routes><Route element={<AppLayout />}><Route path="/" element={<Navigate to="/shipments" replace />} /><Route path="/shipments" element={<ShipmentQueuePage />} /><Route path="/shipments/new" element={<NewShipmentPage />} /><Route path="/shipments/:id/extract" element={<ExtractionProgressPage />} /><Route path="/shipments/:id/review" element={<ReviewWorkspacePage />} /><Route path="/shipments/:id/approval" element={<ApprovalGatekeeperPage />} /><Route path="/analytics" element={<AnalyticsPage />} /></Route></Routes></BrowserRouter></AppProvider></StrictMode>)
