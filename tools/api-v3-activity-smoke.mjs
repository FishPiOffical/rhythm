#!/usr/bin/env node
/** 更新默认客户端权限后，通过 IDEA 重新编译或重启并重新登录。
 * API_V3_TOKEN 使用具有 activity.read/activity.write 的临时账号。
 * 默认只读。API_V3_ALLOW_REWARD_CLAIM=1 真实领取一次符合条件的奖励。
 * 奖励积分会保留。无奖励账号可验证 HTTP 409 且无转账。
 * 可选的 API_V3_READ_ONLY_TOKEN 用于验证缺少写入权限。
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080', token = process.env.API_V3_TOKEN;
const path = '/api/v3/me/activity/yesterday-reward';
let failures = 0;
async function check(name, run) { try { await run(); console.log(`PASS ${name}`); } catch (e) { failures++; console.error(`FAIL ${name}: ${e.code || e.name || 'request failed'}`); } }
async function request({ method = 'GET', body, key, status = 200, credential = token } = {}) {
  const response = await fetch(new URL(path, base), { method, redirect: 'manual', signal: AbortSignal.timeout(20000),
    headers: { 'User-Agent': 'RhythmV3ActivitySmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {}),
      ...(method === 'GET' ? {} : { 'Content-Type': 'application/json' }), ...(key ? { 'Idempotency-Key': key } : {}) },
    ...(method === 'GET' ? {} : { body: JSON.stringify(body || {}) }) });
  assert.equal(response.status, status); assert.ok(response.headers.get('x-request-id'));
  const json = await response.json();
  if (status === 200) assert.equal(json.code, 0); else { assert.notEqual(json.code, 0); assert.equal(json.data, null); }
  return json.data;
}
await check('status requires Bearer', () => request({ credential: null, status: 401 }));
await check('claim requires Bearer', () => request({ method: 'POST', key: randomUUID(), credential: null, status: 401 }));
if (process.env.API_V3_READ_ONLY_TOKEN) await check('claim requires activity.write', () => request({ method: 'POST', key: randomUUID(), credential: process.env.API_V3_READ_ONLY_TOKEN, status: 403 }));
if (token) {
  await check('status whitelist', async () => {
    const data = await request();
    assert.deepEqual(Object.keys(data).sort(), ['date', 'amount', 'claimed', 'claimable'].sort());
    assert.match(data.date, /^[0-9]{8}$/); assert.ok(Number.isInteger(data.amount) && data.amount >= 0);
    assert.equal(data.claimable, !data.claimed && data.amount > 0);
  });
  if (process.env.API_V3_ALLOW_REWARD_CLAIM === '1') await check('claim/replay/no second transfer', async () => {
    await request({ method: 'POST', body: { userId: 'another' }, key: randomUUID(), status: 422 });
    await request({ method: 'POST', status: 422 });
    for (const expectedDate of ['20230229', '20261026\n', null]) await request({ method: 'POST', body: { expectedDate }, key: randomUUID(), status: 422 });
    const before = await request(), key = randomUUID(), body = { expectedDate: before.date };
    if (!before.claimable) {
      await request({ method: 'POST', key, body, status: 409 }); return;
    }
    const first = await request({ method: 'POST', key, body });
    assert.deepEqual(Object.keys(first).sort(), ['date', 'amount', 'claimed'].sort());
    assert.equal(first.date, before.date); assert.equal(first.amount, before.amount); assert.equal(first.claimed, true);
    assert.deepEqual(await request({ method: 'POST', key, body }), first);
    await request({ method: 'POST', key: randomUUID(), status: 409 });
    const after = await request(); assert.equal(after.claimed, true); assert.equal(after.claimable, false);
  });
}
process.exitCode = failures ? 1 : 0;
