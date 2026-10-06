import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { useTheme } from '../theme/ThemeProvider.jsx';

/**
 * Command palette (Ctrl/Cmd+K): fuzzy-jump to pages, an incident by id, or a quick action. Fully
 * keyboard-driven (↑/↓ to move, Enter to run, Esc to close).
 */
export default function CommandPalette({ open, onClose, links }) {
  const navigate = useNavigate();
  const { reducedMotion } = useTheme();
  const [q, setQ] = useState('');
  const [active, setActive] = useState(0);
  const inputRef = useRef(null);

  const commands = useMemo(() => {
    const base = links.map((l) => ({ label: `Go to ${l.label}`, icon: l.icon, run: () => navigate(l.to) }));
    const extra = [];
    const num = q.match(/\d+/);
    if (num) {
      extra.push({ label: `Open incident #${num[0]}`, icon: '✸', run: () => navigate(`/incidents/${num[0]}`) });
    }
    const all = [...extra, ...base];
    if (!q.trim()) return all;
    const needle = q.toLowerCase();
    return all.filter((c) => c.label.toLowerCase().includes(needle));
  }, [q, links, navigate]);

  useEffect(() => { if (open) { setQ(''); setActive(0); setTimeout(() => inputRef.current?.focus(), 10); } }, [open]);
  useEffect(() => { setActive(0); }, [q]);

  function onKeyDown(e) {
    if (e.key === 'ArrowDown') { e.preventDefault(); setActive((a) => Math.min(a + 1, commands.length - 1)); }
    else if (e.key === 'ArrowUp') { e.preventDefault(); setActive((a) => Math.max(a - 1, 0)); }
    else if (e.key === 'Enter') { e.preventDefault(); const c = commands[active]; if (c) { c.run(); onClose(); } }
    else if (e.key === 'Escape') { onClose(); }
  }

  return (
    <AnimatePresence>
      {open && (
        <motion.div className="ui-overlay" style={{ alignItems: 'flex-start', paddingTop: '12vh' }} onClick={onClose}
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={{ duration: reducedMotion ? 0 : 0.15 }}>
          <motion.div className="cmdk" role="dialog" aria-modal="true" aria-label="Command palette"
            onClick={(e) => e.stopPropagation()}
            initial={{ y: reducedMotion ? 0 : -12, opacity: 0 }} animate={{ y: 0, opacity: 1 }} exit={{ opacity: 0 }}
            transition={{ duration: reducedMotion ? 0 : 0.15 }}>
            <input ref={inputRef} className="cmdk__input" placeholder="Jump to a page, incident #, or action…"
              value={q} onChange={(e) => setQ(e.target.value)} onKeyDown={onKeyDown}
              role="combobox" aria-expanded="true" aria-controls="cmdk-list" aria-autocomplete="list" />
            <ul className="cmdk__list" id="cmdk-list" role="listbox">
              {commands.length === 0 && <li className="cmdk__empty">No matches</li>}
              {commands.map((c, i) => (
                <li key={c.label} role="option" aria-selected={i === active}
                  className={`cmdk__item ${i === active ? 'cmdk__item--active' : ''}`}
                  onMouseEnter={() => setActive(i)} onClick={() => { c.run(); onClose(); }}>
                  <span aria-hidden="true">{c.icon}</span> {c.label}
                </li>
              ))}
            </ul>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
