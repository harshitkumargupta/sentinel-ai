import { describe, expect, it } from 'vitest';
import { buildAttackGraph, layoutAttackGraph } from './attackGraphModel.js';

const ev = (t, type, sev, ip, user, host) => ({
  eventTimestamp: `2026-10-08T10:00:0${t}Z`, eventType: type, severity: sev, sourceIp: ip, username: user,
  entityKey: host ? `host:${host}` : null,
});

describe('buildAttackGraph', () => {
  it('builds IP/user/host nodes and step edges with first-seen, counts and max severity', () => {
    const g = buildAttackGraph([
      ev(2, 'FAILED_LOGIN', 'LOW', '45.33.1.1', 'root', 'web-01'),
      ev(1, 'FAILED_LOGIN', 'LOW', '45.33.1.1', 'root', 'web-01'),
      ev(5, 'PRIVILEGE_ESCALATION', 'CRITICAL', null, 'root', 'web-01'),
    ]);
    expect(g.nodes.map((n) => n.id).sort()).toEqual(['host:web-01', 'ip:45.33.1.1', 'user:root']);
    const ipUser = g.edges.find((e) => e.id === 'ip:45.33.1.1->user:root');
    expect(ipUser.count).toBe(2);
    expect(ipUser.firstSeen).toBe(Date.parse('2026-10-08T10:00:01Z'));
    expect(g.edges.find((e) => e.id === 'user:root->host:web-01').severity).toBe('CRITICAL');
    expect(g.nodes.find((n) => n.id === 'user:root').types).toContain('PRIVILEGE_ESCALATION');
    expect(g.start).toBe(Date.parse('2026-10-08T10:00:01Z'));
    expect(g.eventCount).toBe(3);
  });

  it('handles empty input and lays out columns', () => {
    expect(buildAttackGraph([]).nodes).toEqual([]);
    const pos = layoutAttackGraph([{ id: 'ip:a', type: 'ip' }, { id: 'user:b', type: 'user' }, { id: 'host:c', type: 'host' }]);
    expect(pos['ip:a'][0]).toBe(-1);
    expect(pos['host:c'][0]).toBe(1);
  });
});
