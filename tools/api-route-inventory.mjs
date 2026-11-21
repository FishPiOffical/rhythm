#!/usr/bin/env node
/**
 * Generate a source-of-truth route inventory from Java dispatcher declarations
 * and browser-facing endpoint literals in FTL/JS source files.
 *
 * Usage: node tools/api-route-inventory.mjs [output-file]
 */
import fs from 'node:fs';
import path from 'node:path';

const root = process.cwd();
const output = path.resolve(root, process.argv[2] || 'docs/api-route-inventory.md');
const processorRoot = path.join(root, 'src/main/java/org/b3log/symphony/processor');
const resourceRoots = [
  path.join(root, 'src/main/resources/skins'),
  path.join(root, 'src/main/resources/js')
];

function walk(directory, matcher) {
  const result = [];
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const file = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      result.push(...walk(file, matcher));
    } else if (matcher(file)) {
      result.push(file);
    }
  }
  return result;
}

function relative(file) {
  return path.relative(root, file).replaceAll(path.sep, '/');
}

function classifyMiddleware(source) {
  const values = [];
  if (/anonymousViewCheck|anonymousView::handle|anonymous::handle/.test(source)) values.push('匿名访问校验');
  if (/::protocol(?:Token|Revoke)\b/.test(source)) values.push('协议凭据校验，失败转匿名访问校验');
  if (/apiCheck::handle/.test(source)) values.push('必须 apiKey');
  if (/auth::optionalContent/.test(source)) values.push('可选 Bearer（content.read）');
  if (/auth::optionalProfile/.test(source)) values.push('可选 Bearer（profile.read）');
  if (/auth::handle/.test(source)) values.push('必须 Bearer');
  if (/auth::messagesRead/.test(source)) values.push('必须 Bearer（messages.read）');
  if (/auth::notificationsRead/.test(source)) values.push('必须 Bearer（notifications.read）');
  if (/auth::notificationsWrite/.test(source)) values.push('必须 Bearer（notifications.write）');
  if (/auth::commentsWrite/.test(source)) values.push('必须 Bearer（comments.write）');
  if (/auth::reactionsWrite/.test(source)) values.push('必须 Bearer（reactions.write）');
  if (/auth::followsWrite/.test(source)) values.push('必须 Bearer（follows.write）');
  if (/auth::contentWrite/.test(source)) values.push('必须 Bearer（content.write）');
  if (/writes::handle/.test(source)) values.push('写入频控');
  if (/budget::handle/.test(source)) values.push('共享请求预算');
  if (/::administrator/.test(source)) values.push('当前管理员与第一方身份复核');
  if (/auth::contentRead/.test(source)) values.push('必须 Bearer（content.read）');
  if (/auth::activityWrite/.test(source)) values.push('必须 Bearer（activity.write）');
  if (/auth::settingsWrite/.test(source)) values.push('必须 Bearer（settings.write）');
  if (/auth::uploadsWrite/.test(source)) values.push('必须 Bearer（uploads.write）');
  if (/auth::activityRead/.test(source)) values.push('必须 Bearer（activity.read）');
  if (/auth::profileRead/.test(source)) values.push('必须 Bearer（profile.read）');
  if (/loginCheck|login::handle/.test(source)) values.push('会话或 apiKey');
  if (/csrf(?:Midware)?::check|csrf::check/.test(source)) values.push('CSRF 校验');
  if (/permissionMidware|permission::check/.test(source)) values.push('权限校验');
  if (/writeSecurity/.test(source)) values.push('写入安全校验');
  if (/\bmiddlewares\b/.test(source)) values.push('共享中间件（见源码）');
  return values.length ? values.join('、') : '未在路由声明中识别';
}

