/** Local evidence only: no config, cookies, request headers or page HTML are persisted. */
import fs from 'node:fs';
import path from 'node:path';

const escapeHtml = value => String(value ?? '').replace(/[&<>"']/g, character => ({
  '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;',
}[character]));
const short = value => String(value ?? '').replace(/\s+/g, ' ').trim().slice(0, 160);
const time = value => value ? new Intl.DateTimeFormat('zh-CN', {
  timeZone: 'Asia/Hong_Kong', dateStyle: 'medium', timeStyle: 'medium', hourCycle: 'h23',
}).format(new Date(value)) : '未记录';

export function createEvidenceRecorder({ runRoot, capture }) {
  const steps = [];
  const navigation = [];
  fs.mkdirSync(path.join(runRoot, 'screenshots'), { recursive: true });
  const persist = () => fs.writeFileSync(path.join(runRoot, 'evidence.json'), JSON.stringify({ steps, navigation }, null, 2));
  return {
    steps,
    navigation,
    navigate(page) { navigation.push({ ...page, time: new Date().toISOString() }); persist(); },
    async checkpoint({ label, status = 'PASS', session, monitor, evidence = {}, kind = 'check' }) {
      const step = { id: steps.length + 1, label, status, kind, time: new Date().toISOString(), evidence, images: [] };
      steps.push(step);
      // Persist the pending step first so a failed screenshot never disappears from the evidence.
      persist();
      try {
        for (const [role, target] of [['业务页面', session], ['后台监控', monitor]]) {
          if (!target?.client || (role === '后台监控' && target.client === session?.client)) continue;
          const snapshot = await capture(target.client, role === '后台监控' || target.entry?.key === 'Monitor-L');
          const filename = `${String(step.id).padStart(3, '0')}-${step.images.length + 1}.jpg`;
          fs.writeFileSync(path.join(runRoot, 'screenshots', filename), Buffer.from(snapshot.data, 'base64'));
          step.images.push({ role, source: target.entry?.key || '浏览器', path: `screenshots/${filename}`, expanded: snapshot.expanded, page: snapshot.page });
          persist();
        }
        if (!step.images.length) throw new Error('此步骤没有可用浏览器，无法保存截图');
        step.captureStatus = 'PASS';
      } catch (error) {
        step.captureStatus = 'FAIL';
        step.captureError = error.message;
        throw new Error(`截图失败（${label}）：${error.message}`, { cause: error });
      } finally { persist(); }
      return step.id;
    },
  };
}

function rows(values) {
  return `<dl>${values.filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([label, value]) => `<dt>${escapeHtml(label)}</dt><dd>${escapeHtml(value)}</dd>`).join('')}</dl>`;
}

function describe(evidence) {
  const event = evidence.event;
  const http = evidence.http;
  const result = rows([
    [http?.loaderId ? '最终主文档' : 'HTTP 请求', http ? `${http.status ?? '无响应'} ${http.url || http.pathname || ''}${http.location ? ` → ${http.location}` : ''}` : undefined],
    ['来源地址', http?.localAddress], ['响应摘要', http?.preview],
    ['请求', event ? `${event.ip} ${event.method} ${event.uri}` : undefined],
    ['处理器', event?.handler], ['处理流程', event?.flow],
    ['判定', event ? `${event.type}.${event.result} / ${event.reason}` : undefined],
    ['用户状态', event?.userStatus],
    ['统计增长', evidence.counterKey ? `${evidence.counterKey} +${evidence.counterDelta}` : undefined],
    ['页面访问次数', evidence.pageVisits], ['累计请求', evidence.requestCount],
    ['峰值并发', evidence.peakConcurrency], ['并发请求耗时', evidence.burstDurationMs === undefined ? undefined : `${evidence.burstDurationMs} ms`],
    ['HTTP 状态', evidence.httpStatus], ['失败原因', evidence.error],
  ]);
  const selfCheck = evidence.selfCheck;
  const checks = selfCheck ? `<p>结构自检：${escapeHtml(selfCheck.status)}</p><ul>${selfCheck.checks.map(item =>
    `<li>${item.passed ? '通过' : '失败'}：${escapeHtml(item.name)} ${escapeHtml(item.detail || '')}</li>`).join('')}</ul>` : '';
  const controls = evidence.state ? `<p>模式：${escapeHtml(evidence.state.mode)}</p><ul>${evidence.state.controls.map(item =>
    `<li>${escapeHtml(item.id)}：${item.disabled ? '禁用' : '可调整'} / ${escapeHtml(item.value)}</li>`).join('')}</ul>` : '';
  return result + checks + controls;
}

