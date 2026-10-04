import { Navigate, Route, Routes } from 'react-router-dom';
import LoginPage from './pages/LoginPage.jsx';
import DashboardPage from './pages/DashboardPage.jsx';
import EventsPage from './pages/EventsPage.jsx';
import AlertsPage from './pages/AlertsPage.jsx';
import IncidentsPage from './pages/IncidentsPage.jsx';
import IncidentDetailPage from './pages/IncidentDetailPage.jsx';
import AdminPage from './pages/AdminPage.jsx';
import EvaluationPage from './pages/EvaluationPage.jsx';
import SitesPage from './pages/SitesPage.jsx';
import AdminRiskPage from './pages/AdminRiskPage.jsx';
import PipelinePage from './pages/PipelinePage.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';

export default function App() {
  return (
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
  );
}
