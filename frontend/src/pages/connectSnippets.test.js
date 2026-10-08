import { describe, expect, it } from 'vitest';
import * as S from './connectSnippets.js';

const BASE = 'http://192.168.1.20:8088';
const KEY = 'sk_TESTKEY123';

describe('connect snippets', () => {
  it('fill in the SentinelAI URL and key and post to /api/ingest/events', () => {
    for (const fn of [S.nodeSnippet, S.springSnippet, S.flaskSnippet, S.djangoSnippet, S.curlTest]) {
      const out = fn(BASE, KEY);
      expect(out).toContain(`${BASE}/api/ingest/events`);
      expect(out).toContain(KEY);
      for (const f of ['sourceIp', 'method', 'path', 'status', 'userAgent']) expect(out).toContain(f);
    }
    expect(S.agentCommand(BASE, KEY)).toContain(`--url ${BASE}`);
  });

  it('test traffic targets only the owner site and never SentinelAI', () => {
    const cmds = S.testTraffic('https://shop.example.test/').map((t) => t.cmd).join('\n');
    expect(cmds).toContain('https://shop.example.test/login');
    expect(cmds).toContain('/.env');
    expect(cmds).not.toContain('8088');
  });
});