export function buildReportHtml({ result, steps, navigation, runRoot }) {
  const failures = steps.filter(step => step.captureStatus === 'FAIL');
  const status = result.status === 'PASS' && !failures.length ? 'PASS' : 'FAIL';
  const modules = result.modules || [];
  const counts = modules.flatMap(module => module.checks || []).reduce((all, check) => {
    all[check.status] = (all[check.status] || 0) + 1; return all;
  }, {});
  const image = item => {
    // Paths originate in the recorder, but still enforce local screenshot containment on export.
    if (!/^screenshots\/\d+-\d+\.jpg$/.test(item.path)) throw new Error('报告截图路径不合法');
    const data = fs.readFileSync(path.join(runRoot, item.path)).toString('base64');
    return `<figure><figcaption>${escapeHtml(item.role)}${item.expanded ? '（展开取景）' : ''} · ${escapeHtml(item.source)} · ${escapeHtml(item.page?.url || '')}</figcaption>
      <img src="data:image/jpeg;base64,${data}" alt="${escapeHtml(item.role)}"><p class="caption">HTTP ${escapeHtml(item.page?.status ?? '未知')} · ${escapeHtml(item.page?.title || '')}</p></figure>`;
  };
  const detailPages = steps.map(step => `<section class="step">
    <div class="eyebrow">步骤 ${String(step.id).padStart(3, '0')} · ${escapeHtml(time(step.time))}</div>
    <h2><span class="badge ${step.status}">${escapeHtml(step.status)}</span> ${escapeHtml(step.label)}</h2>
    ${step.captureStatus === 'FAIL' ? `<p class="error">截图失败：${escapeHtml(step.captureError)}</p>` : ''}
    ${describe(step.evidence)}
    ${step.images.map(image).join('')}
    ${step.kind === 'http' ? '<p class="note">本步骤使用直接 HTTP 请求，无业务浏览器页面。上图为当时后台；HTTP 状态以请求记录为准。</p>' : ''}
    </section>`).join('');
  const pressure = modules.find(module => module.id === 'pressure')?.pressure;
  return `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
    <meta http-equiv="Content-Security-Policy" content="default-src 'none'; img-src data:; style-src 'unsafe-inline'">
    <title>Rhythm 验证码盾验收报告</title><style>
    @page { size: A4; margin: 13mm 13mm 16mm; }
    *{box-sizing:border-box} body{margin:0;color:#172a35;font:12px/1.5 'Microsoft YaHei','Noto Sans CJK SC',sans-serif}
    h1{font-size:30px;line-height:1.3;margin:16px 0}h2{font-size:17px;line-height:1.5;margin:8px 0 12px}
    h3{font-size:14px;margin:18px 0 8px}.eyebrow{color:#526c79;font-size:10px;letter-spacing:.4px}
    .badge{display:inline-block;background:#e4edf1;color:#294653;padding:2px 7px;font-size:11px;vertical-align:middle}
    .PASS{background:#d9eee5;color:#195c43}.FAIL,.error{background:#fae1de;color:#9a2b22}.NOT_RUN,.OBSERVED{background:#fff0d3;color:#795113}
    .summary{border-top:5px solid #246a75;padding-top:18px}.note{color:#52616a;font-size:11px}
    table{width:100%;border-collapse:collapse;table-layout:fixed;font-size:11px}th,td{text-align:left;padding:7px;border-bottom:1px solid #dce3e7;overflow-wrap:anywhere;vertical-align:top}th{background:#eef3f5}
    dl{display:grid;grid-template-columns:74px 1fr;gap:3px 8px;margin:9px 0 12px;font-size:11px}dt{color:#52616a}dd{margin:0;overflow-wrap:anywhere}
    .step{break-before:page}figure{margin:9px 0;break-inside:avoid}figcaption{font-size:10px;color:#36515e;overflow-wrap:anywhere;margin-bottom:3px}
    img{display:block;max-width:100%;max-height:79mm;object-fit:contain;object-position:left top;border:1px solid #dce3e7}
    .caption{font-size:9px;color:#52616a;margin:2px 0}.appendix{break-before:page}tr{break-inside:avoid}ul{padding-left:18px;font-size:11px}
    </style></head><body><section class="summary">
    <div class="eyebrow">RHYTHM / CAPTCHA SHIELD ACCEPTANCE</div><h1>验证码盾验收报告</h1>
    <p><span class="badge ${status}">${status}</span> 所选模块 ${modules.length} 项 · 断言通过 ${counts.PASS || 0} 项 · 失败 ${counts.FAIL || 0} 项</p>
    ${rows([['开始时间', time(result.startedAt)], ['结束时间', time(result.finishedAt)], ['账号初始化', result.initialization?.status || '未执行'], ['截图步骤', steps.length], ['证据目录', runRoot], ['运行错误', result.error || result.initialization?.error]])}
    <table><thead><tr><th style="width:20%">模块</th><th style="width:16%">状态</th><th>结果</th></tr></thead><tbody>${modules.map(module =>
      `<tr><td>${escapeHtml(module.name || module.id)}</td><td>${escapeHtml(module.status)}</td><td>${escapeHtml(module.error || `${(module.checks || []).filter(check => check.status === 'PASS').length} 项断言通过`)}</td></tr>`).join('')}</tbody></table>
    <h3>范围与说明</h3><p>仅说明本轮所选模块的实测结果。未选择的模块未在本轮运行；未运行、失败和观察记录不计为通过。</p>
    <p>人工极验由操作者完成，脚本只等待并核对真实跳转与监控。截图只保留当时可见区域，后台图片可能仍在加载；不记录密码、Cookie、CSRF 令牌或极验证明。</p>
    <p>Linux ipset：${escapeHtml(pressure?.ipset?.status || 'NOT_VERIFIED')}。Windows 应用层结果不证明操作系统封禁成功。并发阈值验收不代表任意负载下的性能保证。</p>
    ${failures.length ? `<p class="error">有 ${failures.length} 个截图步骤失败，报告证据不完整。</p>` : ''}
    <h3>步骤索引</h3><table><thead><tr><th style="width:10%">编号</th><th>步骤</th><th style="width:17%">状态</th></tr></thead><tbody>${steps.map(step =>
      `<tr><td>${step.id}</td><td>${escapeHtml(step.label)}</td><td>${escapeHtml(step.status)}${step.captureStatus === 'FAIL' ? ' / 截图失败' : ''}</td></tr>`).join('')}</tbody></table>
    </section>${detailPages}
    <section class="appendix"><h2>导航证据</h2><p class="note">主文档状态来自浏览器网络事件。下列记录不单独代表测试断言通过。</p>
    <table><thead><tr><th style="width:14%">来源</th><th style="width:9%">HTTP</th><th>地址与摘要</th></tr></thead><tbody>${navigation.map(item =>
      `<tr><td>${escapeHtml(item.source)}</td><td>${escapeHtml(item.status ?? '未知')}</td><td>${item.requestedUrl && item.requestedUrl !== item.url ? `请求：${escapeHtml(item.requestedUrl)}<br>最终：` : ''}${escapeHtml(item.url)}<br>${escapeHtml(short(item.title))}<br>${escapeHtml(short(item.preview))}</td></tr>`).join('')}</tbody></table></section>
    ${pressure ? `<section class="appendix"><h2>E 直接 HTTP 请求记录</h2>${rows([['来源', pressure.sourceIp], ['匿名阈值', pressure.effectiveThreshold], ['并发', pressure.concurrency], ['越界请求序号', pressure.boundaryRequestCount]])}
      <table><thead><tr><th style="width:10%">序号</th><th style="width:12%">HTTP</th><th>路径 / 跳转 / 错误</th><th style="width:17%">耗时 ms</th></tr></thead><tbody>${pressure.requests.map((item, index) =>
        `<tr><td>${index + 1}</td><td>${escapeHtml(item.status ?? '无响应')}</td><td>${escapeHtml(item.pathname || '')} ${escapeHtml(item.location || item.error || '')}</td><td>${escapeHtml(item.durationMs ?? '')}</td></tr>`).join('')}</tbody></table></section>` : ''}
    </body></html>`;
}

export async function exportPdfReport({ runRoot, result, recorder, print }) {
  const html = buildReportHtml({ result, steps: recorder.steps, navigation: recorder.navigation, runRoot });
  const htmlPath = path.join(runRoot, 'report.html');
  const pdfPath = path.join(runRoot, 'report.pdf');
  fs.writeFileSync(htmlPath, html);
  const pdf = Buffer.from(await print(html), 'base64');
  if (pdf.subarray(0, 5).toString() !== '%PDF-') throw new Error('Chrome 未返回有效 PDF');
  fs.writeFileSync(pdfPath, pdf);
  return { status: 'PASS', pdf: 'report.pdf', html: 'report.html', evidence: 'evidence.json',
    screenshotCount: recorder.steps.reduce((sum, step) => sum + step.images.length, 0), stepCount: recorder.steps.length };
}
