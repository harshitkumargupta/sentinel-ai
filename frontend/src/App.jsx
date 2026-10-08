import { Suspense, lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import Layout from './components/Layout.jsx';

const LandingPage = lazy(() => import('./landing/LandingPage.jsx'));

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
const OffensesPage = lazy(() => import('./pages/OffensesPage.jsx'));
const SearchPage = lazy(() => import('./pages/SearchPage.jsx'));
const DemoCenterPage = lazy(() => import('./pages/DemoCenterPage.jsx'));
const ReportsPage = lazy(() => import('./pages/ReportsPage.jsx'));
const AssetsPage = lazy(() => import('./pages/AssetsPage.jsx'));
const NotificationSettingsPage = lazy(() => import('./pages/NotificationSettingsPage.jsx'));
const PlaybooksPage = lazy(() => import('./pages/PlaybooksPage.jsx'));
const HoneytokensPage = lazy(() => import('./pages/HoneytokensPage.jsx'));
const CoveragePage = lazy(() => import('./pages/CoveragePage.jsx'));
const ExecutivePage = lazy(() => import('./pages/ExecutivePage.jsx'));
const ConnectWebsitePage = lazy(() => import('./pages/ConnectWebsitePage.jsx'));
const VulnerabilitiesPage = lazy(() => import('./pages/VulnerabilitiesPage.jsx'));
const AuditPage = lazy(() => import('./pages/AuditPage.jsx'));
const ReferenceSetsPage = lazy(() => import('./pages/ReferenceSetsPage.jsx'));

const admin = (el) => <ProtectedRoute roles={['ADMIN']}>{el}</ProtectedRoute>;
const analyst = (el) => <ProtectedRoute roles={['ANALYST', 'ADMIN']}>{el}</ProtectedRoute>;

export default function App() {
  return (
    <Suspense fallback={<div className="route-fallback" />}>
      <Routes>
      {/* Public landing page — no auth required, no Layout shell */}
      <Route path="/" element={<LandingPage />} />
      <Route path="/login" element={<LoginPage />} />
      {/* Authenticated shell: persistent sidebar/top bar, animated page transitions. */}
      <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
        <Route path="/dashboard" element={<DashboardPage />} />
        <Route path="/executive" element={<ExecutivePage />} />
        <Route path="/connect-website" element={admin(<ConnectWebsitePage />)} />
        <Route path="/vulnerabilities" element={<VulnerabilitiesPage />} />
        <Route path="/events" element={<EventsPage />} />
        <Route path="/alerts" element={<AlertsPage />} />
        <Route path="/incidents" element={<IncidentsPage />} />
        <Route path="/incidents/:id" element={<IncidentDetailPage />} />
        <Route path="/offenses" element={<OffensesPage />} />
        <Route path="/offenses/:id" element={<IncidentDetailPage />} />
        <Route path="/evaluation" element={<EvaluationPage />} />
        <Route path="/sites" element={<SitesPage />} />
        <Route path="/log-sources" element={analyst(<LogSourcesPage />)} />
        <Route path="/rules" element={<RulesPage />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/reports" element={analyst(<ReportsPage />)} />
        <Route path="/assets" element={<AssetsPage />} />
        <Route path="/notifications" element={admin(<NotificationSettingsPage />)} />
        <Route path="/playbooks" element={<PlaybooksPage />} />
        <Route path="/honeytokens" element={analyst(<HoneytokensPage />)} />
        <Route path="/coverage" element={analyst(<CoveragePage />)} />
        <Route path="/reference-sets" element={<ReferenceSetsPage />} />
        <Route path="/demo-center" element={admin(<DemoCenterPage />)} />
        <Route path="/admin" element={admin(<AdminPage />)} />
        <Route path="/admin-risk" element={admin(<AdminRiskPage />)} />
        <Route path="/audit" element={admin(<AuditPage />)} />
        <Route path="/pipeline" element={admin(<PipelinePage />)} />
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Route>
      </Routes>
    </Suspense>
  );
}
