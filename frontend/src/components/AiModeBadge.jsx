import { useEffect, useState } from 'react';
import { getAiStatus } from '../services/ai.service.js';

let cached = null;

/** "AI: Offline mode" / "AI: Online model" / "AI: Off" badge, from /api/ai/status (fetched once). */
export default function AiModeBadge() {
  const [status, setStatus] = useState(cached);
  useEffect(() => {
    if (cached) return;
    getAiStatus().then((s) => { cached = s; setStatus(s); }).catch(() => {});
  }, []);
  if (!status) return null;
  const label = !status.enabled ? 'AI: Off' : `AI: ${status.label}`;
  const title = status.enabled
    ? `${status.offline ? 'No API key, no network — ' : ''}provider ${status.provider}, model ${status.model}`
    : 'AI features use the deterministic fallback';
  return <span className={`ai-badge ${status.offline ? 'badge-valid' : 'chip'}`} title={title}>{label}</span>;
}
