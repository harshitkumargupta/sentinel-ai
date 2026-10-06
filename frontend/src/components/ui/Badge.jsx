/**
 * Badge — severity and status pills. Color is never the only signal: a severity badge pairs a dot,
 * a glyph and the text label, so it is distinguishable without color (accessibility).
 */
const SEV_GLYPH = { LOW: '▪', MEDIUM: '◆', HIGH: '▲', CRITICAL: '⬣' };

export default function Badge({ variant, children, className = '', ...props }) {
  const v = (variant || '').toLowerCase();
  return (
    <span className={`ui-badge ${v ? `ui-badge--${v}` : ''} ${className}`} {...props}>
      <span className="ui-badge__dot" aria-hidden="true" />
      {children}
    </span>
  );
}

/** SeverityBadge — LOW/MEDIUM/HIGH/CRITICAL with glyph + text. */
export function SeverityBadge({ severity }) {
  const s = String(severity || 'LOW').toUpperCase();
  return (
    <span className={`ui-badge ui-badge--${s.toLowerCase()}`} title={s}>
      <span aria-hidden="true">{SEV_GLYPH[s] || '▪'}</span>
      {s}
    </span>
  );
}
