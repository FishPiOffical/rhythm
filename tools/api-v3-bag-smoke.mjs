#!/usr/bin/env node
/** After default-client scope upgrade, IDEA rebuild/restart and a fresh login.
 * API_V3_TOKEN: disposable account with activity.read/activity.write.
 * Read-only unless API_V3_ALLOW_CARD_USE=1. Card use is persistent and is not undone.
 * API_V3_CARD_CODE=checkin1day|checkin2days (default checkin1day).
 * Optional API_V3_READ_ONLY_TOKEN verifies missing write scope.
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080', token = process.env.API_V3_TOKEN;
let failures = 0;
async function check(name, run) { try { await run(); console.log(`PASS ${name}`); } catch (e) { failures++; console.error(`FAIL ${name}: ${e.code || e.name || 'request failed'}`); } }
async function request(path, { method = 'GET', body, key, status = 200, credential = token } = {}) {
  const response = await fetch(new URL(`/api/v3${path}`, base), { method, redirect: 'manual', signal: AbortSignal.timeout(20000),
    headers: { 'User-Agent': 'RhythmV3BagSmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {}),
      ...(method === 'GET' ? {} : { 'Content-Type': 'application/json' }), ...(key ? { 'Idempotency-Key': key } : {}) },
    ...(method === 'GET' ? {} : { body: JSON.stringify(body || {}) }) });
  assert.equal(response.status, status); assert.ok(response.headers.get('x-request-id'));
  const json = await response.json();
  if (status === 200) assert.equal(json.code, 0); else { assert.notEqual(json.code, 0); assert.equal(json.data, null); }
  return json.data;
}
for (const card of ['checkin1day', 'checkin2days']) await check(`${card} requires Bearer`, () => request(`/me/bag/${card}/use`, { method: 'POST', credential: null, key: randomUUID(), status: 401 }));
if (process.env.API_V3_READ_ONLY_TOKEN) await check('activity.write required', () => request('/me/bag/checkin1day/use', { method: 'POST', credential: process.env.API_V3_READ_ONLY_TOKEN, key: randomUUID(), status: 403 }));
const counts = bag => Object.fromEntries(bag.list.map(item => [item.code, item.count]));
if (token) {
  await check('personal bag DTO', async () => {
    const bag = await request('/me/bag'); assert.deepEqual(Object.keys(bag), ['list']);
    for (const entry of bag.list) { assert.deepEqual(Object.keys(entry).sort(), ['code', 'count']); assert.equal(typeof entry.code, 'string'); assert.ok(Number.isInteger(entry.count)); }
  });
  if (process.env.API_V3_ALLOW_CARD_USE === '1') await check('use one card/replay/check fresh bag', async () => {
    const code = process.env.API_V3_CARD_CODE || 'checkin1day'; assert.ok(['checkin1day', 'checkin2days'].includes(code));
    const path = `/me/bag/${code}/use`, key = randomUUID(), before = counts(await request('/me/bag'));
    await request(path, { method: 'POST', body: { count: 2 }, key: randomUUID(), status: 422 });
    await request(path, { method: 'POST', status: 422 });
    if (!(before[code] > 0)) { await request(path, { method: 'POST', key, status: 409 }); return; }
    const first = await request(path, { method: 'POST', key }), days = code === 'checkin1day' ? 1 : 2;
    assert.deepEqual(Object.keys(first).sort(), ['itemCode', 'remainingDays', 'bag'].sort());
    assert.equal(first.itemCode, code); assert.equal(first.remainingDays, (before.sysCheckinRemain || 0) + days);
    assert.equal(counts(first.bag)[code], before[code] - 1);
    assert.deepEqual(await request(path, { method: 'POST', key }), first);
    const after = counts(await request('/me/bag')); assert.equal(after[code], before[code] - 1); assert.equal(after.sysCheckinRemain, first.remainingDays);
    for (const [item, count] of Object.entries(before)) if (![code, 'sysCheckinRemain'].includes(item)) assert.equal(after[item], count);
  });
}
process.exitCode = failures ? 1 : 0;
