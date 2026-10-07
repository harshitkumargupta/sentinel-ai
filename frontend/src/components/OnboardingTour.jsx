import { useState } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import Button from './ui/Button.jsx';

const KEY = 'sentinel.onboarded';
const STEPS = [
  { icon: '🛡️', title: 'Welcome to the Command Center', body: 'Your live SOC overview: open incidents, event volume, alert reduction and threat level — all at a glance.' },
  { icon: '🌍', title: 'Live attack globe', body: 'The globe maps live geo-flows from attacker origins to your protected site. Click a source to filter the feed and jump to its incidents.' },
  { icon: '⚙️', title: 'Make it yours', body: 'Top-right: switch sites, theme (light/dark), 3D quality and interface Motion (Full / Reduced / Off). The sidebar navigates every module.' },
  { icon: '⌘', title: 'Command palette', body: 'Press ⌘K (Ctrl+K) anywhere to jump to any page or action without leaving the keyboard.' },
];

function seen() {
  try { return localStorage.getItem(KEY) === '1'; } catch { return true; }
}

/** First-run onboarding tour: 3–5 steps, skippable, remembered in preferences. */
export default function OnboardingTour() {
  const [open, setOpen] = useState(() => !seen());
  const [i, setI] = useState(0);

  function finish() {
    try { localStorage.setItem(KEY, '1'); } catch { /* ignore */ }
    setOpen(false);
  }
  const step = STEPS[i];
  const last = i === STEPS.length - 1;

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="ui-overlay" initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
          role="dialog" aria-modal="true" aria-label="Getting started">
          <motion.div className="ui-modal tour-card"
            initial={{ scale: 0.96, opacity: 0, y: 8 }} animate={{ scale: 1, opacity: 1, y: 0 }} exit={{ scale: 0.96, opacity: 0 }}
            transition={{ type: 'spring', stiffness: 420, damping: 32 }}>
            <AnimatePresence mode="wait">
              <motion.div key={i} initial={{ opacity: 0, x: 16 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -16 }}
                transition={{ duration: 0.2 }}>
                <div className="tour-icon" aria-hidden="true">{step.icon}</div>
                <h3 className="tour-title">{step.title}</h3>
                <p className="tour-body">{step.body}</p>
              </motion.div>
            </AnimatePresence>
            <div className="tour-dots" aria-hidden="true">
              {STEPS.map((s, idx) => <span key={s.title} className={`tour-dot${idx === i ? ' is-active' : ''}`} />)}
            </div>
            <div className="tour-actions">
              <Button variant="ghost" size="sm" onClick={finish}>Skip</Button>
              <div style={{ display: 'flex', gap: 'var(--sp-2)' }}>
                {i > 0 && <Button variant="secondary" size="sm" onClick={() => setI((n) => n - 1)}>Back</Button>}
                <Button variant="primary" size="sm" onClick={() => (last ? finish() : setI((n) => n + 1))}>
                  {last ? 'Get started' : 'Next'}
                </Button>
              </div>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
