import { useId, useState } from 'react';

/** Accessible tooltip: shows on hover and keyboard focus, linked via aria-describedby. */
export default function Tooltip({ label, children }) {
  const [open, setOpen] = useState(false);
  const id = useId();
  return (
    <span
      className="ui-tooltip"
      onMouseEnter={() => setOpen(true)}
      onMouseLeave={() => setOpen(false)}
      onFocus={() => setOpen(true)}
      onBlur={() => setOpen(false)}
    >
      <span aria-describedby={open ? id : undefined}>{children}</span>
      {open && <span role="tooltip" id={id} className="ui-tooltip__bubble">{label}</span>}
    </span>
  );
}
