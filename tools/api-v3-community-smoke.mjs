#!/usr/bin/env node
/** Read-only community HTTP checks after IDEA restart.
 * API_V3_BASE_URL defaults to localhost:8080. Optional API_V3_TOKEN carries content.read/profile.read.
 * Optional API_V3_PENDING_GAME_ID and API_V3_PENDING_MILESTONE_ID test unpublished resources.
 * API_V3_CITY defaults to 上海; empty results are valid. City privacy still needs two-account manual fixtures.
 */
import assert from 'node:assert/strict';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080';
let failures = 0;
let gameId;
let milestoneId;
async function check(name, operation) {
  try { await operation(); console.log(`PASS ${name}`); }
  catch (error) { failures++; console.error(`FAIL ${name}: ${error.code || error.name || 'request failed'}`); }
}
function inspect(value) {
  if (!value || typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) {
    assert.ok(!/^(?:userPassword|userEmail|userPhone|userQQ|apiKey|token|secret2fa|userLatestLoginIP|userLatestLoginTime|userCity|userCountry|articleIP|articleUA|fishGameStatus|fishGameEditPending|fishGamePendingUrl|milestoneStatus|milestoneAuthorId)$/i.test(key));
    inspect(child);
  }
}
async function request(path, status = 200, token) {
  const response = await fetch(new URL(`/api/v3${path}`, base), {
    redirect: 'manual', signal: AbortSignal.timeout(30000), headers: {
      'User-Agent': 'RhythmV3CommunitySmoke', ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
  });
  assert.equal(response.status, status); assert.ok(response.headers.get('x-request-id'));
  const body = await response.json();
  if (status === 200) { assert.equal(body.code, 0); inspect(body.data); }
  else { assert.notEqual(body.code, 0); assert.equal(body.data, null); }
  return body.data;
}
function page(data) {
  assert.ok(Array.isArray(data.list)); assert.ok(data.list.length <= data.pagination.size);
  assert.equal(data.pagination.pageCount, Math.ceil(data.pagination.total / data.pagination.size));
  assert.equal(new Set(data.list.map(row => row.id)).size, data.list.length);
}
await check('approved games likes list', async () => {
  const data = await request('/fish-games?size=2&sort=likes'); page(data); gameId = data.list[0]?.id;
  if (data.list.length === 2) assert.ok(data.list[0].upCount >= data.list[1].upCount);
});
await check('default game ordering equals votes', async () => { const implicit = await request('/fish-games?size=2'); const explicit = await request('/fish-games?size=2&sort=votes'); assert.deepEqual(implicit.list.map(r => r.id), explicit.list.map(r => r.id)); });
await check('approved games votes list', async () => page(await request('/fish-games?size=2&sort=votes')));
await check('milestones', async () => { const data = await request('/milestones?size=2'); page(data); milestoneId = data.list[0]?.id; });
if (gameId) {
  await check('game detail', async () => assert.equal((await request(`/fish-games/${gameId}`)).id, gameId));
  await check('game comments', async () => page(await request(`/fish-games/${gameId}/comments?size=2`)));
}
if (milestoneId) await check('milestone detail', async () => assert.equal((await request(`/milestones/${milestoneId}`)).id, milestoneId));
await check('statistics whitelist', async () => {
  const data = await request('/statistics');
  assert.deepEqual(Object.keys(data).sort(), ['memberCount', 'articleCount', 'commentCount', 'domainCount', 'tagCount', 'linkCount', 'peakOnlineVisitorCount', 'onlineVisitorCount', 'onlineMemberCount'].sort());
});
const city = encodeURIComponent(process.env.API_V3_CITY || '上海');
await check('public city users', async () => page(await request(`/cities/${city}/users?size=2`)));
await check('public city articles', async () => {
  const data = await request(`/cities/${city}/articles?size=2`); page(data);
  for (const row of data.list) assert.equal(row.anonymous, false);
});
for (const path of ['/fish-games?size=51', '/fish-games?sort=unknown', '/milestones?p=0', `/cities/${city}/users?size=51`]) {
  await check(`invalid query ${path}`, () => request(path, 422));
}
for (const path of ['/fish-games/0', '/fish-games/0/comments', '/milestones/0']) await check(`not found ${path}`, () => request(path, 404));
for (const path of ['/fish-games', '/milestones', '/statistics', `/cities/${city}/users`]) {
  await check(`invalid Bearer ${path}`, () => request(path, 401, 'invalid'));
}
if (process.env.API_V3_PENDING_GAME_ID) {
  for (const suffix of ['', '/comments']) await check('pending game hidden', () => request(`/fish-games/${process.env.API_V3_PENDING_GAME_ID}${suffix}`, 404));
}
if (process.env.API_V3_PENDING_MILESTONE_ID) await check('pending milestone hidden', () => request(`/milestones/${process.env.API_V3_PENDING_MILESTONE_ID}`, 404));
if (process.env.API_V3_TOKEN) await check('Bearer game list', async () => page(await request('/fish-games?size=2', 200, process.env.API_V3_TOKEN)));
process.exitCode = failures ? 1 : 0;
