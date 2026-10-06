import { useEffect } from 'react';
import { AnimatePresence, motion } from 'framer-motion';
import { useTheme } from '../../theme/ThemeProvider.jsx';
import Button from './Button.jsx';

/** Right-side slide-over Drawer. Escape closes; motion respects reduced-motion. */
export default function Drawer({ open, onClose, title, children }) {
  const { reducedMotion } = useTheme();
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => e.key === 'Escape' && onClose?.();
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [open, onClose]);

  return (
    <AnimatePresence>
      {open && (
        <>
          <motion.div className="ui-overlay" style={{ display: 'block' }} onClick={onClose}
            initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }}
            transition={{ duration: reducedMotion ? 0 : 0.2 }} />
          <motion.aside className="ui-drawer" role="dialog" aria-modal="true" aria-label={title}
            initial={{ x: reducedMotion ? 0 : '100%' }} animate={{ x: 0 }} exit={{ x: reducedMotion ? 0 : '100%' }}
            transition={{ type: reducedMotion ? 'tween' : 'spring', duration: reducedMotion ? 0 : 0.3, bounce: 0.1 }}>
            <div className="ui-card__header">
              <div className="ui-card__title">{title}</div>
              <Button variant="ghost" size="sm" icon="✕" aria-label="Close" onClick={onClose} />
            </div>
            {children}
          </motion.aside>
        </>
      )}
    </AnimatePresence>
  );
}
