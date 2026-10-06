/** Card — surface container with optional title/subtitle/actions header. */
export default function Card({ title, subtitle, actions, children, className = '', ...props }) {
  return (
    <section className={`ui-card ${className}`} {...props}>
      {(title || actions) && (
        <header className="ui-card__header">
          <div>
            {title && <div className="ui-card__title">{title}</div>}
            {subtitle && <div className="ui-card__subtitle">{subtitle}</div>}
          </div>
          {actions && <div className="ui-card__actions">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  );
}
