import { useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useTheme } from '../../theme/ThemeProvider.jsx';
import Button from './Button.jsx';

/** Modal dialog with overlay, Escape-to-close, and motion that respects reduced-motion. */
export default function Modal({ open, onClose, title, children, footer }) {
  const { reducedMotion } = useTheme();
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => e.key === 'Escape' && onClose?.();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  const t = reducedMotion ? { duration: 0 } : { duration: 0.2 };
  return (
    <AnimatePresence>
      {open && (
        <motion.div
          className="ui-overlay" onClick={onClose}
          initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} transition={t}
        >
          <motion.div
            className="ui-modal" role="dialog" aria-modal="true" aria-label={title}
            onClick={(e) => e.stopPropagation()}
            initial={{ scale: reducedMotion ? 1 : 0.96, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }} exit={{ scale: reducedMotion ? 1 : 0.96, opacity: 0 }}
            transition={t}
          >
            <div className="ui-card__header">
              <div className="ui-card__title">{title}</div>
              <Button variant="ghost" size="sm" icon="✕" aria-label="Close" onClick={onClose} />
            </div>
            <div>{children}</div>
            {footer && <div style={{ marginTop: 'var(--sp-4)', display: 'flex', gap: 'var(--sp-2)', justifyContent: 'flex-end' }}>{footer}</div>}
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
