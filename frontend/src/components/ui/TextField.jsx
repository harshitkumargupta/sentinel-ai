import { useId } from 'react';

/**
 * TextField — input with an animated floating label, focus glow, and an inline error that shakes.
 * Uncontrolled or controlled via standard input props. `error` (string) shows the message + shake.
 */
export default function TextField({ label, error, id, className = '', ...props }) {
  const auto = useId();
  const fid = id || auto;
  return (
    <div className={`ui-field${error ? ' ui-field--error' : ''} ${className}`}>
      <input id={fid} className="ui-field__input" placeholder=" " aria-invalid={error ? true : undefined}
        aria-describedby={error ? `${fid}-msg` : undefined} {...props} />
      <label htmlFor={fid} className="ui-field__label">{label}</label>
      {error && <span id={`${fid}-msg`} className="ui-field__msg" role="alert">{error}</span>}
    </div>
  );
}
