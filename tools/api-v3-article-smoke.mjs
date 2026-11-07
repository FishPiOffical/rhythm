#!/usr/bin/env node
/** Run only after IDEA compilation/restart. Defaults to rejection checks, no content creation.
 * API_V3_TOKEN: content.write + content.read, posting/editing permission, bound phone and sufficient points.
 * API_V3_SCOPELESS_TOKEN: optional token lacking content.write; API_V3_SESSION_COOKIE / API_V3_LEGACY_KEY: optional old credentials.
 * API_V3_ARTICLE_WRITE=1 explicitly creates a draft and a persistent public article in a disposable test account.
 * API_V3_ARTICLE_BODY: optional JSON creation fields (title/content/tags default to a test introduction).
 * Newly created articles cannot be deleted until 24 hours old; clean up through the normal UI when eligible.
 * API_V3_ARTICLE_DELETE=1 + API_V3_ARTICLE_DELETE_ID explicitly deletes an eligible disposable existing article.
 * Uses the existing development environment, never bypasses captcha or blacklists; never prints credentials/content.
 */
import assert from 'node:assert/strict';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080';
const token = process.env.API_V3_TOKEN;
let failures = 0;
async function check(name, run) { try { await run(); console.log(`PASS ${name}`); } catch (e) { failures++; console.error(`FAIL ${name}: ${e.code || e.name || 'request failed'}`); } }
async function request(path, status, { method = 'POST', credential = token, body, key, cookie } = {}) {
  const r = await fetch(new URL(`/api/v3${path}`, base), {
    method, redirect: 'manual', signal: AbortSignal.timeout(30000),
    headers: { 'User-Agent': 'RhythmV3ArticleSmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {}),
      ...(cookie ? { Cookie: cookie } : {}), ...(method === 'GET' ? {} : { 'Content-Type': 'application/json' }), ...(key ? { 'Idempotency-Key': key } : {}) },
    ...(method === 'GET' ? {} : { body: JSON.stringify(body || {}) })
  });
  assert.equal(r.status, status); assert.ok(r.headers.get('x-request-id'));
  const json = await r.json();
  if (status === 200) assert.equal(json.code, 0); else { assert.notEqual(json.code, 0); assert.equal(json.data, null); }
  return json.data;
}
const sample = { title: 'v3 smoke', content: '接口投稿测试正文', tags: '新人报道' };
await check('missing Bearer rejected', () => request('/articles', 401, { credential: null, body: sample, key: 'article-smoke-anonymous' }));
await check('edit without Bearer rejected', () => request('/articles/1', 401, { method: 'PUT', credential: null, body: { title: 'unused' }, key: 'article-smoke-edit-anonymous' }));
await check('delete without Bearer rejected', () => request('/articles/1', 401, { method: 'DELETE', credential: null, key: 'article-smoke-delete-anonymous' }));
if (process.env.API_V3_SESSION_COOKIE) await check('legacy cookie alone rejected', () => request('/articles', 401, { credential: null, cookie: process.env.API_V3_SESSION_COOKIE, body: sample, key: 'article-smoke-cookie' }));
if (process.env.API_V3_LEGACY_KEY) await check('legacy apiKey alone rejected', () => request(`/articles?apiKey=${encodeURIComponent(process.env.API_V3_LEGACY_KEY)}`, 401, { credential: null, body: sample, key: 'article-smoke-key' }));
if (process.env.API_V3_SCOPELESS_TOKEN) await check('missing content.write rejected', () => request('/articles', 403, { credential: process.env.API_V3_SCOPELESS_TOKEN, body: sample, key: 'article-smoke-scope' }));
if (token) {
  await check('actor and bypass fields rejected', () => request('/articles', 422, { body: { ...sample, authorId: '1', bypassCensor: true }, key: 'article-smoke-injected' }));
  await check('thought creation stays disabled', () => request('/articles', 403, { body: { ...sample, type: 3 }, key: 'article-smoke-thought' }));
  await check('immutable edit fields rejected', () => request('/articles/1', 422, { method: 'PUT', body: { anonymous: true }, key: 'article-smoke-edit-immutable' }));
}
if (process.env.API_V3_ARTICLE_WRITE === '1') {
  if (!token) throw new Error('API_V3_TOKEN required for opt-in writing');
  await check('draft to published article, replay, conflict and readback', async () => {
    const stamp = Date.now(), key = `article-smoke-${stamp}`;
    const body = { ...sample, title: `v3 smoke ${stamp}`, ...(process.env.API_V3_ARTICLE_BODY ? JSON.parse(process.env.API_V3_ARTICLE_BODY) : {}) };
    let draft;
    try {
      draft = await request('/me/drafts', 200, { body: { title: body.title, content: body.content, tags: body.tags }, key: `${key}-draft` });
      const payload = { ...body, draftId: draft.id };
      const first = await request('/articles', 200, { body: payload, key });
      assert.deepEqual(Object.keys(first).sort(), ['id', 'permalink']); assert.match(first.id, /^[0-9]{1,19}$/);
      assert.equal(first.permalink, `/article/${first.id}`);
      console.log(`Created test article: ${first.permalink}`);
      assert.deepEqual(await request('/articles', 200, { body: payload, key }), first);
      await request('/articles', 409, { body: { ...payload, title: `${body.title} changed` }, key });
      await request(`/me/drafts/${draft.id}`, 404, { method: 'GET' });
      const detail = await request(`/articles/${first.id}`, 200, { method: 'GET' }); assert.equal(detail.id, first.id);
      await request(`/articles/${first.id}`, 422, { method: 'DELETE', key: `${key}-too-young` });
      const source = await request(`/me/articles/${first.id}/source`, 200, { method: 'GET' });
      assert.equal(source.id, first.id); assert.equal(typeof source.commentable, 'boolean');
      const edit = { content: source.content + '\n\nv3 edit smoke' }, editKey = `${key}-edit`;
      assert.deepEqual(await request(`/articles/${first.id}`, 200, { method: 'PUT', body: edit, key: editKey }), first);
      assert.deepEqual(await request(`/articles/${first.id}`, 200, { method: 'PUT', body: edit, key: editKey }), first);
      await request(`/articles/${first.id}`, 409, { method: 'PUT', body: { title: 'different edit' }, key: editKey });
      const newer = { content: edit.content + '\n\nnewer edit' };
      await request(`/articles/${first.id}`, 200, { method: 'PUT', body: newer, key: `${key}-newer` });
      await request(`/articles/${first.id}`, 200, { method: 'PUT', body: edit, key: editKey });
      const latest = await request(`/articles/${first.id}`, 200, { method: 'GET' });
      assert.equal(latest.content, newer.content); assert.equal(latest.title, detail.title); assert.equal(latest.type, detail.type);
      await request(`/articles/${first.id}`, 403, { method: 'PUT', body: { type: 3 }, key: `${key}-convert-thought` });
    } finally {
      if (draft?.id) {
        // Only clean up the draft created by this run if publication did not consume it.
        try {
          await request(`/me/drafts/${draft.id}`, 200, { method: 'GET' });
          await request(`/me/drafts/${draft.id}`, 200, { method: 'DELETE', key: `${key}-cleanup` });
        } catch { /* A consumed draft is already gone; inspect failures manually. */ }
      }
    }
  });
} else console.log('SKIP persistent publication; set API_V3_ARTICLE_WRITE=1 explicitly with a disposable account');
if (process.env.API_V3_ARTICLE_DELETE === '1') {
  const id = process.env.API_V3_ARTICLE_DELETE_ID;
  if (!token || !/^[0-9]{1,19}$/.test(id || '')) throw new Error('Deletion requires token and a disposable eligible article ID');
  await check('eligible owned article removal, replay and new-key missing result', async () => {
    const key = `article-smoke-delete-${Date.now()}`;
    const first = await request(`/articles/${id}`, 200, { method: 'DELETE', key });
    assert.deepEqual(first, { id });
    assert.deepEqual(await request(`/articles/${id}`, 200, { method: 'DELETE', key }), first);
    await request(`/articles/${id}`, 404, { method: 'GET' });
    await request(`/articles/${id}`, 404, { method: 'DELETE', key: `${key}-new` });
  });
} else console.log('SKIP eligible article deletion; requires explicit deletion switch and disposable ID');
process.exitCode = failures ? 1 : 0;
