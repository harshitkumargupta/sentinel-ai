import { useState } from 'react';
import { motion } from 'framer-motion';

/**
 * Tabs — accessible tab list with a sliding underline indicator (framer layoutId).
 * `tabs` is [{ id, label, content }]. Uncontrolled by default.
 */
export default function Tabs({ tabs, initial }) {
  const [active, setActive] = useState(initial || tabs[0]?.id);
  const current = tabs.find((t) => t.id === active) || tabs[0];
  return (
    <div>
      <div className="ui-tabs" role="tablist">
        {tabs.map((t) => (
          <button
            key={t.id}
            role="tab"
            aria-selected={t.id === active}
            className="ui-tab"
            onClick={() => setActive(t.id)}
          >
            {t.label}
            {t.id === active && (
              <motion.span layoutId="tab-underline" className="ui-tab__underline" aria-hidden="true"
                transition={{ type: 'spring', stiffness: 500, damping: 40 }} />
            )}
          </button>
        ))}
      </div>
      <div role="tabpanel">{current?.content}</div>
    </div>
  );
}
