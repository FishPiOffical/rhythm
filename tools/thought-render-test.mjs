#!/usr/bin/env node
/** Isolated DOM check of actual PC/mobile thought rendering expressions; no application HTTP.
 * npm exec --yes --package=jsdom@26.1.0 --package=jquery@3.7.1 -- node tools/thought-render-test.mjs
 */
import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import assert from 'node:assert/strict';
import os from 'node:os';
import vm from 'node:vm';
let require = createRequire(import.meta.url);
try { require.resolve('jsdom'); require.resolve('jquery'); }
catch {
  const bin = process.env.PATH.split(path.delimiter).find(dir => fs.existsSync(path.join(dir, '..', 'jsdom', 'package.json')) && fs.existsSync(path.join(dir, '..', 'jquery', 'package.json')));
  if (!bin) throw new Error('Run with the documented npm exec command or install test DOM dependencies');
  require = createRequire(path.resolve(bin, '..', '__thought_dom_test__.js'));
}
const { JSDOM } = require('jsdom');
const window = new JSDOM('<div id="thought"></div>').window;
const $ = require('jquery')(window);
require(process.env.THOUGHT_TEST_LUTE || path.resolve('node_modules/vditor/dist/js/lute/lute.min.js'));
const Lute = globalThis.Lute;
assert.equal(typeof Lute.Sanitize, 'function');
for (const file of ['article.js', 'm-article.js']) {
  const source = fs.readFileSync('src/main/resources/js/' + file, 'utf8');
  const expressions = [...source.matchAll(/articleHTML = (Lute\.Sanitize[\s\S]*?)\n\n/g)];
  assert.equal(expressions.length, 2);
  const timeSource = source.match(/var recordTime = function \(record\) \{([\s\S]*?)\n\s*\}/);
  assert.ok(timeSource);
  const recordTime = new Function('UNIT_SEPARATOR', 'return function(record) {' + timeSource[1] + '}')('\u001f');
  assert.equal(recordTime('text\u001f20\u001f0-0\u001f0-0'), 20);
  assert.equal(recordTime('20\u001f0-0\u001f0-0'), 20);
  for (const [, expression] of expressions) {
    const render = new Function('Lute', 'articleText', 'return ' + expression);
    for (const payload of ['<script>window.injected=1</script>', '<img src=x onerror="window.injected=1"><svg onload="window.injected=1">', '<a href="javascript:alert(1)">link</a>']) {
      const target = $('#thought').html(render(Lute, payload))[0];
      assert.equal(target.querySelector('script,svg,[onerror],[onload]'), null);
      for (const link of target.querySelectorAll('[href]')) assert.ok(!/^javascript:/i.test(link.getAttribute('href')));
    }
    const target = $('#thought').html(render(Lute, '<b>粗体</b> &amp; 文本\n\t 代码'))[0];
    assert.equal(target.querySelector('b').textContent, '粗体');
    assert.equal(target.textContent.replaceAll('\u00a0', ' '), '粗体 & 文本     代码');
  }
  console.log('PASS ' + file + ': actual playback/preview sanitizer keeps safe markup and removes executable DOM');
}
window.close();
const fixtureFile = path.join(os.tmpdir(), 'rhythm-thought-template-fixtures.json');
if (fs.existsSync(fixtureFile)) {
  for (const fixture of JSON.parse(fs.readFileSync(fixtureFile, 'utf8'))) {
    const dom = new JSDOM('<script>' + fixture.script + '</script>');
    assert.equal(dom.window.document.querySelectorAll('script').length, 1);
    assert.equal(dom.window.document.querySelector('img'), null);
    let captured;
    const context = { Article: { playThought: value => { captured = value; } } };
    vm.runInNewContext(dom.window.document.querySelector('script').textContent, context);
    assert.equal(captured, fixture.raw); assert.equal(context.globalInjected, undefined);
    dom.window.close();
  }
  console.log('PASS real PC/mobile FTL script fixtures round-trip separators, quotes, backslashes and closing tags without injection');
} else console.log('SKIP FTL fixtures: run tools/api-v3-article-test.mjs first');
