#!/usr/bin/env node
/** Read-only HTTP checks after IDEA rebuild/restart.
 * API_V3_BASE_URL defaults to http://localhost:8080; API_V3_TOKEN optionally carries content.read.
 * API_V3_TEST_XIAOICE=1 opts into the external board (upstream unavailability may return 503).
 * Blacklist/captcha behavior and PC/mobile display still require manual checks.
 */
import assert from 'node:assert/strict';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080';
let failures = 0;
async function check(name, operation) {
  try { await operation(); console.log(`PASS ${name}`); }
  catch (error) { failures++; console.error(`FAIL ${name}: ${error.code || error.name || 'request failed'}`); }
}
function inspect(value) {
  if (!value || typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) {
    assert.ok(!/^(?:userPassword|userEmail|userPhone|userQQ|apiKey|token|secret2fa|userLatestLoginIP|userLatestLoginTime|userCity|userCountry|articleIP|articleUA|family|ACHV|profile|userId|passTime)$/i.test(key));
    inspect(child);
  }
}
async function request(path, status = 200, token) {
  const response = await fetch(new URL(`/api/v3/rankings/${path}`, base), { redirect: 'manual', signal: AbortSignal.timeout(30000),
    headers: { 'User-Agent': 'RhythmV3RankingSmoke', ...(token ? { Authorization: `Bearer ${token}` } : {}) } });
  assert.equal(response.status, status); assert.ok(response.headers.get('x-request-id'));
  const body = await response.json();
  if (status !== 200) { assert.notEqual(body.code, 0); assert.equal(body.data, null); return; }
  assert.equal(body.code, 0); inspect(body.data); return body.data;
}
function page(data) {
  assert.ok(Array.isArray(data.list));
  const { p, size, total, pageCount } = data.pagination;
  assert.ok(data.list.length <= size); assert.equal(pageCount, Math.ceil(total / size));
  for (let i = 0; i < data.list.length; i++) {
    const row = data.list[i]; assert.equal(row.rank, (p - 1) * size + i + 1);
    assert.equal(typeof row.score, 'number'); assert.ok(Number.isFinite(row.score));
    if (row.user) assert.deepEqual(Object.keys(row.user).sort(), ['id', 'userName', 'userNickname', 'userAvatarURL'].sort());
  }
}
for (const type of ['links', 'balance', 'consumption', 'checkin', 'online', 'adr', 'mofish', 'smallmofish', 'lifeRestart', 'evolve', 'emoji', 'invite', 'donate', 'perfect']) {
  await check(`${type} first and second page`, async () => {
    const first = await request(`${type}?size=2`); page(first);
    const second = await request(`${type}?p=2&size=2`); page(second);
    const ids = [...first.list, ...second.list].map(row => row.user?.id ?? row.link?.id);
    assert.equal(new Set(ids).size, ids.length);
    if (first.list.length === 2) assert.ok(first.list[0].score >= first.list[1].score);
  });
}
for (const sub of ['achievement', 'know', 'reset', 'days']) await check(`evolve ${sub}`, async () => page(await request(`evolve?subType=${sub}`)));
for (const path of ['unknown', 'evolve?subType=family', 'xiaoice?subType=4', 'balance?subType=days', 'online?size=51', 'online?p=0']) {
  await check(`invalid ${path}`, () => request(path, 422));
}
await check('invalid Bearer', () => request('balance', 401, 'invalid'));
await check('out of range page', async () => assert.equal((await request('balance?p=100000')).list.length, 0));
if (process.env.API_V3_TOKEN) await check('valid Bearer', async () => page(await request('balance', 200, process.env.API_V3_TOKEN)));
if (process.env.API_V3_TEST_XIAOICE === '1') for (const sub of ['0', '1', '2', '3']) {
  await check(`xiaoice ${sub}`, async () => page(await request(`xiaoice?subType=${sub}`)));
}
process.exitCode = failures ? 1 : 0;
