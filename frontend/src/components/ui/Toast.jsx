import { AnimatePresence, motion } from 'framer-motion';
import { createContext, useCallback, useContext, useRef, useState } from 'react';

const ToastContext = createContext(null);

/** ToastProvider — mount once near the app root; use `useToast()` to push messages. */
export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);
  const idRef = useRef(0);

  const dismiss = useCallback((id) => setToasts((t) => t.filter((x) => x.id !== id)), []);
  const push = useCallback((message, opts = {}) => {
    const id = ++idRef.current;
    const ttl = opts.ttl ?? 4000;
    setToasts((t) => [...t, { id, message, variant: opts.variant || 'info', ttl }]);
    if (ttl) setTimeout(() => dismiss(id), ttl);
    return id;
  }, [dismiss]);

  return (
    <ToastContext.Provider value={{ push, dismiss }}>
      {children}
      <div className="ui-toasts" aria-live="polite" aria-atomic="false">
        <AnimatePresence>
          {toasts.map((t) => (
            <motion.div key={t.id} className={`ui-toast ui-toast--${t.variant}`} role="status"
              initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: 24 }}
              transition={{ type: 'spring', stiffness: 420, damping: 32 }}
              onClick={() => dismiss(t.id)}>
              <span>{t.message}</span>
              {t.ttl > 0 && (
                <motion.span className="ui-toast__timer" aria-hidden="true"
                  initial={{ scaleX: 1 }} animate={{ scaleX: 0 }}
                  transition={{ duration: t.ttl / 1000, ease: 'linear' }} />
              )}
            </motion.div>
          ))}
        </AnimatePresence>
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const ctx = useContext(ToastContext);
  if (!ctx) throw new Error('useToast must be used within ToastProvider');
  return ctx;
}
