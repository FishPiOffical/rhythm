#!/usr/bin/env node
// The profile controller now has both resources; share its compilation adapters.
import {spawnSync} from 'node:child_process';
const result=spawnSync(process.execPath,['tools/api-v3-profile-tags-test.mjs','--qq-only'],{stdio:'inherit'});
if(result.error)throw result.error;process.exit(result.status??1);
