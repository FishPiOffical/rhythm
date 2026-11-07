#!/usr/bin/env node
/** Notification HTTP tests after SQL scope upgrade, IDEA restart and a fresh v3 login.
 * API_V3_BASE_URL defaults to localhost:8080.
 * API_V3_TOKEN: notifications.read + notifications.write.
 * API_V3_OTHER_TOKEN: another user with the same scopes (optional).
 * API_V3_READONLY_TOKEN: notifications.read without notifications.write (optional).
 * API_V3_NOTIFICATION_ID: an owned test notification; marks it read (optional).
 * API_V3_DELETE_NOTIFICATION_ID: an owned disposable notification; permanently deletes it (optional).
 * Bulk mutations are deliberately left to the manual checklist; this script does not clear a real inbox.
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080';
const token = process.env.API_V3_TOKEN;
let failed = 0;
function inspect(value) {
  if (!value || typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) {
    assert.ok(!/^(?:userId|dataId|userPassword|userEmail|userPhone|userQQ|apiKey|token|secret2fa|articleIP|articleUA|commentIP|commentUA|sourceRequestId)$/i.test(key));
    inspect(child);
  }
}
async function check(name, run) {
  try { await run(); console.log(`PASS ${name}`); }
  catch (error) { failed++; console.error(`FAIL ${name}: ${error.code || error.name || 'request failed'}`); }
}
async function request(method, suffix, status, { credential = token, key, body } = {}) {
  const response = await fetch(new URL(`/api/v3/me/notifications${suffix}`, base), {
    method, redirect: 'manual', signal: AbortSignal.timeout(30000), headers: {
      'User-Agent': 'RhythmV3NotificationSmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {}),
      ...(key ? { 'Idempotency-Key': key } : {}), ...(body !== undefined ? { 'Content-Type': 'application/json' } : {})
    }, ...(body !== undefined ? { body: JSON.stringify(body) } : {})
  });
  assert.equal(response.status, status);
  assert.ok(response.headers.get('x-request-id'));
  assert.match(response.headers.get('cache-control') || '', /no-store/);
  const result = await response.json();
  if (status === 200) { assert.equal(result.code, 0); inspect(result.data); }
  else { assert.notEqual(result.code, 0); assert.equal(result.data, null); }
  return result.data;
}
for (const [method, path] of [['GET', ''], ['GET', '/unread-count'], ['GET', '/0'], ['POST', '/0/read'], ['POST', '/read'], ['DELETE', '/0'], ['DELETE', '']]) {
  await check(`anonymous denied ${method} ${path}`, () => request(method, path, 401, { credential: null, key: randomUUID() }));
}
if (token) {
  for (const type of ['all', 'commented', 'reply', 'at', 'following', 'point', 'broadcast', 'sys-announce', 'other']) {
    await check(`list ${type}`, async () => {
      const data = await request('GET', `?type=${type}&size=2`, 200);
      assert.ok(Array.isArray(data.list)); assert.ok(data.list.length <= 2);
      assert.equal(data.pagination.pageCount, Math.ceil(data.pagination.total / data.pagination.size));
      for (const row of data.list) {
        if (type !== 'all') assert.equal(row.category, type);
        if (!row.contentAvailable) { assert.equal(row.url, ''); assert.ok(!row.article && !row.comment && !row.actor); }
      }
    });
  }
  await check('unread count static route', async () => {
    const data = await request('GET', '/unread-count', 200);
    assert.equal(data.total, Object.values(data.categories).reduce((sum, value) => sum + value, 0));
  });
  await check('unread filter', async () => {
    const data = await request('GET', '?status=unread&size=2', 200);
    for (const row of data.list) assert.equal(row.hasRead, false);
  });
  for (const suffix of ['?type=unknown', '?status=unknown', '?size=51', '/not-an-id']) {
    await check(`invalid query ${suffix}`, () => request('GET', suffix, 422));
  }
  await check('missing notification', () => request('GET', '/0', 404));
  await check('delete category requires explicit category', () => request('DELETE', '', 422, { key: randomUUID(), body: {} }));
  await check('delete all is not a supported category mutation', () => request('DELETE', '', 422, { key: randomUUID(), body: { type: 'all' } }));
  if (process.env.API_V3_READONLY_TOKEN) {
    await check('write scope required', () => request('POST', '/0/read', 403, { credential: process.env.API_V3_READONLY_TOKEN, key: randomUUID() }));
  }
  const id = process.env.API_V3_NOTIFICATION_ID;
  if (id) {
    await check('owned detail', async () => assert.equal((await request('GET', `/${id}`, 200)).id, id));
    await check('mark read and replay', async () => {
      const key = randomUUID();
      const first = await request('POST', `/${id}/read`, 200, { key });
      assert.deepEqual(await request('POST', `/${id}/read`, 200, { key }), first);
      assert.equal((await request('GET', `/${id}`, 200)).hasRead, true);
    });
    if (process.env.API_V3_OTHER_TOKEN) {
      for (const [method, suffix] of [['GET', `/${id}`], ['POST', `/${id}/read`], ['DELETE', `/${id}`]]) {
        await check(`another user denied ${method}`, () => request(method, suffix, 404, { credential: process.env.API_V3_OTHER_TOKEN, key: randomUUID() }));
      }
    }
  } else console.log('SKIP read mutation fixture: set API_V3_NOTIFICATION_ID');
  const disposable = process.env.API_V3_DELETE_NOTIFICATION_ID;
  if (disposable) {
    await check('delete owned disposable notification and replay', async () => {
      const key = randomUUID();
      const first = await request('DELETE', `/${disposable}`, 200, { key });
      assert.deepEqual(await request('DELETE', `/${disposable}`, 200, { key }), first);
      await request('GET', `/${disposable}`, 404);
    });
  } else console.log('SKIP deletion fixture: set API_V3_DELETE_NOTIFICATION_ID');
} else console.log('SKIP authenticated checks: fresh login and set API_V3_TOKEN');
process.exitCode = failed ? 1 : 0;
