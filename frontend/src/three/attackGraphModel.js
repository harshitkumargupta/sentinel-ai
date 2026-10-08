// Pure model for the incident attack graph (no three.js imports — shared by 2D and 3D views).
//
// Nodes are the entities in the incident's events: source IPs, users and hosts. Edges are attack
// steps between them: IP → user (the IP acted on the account), user → host, IP → host (no user).
// Each node/edge keeps its first-seen time (for replay), event count, event types and the highest
// severity seen (for colour).

export const SEVERITY_ORDER = { LOW: 1, MEDIUM: 2, HIGH: 3, CRITICAL: 4 };
export const SEVERITY_COLOR = { LOW: '#3ddc97', MEDIUM: '#f0b429', HIGH: '#ff8c42', CRITICAL: '#ff5a5f' };

function hostOf(e) {
  return e.entityKey && e.entityKey.startsWith('host:') ? e.entityKey.slice(5) : null;
}

function maxSev(a, b) {
  return (SEVERITY_ORDER[b] || 0) > (SEVERITY_ORDER[a] || 0) ? b : a;
}

export function buildAttackGraph(events = []) {
  const nodes = new Map();
  const edges = new Map();
  const sorted = [...events].filter((e) => e && e.eventTimestamp).sort((a, b) => a.eventTimestamp.localeCompare(b.eventTimestamp));

  const touchNode = (id, type, label, e) => {
    const t = Date.parse(e.eventTimestamp);
    const n = nodes.get(id) || { id, type, label, firstSeen: t, lastSeen: t, count: 0, types: new Set(), severity: 'LOW' };
    n.count += 1;
    n.firstSeen = Math.min(n.firstSeen, t);
    n.lastSeen = Math.max(n.lastSeen, t);
    n.types.add(e.eventType);
    n.severity = maxSev(n.severity, e.severity || 'LOW');
    nodes.set(id, n);
    return n;
  };
  const touchEdge = (source, target, e) => {
    const id = `${source}->${target}`;
    const t = Date.parse(e.eventTimestamp);
    const ed = edges.get(id) || { id, source, target, firstSeen: t, count: 0, types: new Set(), severity: 'LOW' };
    ed.count += 1;
    ed.firstSeen = Math.min(ed.firstSeen, t);
    ed.types.add(e.eventType);
    ed.severity = maxSev(ed.severity, e.severity || 'LOW');
    edges.set(id, ed);
  };

  sorted.forEach((e) => {
    const ip = e.sourceIp ? touchNode(`ip:${e.sourceIp}`, 'ip', e.sourceIp, e) : null;
    const user = e.username ? touchNode(`user:${e.username}`, 'user', e.username, e) : null;
    const h = hostOf(e);
    const host = h ? touchNode(`host:${h}`, 'host', h, e) : null;
    if (ip && user) touchEdge(ip.id, user.id, e);
    if (user && host) touchEdge(user.id, host.id, e);
    if (ip && host && !user) touchEdge(ip.id, host.id, e);
  });

  const out = (m) => [...m.values()].map((x) => ({ ...x, types: [...x.types] }));
  const times = sorted.map((e) => Date.parse(e.eventTimestamp));
  return { nodes: out(nodes), edges: out(edges), start: times[0] ?? null, end: times[times.length - 1] ?? null, eventCount: sorted.length };
}

/** Deterministic layout: IPs left, users centre, hosts right; spread vertically. Returns id → [x, y]. */
export function layoutAttackGraph(nodes) {
  const cols = { ip: -1, user: 0, host: 1 };
  const byType = { ip: [], user: [], host: [] };
  nodes.forEach((n) => (byType[n.type] || byType.ip).push(n));
  const pos = {};
  Object.entries(byType).forEach(([type, list]) => {
    list.forEach((n, i) => { pos[n.id] = [cols[type], (i - (list.length - 1) / 2)]; });
  });
  return pos;
}
