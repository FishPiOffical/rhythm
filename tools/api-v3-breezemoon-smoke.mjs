#!/usr/bin/env node
/** 在 IDEA 重新编译或重启后运行，API_V3_ALLOW_BREEZEMOON_WRITES=1 才启用写入。
 * API_V3_TOKEN 使用已绑定手机并具备 content.read/content.write、发布与删除权限的临时账号。
 * 仅创建一条临时清风明月，并在 finally 中删除。共享业务配额照常生效。
 * 可选的 API_V3_PRIVATE_BREEZEMOON_ID 用于检查不可访问的夹具，请使用无关账号的令牌。
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080';
const token = process.env.API_V3_TOKEN;
let failures = 0;
async function check(name, run) { try { await run(); console.log(`PASS ${name}`); } catch (e) { failures++; console.error(`FAIL ${name}: ${e.code || e.name || 'request failed'}`); } }
async function request(path, { method = 'GET', body, key, status = 200, credential = token } = {}) {
  const response = await fetch(new URL(`/api/v3${path}`, base), { method, redirect: 'manual', signal: AbortSignal.timeout(30000),
    headers: { 'User-Agent': 'RhythmV3BreezemoonSmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {}),
      ...(method === 'GET' ? {} : { 'Content-Type': 'application/json' }), ...(key ? { 'Idempotency-Key': key } : {}) },
    ...(method === 'GET' ? {} : { body: JSON.stringify(body || {}) }) });
  assert.equal(response.status, status); assert.ok(response.headers.get('x-request-id'));
  const json = await response.json();
  if (status !== 200) { assert.notEqual(json.code, 0); assert.equal(json.data, null); return; }
  assert.equal(json.code, 0); return json.data;
}
function dto(row) {
  assert.deepEqual(Object.keys(row).sort(), ['id', 'content', 'contentHtml', 'createdAt', 'updatedAt', 'author'].sort());
  assert.deepEqual(Object.keys(row.author).sort(), ['id', 'userName', 'userNickname', 'userAvatarURL'].sort());
}
await check('public list', async () => { const result = await request('/breezemoons?size=2', { credential: null }); result.list.forEach(dto); });
await check('missing detail', () => request('/breezemoons/0', { status: 404, credential: null }));
await check('invalid detail ID', () => request('/breezemoons/not-an-id', { status: 422, credential: null }));
await check('publish requires Bearer', () => request('/breezemoons', { method: 'POST', body: { content: 'test' }, key: randomUUID(), credential: null, status: 401 }));
if (process.env.API_V3_PRIVATE_BREEZEMOON_ID) await check('private fixture hidden', () => request(`/breezemoons/${process.env.API_V3_PRIVATE_BREEZEMOON_ID}`, { status: 404, credential: null }));
await check('following feed requires Bearer', () => request('/me/watching/breezemoons', { status: 401, credential: null }));
if (token) await check('following feed', async () => { const page = await request('/me/watching/breezemoons?size=2'); page.list.forEach(dto); assert.equal(new Set(page.list.map(row => row.id)).size, page.list.length); });
if (token && process.env.API_V3_ALLOW_BREEZEMOON_WRITES === '1') await check('create/read/replay/delete', async () => {
  const key = randomUUID(), content = `v3 接口联调测试 ${Date.now()}`;
  let id;
  try {
    const first = await request('/breezemoons', { method: 'POST', body: { content }, key }); id = first.id; dto(first);
    assert.deepEqual(await request('/breezemoons', { method: 'POST', body: { content }, key }), first);
    await request('/breezemoons', { method: 'POST', body: { content: `${content} changed` }, key, status: 409 });
    assert.equal((await request(`/breezemoons/${id}`)).content, content);
    const editKey = randomUUID(), edited = `${content} 已编辑`;
    const updated = await request(`/breezemoons/${id}`, { method: 'PUT', key: editKey, body: { content: edited } }); dto(updated);
    assert.equal(updated.createdAt, first.createdAt); assert.equal(updated.author.id, first.author.id);
    assert.deepEqual(await request(`/breezemoons/${id}`, { method: 'PUT', key: editKey, body: { content: edited } }), updated);
    await request(`/breezemoons/${id}`, { method: 'PUT', key: editKey, body: { content }, status: 409 });
    assert.equal((await request(`/breezemoons/${id}`)).content, edited);
    const removeKey = randomUUID();
    await request(`/breezemoons/${id}`, { method: 'DELETE', key: removeKey });
    await request(`/breezemoons/${id}`, { method: 'DELETE', key: removeKey });
    await request(`/breezemoons/${id}`, { status: 404 });
    assert.deepEqual(await request('/breezemoons', { method: 'POST', body: { content }, key }), first);
    await request(`/breezemoons/${id}`, { status: 404 }); id = undefined;
  } finally { if (id) await request(`/breezemoons/${id}`, { method: 'DELETE', key: randomUUID() }); }
});
process.exitCode = failures ? 1 : 0;
