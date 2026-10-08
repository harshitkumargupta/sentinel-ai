import { useEffect, useRef } from 'react';

// Live refresh: every data view polls on an interval AND reloads immediately when anything in the app
// (or another tab) announces a change — e.g. a Demo Center run, an upload, a response action.
const EVENT = 'sentinel:data-changed';
const CHANNEL = 'sentinel-live';

let channel = null;
function getChannel() {
  if (channel === null) {
    try { channel = typeof BroadcastChannel === 'undefined' ? false : new BroadcastChannel(CHANNEL); } catch { channel = false; }
  }
  return channel || null;
}

/** Tell every mounted data view (this tab and others) to reload now. */
export function emitDataChanged(reason = 'change') {
  window.dispatchEvent(new CustomEvent(EVENT, { detail: { reason } }));
  try { getChannel()?.postMessage({ reason }); } catch { /* tab messaging is best-effort */ }
}

/**
 * Calls `load` on an interval (paused while the tab is hidden) and whenever emitDataChanged fires.
 * `load` may change identity between renders; the latest one is always used.
 */
export function useLiveRefresh(load, intervalMs = 10000) {
  const ref = useRef(load);
  ref.current = load;

  useEffect(() => {
    const run = () => { if (!document.hidden) ref.current?.(); };
    const timer = intervalMs > 0 ? setInterval(run, intervalMs) : null;
    const onChange = () => ref.current?.();
    window.addEventListener(EVENT, onChange);
    const ch = getChannel();
    ch?.addEventListener('message', onChange);
    return () => {
      if (timer) clearInterval(timer);
      window.removeEventListener(EVENT, onChange);
      ch?.removeEventListener('message', onChange);
    };
  }, [intervalMs]);
}
