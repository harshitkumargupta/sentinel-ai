import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { markAllRead, myNotifications } from '../services/notifications.service.js';
import { useLiveRefresh } from '../hooks/useLiveRefresh.js';

/** Top-bar bell: unread count, latest notifications, mark all read. */
export default function NotificationBell() {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  const load = useCallback(() => { myNotifications().then(setItems).catch(() => {}); }, []);
  useEffect(() => { load(); }, [load]);
  useLiveRefresh(load, 20000);
  useEffect(() => {
    const close = (e) => { if (ref.current && !ref.current.contains(e.target)) setOpen(false); };
    document.addEventListener('mousedown', close);
    return () => document.removeEventListener('mousedown', close);
  }, []);
  const unread = items.filter((n) => !n.read).length;
  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <button className="ui-btn ui-btn--ghost ui-btn--icon" aria-label={`Notifications (${unread} unread)`}
        title="Notifications" onClick={() => setOpen((o) => !o)}>
        🔔{unread > 0 && <span className="chip" style={{ marginLeft: 2 }}>{unread}</span>}
      </button>
      {open && (
        <div className="panel" style={{ position: 'absolute', right: 0, top: '110%', width: 360, maxHeight: 420, overflowY: 'auto', zIndex: 50 }}>
          <div className="brand-row" style={{ justifyContent: 'space-between' }}>
            <strong>Notifications</strong>
            {unread > 0 && <button className="ghost small" onClick={async () => { await markAllRead(); load(); }}>Mark all read</button>}
          </div>
          {items.length === 0 && <p className="muted small">Nothing yet.</p>}
          <ul className="breakdown">
            {items.map((n) => (
              <li key={n.id} className={n.read ? 'muted' : ''} style={{ cursor: n.incidentId ? 'pointer' : 'default' }}
                onClick={() => { if (n.incidentId) { setOpen(false); navigate(`/offenses/${n.incidentId}`); } }}>
                <span className="small">{n.message}</span>
                <span className="muted small">{new Date(n.createdAt).toLocaleTimeString()}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
