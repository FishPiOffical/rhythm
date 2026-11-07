#!/usr/bin/env node
/** Read-only checks after IDEA rebuild/restart.
 * API_V3_TOKEN: content.read owner token; API_V3_OTHER_TOKEN: another account with content.read.
 * Optional API_V3_OWN_PENDING_MILESTONE_ID / API_V3_OWN_REJECTED_MILESTONE_ID check owner/public separation.
 * Optional API_V3_NO_CONTENT_SCOPE_TOKEN checks 403.
 * API_V3_ALLOW_MILESTONE_SUBMISSION=1 submits one persistent pending test record (no v3 delete).
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080', token = process.env.API_V3_TOKEN;
let failures = 0;
async function check(name, run) { try { await run(); console.log(`PASS ${name}`); } catch (e) { failures++; console.error(`FAIL ${name}: ${e.code || e.name || 'request failed'}`); } }
async function request(path, status = 200, credential = token, { method = 'GET', body, key } = {}) {
  const response = await fetch(new URL(`/api/v3${path}`, base), { method, redirect: 'manual', signal: AbortSignal.timeout(20000), headers: { 'User-Agent': 'RhythmV3MilestoneSmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {}), ...(method === 'GET' ? {} : { 'Content-Type': 'application/json' }), ...(key ? { 'Idempotency-Key': key } : {}) }, ...(method === 'GET' ? {} : { body: JSON.stringify(body || {}) }) });
  assert.equal(response.status, status); assert.ok(response.headers.get('x-request-id'));
  const json = await response.json();
  if (status === 200) assert.equal(json.code, 0); else { assert.notEqual(json.code, 0); assert.equal(json.data, null); }
  return json.data;
}
function dto(row) {
  assert.deepEqual(Object.keys(row).sort(), ['id', 'title', 'date', 'endDate', 'content', 'media', 'link', 'status', 'createdAt', 'updatedAt'].sort());
  assert.ok(['pending', 'approved', 'rejected'].includes(row.status));
  assert.deepEqual(Object.keys(row.media).sort(), ['url', 'type', 'caption'].sort());
}
await check('submission requires Bearer', () => request('/milestones', 401, null, { method: 'POST', key: randomUUID() }));
for (const path of ['/me/milestones', '/me/milestones/0']) await check(`Bearer required ${path}`, () => request(path, 401, null));
if (process.env.API_V3_NO_CONTENT_SCOPE_TOKEN) await check('content.read required', () => request('/me/milestones', 403, process.env.API_V3_NO_CONTENT_SCOPE_TOKEN));
if (token) {
  for (const status of ['all', 'pending', 'approved', 'rejected']) await check(`owner status ${status}`, async () => {
    const data = await request(`/me/milestones?status=${status}&size=2`);
    assert.ok(Array.isArray(data.list)); assert.ok(data.list.length <= 2);
    assert.equal(data.pagination.pageCount, Math.ceil(data.pagination.total / data.pagination.size));
    for (const row of data.list) { dto(row); if (status !== 'all') assert.equal(row.status, status); }
    if (data.list.length) { const row = await request(`/me/milestones/${data.list[0].id}`); dto(row); assert.equal(row.id, data.list[0].id); }
  });
  for (const query of ['status=other', 'size=51', 'p=0']) await check(`invalid query ${query}`, () => request(`/me/milestones?${query}`, 422));
  for (const [state, id] of [['pending', process.env.API_V3_OWN_PENDING_MILESTONE_ID], ['rejected', process.env.API_V3_OWN_REJECTED_MILESTONE_ID]]) {
    if (!id) continue; assert.match(id, /^[0-9]{1,32}$/);
    await check(`owner sees ${state}; public cannot`, async () => {
      const row = await request(`/me/milestones/${id}`); dto(row); assert.equal(row.status, state);
      await request(`/milestones/${id}`, 404);
      if (process.env.API_V3_OTHER_TOKEN) await request(`/me/milestones/${id}`, 404, process.env.API_V3_OTHER_TOKEN);
    });
  }
}
if (token && process.env.API_V3_ALLOW_MILESTONE_SUBMISSION === '1') await check('Markdown submission/replay/owner detail/public hidden', async () => {
  const key = randomUUID(), body = { title: `v3 submission test ${Date.now()}`, date: '2026-10-17', content: '# Heading\n\n**Bold**\n\n<script>alert(1)</script>' };
  await request('/milestones', 422, token, { method: 'POST', key: randomUUID(), body: { ...body, status: 'approved' } });
  await request('/milestones', 422, token, { method: 'POST', body });
  const first = await request('/milestones', 200, token, { method: 'POST', key, body });
  assert.deepEqual(Object.keys(first).sort(), ['id', 'status', 'createdAt', 'updatedAt'].sort()); assert.equal(first.status, 'pending');
  assert.deepEqual(await request('/milestones', 200, token, { method: 'POST', key, body }), first);
  await request('/milestones', 409, token, { method: 'POST', key, body: { ...body, title: 'changed title' } });
  const own = await request(`/me/milestones/${first.id}`); dto(own);
  assert.equal(own.status, 'pending'); assert.match(own.content, /<strong>Bold<\/strong>/); assert.ok(!own.content.includes('<script'));
  await request(`/milestones/${first.id}`, 404);
  if (process.env.API_V3_OTHER_TOKEN) await request(`/me/milestones/${first.id}`, 404, process.env.API_V3_OTHER_TOKEN);
});
process.exitCode = failures ? 1 : 0;
