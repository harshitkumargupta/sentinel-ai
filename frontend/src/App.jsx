import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute.jsx';

// Route-level code splitting: each page (and its heavy deps like Recharts) loads on demand, keeping
// the initial/login payload small. A lightweight fallback shows while a page chunk loads.
const LoginPage = lazy(() => import('./pages/LoginPage.jsx'));
const DashboardPage = lazy(() => import('./pages/DashboardPage.jsx'));
const EventsPage = lazy(() => import('./pages/EventsPage.jsx'));
const AlertsPage = lazy(() => import('./pages/AlertsPage.jsx'));
const IncidentsPage = lazy(() => import('./pages/IncidentsPage.jsx'));
const IncidentDetailPage = lazy(() => import('./pages/IncidentDetailPage.jsx'));
const AdminPage = lazy(() => import('./pages/AdminPage.jsx'));
const EvaluationPage = lazy(() => import('./pages/EvaluationPage.jsx'));
const SitesPage = lazy(() => import('./pages/SitesPage.jsx'));
const AdminRiskPage = lazy(() => import('./pages/AdminRiskPage.jsx'));
const PipelinePage = lazy(() => import('./pages/PipelinePage.jsx'));

const Loading = () => <div style={{ padding: '2rem', color: 'var(--muted)' }}>Loading…</div>;

export default function App() {
  return (
    <Suspense fallback={<Loading />}>
      <Routes>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/dashboard" element={<ProtectedRoute><DashboardPage /></ProtectedRoute>} />
        <Route path="/events" element={<ProtectedRoute><EventsPage /></ProtectedRoute>} />
        <Route path="/alerts" element={<ProtectedRoute><AlertsPage /></ProtectedRoute>} />
        <Route path="/incidents" element={<ProtectedRoute><IncidentsPage /></ProtectedRoute>} />
        <Route path="/incidents/:id" element={<ProtectedRoute><IncidentDetailPage /></ProtectedRoute>} />
        <Route path="/evaluation" element={<ProtectedRoute><EvaluationPage /></ProtectedRoute>} />
        <Route path="/admin" element={<ProtectedRoute roles={['ADMIN']}><AdminPage /></ProtectedRoute>} />
        <Route path="/sites" element={<ProtectedRoute><SitesPage /></ProtectedRoute>} />
        <Route path="/admin-risk" element={<ProtectedRoute roles={['ADMIN']}><AdminRiskPage /></ProtectedRoute>} />
        <Route path="/pipeline" element={<ProtectedRoute roles={['ADMIN']}><PipelinePage /></ProtectedRoute>} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </Suspense>
  );
}
