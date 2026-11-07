#!/usr/bin/env node
/** Run only after IDEA compilation/restart. Default mode checks rejections, never writes content.
 * API_V3_TOKEN: comments.write (+ content.read for optional readback) and commonUpdateComment.
 * API_V3_SCOPELESS_TOKEN / API_V3_SESSION_COOKIE / API_V3_LEGACY_KEY: optional credential fixtures.
 * API_V3_OTHER_COMMENT_ID: another account's comment; must return 404.
 * API_V3_COMMENT_WRITE=1 + API_V3_COMMENT_ID explicitly edits an existing disposable owned comment twice.
 * API_V3_COMMENT_ARTICLE_ID + API_V3_COMMENT_PAGE (default 1): optional ordinary-comment readback fixture.
 * API_V3_LONG_ARTICLE_ID enables read-only anchor/HTML/paragraph/detail checks.
 * API_V3_COMMENT_PARAGRAPH_AUTO=1 obtains a current v3 anchor for an explicitly enabled publication.
 * API_V3_COMMENT_PUBLISH=1 + API_V3_COMMENT_ARTICLE_ID publishes one disposable comment; optional parent/paragraph fixture.
 * Edits/publications/fees are persistent; this does not restore old content. Never run against a production fixture.
 */
import assert from 'node:assert/strict';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080';
const token = process.env.API_V3_TOKEN;
let failed = 0;
async function check(name, run) { try { await run(); console.log(`PASS ${name}`); } catch (e) { failed++; console.error(`FAIL ${name}: ${e.code || e.name || 'request failed'}`); } }
async function request(path, status, { method = 'PUT', credential = token, body = {}, key, cookie } = {}) {
  const r = await fetch(new URL(`/api/v3${path}`, base), {
    method, redirect: 'manual', signal: AbortSignal.timeout(30000),
    headers: { 'User-Agent': 'RhythmV3CommentSmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {}),
      ...(cookie ? { Cookie: cookie } : {}), ...(method === 'GET' ? {} : { 'Content-Type': 'application/json' }), ...(key ? { 'Idempotency-Key': key } : {}) },
    ...(method === 'GET' ? {} : { body: JSON.stringify(body) })
  });
  assert.equal(r.status, status); assert.ok(r.headers.get('x-request-id'));
  const json = await r.json();
  if (status === 200) assert.equal(json.code, 0); else { assert.notEqual(json.code, 0); assert.equal(json.data, null); }
  return json.data;
}
const sample = { content: 'v3 comment smoke' };
await check('missing single comment', () => request('/comments/0', 404, { method: 'GET' }));
await check('missing paragraph article', () => request('/articles/0/paragraphs', 404, { method: 'GET' }));
await check('paragraph anchor format', () => request('/articles/0/paragraph-comments?paragraphId=bad', 422, { method: 'GET' }));
await check('paragraph state whitelist', () => request('/articles/0/paragraph-comments?state=bad', 422, { method: 'GET' }));
await check('paragraph content flag whitelist', () => request('/articles/0/paragraphs?includeContent=1', 422, { method: 'GET' }));
if (process.env.API_V3_LONG_ARTICLE_ID) await check('long article anchors, annotated content and paragraph rows', async () => {
  const article = process.env.API_V3_LONG_ARTICLE_ID;
  if (!/^[0-9]{1,32}$/.test(article)) throw new Error('Invalid long article fixture');
  const anchors = await request(`/articles/${article}/paragraphs?size=50&includeContent=true`, 200, { method: 'GET' });
  assert.ok(Array.isArray(anchors.list)); assert.equal(typeof anchors.contentHtml, 'string');
  assert.ok(Number.isSafeInteger(anchors.pagination.total));
  for (const anchor of anchors.list) {
    assert.match(anchor.id, /^[0-9a-f]{64}$/); assert.equal(anchor.state, 'active');
    assert.ok(anchors.contentHtml.includes(`data-long-paragraph-id="${anchor.id}"`));
    assert.ok(Number.isSafeInteger(anchor.commentCount));
  }
  const plain = await request(`/articles/${article}/paragraphs?size=50`, 200, { method: 'GET' });
  assert.ok(!Object.hasOwn(plain, 'contentHtml')); assert.deepEqual(plain.list, anchors.list);
  const anchor = anchors.list[0]?.id;
  if (anchor) {
    const comments = await request(`/articles/${article}/paragraph-comments?paragraphId=${anchor}&state=all&size=50`, 200, { method: 'GET' });
    for (const row of comments.list) {
      assert.equal(row.articleId, article); assert.equal(row.type, 1); assert.equal(row.paragraph.id, anchor);
      assert.ok(!Object.hasOwn(row.paragraph, 'snapshot'));
      assert.ok(!Object.hasOwn(row, 'commentIP')); assert.ok(!Object.hasOwn(row, 'commentUA'));
      assert.deepEqual(await request(`/comments/${row.id}`, 200, { method: 'GET' }), row);
    }
  }
});

