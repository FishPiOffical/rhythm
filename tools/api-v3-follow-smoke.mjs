#!/usr/bin/env node
/** After IDEA rebuild/restart. Read-only by default.
 * API_V3_TOKEN: test account with profile.read/content.read/follows.write.
 * API_V3_ALLOW_RELATION_WRITES=1 enables writes ONLY for explicit disposable fixture IDs:
 * API_V3_FOLLOW_USER_ID, API_V3_FOLLOW_TAG_ID, API_V3_COLLECT_ARTICLE_ID, API_V3_SUBSCRIBE_ARTICLE_ID.
 * Existing relations are skipped; only newly added test relations are cleaned up.
 * Role restrictions, private discussions, cache rollback and recipient notification counts require manual fixtures.
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080';
const token = process.env.API_V3_TOKEN;
let failures = 0;
async function check(name, run) { try { await run(); console.log(`PASS ${name}`); } catch (e) { failures++; console.error(`FAIL ${name}: ${e.code || e.name || 'request failed'}`); } }
async function request(path, { method = 'GET', key, status = 200, credential = token } = {}) {
  const response = await fetch(new URL(`/api/v3${path}`, base), { method, redirect: 'manual', signal: AbortSignal.timeout(20000),
    headers: { 'User-Agent': 'RhythmV3FollowSmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {}),
      ...(method === 'GET' ? {} : { 'Content-Type': 'application/json' }), ...(key ? { 'Idempotency-Key': key } : {}) },
    ...(method === 'GET' ? {} : { body: '{}' }) });
  assert.equal(response.status, status); assert.ok(response.headers.get('x-request-id'));
  const body = await response.json();
  if (status === 200) assert.equal(body.code, 0); else { assert.notEqual(body.code, 0); assert.equal(body.data, null); }
  return body.data;
}
const paths = ['/me/follows/users', '/me/follows/tags', '/me/collections', '/me/subscriptions'];
for (const path of paths) {
  await check(`no Bearer ${path}`, () => request(path, { status: 401, credential: null }));
  if (token) await check(`owned list ${path}`, async () => {
    const data = await request(`${path}?size=2`); assert.ok(Array.isArray(data.list));
    assert.equal(data.pagination.pageCount, Math.ceil(data.pagination.total / 2));
    for (const row of data.list) if (row.available === false) assert.deepEqual(Object.keys(row).sort(), ['available', 'id']);
  });
}
async function exists(path, id) {
  for (let p = 1; p <= 100; p++) {
    const data = await request(`${path}?p=${p}&size=50`);
    if (data.list.some(row => row.id === id)) return true;
    if (p >= data.pagination.pageCount) return false;
  }
  throw new Error('Use a smaller disposable test account');
}
if (token && process.env.API_V3_ALLOW_RELATION_WRITES === '1') {
  for (const [path, variable] of paths.map((path, i) => [path, ['API_V3_FOLLOW_USER_ID', 'API_V3_FOLLOW_TAG_ID', 'API_V3_COLLECT_ARTICLE_ID', 'API_V3_SUBSCRIBE_ARTICLE_ID'][i]])) {
    const id = process.env[variable]; if (!id) continue;
    assert.match(id, /^[0-9]{1,32}$/);
    await check(`write/replay/cleanup ${path}`, async () => {
      if (await exists(path, id)) { console.log(`SKIP existing fixture relationship ${path}`); return; }
      const target = `${path}/${id}`, key = randomUUID(), removeKey = randomUUID();
      let created = false;
      try {
        const first = await request(target, { method: 'PUT', key }); created = true;
        assert.deepEqual(first, { following: true, changed: true });
        assert.deepEqual(await request(target, { method: 'PUT', key }), first);
        assert.deepEqual(await request(target, { method: 'PUT', key: randomUUID() }), { following: true, changed: false });
        assert.ok(await exists(path, id));
        await request(target, { method: 'DELETE', key, status: 409 });
        const removed = await request(target, { method: 'DELETE', key: removeKey });
        assert.deepEqual(removed, { following: false, changed: true });
        assert.deepEqual(await request(target, { method: 'DELETE', key: removeKey }), removed);
        assert.deepEqual(await request(target, { method: 'DELETE', key: randomUUID() }), { following: false, changed: false });
        assert.deepEqual(await request(target, { method: 'PUT', key }), first); // old replay must not recreate
        assert.equal(await exists(path, id), false); created = false;
      } finally { if (created) await request(target, { method: 'DELETE', key: randomUUID() }); }
    });
  }
}
process.exitCode = failures ? 1 : 0;
