#!/usr/bin/env node
/** Read-only integration checks after IDEA compilation/restart.
 * API_V3_BASE_URL defaults to http://localhost:8080.
 * Optional API_V3_TOKEN carries content.read + profile.read.
 * API_V3_PUBLIC_USER selects a user with public articles/breezemoons/follow lists.
 * API_V3_PRIVATE_USER selects a user with those lists set private.
 * API_V3_PRIVATE_USER_TOKEN is that user's own Bearer (same scopes).
 * API_V3_ACTIVITY_PUBLIC_USER has public comments/watching lists; API_V3_ACTIVITY_PRIVATE_USER has both private.
 * API_V3_ACTIVITY_PRIVATE_TOKEN belongs to that private user.
 * Fixtures are never created or modified. Tokens and response bodies are not printed.
 */
import assert from 'node:assert/strict';

const base = process.env.API_V3_BASE_URL || 'http://localhost:8080';
const token = process.env.API_V3_TOKEN;
let failures = 0;
let tagId;
let domainId;
const forbidden = /^(?:userPassword|userEmail|userPhone|userQQ|secret2fa|apiKey|token|accessToken|refreshToken|userLatestLoginIP|breezemoonIP|breezemoonUA|commentIP|commentUA|articleIP|articleUA|tagCSS|domainCSS|tagAd|userStatus|session)$/i;
function inspect(value) {
  if (!value || typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) { assert.ok(!forbidden.test(key)); inspect(child); }
}
async function check(name, run) {
  try { await run(); console.log(`PASS ${name}`); }
  catch (error) { failures++; console.error(`FAIL ${name}: ${error.code || error.name || 'request failed'}`); }
}
async function request(path, status = 200, credential) {
  const response = await fetch(new URL(`/api/v3${path}`, base), {
    redirect: 'manual', signal: AbortSignal.timeout(20000), headers: {
      'User-Agent': 'RhythmV3DiscoverySmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {})
    }
  });
  assert.equal(response.status, status);
  assert.match(response.headers.get('content-type') || '', /application\/json/);
  assert.ok(response.headers.get('x-request-id'));
  const body = await response.json();
  if (status === 200) {
    assert.equal(body.code, 0); inspect(body.data);
    assert.match(response.headers.get('cache-control') || '', /no-store/);
  } else { assert.notEqual(body.code, 0); assert.equal(body.data, null); }
  return body.data;
}
function page(data) {
  assert.ok(Array.isArray(data.list));
  assert.ok(data.list.length <= data.pagination.size);
  assert.equal(new Set(data.list.map(row => row.id)).size, data.list.length);
  assert.equal(data.pagination.pageCount, Math.ceil(data.pagination.total / data.pagination.size));
}
function author(row) {
  assert.deepEqual(Object.keys(row.author).sort(), ['id', 'userName', 'userNickname', 'userAvatarURL'].sort());
  if (row.anonymous) assert.equal(row.author.id, '');
}
await check('tags list', async () => { const data = await request('/tags?size=2'); page(data); tagId = data.list[0]?.id; });
await check('domains list', async () => { const data = await request('/domains?size=2'); page(data); domainId = data.list[0]?.id; });
if (tagId) {
  await check('tag detail', async () => assert.equal((await request(`/tags/${tagId}`)).id, tagId));
  await check('tag articles', async () => { const data = await request(`/tags/${tagId}/articles?size=2`); page(data); data.list.forEach(author); });
}
if (domainId) {
  await check('domain detail', async () => assert.equal((await request(`/domains/${domainId}`)).id, domainId));
  await check('domain tags', async () => page(await request(`/domains/${domainId}/tags?size=2`)));
  await check('domain articles', async () => { const data = await request(`/domains/${domainId}/articles?size=2`); page(data); data.list.forEach(author); });
}
await check('search', async () => {
  const data = await request(`/search?q=${encodeURIComponent("v3-smoke-%_'!" + Date.now())}&size=2`);
  page(data); data.list.forEach(author);
});
for (const query of ['q=', `q=${'a'.repeat(101)}`, 'q=a%00b', 'q=test&size=51', 'q=test&p=0', 'q=test&sort=unknown']) {
  await check('invalid search input', () => request(`/search?${query}`, 422));
}
for (const path of ['/tags/0', '/domains/0', '/tags/0/articles', '/domains/0/tags', '/domains/0/articles']) {
  await check(`not found ${path}`, () => request(path, 404));
}
for (const path of ['/tags/no', '/domains/no', '/tags?size=51', '/domains?p=2147483647']) {
  await check(`invalid input ${path}`, () => request(path, 422));
}
await check('breezemoons list', async () => {
  const data = await request('/breezemoons?size=2'); page(data); data.list.forEach(author);
});
for (const path of ['/tags', '/domains', '/breezemoons', '/users/someone']) {
  await check(`invalid Bearer ${path}`, () => request(path, 401, 'invalid'));
}
if (token) await check('Bearer discovery', async () => page(await request('/tags?size=2', 200, token)));
else console.log('SKIP valid Bearer: set API_V3_TOKEN');

