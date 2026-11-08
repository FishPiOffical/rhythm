#!/usr/bin/env node
/**
 * Phase 1 of the captcha-shield acceptance runner.
 * This phase only saves configuration and human confirmations; it never sends
 * requests to the service and never attempts to solve a captcha.
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { execFileSync } from 'node:child_process';
import readline from 'node:readline/promises';
import { stdin as input, stdout as output } from 'node:process';

const REQUIRED_EXTRA_IPS = 5;
const CONFIG_DIR = process.env.LOCALAPPDATA
  ? path.join(process.env.LOCALAPPDATA, 'RhythmCaptchaShieldE2E')
  : path.join(os.homedir(), '.rhythm-captcha-shield-e2e');
const CONFIG_PATH = path.join(CONFIG_DIR, 'config.json');

const rl = readline.createInterface({ input, output });

function ask(question, defaultValue = '') {
  const suffix = defaultValue ? ` [${defaultValue}]` : '';
  return rl.question(`${question}${suffix}: `).then(value => value.trim() || defaultValue);
}

async function askSecret(question) {
  if (!input.isTTY || typeof input.setRawMode !== 'function') {
    return ask(`${question}（当前终端无法隐藏输入）`);
  }

  output.write(`${question}: `);
  return new Promise((resolve, reject) => {
    let value = '';
    const onData = chunk => {
      const text = chunk.toString('utf8');
      for (const char of text) {
        if (char === '\u0003') {
          input.setRawMode(false);
          input.pause();
          reject(new Error('用户取消输入'));
          return;
        }
        if (char === '\r' || char === '\n') {
          input.setRawMode(false);
          input.pause();
          input.removeListener('data', onData);
          output.write('\n');
          resolve(value);
          return;
        }
        if (char === '\u007f' || char === '\b') {
          if (value.length) {
            value = value.slice(0, -1);
            output.write('\b \b');
          }
          continue;
        }
        value += char;
        output.write('*');
      }
    };
    input.resume();
    input.setRawMode(true);
    input.on('data', onData);
  });
}

function powershell(command, extraEnv = {}) {
  return execFileSync('powershell.exe', [
    '-NoProfile',
    '-NonInteractive',
    '-ExecutionPolicy',
    'Bypass',
    '-Command',
    command,
  ], {
    encoding: 'utf8',
    windowsHide: true,
    env: { ...process.env, ...extraEnv },
    maxBuffer: 1024 * 1024,
  }).trim();
}

function protectSecret(secret) {
  if (!secret) return '';
  return powershell(
    '$secure = ConvertTo-SecureString -String $env:RH_SECRET -AsPlainText -Force; ConvertFrom-SecureString $secure',
    { RH_SECRET: secret },
  );
}

function listLocalIps() {
  const command = [
    '$items = Get-NetIPAddress -ErrorAction Stop;',
    '$items = $items | Where-Object { $_.IPAddress -notin @("0.0.0.0", "::") -and $_.AddressState -eq "Preferred" };',
    '$items | Select-Object InterfaceAlias, AddressFamily, IPAddress, PrefixLength, AddressState, SkipAsSource | Sort-Object InterfaceAlias, AddressFamily, IPAddress | ConvertTo-Json -Compress',
  ].join(' ');
  const raw = powershell(command);
  if (!raw) return [];
  const parsed = JSON.parse(raw);
  return (Array.isArray(parsed) ? parsed : [parsed]).map(item => ({
    interfaceAlias: String(item.InterfaceAlias || ''),
    addressFamily: String(item.AddressFamily || ''),
    ip: String(item.IPAddress || ''),
    prefixLength: Number(item.PrefixLength || 0),
    addressState: String(item.AddressState || ''),
    skipAsSource: Boolean(item.SkipAsSource),
  }));
}

function isLoopback(item) {
  return item.interfaceAlias.toLowerCase().includes('loopback')
    || item.ip === '127.0.0.1'
    || item.ip === '::1';
}

function printIps(items) {
  if (!items.length) {
    console.log('未发现可用网卡地址。');
    return;
  }
  console.log('\n本机可用网卡地址：');
  items.forEach((item, index) => {
    const kind = isLoopback(item) ? 'L候选' : '额外IP候选';
    console.log(`  [${index + 1}] ${item.ip}/${item.prefixLength}  ${item.interfaceAlias}  ${item.addressFamily}  ${kind}`);
  });
}

function selectIp(items, indexText) {
  const index = Number(indexText) - 1;
  if (!Number.isInteger(index) || index < 0 || index >= items.length) {
    throw new Error(`IP编号无效：${indexText}`);
  }
  return items[index];
}

function saveConfig(config) {
  fs.mkdirSync(CONFIG_DIR, { recursive: true });
  fs.writeFileSync(CONFIG_PATH, `${JSON.stringify(config, null, 2)}\n`, { encoding: 'utf8', mode: 0o600 });
  try {
    fs.chmodSync(CONFIG_PATH, 0o600);
  } catch {
    // Windows ACLs, not POSIX mode bits, protect the LOCALAPPDATA file.
  }
}

function loadConfig() {
  if (!fs.existsSync(CONFIG_PATH)) return null;
  return JSON.parse(fs.readFileSync(CONFIG_PATH, 'utf8'));
}

function maskUsername(account) {
  return account.username || '(未填写)';
}

function showConfig(config) {
  if (!config) {
    console.log(`尚未保存配置：${CONFIG_PATH}`);
    return;
  }
  console.log(`配置文件：${CONFIG_PATH}`);
  console.log(`测试地址：${config.baseUrl}`);
  console.log(`期望 commit：${config.expectedCommit || '(未填写)'}`);
  console.log(`账号数量：${config.accounts.length}`);
  config.accounts.forEach((account, index) => {
    console.log(`  ${index + 1}. ${maskUsername(account)} / 权限组：${account.role || '(未填写)'}`);
  });
  console.log(`L：${config.ipPlan.loopback?.ip || '(未选择)'}`);
  config.ipPlan.extra.forEach((item, index) => console.log(`${String.fromCharCode(65 + index)}：${item.ip}`));
  console.log(`最近保存：${config.updatedAt || '(未知)'}`);
}

async function configure() {
  console.log('=== 验证码盾自动化验收：阶段 1 配置 ===');
  console.log('本阶段只保存配置，不访问服务，不验证账号，不执行压测。');

  const previous = loadConfig();
  const port = await ask('服务端端口', previous?.port || '8080');
  const expectedCommit = await ask('期望测试的 commit SHA（可留空）', previous?.expectedCommit || '');

  let accounts = [];
  const accountCountText = await ask('需要保存几个用户账号 N', String(previous?.accounts?.length || 2));
  const accountCount = Number(accountCountText);
  if (!Number.isInteger(accountCount) || accountCount < 1) {
    throw new Error('N 必须是正整数');
  }
  for (let index = 0; index < accountCount; index++) {
    const old = previous?.accounts?.[index];
    const label = await ask(`账号 ${index + 1} 名称`, old?.label || `account-${index + 1}`);
    const username = await ask(`账号 ${index + 1} 用户名`, old?.username || '');
    const role = await ask(`账号 ${index + 1} 权限组（例如 admin、普通用户）`, old?.role || '');
    const password = await askSecret(`账号 ${index + 1} 密码（不会明文保存）`);
    accounts.push({
      label,
      username,
      role,
      passwordProtected: protectSecret(password),
    });
  }

  const ips = listLocalIps();
  printIps(ips);
  const loopbackCandidates = ips.map((item, index) => ({ item, index })).filter(({ item }) => isLoopback(item));
  if (!loopbackCandidates.length) throw new Error('没有发现回环地址候选，无法固定 L');
  const loopbackIndex = await ask('选择 L 的回环地址编号', String(loopbackCandidates[0].index + 1));
  const loopback = selectIp(ips, loopbackIndex);

  const nonLoopback = ips.map((item, index) => ({ item, index })).filter(({ item }) => !isLoopback(item));
  if (nonLoopback.length < REQUIRED_EXTRA_IPS) {
    throw new Error(`可选额外 IP 只有 ${nonLoopback.length} 个，严格要求 ${REQUIRED_EXTRA_IPS} 个`);
  }
  console.log(`请从非回环地址中选择恰好 ${REQUIRED_EXTRA_IPS} 个编号，逗号分隔。`);
  const extraText = await ask('额外 IP 编号');
  const indexes = extraText.split(',').map(value => Number(value.trim()) - 1);
  if (indexes.length !== REQUIRED_EXTRA_IPS || new Set(indexes).size !== REQUIRED_EXTRA_IPS) {
    throw new Error(`必须选择恰好 ${REQUIRED_EXTRA_IPS} 个不同的额外 IP`);
  }
  const extra = indexes.map(index => {
    if (!Number.isInteger(index) || index < 0 || index >= ips.length || isLoopback(ips[index])) {
      throw new Error('额外 IP 编号必须指向非回环网卡地址');
    }
    return ips[index];
  });

  console.log('\n=== 三项确认（脚本完全信任勾选，不做额外环境检查）===');
  const environmentConfirmed = (await ask('1. 我已确认端口、runtimeMode、临时 jiyan.key、服务已启动且代码最新（输入 y）', ''))
    .toLowerCase() === 'y';
  const accountsConfirmed = (await ask('2. 我已确认账号用户名、密码和权限组（输入 y）', ''))
    .toLowerCase() === 'y';
  console.log('3. 已选择的 IP：');
  console.log(`   L: ${loopback.ip}`);
  extra.forEach((item, index) => console.log(`   ${String.fromCharCode(65 + index)}: ${item.ip}`));
  const ipsConfirmed = (await ask(`3. 我已确认 L 加 ${REQUIRED_EXTRA_IPS} 个额外 IP（输入 y）`, ''))
    .toLowerCase() === 'y';

  if (!environmentConfirmed || !accountsConfirmed || !ipsConfirmed) {
    throw new Error('三项确认未全部勾选，未保存新配置');
  }

  saveConfig({
    version: 1,
    baseUrl: `http://localhost:${port}`,
    port,
    expectedCommit,
    accounts,
    ipPlan: { loopback, extra },
    updatedAt: new Date().toISOString(),
  });
  console.log(`阶段 1 配置已保存：${CONFIG_PATH}`);
  console.log('密码已使用当前 Windows 用户 DPAPI 保护，jiyan.key 未被读取或保存。');
}

async function main() {
  const command = process.argv[2] || 'configure';
  try {
    if (command === 'configure') await configure();
    else if (command === 'show') showConfig(loadConfig());
    else if (command === 'ips') printIps(listLocalIps());
    else throw new Error(`未知命令：${command}（可用 configure、show、ips）`);
  } catch (error) {
    console.error(`失败：${error.message}`);
    process.exitCode = 1;
  } finally {
    rl.close();
  }
}

await main();
