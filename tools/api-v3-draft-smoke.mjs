#!/usr/bin/env node
/** Mutating development integration test: creates temporary drafts and deletes only those drafts.
 * Run after applying sql/20261016_api_v3.sql and IDEA recompilation/restart.
 * API_V3_BASE_URL defaults to http://localhost:8080.
 * Required API_V3_TOKEN has content.read + content.write.
 * Optional API_V3_OTHER_TOKEN tests another user attempting to edit/delete the created draft.
 * Optional API_V3_READONLY_TOKEN lacks content.write.
 * No credentials or response bodies are printed. IDs are printed only if cleanup fails.
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080';
const token = process.env.API_V3_TOKEN;
if (!token) throw new Error('Set API_V3_TOKEN with content.read and content.write');
const cleanup = new Set();
let failed = 0;
async function check(name, run) {
  try { await run(); console.log(`PASS ${name}`); }
  catch (error) { failed++; console.error(`FAIL ${name}: ${error.code || error.name || 'request failed'}`); }
}
async function request(method, suffix, status, { credential = token, key, body, raw, contentType = 'application/json' } = {}) {
  const response = await fetch(new URL(`/api/v3/me/drafts${suffix}`, base), {
    method, redirect: 'manual', signal: AbortSignal.timeout(30000), headers: {
      'User-Agent': 'RhythmV3DraftSmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {}),
      ...(key ? { 'Idempotency-Key': key } : {}), ...(contentType ? { 'Content-Type': contentType } : {})
    }, ...(raw !== undefined ? { body: raw } : body !== undefined ? { body: JSON.stringify(body) } : {})
  });
  assert.equal(response.status, status);
  assert.ok(response.headers.get('x-request-id'));
  assert.match(response.headers.get('cache-control') || '', /no-store/);
  const data = await response.json();
  if (status === 200) assert.equal(data.code, 0);
  else { assert.notEqual(data.code, 0); assert.equal(data.data, null); }
  return data.data;
}
const payload = { title: 'v3 接口临时测试草稿', content: '编辑器正文\n<b>源文本</b>', thoughtContent: '[思绪记录]', type: 3 };
try {
  await check('unauthenticated create denied', () => request('POST', '', 401, { credential: null, key: randomUUID(), body: payload }));
  if (process.env.API_V3_READONLY_TOKEN) {
    await check('missing write scope denied', () => request('POST', '', 403, { credential: process.env.API_V3_READONLY_TOKEN, key: randomUUID(), body: payload }));
  }
  await check('missing key', () => request('POST', '', 422, { body: payload }));
  await check('unsupported media type', () => request('POST', '', 415, { key: randomUUID(), body: payload, contentType: 'text/plain' }));
  await check('ambiguous JSON rejected', () => request('POST', '', 400, { key: randomUUID(), raw: '{"title":"a","title":"b"}' }));
  await check('unknown owner field rejected', () => request('POST', '', 422, { key: randomUUID(), body: { ...payload, userId: 'other' } }));
  await check('wrong scalar type rejected', () => request('POST', '', 422, { key: randomUUID(), body: { ...payload, commentable: 'true' } }));
  let draft;
  const createKey = randomUUID();
  await check('create', async () => {
    draft = await request('POST', '', 200, { key: createKey, body: payload });
    cleanup.add(draft.id);
    assert.ok(draft.id);
    assert.ok(!Object.hasOwn(draft, 'content'));
  });
  if (draft) {
    await check('retry same request returns same draft', async () => {
      const retry = await request('POST', '', 200, { key: createKey, body: { type: 3, thoughtContent: payload.thoughtContent, content: payload.content, title: payload.title } });
      assert.deepEqual(retry, draft);
    });
    await check('different payload conflicts', () => request('POST', '', 409, { key: createKey, body: { ...payload, title: 'changed' } }));
    await check('read preserves both editor and thought content', async () => {
      const detail = await request('GET', `/${draft.id}`, 200, { contentType: null });
      assert.equal(detail.content, payload.content);
      assert.equal(detail.thoughtContent, payload.thoughtContent);
    });
    if (process.env.API_V3_OTHER_TOKEN) {
      for (const method of ['PUT', 'DELETE']) {
        await check(`another user ${method} denied`, () => request(method, `/${draft.id}`, 404, {
          credential: process.env.API_V3_OTHER_TOKEN, key: randomUUID(), ...(method === 'PUT' ? { body: payload } : {})
        }));
      }
    }
    const updateKey = randomUUID();
    let updated;
    await check('full replacement preserves ID and creation time', async () => {
      updated = await request('PUT', `/${draft.id}`, 200, { key: updateKey, body: { title: '替换后的草稿', content: 'updated' } });
      assert.equal(updated.id, draft.id);
      assert.equal(updated.createdAt, draft.createdAt);
      const detail = await request('GET', `/${draft.id}`, 200, { contentType: null });
      assert.equal(detail.content, 'updated');
      assert.equal(detail.thoughtContent, '');
    });
    await check('retry replacement does not create another revision', async () => {
      const retry = await request('PUT', `/${draft.id}`, 200, { key: updateKey, body: { content: 'updated', title: '替换后的草稿' } });
      assert.deepEqual(retry, updated);
    });
    const deleteKey = randomUUID();
    await check('delete and retry', async () => {
      await request('DELETE', `/${draft.id}`, 200, { key: deleteKey });
      await request('DELETE', `/${draft.id}`, 200, { key: deleteKey });
      await request('GET', `/${draft.id}`, 404, { contentType: null });
      cleanup.delete(draft.id);
    });
  }
  await check('concurrent create with one key produces one draft', async () => {
    const key = randomUUID();
    const outcomes = await Promise.allSettled(Array.from({ length: 4 }, () => request('POST', '', 200, { key, body: payload })));
    const succeeded = outcomes.filter(row => row.status === 'fulfilled').map(row => row.value);
    for (const row of succeeded) cleanup.add(row.id);
    assert.equal(succeeded.length, 4);
    assert.equal(new Set(succeeded.map(row => row.id)).size, 1);
  });
} finally {
  for (const id of cleanup) {
    try { await request('DELETE', `/${id}`, 200, { key: randomUUID() }); }
    catch { failed++; console.error(`Cleanup incomplete for test draft ${id}`); }
  }
}
process.exitCode = failed ? 1 : 0;
