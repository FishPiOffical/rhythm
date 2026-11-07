#!/usr/bin/env node
/** After IDEA rebuild/restart. Read-only unless API_V3_ALLOW_REACTION_WRITES=1.
 * API_V3_TOKEN: disposable account with content.read/reactions.write.
 * Explicit fixtures: API_V3_REACTION_ARTICLE_ID, API_V3_REACTION_COMMENT_ID, API_V3_REACTION_CHAT_ID.
 * Existing own reactions are skipped. Newly added reactions are cleared in finally.
 * Discussions/private comments and event recipients require separate manual fixtures.
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080';
const token = process.env.API_V3_TOKEN;
let failures = 0;
async function check(name, run) { try { await run(); console.log(`PASS ${name}`); } catch (e) { failures++; console.error(`FAIL ${name}: ${e.code || e.name || 'request failed'}`); } }
async function request(path, { method = 'GET', value, key, credential = token, status = 200 } = {}) {
  const response = await fetch(new URL(`/api/v3${path}`, base), { method, redirect: 'manual', signal: AbortSignal.timeout(20000),
    headers: { 'User-Agent': 'RhythmV3ReactionSmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {}),
      ...(method === 'GET' ? {} : { 'Content-Type': 'application/json' }), ...(key ? { 'Idempotency-Key': key } : {}) },
    ...(method === 'GET' ? {} : { body: JSON.stringify(value === undefined ? {} : { value }) }) });
  assert.equal(response.status, status); assert.ok(response.headers.get('x-request-id'));
  const body = await response.json();
  if (status === 200) { assert.equal(body.code, 0); inspect(body.data); }
  else { assert.notEqual(body.code, 0); assert.equal(body.data, null); }
  return body.data;
}
function inspect(value) {
  if (!value || typeof value !== 'object') return;
  for (const [key, child] of Object.entries(value)) {
    assert.ok(!/^(?:userPassword|userEmail|userPhone|userQQ|secret2fa|apiKey|token|accessToken|refreshToken|userLatestLoginIP|commentIP|commentUA|articleIP|articleUA|content|reactionUserId|reactionTargetId)$/i.test(key)); inspect(child);
  }
}
function result(data) {
  assert.equal(data.groupType, 'emoji'); assert.ok(Array.isArray(data.summary));
  for (const item of data.summary) {
    assert.ok(item.count > 0); assert.equal(typeof item.selected, 'boolean');
    for (const user of item.userDetails) assert.deepEqual(Object.keys(user).sort(), ['userName', 'displayName', 'avatarURL'].sort());
  }
}
await check('emoji catalog', async () => { const data = await request('/reactions/emojis', { credential: null }); assert.ok(data.list.length > 0); assert.equal(new Set(data.list.map(r => r.value)).size, data.list.length); });
await check('chat read requires Bearer', () => request('/chat/messages/0/reactions', { status: 401, credential: null }));
for (const path of ['/articles/0/reactions', '/comments/0/reactions']) await check('unavailable target', () => request(path, { status: 404, credential: null }));
for (const [type, variable] of [['articles', 'API_V3_REACTION_ARTICLE_ID'], ['comments', 'API_V3_REACTION_COMMENT_ID'], ['chat/messages', 'API_V3_REACTION_CHAT_ID']]) {
  const id = process.env[variable]; if (!id) continue; assert.match(id, /^[0-9]{1,32}$/);
  const path = `/${type}/${id}/reactions`;
  await check(`read ${type}`, async () => result(await request(path)));
  await check(`write requires Bearer ${type}`, () => request(path, { method: 'PUT', value: 'heart', key: randomUUID(), credential: null, status: 401 }));
  if (!token || process.env.API_V3_ALLOW_REACTION_WRITES !== '1') continue;
  await check(`set/replace/clear/replay ${type}`, async () => {
    if ((await request(path)).currentUserReaction !== '') { console.log(`SKIP existing own reaction ${type}`); return; }
    const firstKey = randomUUID(), replaceKey = randomUUID(), clearKey = randomUUID(); let changed = false;
    try {
      const first = await request(path, { method: 'PUT', value: 'heart', key: firstKey }); changed = true; result(first);
      assert.equal(first.changed, true); assert.equal(first.currentUserReaction, 'heart');
      assert.deepEqual(await request(path, { method: 'PUT', value: 'heart', key: firstKey }), first);
      assert.equal((await request(path, { method: 'PUT', value: 'heart', key: randomUUID() })).changed, false);
      const replaced = await request(path, { method: 'PUT', value: 'fire', key: replaceKey }); assert.equal(replaced.currentUserReaction, 'fire');
      assert.deepEqual(await request(path, { method: 'PUT', value: 'heart', key: firstKey }), first);
      assert.equal((await request(path)).currentUserReaction, 'fire');
      const cleared = await request(path, { method: 'DELETE', key: clearKey }); assert.equal(cleared.currentUserReaction, '');
      assert.deepEqual(await request(path, { method: 'DELETE', key: clearKey }), cleared);
      assert.equal((await request(path, { method: 'DELETE', key: randomUUID() })).changed, false);
      assert.deepEqual(await request(path, { method: 'PUT', value: 'fire', key: replaceKey }), replaced);
      assert.equal((await request(path)).currentUserReaction, ''); changed = false;
    } finally { if (changed) await request(path, { method: 'DELETE', key: randomUUID() }); }
  });
}
process.exitCode = failures ? 1 : 0;
