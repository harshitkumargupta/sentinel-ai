import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import Layout from './components/Layout.jsx';

// Route-level code splitting: each page (and its heavy deps like Recharts) loads on demand, keeping
// the initial/login payload small. The shell (Layout) stays mounted; only page content transitions.
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
const LogSourcesPage = lazy(() => import('./pages/LogSourcesPage.jsx'));
const RulesPage = lazy(() => import('./pages/RulesPage.jsx'));

const admin = (el) => <ProtectedRoute roles={['ADMIN']}>{el}</ProtectedRoute>;
const analyst = (el) => <ProtectedRoute roles={['ANALYST', 'ADMIN']}>{el}</ProtectedRoute>;

export default function App() {
  return (
    <Suspense fallback={<div className="route-fallback" />}>
      <Routes>
      <Route path="/login" element={<LoginPage />} />
      {/* Authenticated shell: persistent sidebar/top bar, animated page transitions. */}
      <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/events" element={<EventsPage />} />
        <Route path="/alerts" element={<AlertsPage />} />
        <Route path="/incidents" element={<IncidentsPage />} />
        <Route path="/incidents/:id" element={<IncidentDetailPage />} />
        <Route path="/evaluation" element={<EvaluationPage />} />
        <Route path="/sites" element={<SitesPage />} />
        <Route path="/log-sources" element={analyst(<LogSourcesPage />)} />
        <Route path="/rules" element={<RulesPage />} />
        <Route path="/admin" element={admin(<AdminPage />)} />
        <Route path="/admin-risk" element={admin(<AdminRiskPage />)} />
        <Route path="/pipeline" element={admin(<PipelinePage />)} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Route>
      </Routes>
    </Suspense>
  );
}