const routes = [];
for (const file of walk(processorRoot, file => file.endsWith('.java'))) {
  const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index];
    const match = line.match(/Dispatcher\.(get|post|put|delete|patch)\(\s*"([^"]+)"\s*,\s*([^,;]+)/);
    if (!match) continue;
    const disabled = line.trimStart().startsWith('//');
    let end = index;
    while (end < lines.length && !lines[end].includes(';')) end += 1;
    const context = lines.slice(index, Math.min(end + 1, lines.length)).join(' ');
    routes.push({
      method: match[1].toUpperCase(),
      uri: match[2],
      handler: match[3].trim(),
      middleware: classifyMiddleware(context),
      state: disabled ? '已禁用' : '有效',
      source: `${relative(file)}:${index + 1}`
    });
  }

  const source = lines.join('\n');
  for(const match of source.matchAll(/Dispatcher\.webSocket\("([^"]+)"\s*,\s*([^;]+)\);/g)){
    const line=source.slice(0,match.index).split('\n').length;
    routes.push({method:'WS',uri:match[1],handler:match[2].trim(),middleware:match[1].startsWith('/api/v3/')?'首帧一次性票据及显式Socket风控（见通道）':'通道内校验（见源码）',state:lines[line-1].trimStart().startsWith('//')?'已禁用':'有效',source:`${relative(file)}:${line}`});
  }
  const groupPattern = /Dispatcher\.group\(\)\.middlewares\(([^)]*)\)\.router\(\)\.(get|post|put|delete|patch)\(\)\.uris\(new String\[\]\{([^}]*)\}\)\.handler\(([^)]+)\)/g;
  for (const match of source.matchAll(groupPattern)) {
    const line = source.slice(0, match.index).split('\n').length;
    const uris = [...match[3].matchAll(/"([^"]+)"/g)].map(uri => uri[1]);
    for (const uri of uris) {
      routes.push({
        method: match[2].toUpperCase(),
        uri,
        handler: match[4].trim(),
        middleware: classifyMiddleware(match[1]),
        state: '有效',
        source: `${relative(file)}:${line}`
      });
    }
  }

  const singleGroupPattern = /Dispatcher\.group\(\)(?:\.middlewares\(([^)]*)\))?\.router\(\)\.(get|post|put|delete|patch)\(\)(?:\.(head)\(\))?\.uri\("([^"]+)"\)\.handler\(([^)]+)\)/g;
  for (const match of source.matchAll(singleGroupPattern)) {
    const line = source.slice(0, match.index).split('\n').length;
    routes.push({
      method: match[3] ? `${match[2].toUpperCase()}, HEAD` : match[2].toUpperCase(),
      uri: match[4],
      handler: match[5].trim(),
      middleware: classifyMiddleware(match[1] || ''),
      state: '有效',
      source: `${relative(file)}:${line}`
    });
  }
}

routes.sort((a, b) => a.uri.localeCompare(b.uri) || a.method.localeCompare(b.method));

const endpointRefs = [];
const endpointPattern = /(?:https?:\/\/[^\s"'`]+)?(?:\/api\/|\/openid\/|\/chat-room\/|\/article\/|\/comment\/|\/user\/|\/settings\/|\/notifications\/|\/follow\/|\/vote\/|\/upload\b)[A-Za-z0-9_./?{}=-]*/g;
for (const resourceRoot of resourceRoots) {
  for (const file of walk(resourceRoot, file => /\.(ftl|js)$/.test(file) && !file.endsWith('.min.js'))) {
    const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
    for (let index = 0; index < lines.length; index += 1) {
      const matches = lines[index].match(endpointPattern) || [];
      for (const endpoint of matches) {
        if (endpoint.length > 4) endpointRefs.push({ endpoint, source: `${relative(file)}:${index + 1}` });
      }
    }
  }
}
const distinctRefs = [...new Map(endpointRefs.map(item => [`${item.endpoint}\u0000${item.source}`, item])).values()]
  .sort((a, b) => a.endpoint.localeCompare(b.endpoint) || a.source.localeCompare(b.source));

const counts = routes.reduce((result, route) => {
  result[route.middleware] = (result[route.middleware] || 0) + 1;
  return result;
}, {});
const generatedAt = new Date().toISOString();
const markdown = [
  '# 路由清单',
  '',
  `由源码于 ${generatedAt} 生成。路由变更后执行 \`node tools/api-route-inventory.mjs\` 更新本清单。`,
  '',
  '本清单按源码机械提取：中间件信息根据每条路由附近的 Dispatcher 声明推断。通过非字面量变量或框架配置组装的路由，仍需回到源码复核。',
  '',
  `- Dispatcher 直接声明路由：${routes.length}`,
  `- 非压缩 FTL/JS 中的浏览器端接口引用：${distinctRefs.length}`,
  `- 中间件分组：${Object.entries(counts).map(([key, value]) => `${key}（${value}）`).join('，')}`,
  '',
  '## 服务端路由',
  '',
  '| 方法 | URI | 处理器 | 声明的保护措施 | 状态 | 源码位置 |',
  '| --- | --- | --- | --- | --- | --- |',
  ...routes.map(route => `| ${route.method} | \`${route.uri}\` | \`${route.handler}\` | ${route.middleware} | ${route.state} | [${route.source}](../${route.source}) |`),
  '',
  '## FTL 与 JavaScript 接口引用',
  '',
  '此处仅记录浏览器端源码中的接口字面量引用。由于许多 FTL 页面会加载并由未压缩 JavaScript 发起请求，该清单用于补充服务端路由清单，不能替代服务端路由清单。',
  '',
  '| 接口字面量 | 源码位置 |',
  '| --- | --- |',
  ...distinctRefs.map(reference => `| \`${reference.endpoint}\` | [${reference.source}](../${reference.source}) |`),
  ''
].join('\n');

fs.mkdirSync(path.dirname(output), { recursive: true });
fs.writeFileSync(output, markdown, 'utf8');
console.log(`已生成 ${path.relative(root, output)}，包含 ${routes.length} 条路由和 ${distinctRefs.length} 条浏览器端引用。`);