await check('publish missing Bearer denied', () => request('/articles/0/comments', 401, { method: 'POST', credential: null, body: sample, key: 'comment-publish-anonymous' }));
await check('missing Bearer denied', () => request('/comments/0', 401, { credential: null, body: sample, key: 'comment-smoke-anonymous' }));
if (process.env.API_V3_SESSION_COOKIE) await check('cookie cannot replace Bearer', () => request('/comments/0', 401, { credential: null, cookie: process.env.API_V3_SESSION_COOKIE, body: sample, key: 'comment-smoke-cookie' }));
if (process.env.API_V3_LEGACY_KEY) await check('legacy apiKey cannot replace Bearer', () => request(`/comments/0?apiKey=${encodeURIComponent(process.env.API_V3_LEGACY_KEY)}`, 401, { credential: null, body: sample, key: 'comment-smoke-key' }));
if (process.env.API_V3_SCOPELESS_TOKEN) await check('comments.write required', () => request('/comments/0', 403, { credential: process.env.API_V3_SCOPELESS_TOKEN, body: sample, key: 'comment-smoke-scope' }));
if (token) {
  await check('publish missing article', () => request('/articles/0/comments', 404, { method: 'POST', body: sample, key: 'comment-publish-missing' }));
  await check('publish server fields denied', () => request('/articles/0/comments', 422, { method: 'POST', body: { ...sample, authorId: 'injected' }, key: 'comment-publish-injected' }));
  await check('missing comment', () => request('/comments/0', 404, { body: sample, key: 'comment-smoke-missing' }));
  await check('immutable fields denied', () => request('/comments/0', 422, { body: { ...sample, anonymous: true, authorId: '1' }, key: 'comment-smoke-injected' }));
  await check('invalid comment ID', () => request('/comments/not-an-id', 422, { body: sample, key: 'comment-smoke-invalid' }));
  if (process.env.API_V3_OTHER_COMMENT_ID) await check('foreign comment hidden', () => request(`/comments/${encodeURIComponent(process.env.API_V3_OTHER_COMMENT_ID)}`, 404, { body: sample, key: 'comment-smoke-foreign' }));
}
if (process.env.API_V3_COMMENT_WRITE === '1') {
  const id = process.env.API_V3_COMMENT_ID;
  if (!token || !/^[0-9]{1,19}$/.test(id || '')) throw new Error('Explicit edit requires token and a disposable owned comment ID');
  await check('owned edit, replay, conflict and old replay after newer edit', async () => {
    const key = `comment-smoke-${Date.now()}`, body = { content: `v3 comment smoke ${Date.now()}` };
    const first = await request(`/comments/${id}`, 200, { body, key });
    assert.deepEqual(Object.keys(first).sort(), ['id', 'permalink']); assert.equal(first.id, id);
    assert.deepEqual(await request(`/comments/${id}`, 200, { body, key }), first);
    await request(`/comments/${id}`, 409, { body: { content: body.content + ' conflict' }, key });
    const newer = { content: body.content + ' newer' };
    await request(`/comments/${id}`, 200, { body: newer, key: `${key}-newer` });
    await request(`/comments/${id}`, 200, { body, key });
    if (process.env.API_V3_COMMENT_ARTICLE_ID) {
      const article = process.env.API_V3_COMMENT_ARTICLE_ID, page = process.env.API_V3_COMMENT_PAGE || '1';
      if (!/^[0-9]{1,19}$/.test(article) || !/^[1-9][0-9]{0,6}$/.test(page)) throw new Error('Invalid readback fixture');
      const result = await request(`/articles/${article}/comments?p=${page}&size=50&sort=oldest`, 200, { method: 'GET' });
      const edited = result.list.find(item => item.id === id); assert.ok(edited, 'Fixture must be on the configured ordinary-comment page');
      assert.equal(edited.content.trim(), newer.content);
    } else console.log('SKIP persisted readback: configure article ID and fixture page; paragraph comments require manual readback');
  });
} else console.log('SKIP persistent edit: explicitly set API_V3_COMMENT_WRITE=1 with a disposable owned comment');
if (process.env.API_V3_COMMENT_PUBLISH === '1') {
  const article = process.env.API_V3_COMMENT_ARTICLE_ID;
  if (!token || !/^[0-9]{1,19}$/.test(article || '')) throw new Error('Explicit publication requires token and disposable article fixture');
  await check('publication, replay and conflicting replay', async () => {
    const key = `comment-publish-${Date.now()}`, body = { content: `v3 comment publication smoke ${Date.now()}` };
    if (process.env.API_V3_COMMENT_PARENT_ID) body.parentId = process.env.API_V3_COMMENT_PARENT_ID;
    if (process.env.API_V3_COMMENT_PARAGRAPH_ID) body.paragraphId = process.env.API_V3_COMMENT_PARAGRAPH_ID;
    if (process.env.API_V3_COMMENT_PARAGRAPH_AUTO === '1') {
      const anchors = await request(`/articles/${article}/paragraphs?size=1`, 200, { method: 'GET' });
      assert.ok(anchors.list.length); body.paragraphId = anchors.list[0].id;
    }
    const route = `/articles/${article}/comments`;
    const first = await request(route, 200, { method: 'POST', body, key });
    assert.deepEqual(Object.keys(first).sort(), ['id', 'permalink']);
    assert.match(first.id, /^[0-9]{1,19}$/); assert.ok(first.permalink.includes(first.id));
    assert.deepEqual(await request(route, 200, { method: 'POST', body, key }), first);
    await request(route, 409, { method: 'POST', body: { ...body, content: `${body.content} conflict` }, key });
    const detail = await request(`/comments/${first.id}`, 200, { method: 'GET' });
    assert.equal(detail.content.trim(), body.content); assert.equal(detail.contentVisible, true);
    if (first.permalink.includes('?paragraph=')) {
      const result = await request(`/articles/${article}/paragraph-comments?paragraphId=${detail.paragraph.id}&state=all&sort=latest&size=50`, 200, { method: 'GET' });
      assert.ok(result.list.some(item => item.id === first.id));
    } else {
      const result = await request(`/articles/${article}/comments?size=50`, 200, { method: 'GET' });
      const row = result.list.find(item => item.id === first.id); assert.ok(row, 'Use a fixture where the new comment remains on page one');
      assert.equal(row.content.trim(), body.content); assert.equal(row.parentId, body.parentId || '');
    }
  });
} else console.log('SKIP persistent publication: explicitly set API_V3_COMMENT_PUBLISH=1 with a disposable article');
process.exitCode = failed ? 1 : 0;