const suffixes = ['/articles', '/breezemoons', '/following/users', '/following/tags', '/following/articles', '/followers'];
const publicUser = process.env.API_V3_PUBLIC_USER;
if (publicUser) {
  const root = `/users/${encodeURIComponent(publicUser)}`;
  await check('public profile', async () => {
    const data = await request(root); assert.equal(data.userName.toLowerCase(), publicUser.toLowerCase());assert.equal(typeof data.cardBg,'string');assert.ok(Number.isSafeInteger(data.userPoint));assert.ok(Number.isSafeInteger(data.onlineMinute)&&data.onlineMinute>=0);
    assert.deepEqual(Object.keys(data).sort(), ['id', 'userName', 'userNickname', 'userAvatarURL', 'userIntro', 'userURL', 'cardBg', 'userPoint', 'onlineMinute'].sort());
  });
  for (const suffix of suffixes) await check(`public user list ${suffix}`, async () => page(await request(`${root}${suffix}?size=2`)));
} else console.log('SKIP public profile lists: set API_V3_PUBLIC_USER with all tested lists public');
const privateUser = process.env.API_V3_PRIVATE_USER;
if (privateUser) {
  const root = `/users/${encodeURIComponent(privateUser)}`;
  for (const suffix of suffixes) {
    await check(`private user list anonymous ${suffix}`, () => request(`${root}${suffix}`, 403));
    if (process.env.API_V3_PRIVATE_USER_TOKEN) {
      await check(`private user list owner ${suffix}`, async () => page(await request(`${root}${suffix}?size=2`, 200, process.env.API_V3_PRIVATE_USER_TOKEN)));
    }
  }
} else console.log('SKIP privacy fixtures: set API_V3_PRIVATE_USER with all tested lists private');
process.exitCode = failures ? 1 : 0;

if (process.env.API_V3_ACTIVITY_PUBLIC_USER) {
  const name = encodeURIComponent(process.env.API_V3_ACTIVITY_PUBLIC_USER);
  for (const suffix of ['comments', 'watching', 'watching?source=users', 'watching?source=tags', 'watching/articles']) {
    await check(`public activity ${suffix}`, async () => {
      const data = await request(`/users/${name}/${suffix}${suffix.includes('?') ? '&' : '?'}size=2`); page(data);
      for (const row of data.list) {
        author(row);
        if (suffix === 'comments') {
          assert.equal(row.anonymous, false); author(row.article);
          if (!row.contentVisible) { assert.equal(row.content, ''); assert.equal(row.contentHtml, ''); }
        }
        if (suffix === 'watching?source=users') assert.equal(row.anonymous, false);
      }
    });
  }
  for (const suffix of ['comments?size=51', 'watching?source=articles', 'watching?source=unknown', 'watching/articles?sort=unknown']) {
    await check(`invalid activity ${suffix}`, () => request(`/users/${name}/${suffix}`, 422));
  }
}
if (process.env.API_V3_ACTIVITY_PRIVATE_USER) {
  const name = encodeURIComponent(process.env.API_V3_ACTIVITY_PRIVATE_USER);
  for (const suffix of ['comments', 'watching', 'watching/articles']) {
    await check(`private activity ${suffix}`, () => request(`/users/${name}/${suffix}`, 403));
    if (process.env.API_V3_ACTIVITY_PRIVATE_TOKEN) await check(`own private activity ${suffix}`, async () => page(await request(`/users/${name}/${suffix}`, 200, process.env.API_V3_ACTIVITY_PRIVATE_TOKEN)));
  }
}
process.exitCode = failures ? 1 : 0;
