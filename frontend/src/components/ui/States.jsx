import Button from './Button.jsx';

/** EmptyState — shown when a data view has no rows. */
export function EmptyState({ icon = '∅', title = 'Nothing here yet', message, action }) {
  return (
    <div className="ui-state">
      <div className="ui-state__icon" aria-hidden="true">{icon}</div>
      <div className="ui-state__title">{title}</div>
      {message && <div>{message}</div>}
      {action}
    </div>
  );
}

/** ErrorState — shown when a data view fails to load; message is derived from the API error code. */
export function ErrorState({ title = 'Something went wrong', message, onRetry }) {
  return (
    <div className="ui-state ui-state--error" role="alert">
      <div className="ui-state__icon" aria-hidden="true">⚠</div>
      <div className="ui-state__title">{title}</div>
      {message && <div>{message}</div>}
      {onRetry && (
        <div style={{ marginTop: 'var(--sp-3)' }}>
          <Button size="sm" onClick={onRetry}>Retry</Button>
        </div>
      )}
    </div>
  );
}
