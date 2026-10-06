/**
 * Renders the right state for a data view. Pass `loading`, `error`, `empty`, and `children`.
 */
export default function DataState({ loading, error, empty, emptyText = 'No data.', children }) {
  if (loading) {
    return <div className="state-box">Loading…</div>;
  }
  if (error) {
    return <div className="state-box error-text">{error}</div>;
  }
  if (empty) {
    return <div className="state-box muted">{emptyText}</div>;
  }
  return children;
}
