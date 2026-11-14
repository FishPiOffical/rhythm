#!/usr/bin/env node
// 单次本地持久化循环，由外层协调器控制总时长；此脚本不会发 HTTP 或连接真实数据库。
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { cachedRuntime, dependencies, runJava, suites } from './api-v3-pets-test-runtime.mjs';

const values = new Map();
let baseline = false;
for (let index = 2; index < process.argv.length; index++) {
  const key = process.argv[index];
  if (key === '--baseline') { baseline = true; continue; }
  if (!['--seed', '--steps', '--cache-dir', '--summary', '--log'].includes(key) || !process.argv[index + 1]) {
    throw new Error('参数：--seed 整数 --steps 1..2000 --cache-dir 目录 --summary JSON路径 [--log 路径] [--baseline]');
  }
  values.set(key, process.argv[++index]);
}
const seed = Number(values.get('--seed') ?? 20261113);
const steps = Number(values.get('--steps') ?? 80);
if (!Number.isSafeInteger(seed) || !Number.isInteger(steps) || steps < 1 || steps > 2000) throw new Error('seed 或 steps 非法');
const summaryPath = path.resolve(values.get('--summary') ?? path.join(os.tmpdir(), `pets-soak-${seed}.json`));
const logPath = path.resolve(values.get('--log') ?? summaryPath.replace(/\.json$/, '') + '.log');
const cacheDirectory = path.resolve(values.get('--cache-dir') ?? path.join(os.tmpdir(), 'rhythm-pets-soak-classes'));
fs.mkdirSync(path.dirname(summaryPath), { recursive: true });
fs.mkdirSync(path.dirname(logPath), { recursive: true });
const log = fs.openSync(logPath, 'w', 0o600);
const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'pets-cycle-'));
const javaSummary = path.join(temporary, 'sequence.json');
const startedAt = Date.now();
const summary = { schemaVersion: 1, status: 'running', seed, steps, baselineRequested: baseline,
  baselineSuitesPassed: 0, startedAt: new Date(startedAt).toISOString(), logPath,
  isolation: 'temporary SQLite only; no HTTP, no real account database' };
try {
  const runtime = cachedRuntime(cacheDirectory, { stdio: ['ignore', log, log] });
  Object.assign(summary, { fingerprint: runtime.fingerprint, compiled: runtime.compiled });
  const classpath = runtime.output + path.delimiter + dependencies;
  if (baseline) for (const suite of suites) {
    runJava('java', ['-cp', classpath, `org.b3log.symphony.service.${suite}`], { stdio: ['ignore', log, log] });
    summary.baselineSuitesPassed++;
  }
  runJava('java', ['-cp', classpath, 'org.b3log.symphony.service.ApiV3PetSequenceTest', String(seed), String(steps), javaSummary],
    { stdio: ['ignore', log, log] });
  summary.status = 'passed';
} catch (error) {
  summary.status = 'failed';
  summary.error = error.message;
  process.exitCode = 1;
} finally {
  try {
    if (fs.existsSync(javaSummary)) summary.sequence = JSON.parse(fs.readFileSync(javaSummary, 'utf8'));
    if (summary.status === 'passed' && (!summary.sequence || summary.sequence.status !== 'passed'
      || summary.sequence.seed !== seed || summary.sequence.requestedSteps !== steps)) {
      throw new Error('Java 摘要缺失或与本轮参数不符');
    }
  } catch (error) {
    // 摘要损坏也是失败，仍然清理并覆盖本轮结果，不能让协调器误读上一轮成功。
    summary.status = 'failed';
    summary.error = `Java 摘要无法读取：${error.message}`;
    process.exitCode = 1;
  }
  summary.finishedAt = new Date().toISOString();
  summary.durationMs = Date.now() - startedAt;
  fs.closeSync(log);
  fs.rmSync(temporary, { recursive: true, force: true });
  const atomic = summaryPath + `.tmp-${process.pid}`;
  fs.writeFileSync(atomic, JSON.stringify(summary, null, 2) + '\n', { mode: 0o600 });
  fs.renameSync(atomic, summaryPath);
  process.stdout.write(JSON.stringify(summary) + '\n');
}
