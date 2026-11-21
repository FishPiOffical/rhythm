#!/usr/bin/env node
// QQ 与个人标签由同一个资料控制器提供，共用隔离编译的适配器。
import {spawnSync} from 'node:child_process';

const result = spawnSync(process.execPath, ['tools/api-v3-profile-tags-test.mjs', '--qq-only'], {stdio: 'inherit'});
if (result.error) throw result.error;
process.exit(result.status ?? 1);
