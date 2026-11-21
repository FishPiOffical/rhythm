#!/usr/bin/env node
// 临时输出编译结果，运行持久化 Java 回归；不启动服务或连接真实数据库。
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { compileRuntime, dependencies, runJava, suites } from './api-v3-pets-test-runtime.mjs';
const output = fs.mkdtempSync(path.join(os.tmpdir(), 'rhythm-pets-'));
try {
  compileRuntime(output);
  for (const test of suites) runJava('java', ['-cp', output + path.delimiter + dependencies, `org.b3log.symphony.service.${test}`]);
  runJava('java', ['-cp', output + path.delimiter + dependencies, 'org.b3log.symphony.service.ApiV3PetSequenceTest', '20261113', '80']);
} finally {
  fs.rmSync(output, { recursive: true, force: true });
}
