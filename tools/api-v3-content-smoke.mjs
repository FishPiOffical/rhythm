#!/usr/bin/env node
/**
 * Read-only integration checks. Run after IDEA compilation/restart.
 * API_V3_BASE_URL defaults to http://localhost:8080.
 * Optional fixtures (IDs must already exist; the script never creates content):
 * API_V3_TOKEN: valid Bearer with content.read.
 * API_V3_SCOPELESS_TOKEN: valid Bearer without content.read.
 * API_V3_PUBLIC_ARTICLE_ID: publicly readable article (otherwise selected from list).
 * API_V3_PRIVATE_ARTICLE_ID: article disallowing anonymous reads, readable by API_V3_TOKEN.
 * API_V3_DISCUSSION_ARTICLE_ID: discussion inaccessible to API_V3_TOKEN.
 * API_V3_LEGACY_KEY / API_V3_SESSION_COOKIE: old credentials to test against private article.
 * Never prints tokens or response bodies. Disable development captcha shield only through
 * the project's existing development mode; do not use this script to bypass production checks.
 */
import assert from 'node:assert/strict';

const base = process.env.API_V3_BASE_URL || 'http://localhost:8080';
const bearer = process.env.API_V3_TOKEN;
const scopeLess = process.env.API_V3_SCOPELESS_TOKEN;
let publicId = process.env.API_V3_PUBLIC_ARTICLE_ID;
let failures = 0;

async function check(name, run) {
  try { await run(); console.log(`PASS ${name}`); }
  catch (error) {
    failures += 1;
    console.error(`FAIL ${name}: ${error.code || error.name || 'request failed'}`);
  }
}

function noSensitiveFields(value) {
  if (!value || typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) {
    assert.ok(!/^(?:userPassword|userEmail|userPhone|userQQ|secret2fa|apiKey|token|accessToken|refreshToken|userLatestLoginIP|userUA|commentIP|commentUA|articleIP|articleUA|session|userSession)$/i.test(key));
    noSensitiveFields(child);
  }
}

async function request(path, status = 200, token, extraHeaders = {}) {
  const response = await fetch(new URL(`/api/v3${path}`, base), {
    redirect: 'manual', signal: AbortSignal.timeout(20000),
    headers: { 'User-Agent': 'RhythmV3ContentSmoke', ...(token ? { Authorization: `Bearer ${token}` } : {}), ...extraHeaders }
  });
  assert.equal(response.status, status);
  assert.match(response.headers.get('content-type') || '', /application\/json/);
  assert.ok(response.headers.get('x-request-id'));
  const body = await response.json();
  if (status === 200) {
    assert.equal(body.code, 0);
    noSensitiveFields(body.data);
    assert.match(response.headers.get('cache-control') || '', /no-store/);
  } else {
    assert.notEqual(body.code, 0);
    assert.equal(body.data, null);
  }
  return body.data;
}

function author(row) {
  assert.deepEqual(Object.keys(row.author).sort(), ['id', 'userAvatarURL', 'userName', 'userNickname'].sort());
  if (row.anonymous) {
    assert.equal(row.author.id, '');
    assert.equal(row.author.userAvatarURL, '');
  }
}

function page(data, size) {
  assert.ok(Array.isArray(data.list));
  assert.ok(data.list.length <= size);
  assert.equal(data.pagination.size, size);
  assert.ok(data.pagination.total >= 0);
  assert.equal(data.pagination.pageCount, Math.ceil(data.pagination.total / size));
}

await check('anonymous article list and DTOs', async () => {
  const data = await request('/articles?size=2');
  page(data, 2);
  data.list.forEach(author);
  publicId ||= data.list[0]?.id;
});
await check('long article list', async () => {
  const data = await request('/articles?type=long&sort=hot&size=2');
  page(data, 2);
  for (const row of data.list) assert.equal(row.type, 6);
});
await check('out-of-range page is empty', async () => {
  const data = await request('/articles?p=42949672&size=1');
  assert.equal(data.list.length, 0);
});
for (const query of ['p=0', 'p=2147483647', 'size=51', 'size=-1', 'p=no', 'sort=unknown', 'type=unknown']) {
  await check(`invalid input ${query}`, () => request(`/articles?${query}`, 422));
}
await check('malformed article ID', () => request('/articles/not-an-id', 422));
await check('missing article', () => request('/articles/0', 404));
await check('invalid Bearer cannot become anonymous', () => request('/articles', 401, 'invalid'));
await check('invalid comment Bearer', () => request('/articles/0/comments', 401, 'invalid'));

if (publicId) {
  await check('public detail and reward boundary', async () => {
    const data = await request(`/articles/${publicId}`);
    author(data);
    assert.equal(data.id, publicId);
    assert.equal(data.rewardContent, '');
    assert.equal(data.rewardContentHtml, '');
    assert.ok(['markdown', 'thought'].includes(data.contentFormat));
  });
  await check('public comments and private content placeholders', async () => {
    const data = await request(`/articles/${publicId}/comments?size=2`);
    page(data, 2);
    for (const row of data.list) {
      author(row);
      if (!row.contentVisible) {
        assert.equal(row.content, '');
        assert.equal(row.contentHtml, '');
      }
    }
  });
}
if (bearer) {
  await check('Bearer content.read', () => request('/articles?size=1', 200, bearer));
} else console.log('SKIP valid Bearer: set API_V3_TOKEN');
if (scopeLess) {
  await check('missing content.read', () => request('/articles', 403, scopeLess));
} else console.log('SKIP scope: set API_V3_SCOPELESS_TOKEN');

const privateId = process.env.API_V3_PRIVATE_ARTICLE_ID;
if (privateId) {
  for (const suffix of ['', '/comments']) {
    await check(`private article anonymous ${suffix || 'detail'}`, () => request(`/articles/${privateId}${suffix}`, 404));
    if (bearer) await check(`private article Bearer ${suffix || 'detail'}`, () => request(`/articles/${privateId}${suffix}`, 200, bearer));
    if (process.env.API_V3_LEGACY_KEY) {
      await check(`legacy key is not v3 identity ${suffix || 'detail'}`, () => request(`/articles/${privateId}${suffix}?apiKey=${encodeURIComponent(process.env.API_V3_LEGACY_KEY)}`, 404));
    }
    if (process.env.API_V3_SESSION_COOKIE) {
      await check(`session is not v3 identity ${suffix || 'detail'}`, () => request(`/articles/${privateId}${suffix}`, 404, null, { Cookie: process.env.API_V3_SESSION_COOKIE }));
    }
  }
} else console.log('SKIP private article: set API_V3_PRIVATE_ARTICLE_ID');

const discussionId = process.env.API_V3_DISCUSSION_ARTICLE_ID;
if (discussionId) {
  for (const suffix of ['', '/comments']) {
    await check(`discussion anonymous ${suffix || 'detail'}`, () => request(`/articles/${discussionId}${suffix}`, 404));
    if (bearer) await check(`discussion uninvited Bearer ${suffix || 'detail'}`, () => request(`/articles/${discussionId}${suffix}`, 404, bearer));
  }
} else console.log('SKIP discussion: set API_V3_DISCUSSION_ARTICLE_ID');
process.exitCode = failures ? 1 : 0;
