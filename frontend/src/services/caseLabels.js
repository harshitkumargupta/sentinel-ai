// Case-management wording shared by the incident and offense views.

export const STATUS_LABELS = {
  OPEN: 'New',
  INVESTIGATING: 'In Progress',
  CONTAINED: 'Contained',
  RESOLVED: 'Resolved',
  FALSE_POSITIVE: 'False Positive',
  CLOSED: 'Closed',
};

export const STATUSES = Object.keys(STATUS_LABELS);

/** Allowed next statuses — mirrors IncidentService.TRANSITIONS on the server. */
export const NEXT_STATUS = {
  OPEN: ['INVESTIGATING', 'FALSE_POSITIVE'],
  INVESTIGATING: ['CONTAINED', 'RESOLVED', 'FALSE_POSITIVE'],
  CONTAINED: ['RESOLVED', 'FALSE_POSITIVE'],
  RESOLVED: ['CLOSED', 'INVESTIGATING'],
  FALSE_POSITIVE: ['CLOSED'],
  CLOSED: [],
};

export const PRIORITIES = ['P1', 'P2', 'P3', 'P4'];

export function statusLabel(s) {
  return STATUS_LABELS[s] || s;
}
