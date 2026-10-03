import { useEffect, useState } from 'react';
import { getHealth } from '../services/health.service.js';

/**
 * Polls the backend /api/health endpoint once on mount so the dashboard can
 * show whether the backend is reachable.
 */
export function useHealth() {
  const [status, setStatus] = useState('checking');
  const [detail, setDetail] = useState(null);

  useEffect(() => {
    let active = true;
    getHealth()
      .then((data) => {
        if (!active) return;
        setStatus('up');
        setDetail(data);
      })
      .catch(() => {
        if (!active) return;
        setStatus('down');
      });
    return () => {
      active = false;
    };
  }, []);

  return { status, detail };
}
