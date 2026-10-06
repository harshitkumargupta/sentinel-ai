/** Button — variants: default | primary | danger | ghost; sizes: md | sm. */
export default function Button({ variant = 'default', size = 'md', icon, children, className = '', ...props }) {
  const cls = [
    'ui-btn',
    variant !== 'default' && `ui-btn--${variant}`,
    size === 'sm' && 'ui-btn--sm',
    !children && icon && 'ui-btn--icon',
    className,
  ].filter(Boolean).join(' ');
  return (
    <button className={cls} {...props}>
      {icon && <span aria-hidden="true">{icon}</span>}
      {children}
    </button>
  );
}
