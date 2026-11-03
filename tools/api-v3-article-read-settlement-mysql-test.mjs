#!/usr/bin/env node
/** Shared exact-schema disposable MySQL harness, adding production settlement/point primitives. */
import {spawnSync} from 'node:child_process';
const result=spawnSync(process.execPath,['tools/api-v3-article-read-mysql-test.mjs','--settlement'],{stdio:'inherit'});
if(result.error)throw result.error;process.exitCode=result.status??1;
