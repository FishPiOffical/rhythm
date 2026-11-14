#!/usr/bin/env node
/** 确认通过 IDEA 重新编译或重启后运行。只读请求需要 content.read，日志禁止输出响应正文与凭据。 */
import assert from 'node:assert/strict';

const base = process.env.API_V3_BASE_URL || 'http://localhost:8080', token = process.env.API_V3_TOKEN;
let failures = 0;

async function check(name, work) {
  try {
    await work();
    console.log(`PASS ${name}`);
  } catch (e) {
    failures++;
    console.error(`FAIL ${name}: ${e.code||e.name||'request failed'}`);
  }
}

async function request(path, {credential = token, status = 200, online = false, resource} = {}) {
  const r = await fetch(new URL(`api/v3/chat/${resource||(online?'online-users':'messages')}${path}`, base.endsWith('/') ? base : base + '/'), {
    headers: {
      'User-Agent': 'RhythmV3ChatReadSmoke',
      ...(credential ? {Authorization: `Bearer ${credential}`} : {})
    },
    redirect: 'manual',
    signal: AbortSignal.timeout(20000)
  });
  assert.equal(r.status, status);
  assert.ok(r.headers.get('x-request-id'));
  const value = await r.json();
  if (status === 200) {
    assert.equal(value.code, 0);
    assert.match(r.headers.get('cache-control'), /no-store/);
    return value.data;
  }
  assert.notEqual(value.code, 0);
  assert.equal(value.data, null);
}

function message(row) {
  assert.deepEqual(Object.keys(row).sort(), ['appearance', 'author', 'card', 'client', 'content', 'contentHtml', 'createdAt', 'id', 'type']);
  assert.deepEqual(Object.keys(row.appearance).sort(), ['bubbleId', 'frameId', 'version']);
  assert.equal(row.appearance.version, 1);
  assert.match(row.id, /^[0-9]{1,32}$/);
  assert.ok(Number.isSafeInteger(row.createdAt) && row.createdAt >= 0);
  assert.deepEqual(Object.keys(row.author).sort(), ['id', 'userAvatarURL', 'userName', 'userNickname']);
  const allowed = {
    redPacket: ['count', 'got', 'message', 'money', 'type'],
    weather: ['date', 'max', 'min', 'st', 't', 'weatherCode'],
    music: ['coverURL', 'from', 'source', 'title'],
    text: [],
    unavailable: []
  };
  assert.ok(row.type in allowed);
  assert.deepEqual(Object.keys(row.card).sort(), allowed[row.type]);
  if (row.type !== 'text') {
    assert.equal(row.content, '');
    assert.equal(row.contentHtml, '');
  }
}
await check('chat history requires Bearer', () => request('', {credential: null, status: 401}));
await check('online list requires Bearer', () => request('', {online: true, credential: null, status: 401}));
for (const resource of ['topic', 'barrager/price'])
  await check(`${resource} requires Bearer`, () => request('', {resource, credential: null, status: 401}));
if (process.env.API_V3_NO_CONTENT_SCOPE_TOKEN)
  await check('chat history requires content.read', () => request('', {credential: process.env.API_V3_NO_CONTENT_SCOPE_TOKEN, status: 403}));
if (token) {
  await check('topic and price are read-only whitelisted metadata', async () => {
    const topic = await request('', {resource: 'topic'});
    assert.deepEqual(Object.keys(topic), ['text']);
    assert.equal(typeof topic.text, 'string');
    const price = await request('', {resource: 'barrager/price'});
    assert.deepEqual(Object.keys(price).sort(), ['amount', 'unit']);
    assert.ok(Number.isInteger(price.amount));
    assert.equal(typeof price.unit, 'string');
  });
  await check('online list is paginated and whitelisted', async () => {
    const data = await request('?size=3', {online: true});
    assert.deepEqual(Object.keys(data).sort(), ['hasMore', 'list', 'nodesComplete', 'nodesSampledAt', 'pagination']);
    assert.ok(data.list.length <= 3);
    assert.ok(data.pagination.total >= data.list.length);
    assert.equal(data.pagination.size, 3);
    for (const row of data.list) assert.deepEqual(Object.keys(row).sort(), ['id', 'userAvatarURL', 'userName']);
    assert.equal(new Set(data.list.map(row => row.id)).size, data.list.length);
    const names = data.list.map(row => row.userName);
    assert.deepEqual(names, [...names].sort());
    assert.ok(Number.isSafeInteger(data.nodesSampledAt) && data.nodesSampledAt >= 0);
    assert.equal(typeof data.nodesComplete, 'boolean');
    await request('?size=51', {online: true, status: 422});
    await request('?p=0', {online: true, status: 422});
  });
  await check('invalid limits and conflicting cursors reject', async () => {
    for (const path of ['?size=51', '?size=0', '?before=bad', '?before=1&after=2', '/1/context?radius=26'])
      await request(path, {status: 422});
  });
  await check('history/detail/context are whitelisted and cursor slices do not overlap', async () => {
    const latest = await request('?size=3');
    assert.ok(latest.list.length <= 3);
    latest.list.forEach(message);
    if (!latest.list.length) return;
    const ids = latest.list.map(row => row.id);
    assert.deepEqual(ids, [...ids].sort());
    const anchor = ids.at(-1), detail = await request(`/${anchor}`);
    message(detail);
    assert.equal(detail.id, anchor);
    const context = await request(`/${anchor}/context?radius=2`);
    assert.ok(context.list.length <= 5);
    assert.equal(context.anchorId, anchor);
    context.list.forEach(message);
    assert.equal(context.list.filter(row => row.id === anchor).length, 1);
    const older = await request(`?before=${latest.nextBefore}&size=3`);
    older.list.forEach(message);
    assert.ok(older.list.every(row => !ids.includes(row.id)));
  });
}
process.exitCode = failures ? 1 : 0;
