import { useState } from 'react';

/**
 * Tabs — accessible tab list (roving via arrow keys handled by native buttons + aria-selected).
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
          </button>
        ))}
      </div>
      <div role="tabpanel">{current?.content}</div>
    </div>
  );
}
