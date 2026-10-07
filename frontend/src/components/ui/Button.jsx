import { useState } from 'react';
import { motion } from 'framer-motion';

/**
 * Button — variants: default | primary (gradient) | secondary | ghost | danger | success; sizes md | sm.
 * Motion: hover lift + glow, press scale-down, click ripple, a loading spinner that morphs the label,
 * and a success checkmark. Reduced/Off motion is handled centrally by <MotionConfig> + CSS, so this
 * stays usable without a theme provider. Keeps the <button> element and ui-btn classes intact.
 */
export default function Button({
  variant = 'default', size = 'md', icon, loading = false, success = false,
  children, className = '', onClick, disabled, ...props
}) {
  const [ripples, setRipples] = useState([]);
  const inert = disabled || loading;

  function handleClick(e) {
    if (inert) return;
    const rect = e.currentTarget.getBoundingClientRect();
    const id = Date.now() + Math.random();
    setRipples((r) => [...r, { id, x: e.clientX - rect.left, y: e.clientY - rect.top }]);
    setTimeout(() => setRipples((r) => r.filter((p) => p.id !== id)), 600);
    onClick?.(e);
  }

  const cls = [
    'ui-btn',
    variant !== 'default' && `ui-btn--${variant}`,
    size === 'sm' && 'ui-btn--sm',
    !children && (icon || loading) && 'ui-btn--icon',
    className,
  ].filter(Boolean).join(' ');

  return (
    <motion.button
      className={cls} onClick={handleClick} disabled={inert}
      aria-busy={loading || undefined}
      whileHover={inert ? undefined : { y: -1 }}
      whileTap={inert ? undefined : { scale: 0.97 }}
      transition={{ type: 'spring', stiffness: 500, damping: 30 }}
      {...props}
    >
      <span className="ui-btn__label" data-state={loading ? 'loading' : success ? 'success' : 'idle'}>
        {loading && <span className="ui-btn__spinner" aria-hidden="true" />}
        {!loading && success && <span className="ui-btn__check" aria-hidden="true">✓</span>}
        {!loading && !success && icon && <span aria-hidden="true">{icon}</span>}
        {children && <span>{children}</span>}
      </span>
      {ripples.map((r) => (
        <span key={r.id} className="ui-btn__ripple" style={{ left: r.x, top: r.y }} aria-hidden="true" />
      ))}
    </motion.button>
  );
}
