#!/usr/bin/env node
/** Read-only personal resource checks after IDEA compilation/restart.
 * API_V3_BASE_URL defaults to localhost:8080. API_V3_TOKEN is a valid Bearer
 * with profile.read, content.read and activity.read.
 * Optional API_V3_OWN_DRAFT_ID and API_V3_OTHER_DRAFT_ID are existing fixtures.
 * Optional API_V3_OWN_ARTICLE_ID and API_V3_OTHER_ARTICLE_ID check editor source ownership.
 * Optional API_V3_SCOPELESS_TOKEN lacks all three required read scopes.
 */
import assert from 'node:assert/strict';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080';
const token = process.env.API_V3_TOKEN;
let failed = 0;
const paths = ['/me/settings', '/me/checkin', '/me/bag', '/me/points', '/me/drafts', '/me/drafts/0', '/me/columns', '/me/articles/0/source'];
function inspect(value) {
  if (!value || typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) {
    assert.ok(!/^(?:userPassword|userEmail|userPhone|userQQ|apiKey|token|secret2fa|userLatestLoginIP|articleDraftUserId|sourceRequestId|sourceAppId|fromBalance|toBalance|articleIP|articleUA)$/i.test(key));
    inspect(child);
  }
}
async function check(name, run) {
  try { await run(); console.log(`PASS ${name}`); }
  catch (error) { failed++; console.error(`FAIL ${name}: ${error.code || error.name || 'request failed'}`); }
}
async function request(path, status, credential) {
  const response = await fetch(new URL(`/api/v3${path}`, base), {
    redirect: 'manual', signal: AbortSignal.timeout(20000), headers: {
      'User-Agent': 'RhythmV3PersonalSmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {})
    }
  });
  assert.equal(response.status, status);
  assert.ok(response.headers.get('x-request-id'));
  assert.match(response.headers.get('cache-control') || '', /no-store/);
  const body = await response.json();
  if (status === 200) { assert.equal(body.code, 0); inspect(body.data); }
  else { assert.notEqual(body.code, 0); assert.equal(body.data, null); }
  return body.data;
}
function page(data) {
  assert.ok(Array.isArray(data.list));
  assert.ok(data.list.length <= data.pagination.size);
  assert.equal(data.pagination.pageCount, Math.ceil(data.pagination.total / data.pagination.size));
}
for (const path of paths) {
  await check(`anonymous denied ${path}`, () => request(path, 401));
  await check(`invalid Bearer denied ${path}`, () => request(path, 401, 'invalid'));
  if (process.env.API_V3_SCOPELESS_TOKEN) {
    await check(`missing scope denied ${path}`, () => request(path, 403, process.env.API_V3_SCOPELESS_TOKEN));
  }
}
if (token) {
  for (const path of ['/me/points', '/me/drafts', '/me/columns']) {
    await check(`owned list ${path}`, async () => page(await request(`${path}?size=2`, 200, token)));
    await check(`invalid size ${path}`, () => request(`${path}?size=51`, 422, token));
  }
  await check('own settings', async () => {
    const data = await request('/me/settings', 200, token);
    assert.ok(data.profile && data.privacy && data.display);
    for (const value of Object.values(data.privacy)) assert.equal(typeof value, 'boolean');
  });
  await check('checkin status is read only', async () => {
    const data = await request('/me/checkin', 200, token);
    assert.equal(typeof data.checkedIn, 'boolean');
    assert.equal(typeof data.liveness, 'number');
  });
  await check('inventory DTO', async () => {
    const data = await request('/me/bag', 200, token);
    for (const item of data.list) assert.deepEqual(Object.keys(item).sort(), ['code', 'count']);
  });
  await check('missing article source', () => request('/me/articles/0/source', 404, token));
  await check('malformed article source ID', () => request('/me/articles/not-an-id/source', 422, token));
  if (process.env.API_V3_OWN_ARTICLE_ID) {
    await check('owned editor source fields', async () => {
      const data = await request(`/me/articles/${encodeURIComponent(process.env.API_V3_OWN_ARTICLE_ID)}/source`, 200, token);
      assert.equal(data.id, process.env.API_V3_OWN_ARTICLE_ID);
      for (const key of ['content', 'rewardContent', 'columnId', 'chapterNo']) assert.equal(typeof data[key], 'string');
      assert.equal(typeof data.commentable, 'boolean'); assert.equal(typeof data.anonymous, 'boolean');
      assert.ok(!('authorId' in data));
    });
  }
  if (process.env.API_V3_OTHER_ARTICLE_ID) {
    await check('foreign editor source inaccessible', () => request(`/me/articles/${encodeURIComponent(process.env.API_V3_OTHER_ARTICLE_ID)}/source`, 404, token));
  }
  await check('missing draft', () => request('/me/drafts/0', 404, token));
  await check('malformed draft', () => request('/me/drafts/not-an-id', 422, token));
  if (process.env.API_V3_OWN_DRAFT_ID) {
    await check('own draft includes both editor and thought source', async () => {
      const data = await request(`/me/drafts/${encodeURIComponent(process.env.API_V3_OWN_DRAFT_ID)}`, 200, token);
      assert.equal(typeof data.content, 'string'); assert.equal(typeof data.thoughtContent, 'string');
    });
  }
  if (process.env.API_V3_OTHER_DRAFT_ID) {
    await check('another user draft is inaccessible', () => request(`/me/drafts/${encodeURIComponent(process.env.API_V3_OTHER_DRAFT_ID)}`, 404, token));
  }
} else console.log('SKIP authenticated personal checks: set API_V3_TOKEN');
process.exitCode = failed ? 1 : 0;
