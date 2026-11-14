#!/usr/bin/env node
/** 在 IDEA 重新编译或重启后运行，投票写入需要 API_V3_ALLOW_GAME_VOTE_WRITES=1。
 * API_V3_TOKEN 使用具有 content.read/reactions.write/comments.write 的临时账号。
 * API_V3_APPROVED_GAME_ID 指向已审核的临时游戏。跳过既有本人投票并清除本次新增投票。
 * API_V3_ALLOW_GAME_COMMENT_WRITE=1 另行启用评论写入。会创建一条可见评论，v3 无删除入口。
 * API_V3_PENDING_GAME_ID 指向待审夹具。脚本覆盖读取、投票和评论。
 */
import assert from 'node:assert/strict';
import { randomUUID } from 'node:crypto';
const base = process.env.API_V3_BASE_URL || 'http://localhost:8080', token = process.env.API_V3_TOKEN;
let failures = 0;
async function check(name, run) { try { await run(); console.log(`PASS ${name}`); } catch (e) { failures++; console.error(`FAIL ${name}: ${e.code || e.name || 'request failed'}`); } }
async function request(path, { method = 'GET', body, key, status = 200, credential = token } = {}) {
  const response = await fetch(new URL(`/api/v3${path}`, base), { method, redirect: 'manual', signal: AbortSignal.timeout(20000),
    headers: { 'User-Agent': 'RhythmV3GameSmoke', ...(credential ? { Authorization: `Bearer ${credential}` } : {}),
      ...(method === 'GET' ? {} : { 'Content-Type': 'application/json' }), ...(key ? { 'Idempotency-Key': key } : {}) },
    ...(method === 'GET' ? {} : { body: JSON.stringify(body || {}) }) });
  assert.equal(response.status, status);
  assert.ok(response.headers.get('x-request-id'));
  const json = await response.json();
  if (status === 200) assert.equal(json.code, 0);
  else {
    assert.notEqual(json.code, 0);
    assert.equal(json.data, null);
  }
  return json.data;
}
for (const suffix of ['/vote', '/comments']) await check(`write requires Bearer ${suffix}`, () => request(`/fish-games/0${suffix}`, { method: suffix === '/vote' ? 'PUT' : 'POST', credential: null, key: randomUUID(), status: 401 }));
const id = process.env.API_V3_APPROVED_GAME_ID;
if (id) {
  assert.match(id, /^[0-9]{1,32}$/);
  const gamePath = `/fish-games/${id}`, votePath = `${gamePath}/vote`;
  await check('approved game detail', async () => assert.equal((await request(gamePath)).id, id));
  if (token && process.env.API_V3_ALLOW_GAME_VOTE_WRITES === '1') await check('vote set/replace/clear/replay', async () => {
    const before = await request(gamePath);
    if (before.userVote) {
      console.log('SKIP existing own vote');
      return;
    }
    const firstKey = randomUUID(), clearKey = randomUUID();
    let changed = false;
    try {
      const first = await request(votePath, { method: 'PUT', body: { value: 'like' }, key: firstKey });
      changed = true;
      assert.equal(first.userVote, 'like');
      assert.equal(first.changed, true);
      assert.deepEqual(await request(votePath, { method: 'PUT', body: { value: 'like' }, key: firstKey }), first);
      assert.equal((await request(votePath, { method: 'PUT', body: { value: 'like' }, key: randomUUID() })).changed, false);
      assert.equal((await request(votePath, { method: 'PUT', body: { value: 'dislike' }, key: randomUUID() })).userVote, 'dislike');
      await request(votePath, { method: 'PUT', body: { value: 'like' }, key: firstKey });
      assert.equal((await request(gamePath)).userVote, 'dislike');
      const clear = await request(votePath, { method: 'DELETE', key: clearKey });
      assert.equal(clear.userVote, '');
      assert.deepEqual(await request(votePath, { method: 'DELETE', key: clearKey }), clear);
      await request(votePath, { method: 'PUT', body: { value: 'like' }, key: firstKey });
      assert.equal((await request(gamePath)).userVote, '');
      changed = false;
    } finally {
      if (changed) await request(votePath, { method: 'DELETE', key: randomUUID() });
    }
  });
  if (token && process.env.API_V3_ALLOW_GAME_COMMENT_WRITE === '1') await check('one explicit persistent test comment/replay', async () => {
    const key = randomUUID(), content = `v3 鱼游评论联调 ${Date.now()}`;
    const first = await request(`${gamePath}/comments`, { method: 'POST', body: { content }, key });
    assert.equal(first.content, content);
    assert.equal(first.gameId, id);
    assert.deepEqual(Object.keys(first).sort(), ['id', 'gameId', 'content', 'contentAvailable', 'author', 'createdAt'].sort());
    assert.deepEqual(await request(`${gamePath}/comments`, { method: 'POST', body: { content }, key }), first);
  });
}
if (token && process.env.API_V3_PENDING_GAME_ID) {
  const pending = process.env.API_V3_PENDING_GAME_ID;
  assert.match(pending, /^[0-9]{1,32}$/);
  for (const [suffix, method, body] of [['vote', 'PUT', { value: 'like' }], ['comments', 'POST', { content: 'not persisted' }]]) {
    await check(`pending parent rejects ${suffix}`, () => request(`/fish-games/${pending}/${suffix}`, { method, body, key: randomUUID(), status: 404 }));
  }
}
process.exitCode = failures ? 1 : 0;
