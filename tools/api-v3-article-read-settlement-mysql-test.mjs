#!/usr/bin/env node
/** 复用实际表结构的临时 MySQL 夹具，并启用生产结算与积分操作。 */
import {spawnSync} from 'node:child_process';

const result = spawnSync(process.execPath, ['tools/api-v3-article-read-mysql-test.mjs', '--settlement'], {stdio: 'inherit'});
if (result.error) throw result.error;
process.exitCode = result.status ?? 1;
