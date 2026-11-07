#!/usr/bin/env node
/** Run after IDEA rebuild/restart. Defaults to auth checks and personal list reads.
 * API_V3_TOKEN: disposable owner token with content.read/content.write.
 * API_V3_COLUMN_ID: existing test column; mutations require API_V3_ALLOW_COLUMN_WRITES=1.
 * Original title/cover restored after mutation. Order and deletion are manual tests.
 * API_V3_OTHER_COLUMN_ID: a column owned by a different account (must return 404).
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080', token = process.env.API_V3_TOKEN;
let failures = 0;
async function check(name, run) { try { await run(); console.log(`PASS ${name}`); } catch (e) { failures++; console.error(`FAIL ${name}: ${e.code || e.name || 'request failed'}`); } }
async function request(path, { method = 'GET', body, key, status = 200, credential = token } = {}) {
  const response = await fetch(new URL(`/api/v3${path}`, base), { method, redirect: 'manual', signal: AbortSignal.timeout(20000),
    headers: { 'User-Agent': 'RhythmV3ColumnSmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {}),
      ...(method === 'GET' ? {} : { 'Content-Type': 'application/json' }), ...(key ? { 'Idempotency-Key': key } : {}) },
    ...(method === 'GET' ? {} : { body: JSON.stringify(body || {}) }) });
  assert.equal(response.status, status); assert.ok(response.headers.get('x-request-id'));
  const json = await response.json();
  if (status === 200) assert.equal(json.code, 0); else { assert.notEqual(json.code, 0); assert.equal(json.data, null); }
  return json.data;
}
for (const [path, method] of [['/columns/0/title', 'PUT'], ['/columns/0/cover', 'PUT'], ['/me/columns/order', 'PUT'], ['/columns/0', 'DELETE']]) {
  await check(`Bearer required ${method} ${path}`, () => request(path, { method, credential: null, key: randomUUID(), status: 401 }));
}
if (token) await check('owner list', async () => assert.ok(Array.isArray((await request('/me/columns')).list)));
if (token && process.env.API_V3_ALLOW_COLUMN_WRITES === '1') {
  const id = process.env.API_V3_COLUMN_ID;
  if (!id || !/^[0-9]{1,19}$/.test(id)) throw new Error('Set API_V3_COLUMN_ID to a disposable column');
  await check('title and cover, replay, stale replay, restore', async () => {
    let original, latest;
    const firstKey = randomUUID(), coverKey = randomUUID();
    try {
      // Obtain original from all personal pages to avoid assuming the fixture is on page one.
      for (let p = 1; ; p++) {
        const page = await request(`/me/columns?p=${p}&size=50`);
        original = page.list.find(c => c.id === id); if (original || page.list.length < 50) break;
      }
      assert.ok(original);
      const title = `v3 test ${Date.now()}`;
      latest = await request(`/columns/${id}/title`, { method: 'PUT', body: { title }, key: firstKey });
      assert.equal(latest.title, title);
      assert.deepEqual(Object.keys(latest).sort(), ['id', 'title', 'order', 'coverURL', 'articleCount', 'createdAt', 'updatedAt'].sort());
      assert.deepEqual(await request(`/columns/${id}/title`, { method: 'PUT', body: { title }, key: firstKey }), latest);
      await request(`/columns/${id}/title`, { method: 'PUT', body: { title: 'different' }, key: firstKey, status: 409 });
      const cover = await request(`/columns/${id}/cover`, { method: 'PUT', body: { coverURL: '/images/favicon.png' }, key: coverKey });
      assert.equal(cover.coverURL, '/images/favicon.png');
      assert.deepEqual(await request(`/columns/${id}/cover`, { method: 'PUT', body: { coverURL: '/images/favicon.png' }, key: coverKey }), cover);
      await request(`/columns/${id}/cover`, { method: 'PUT', body: { coverURL: 'javascript:alert(1)' }, key: randomUUID(), status: 422 });
      await request('/me/columns/order', { method: 'PUT', body: { columnIds: [id, id] }, key: randomUUID(), status: 422 });
      await request(`/columns/${id}/title`, { method: 'PUT', body: { title: original.title }, key: randomUUID() });
      await request(`/columns/${id}/title`, { method: 'PUT', body: { title }, key: firstKey });
      const current = await request(`/columns/${id}/cover`, { method: 'PUT', body: { coverURL: original.coverURL }, key: randomUUID() });
      assert.equal(current.title, original.title);
    } finally {
      if (original) {
        await request(`/columns/${id}/title`, { method: 'PUT', body: { title: original.title }, key: randomUUID() });
        await request(`/columns/${id}/cover`, { method: 'PUT', body: { coverURL: original.coverURL }, key: randomUUID() });
      }
    }
  });
  const other = process.env.API_V3_OTHER_COLUMN_ID;
  if (other) {
    assert.match(other, /^[0-9]{1,19}$/);
    for (const [path, method, body] of [[`/columns/${other}/title`, 'PUT', { title: 'denied' }], [`/columns/${other}/cover`, 'PUT', { coverURL: '' }], ['/me/columns/order', 'PUT', { columnIds: [id, other] }], [`/columns/${other}`, 'DELETE', {}]]) {
      await check(`owner check ${path}`, () => request(path, { method, body, key: randomUUID(), status: 404 }));
    }
  }
}
process.exitCode = failures ? 1 : 0;
