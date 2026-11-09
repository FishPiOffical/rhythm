#!/usr/bin/env node
/** 确认 IDEA 重新编译或重启后运行。默认只检查弹幕发送的拒绝路径。
 * 发送按当前价格扣费。显式设置 API_V3_ALLOW_CHAT_BARRAGE=1、API_V3_CHAT_BARRAGE_CONTENT、
 * API_V3_CHAT_BARRAGE_KEY，并使用临时 API_V3_TOKEN。
 * 响应不确定时保留原键与请求体。脚本仅使用 v3 发送入口并保留现有价格。
 */
import assert from 'node:assert/strict';
import {randomUUID} from 'node:crypto';

const base = process.env.API_V3_BASE_URL || 'http://localhost:8080', token = process.env.API_V3_TOKEN;
const endpoint = new URL('api/v3/chat/barrager', base.endsWith('/') ? base : base + '/');
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

async function request({credential = token, key = randomUUID(), body = {}, type = 'application/json', status = 200} = {}) {
  const r = await fetch(endpoint, {
    method: 'POST',
    redirect: 'manual',
    signal: AbortSignal.timeout(20000),
    headers: {
      'User-Agent': 'RhythmV3ChatBarrageSmoke',
      ...(credential ? {Authorization: `Bearer ${credential}`} : {}),
      ...(key ? {'Idempotency-Key': key} : {}),
      'Content-Type': type
    },
    body: JSON.stringify(body)
  });
  assert.equal(r.status, status);
  assert.ok(r.headers.get('x-request-id'));
  assert.match(r.headers.get('cache-control'), /no-store/);
  const value = await r.json();
  if (status === 200) {
    assert.equal(value.code, 0);
    assert.deepEqual(Object.keys(value.data).sort(), ['cost', 'createdAt', 'id', 'unit']);
    assert.match(value.data.id, /^[0-9a-f-]{36}$/);
    assert.ok(Number.isSafeInteger(value.data.cost) && value.data.cost >= 0);
    assert.ok(Number.isSafeInteger(value.data.createdAt));
    assert.equal(typeof value.data.unit, 'string');
  } else {
    assert.notEqual(value.code, 0);
    assert.equal(value.data, null);
  }
  return value;
}

await check('barrage requires Bearer', () => request({credential: null, status: 401}));
if (process.env.API_V3_NO_WRITE_SCOPE_TOKEN)
  await check('barrage requires content.write', () => request({credential: process.env.API_V3_NO_WRITE_SCOPE_TOKEN, status: 403}));
if (token) {
  await check('invalid requests do not send or charge', async () => {
    for (const body of [
      {}, {content: ''}, {content: 'x'.repeat(33)}, {content: '<script>x</script>'},
      {content: 'Hi', color: 'red'}, {content: 'Hi', cost: 0}, {content: 'Hi', apiKey: 'legacy'}
    ]) await request({body, status: 422});
    await request({body: {content: 'Hi'}, key: null, status: 422});
    await request({type: 'text/plain', status: 415});
  });
  if (process.env.API_V3_ALLOW_CHAT_BARRAGE === '1') await check('explicit barrage, original receipt replay and body conflict', async () => {
    const content = process.env.API_V3_CHAT_BARRAGE_CONTENT, key = process.env.API_V3_CHAT_BARRAGE_KEY;
    assert.ok(content && content.length <= 32);
    assert.match(key || '', /^[A-Za-z0-9._:-]{1,128}$/);
    const body = {content, color: '#FFFFFF'}, first = await request({key, body});
    assert.deepEqual((await request({key, body})).data, first.data);
    await request({key, body: {...body, color: '#000000'}, status: 409});
  });
}
process.exitCode = failures ? 1 : 0;
