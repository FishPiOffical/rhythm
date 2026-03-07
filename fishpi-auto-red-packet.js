// ==UserScript==
// @name         FishPi 聊天室自动抢红包
// @namespace    https://fishpi.cn/
// @version      0.4.0
// @description  FishPi 聊天室自动抢红包脚本，支持经典/简约样式、悬浮拖拽设置面板、红包统计、自定义官方感谢文案与频控
// @author       FishPi Offical
// @match        https://fishpi.cn/cr*
// @grant        GM_addStyle
// @grant        GM_getValue
// @grant        GM_setValue
// @grant        GM_registerMenuCommand
// @run-at       document-end
// @noframes
// ==/UserScript==


(function () {
    'use strict';

    if (window.location.pathname !== '/cr') {
        return;
    }

    const pageWindow = typeof unsafeWindow !== 'undefined' ? unsafeWindow : window;
    const STORAGE_KEY = 'fishpi-auto-red-packet-settings-v1';
    const STATS_STORAGE_KEY = 'fishpi-auto-red-packet-stats-v1';
    const PANEL_ID = 'arp-panel';
    const PANEL_BODY_ID = 'arp-panel-body';
    const LAUNCHER_ID = 'arp-launcher';
    const DEFAULT_THANK_TEMPLATE = '我通过官方抢红包扩展抢到了{points}积分，谢谢老板~';
    const OFFICIAL_EXTENSION_QUOTE = '> 来自官方抢红包扩展，下载地址：https://ext.adventext.fun/market';
    const DAY_MS = 24 * 60 * 60 * 1000;
    const DEFAULT_SETTINGS = {
        enabled: true,
        delaySeconds: 10,
        autoReplyEnabled: true,
        thankTemplate: DEFAULT_THANK_TEMPLATE,
        thankLimitEnabled: true,
        thankLimitWindowMinutes: 60,
        thankLimitMaxPerUser: 1,
        panelOpen: false,
        panelPosition: null,
        launcherPosition: null,
        types: {
            random: true,
            average: true,
            specify: true,
            heartbeat: false,
            rockPaperScissors: false
        },
        rockPaperScissors: {
            randomGesture: true,
            fixedGesture: '0'
        }
    };


    const state = {
        settings: loadSettings(),
        stats: loadStats(),
        currentUserId: '',
        currentUserName: '',
        servePath: '',
        scheduled: new Map(),
        messageCache: new Map(),
        manualHandled: new Set(),
        statusText: '等待初始化',
        lastGrabText: '暂无',
        renderPatched: false,
        readyTimer: null,
        dragState: null,
        launcherDragState: null,
        launcherClickSuppress: false,
        uiMounted: false
    };


    function clone(value) {
        return JSON.parse(JSON.stringify(value));
    }

    function mergeSettings(raw) {
        const merged = clone(DEFAULT_SETTINGS);
        if (!raw || typeof raw !== 'object') {
            return merged;
        }

        merged.enabled = raw.enabled !== undefined ? !!raw.enabled : merged.enabled;
        merged.delaySeconds = normalizeDelay(raw.delaySeconds);
        merged.autoReplyEnabled = raw.autoReplyEnabled !== undefined ? !!raw.autoReplyEnabled : merged.autoReplyEnabled;
        merged.thankTemplate = normalizeThankTemplate(raw.thankTemplate);
        merged.thankLimitEnabled = raw.thankLimitEnabled !== undefined ? !!raw.thankLimitEnabled : merged.thankLimitEnabled;
        merged.thankLimitWindowMinutes = normalizeThankLimitWindow(raw.thankLimitWindowMinutes);
        merged.thankLimitMaxPerUser = normalizeThankLimitMax(raw.thankLimitMaxPerUser);
        merged.panelOpen = raw.panelOpen !== undefined ? !!raw.panelOpen : merged.panelOpen;
        merged.panelPosition = raw.panelPosition && typeof raw.panelPosition === 'object'
            ? {
                left: Number(raw.panelPosition.left),
                top: Number(raw.panelPosition.top)
            }
            : null;
        merged.launcherPosition = raw.launcherPosition && typeof raw.launcherPosition === 'object'
            ? {
                left: Number(raw.launcherPosition.left),
                top: Number(raw.launcherPosition.top)
            }
            : null;

        merged.types = Object.assign({}, merged.types, raw.types || {});
        merged.types.random = !!merged.types.random;
        merged.types.average = !!merged.types.average;
        merged.types.specify = !!merged.types.specify;
        merged.types.heartbeat = !!merged.types.heartbeat;
        merged.types.rockPaperScissors = !!merged.types.rockPaperScissors;

        merged.rockPaperScissors = Object.assign({}, merged.rockPaperScissors, raw.rockPaperScissors || {});
        merged.rockPaperScissors.randomGesture = !!merged.rockPaperScissors.randomGesture;
        merged.rockPaperScissors.fixedGesture = ['0', '1', '2'].includes(String(merged.rockPaperScissors.fixedGesture))
            ? String(merged.rockPaperScissors.fixedGesture)
            : '0';

        return merged;
    }

    function storageGet(key, fallback) {
        const storageKey = typeof key === 'string' ? key : STORAGE_KEY;
        const defaultValue = arguments.length > 1 ? fallback : null;
        try {
            if (typeof GM_getValue === 'function') {
                return GM_getValue(storageKey, defaultValue);
            }
        } catch (error) {
            console.warn('[ARP] GM_getValue 读取失败', error);
        }

        try {
            const raw = window.localStorage.getItem(storageKey);
            return raw ? JSON.parse(raw) : defaultValue;
        } catch (error) {
            console.warn('[ARP] localStorage 读取失败', error);
            return defaultValue;
        }
    }

    function storageSet(key, value) {
        const storageKey = value === undefined ? STORAGE_KEY : key;
        const data = value === undefined ? key : value;
        try {
            if (typeof GM_setValue === 'function') {
                GM_setValue(storageKey, data);
                return;
            }
        } catch (error) {
            console.warn('[ARP] GM_setValue 写入失败', error);
        }

        try {
            window.localStorage.setItem(storageKey, JSON.stringify(data));
        } catch (error) {
            console.warn('[ARP] localStorage 写入失败', error);
        }
    }

    function loadSettings() {
        return mergeSettings(storageGet(STORAGE_KEY, null));
    }

    function saveSettings() {
        storageSet(STORAGE_KEY, state.settings);
    }

    function normalizeHistoryItem(item) {
        if (!item || typeof item !== 'object' || !item.oId) {
            return null;
        }
        return {
            oId: String(item.oId),
            time: Number(item.time) || 0,
            points: Number(item.points) || 0,
            senderId: String(item.senderId || ''),
            senderName: String(item.senderName || ''),
            type: String(item.type || '')
        };
    }

    function normalizeThankItem(item) {
        if (!item || typeof item !== 'object') {
            return null;
        }
        return {
            oId: String(item.oId || ''),
            time: Number(item.time) || 0,
            senderId: String(item.senderId || ''),
            senderName: String(item.senderName || '')
        };
    }

    function mergeStats(raw) {
        const merged = {
            totalPoints: 0,
            totalCount: 0,
            history: [],
            thanksHistory: []
        };
        if (!raw || typeof raw !== 'object') {
            return merged;
        }

        merged.totalPoints = Number.isFinite(Number(raw.totalPoints)) ? Number(raw.totalPoints) : 0;
        merged.totalCount = Number.isFinite(Number(raw.totalCount)) ? Math.max(0, Math.floor(Number(raw.totalCount))) : 0;
        merged.history = Array.isArray(raw.history) ? raw.history.map(normalizeHistoryItem).filter(Boolean) : [];
        merged.thanksHistory = Array.isArray(raw.thanksHistory) ? raw.thanksHistory.map(normalizeThankItem).filter(Boolean) : [];
        return merged;
    }

    function loadStats() {
        return mergeStats(storageGet(STATS_STORAGE_KEY, null));
    }

    function saveStats() {
        pruneStats();
        storageSet(STATS_STORAGE_KEY, state.stats);
    }

    function normalizeDelay(value) {
        const delay = Number(value);
        if (!Number.isFinite(delay)) {
            return DEFAULT_SETTINGS.delaySeconds;
        }
        return Math.min(600, Math.max(3, Math.round(delay)));
    }

    function normalizeThankLimitWindow(value) {
        const minutes = Number(value);
        if (!Number.isFinite(minutes)) {
            return DEFAULT_SETTINGS.thankLimitWindowMinutes;
        }
        return Math.min(1440, Math.max(1, Math.round(minutes)));
    }

    function normalizeThankLimitMax(value) {
        const count = Number(value);
        if (!Number.isFinite(count)) {
            return DEFAULT_SETTINGS.thankLimitMaxPerUser;
        }
        return Math.min(20, Math.max(1, Math.round(count)));
    }

    function normalizeThankTemplate(value) {
        const template = String(value == null ? '' : value).trim();
        if (!template) {
            return DEFAULT_THANK_TEMPLATE;
        }
        return template.slice(0, 300);
    }

    function notify(type, message) {

        if (pageWindow.Util && typeof pageWindow.Util.notice === 'function') {
            pageWindow.Util.notice(type, 3000, message);
            return;
        }
        console.log(`[ARP:${type}] ${message}`);
    }

    function updateStatus(message, lastGrabText) {
        state.statusText = message;
        if (lastGrabText !== undefined) {
            state.lastGrabText = lastGrabText;
        }
        renderPanelBody();
    }

    function ensureUserContext() {
        const label = pageWindow.Label || {};
        state.currentUserId = String(label.currentUserId || '');
        state.currentUserName = String(label.currentUser || '');
        state.servePath = String(label.servePath || '');
        return !!(state.currentUserId && state.currentUserName && state.servePath);
    }

    function injectStyles() {
        const css = `
            #${LAUNCHER_ID} {
                position: fixed;
                right: 24px;
                bottom: 112px;
                z-index: 99999;
                border: 0;
                border-radius: 999px;
                padding: 10px 14px;
                background: linear-gradient(135deg, #ff7a45, #ff4d4f);
                color: #fff;
                box-shadow: 0 10px 28px rgba(255, 77, 79, 0.28);
                cursor: grab;
                touch-action: none;
                font-size: 13px;
                font-weight: 600;
            }

            #${LAUNCHER_ID}:hover {
                transform: translateY(-1px);
            }

            #${LAUNCHER_ID}:active {
                cursor: grabbing;
            }

            #${PANEL_ID} {
                position: fixed;
                right: 24px;
                bottom: 164px;
                width: 340px;
                z-index: 100000;
                border-radius: 14px;
                overflow: hidden;
                background: rgba(255, 255, 255, 0.97);
                color: #222;
                box-shadow: 0 20px 48px rgba(0, 0, 0, 0.18);
                border: 1px solid rgba(0, 0, 0, 0.08);
                backdrop-filter: blur(10px);
            }

            #${PANEL_ID}.arp-hidden {
                display: none;
            }

            #${PANEL_ID} * {
                box-sizing: border-box;
            }

            #${PANEL_ID} .arp-header {
                display: flex;
                align-items: center;
                justify-content: space-between;
                gap: 12px;
                padding: 12px 14px;
                background: linear-gradient(135deg, #fff7e8, #ffe7ba);
                cursor: move;
                user-select: none;
                border-bottom: 1px solid rgba(0, 0, 0, 0.06);
            }

            #${PANEL_ID} .arp-title {
                font-size: 14px;
                font-weight: 700;
            }

            #${PANEL_ID} .arp-header-actions {
                display: flex;
                align-items: center;
                gap: 8px;
            }

            #${PANEL_ID} .arp-header-actions button,
            #${PANEL_ID} .arp-inline-button {
                border: 0;
                border-radius: 8px;
                padding: 6px 10px;
                cursor: pointer;
                font-size: 12px;
                background: #fff;
                color: #333;
                box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
            }

            #${PANEL_ID} .arp-close {
                width: 28px;
                height: 28px;
                padding: 0;
                font-size: 16px;
                line-height: 28px;
                text-align: center;
            }

            #${PANEL_ID} .arp-body {
                padding: 14px;
                max-height: min(72vh, 720px);
                overflow: auto;
            }

            #${PANEL_ID} .arp-card {
                margin-bottom: 12px;
                padding: 12px;
                border-radius: 12px;
                background: #fafafa;
                border: 1px solid rgba(0, 0, 0, 0.05);
            }

            #${PANEL_ID} .arp-card:last-child {
                margin-bottom: 0;
            }

            #${PANEL_ID} .arp-card-title {
                margin-bottom: 10px;
                font-size: 13px;
                font-weight: 700;
                color: #111;
            }

            #${PANEL_ID} .arp-status-grid {
                display: grid;
                grid-template-columns: repeat(2, minmax(0, 1fr));
                gap: 8px;
            }

            #${PANEL_ID} .arp-status-item {
                padding: 8px 10px;
                border-radius: 10px;
                background: #fff;
            }

            #${PANEL_ID} .arp-status-label {
                display: block;
                margin-bottom: 4px;
                font-size: 11px;
                color: #888;
            }

            #${PANEL_ID} .arp-status-value {
                display: block;
                font-size: 12px;
                color: #222;
                word-break: break-word;
            }

            #${PANEL_ID} .arp-row,
            #${PANEL_ID} .arp-option {
                display: flex;
                align-items: center;
                justify-content: space-between;
                gap: 12px;
            }

            #${PANEL_ID} .arp-row + .arp-row,
            #${PANEL_ID} .arp-option + .arp-option,
            #${PANEL_ID} .arp-note + .arp-option,
            #${PANEL_ID} .arp-option + .arp-note,
            #${PANEL_ID} .arp-inline-grid + .arp-note,
            #${PANEL_ID} .arp-option + .arp-inline-grid {
                margin-top: 10px;
            }

            #${PANEL_ID} .arp-option--stack {
                align-items: stretch;
                flex-direction: column;
            }

            #${PANEL_ID} .arp-option span,
            #${PANEL_ID} .arp-row span {
                font-size: 13px;
                color: #222;
            }

            #${PANEL_ID} .arp-sub {
                display: block;
                margin-top: 3px;
                font-size: 11px;
                color: #888;
            }

            #${PANEL_ID} input[type="checkbox"] {
                width: 18px;
                height: 18px;
                accent-color: #ff7a45;
                cursor: pointer;
                flex-shrink: 0;
            }

            #${PANEL_ID} input[type="number"],
            #${PANEL_ID} select {
                width: 110px;
                padding: 6px 8px;
                border: 1px solid rgba(0, 0, 0, 0.12);
                border-radius: 8px;
                background: #fff;
                color: #222;
            }

            #${PANEL_ID} textarea {
                width: 100%;
                min-height: 82px;
                padding: 8px;
                border: 1px solid rgba(0, 0, 0, 0.12);
                border-radius: 8px;
                background: #fff;
                color: #222;
                resize: vertical;
                font: inherit;
            }

            #${PANEL_ID} .arp-inline-grid {
                display: grid;
                grid-template-columns: repeat(2, minmax(0, 1fr));
                gap: 10px;
            }

            #${PANEL_ID} .arp-inline-grid input[type="number"] {
                width: 100%;
            }

            #${PANEL_ID} .arp-note {
                font-size: 11px;
                line-height: 1.5;
                color: #666;
            }

            #${PANEL_ID} .arp-danger {
                color: #cf1322;
            }

            #${PANEL_ID} .arp-actions-row {
                display: flex;
                gap: 8px;
                flex-wrap: wrap;
            }

            @media (max-width: 768px) {
                #${LAUNCHER_ID} {
                    right: 12px;
                    bottom: 96px;
                }

                #${PANEL_ID} {
                    width: min(92vw, 340px);
                    right: 12px;
                    bottom: 144px;
                }
            }
        `;

        if (typeof GM_addStyle === 'function') {
            GM_addStyle(css);
            return;
        }

        const style = document.createElement('style');
        style.textContent = css;
        document.head.appendChild(style);
    }

    function createUI() {
        if (state.uiMounted || !document.body) {
            return;
        }

        injectStyles();

        const launcher = document.createElement('button');
        launcher.id = LAUNCHER_ID;
        launcher.type = 'button';
        launcher.textContent = '🧧 抢红包设置';
        launcher.addEventListener('pointerdown', startLauncherDrag);
        launcher.addEventListener('click', function (event) {
            if (state.launcherClickSuppress) {
                event.preventDefault();
                event.stopPropagation();
                state.launcherClickSuppress = false;
                return;
            }
            togglePanel();
        });
        document.body.appendChild(launcher);

        if (state.settings.launcherPosition && Number.isFinite(state.settings.launcherPosition.left) && Number.isFinite(state.settings.launcherPosition.top)) {
            applyLauncherPosition(state.settings.launcherPosition.left, state.settings.launcherPosition.top);
        }

        const panel = document.createElement('div');
        panel.id = PANEL_ID;
        panel.className = state.settings.panelOpen ? '' : 'arp-hidden';
        panel.innerHTML = `
            <div class="arp-header">
                <div class="arp-title">🧧 自动抢红包设置</div>
                <div class="arp-header-actions">
                    <button type="button" class="arp-inline-button" data-action="scan">立即扫描</button>
                    <button type="button" class="arp-close" data-action="close">×</button>
                </div>
            </div>
            <div class="arp-body" id="${PANEL_BODY_ID}"></div>
        `;
        document.body.appendChild(panel);

        panel.querySelector('.arp-header').addEventListener('pointerdown', startDrag);
        panel.addEventListener('click', handlePanelAction);

        if (state.settings.panelPosition && Number.isFinite(state.settings.panelPosition.left) && Number.isFinite(state.settings.panelPosition.top)) {
            applyPanelPosition(state.settings.panelPosition.left, state.settings.panelPosition.top);
        }

        state.uiMounted = true;
        renderPanelBody();
        registerMenuCommands();
    }

    function registerMenuCommands() {

        if (typeof GM_registerMenuCommand !== 'function') {
            return;
        }

        try {
            GM_registerMenuCommand('打开抢红包设置', function () {
                openPanel();
            });
            GM_registerMenuCommand(state.settings.enabled ? '关闭自动抢红包' : '开启自动抢红包', function () {
                state.settings.enabled = !state.settings.enabled;
                saveSettings();
                applySettingsChange();
            });
        } catch (error) {
            console.warn('[ARP] 注册菜单失败', error);
        }
    }

    function renderPanelBody() {
        const body = document.getElementById(PANEL_BODY_ID);
        if (!body) {
            return;
        }

        const settings = state.settings;
        const statsSummary = getStatsSummary();
        body.innerHTML = `
            <div class="arp-card">
                <div class="arp-card-title">运行状态</div>
                <div class="arp-status-grid">
                    <div class="arp-status-item">
                        <span class="arp-status-label">功能状态</span>
                        <span class="arp-status-value">${settings.enabled ? '运行中' : '已关闭'}</span>
                    </div>
                    <div class="arp-status-item">
                        <span class="arp-status-label">排队数量</span>
                        <span class="arp-status-value">${state.scheduled.size}</span>
                    </div>
                    <div class="arp-status-item">
                        <span class="arp-status-label">当前状态</span>
                        <span class="arp-status-value">${escapeHtml(state.statusText)}</span>
                    </div>
                    <div class="arp-status-item">
                        <span class="arp-status-label">最近一次</span>
                        <span class="arp-status-value">${escapeHtml(state.lastGrabText)}</span>
                    </div>
                    <div class="arp-status-item">
                        <span class="arp-status-label">近7天积分</span>
                        <span class="arp-status-value">${formatPoints(statsSummary.last7DaysPoints, true)} 积分</span>
                    </div>
                    <div class="arp-status-item">
                        <span class="arp-status-label">近7天次数</span>
                        <span class="arp-status-value">${statsSummary.last7DaysCount} 次</span>
                    </div>
                    <div class="arp-status-item">
                        <span class="arp-status-label">累计积分</span>
                        <span class="arp-status-value">${formatPoints(statsSummary.totalPoints, true)} 积分</span>
                    </div>
                    <div class="arp-status-item">
                        <span class="arp-status-label">累计次数</span>
                        <span class="arp-status-value">${statsSummary.totalCount} 次</span>
                    </div>
                </div>
            </div>

            <div class="arp-card">
                <div class="arp-card-title">基础设置</div>
                <label class="arp-option">
                    <span>启用自动抢红包</span>
                    <input type="checkbox" data-setting="enabled" ${settings.enabled ? 'checked' : ''}>
                </label>
                <label class="arp-option">
                    <span>
                        抢红包延迟
                        <span class="arp-sub">单位：秒，最小 3 秒</span>
                    </span>
                    <input type="number" min="3" max="600" step="1" data-setting="delaySeconds" value="${settings.delaySeconds}">
                </label>
            </div>

            <div class="arp-card">
                <div class="arp-card-title">红包类型</div>
                <label class="arp-option">
                    <span>拼手气红包</span>
                    <input type="checkbox" data-type="random" ${settings.types.random ? 'checked' : ''}>
                </label>
                <label class="arp-option">
                    <span>普通红包</span>
                    <input type="checkbox" data-type="average" ${settings.types.average ? 'checked' : ''}>
                </label>
                <label class="arp-option">
                    <span>
                        专属红包
                        <span class="arp-sub">仅在红包指定了你时自动抢</span>
                    </span>
                    <input type="checkbox" data-type="specify" ${settings.types.specify ? 'checked' : ''}>
                </label>
                <label class="arp-option">
                    <span>
                        心跳红包
                        <span class="arp-sub arp-danger">可能抢到负积分，默认关闭</span>
                    </span>
                    <input type="checkbox" data-type="heartbeat" ${settings.types.heartbeat ? 'checked' : ''}>
                </label>
                <label class="arp-option">
                    <span>
                        猜拳红包
                        <span class="arp-sub arp-danger">猜错会扣积分，默认关闭</span>
                    </span>
                    <input type="checkbox" data-type="rockPaperScissors" ${settings.types.rockPaperScissors ? 'checked' : ''}>
                </label>
                <div class="arp-row">
                    <span>
                        猜拳出拳策略
                        <span class="arp-sub">只在开启“猜拳红包”后生效</span>
                    </span>
                    <select data-setting="rpsMode">
                        <option value="random" ${settings.rockPaperScissors.randomGesture ? 'selected' : ''}>随机出拳</option>
                        <option value="fixed" ${settings.rockPaperScissors.randomGesture ? '' : 'selected'}>固定出拳</option>
                    </select>
                </div>
                <div class="arp-row">
                    <span>固定出拳</span>
                    <select data-setting="fixedGesture" ${settings.rockPaperScissors.randomGesture ? 'disabled' : ''}>
                        <option value="0" ${settings.rockPaperScissors.fixedGesture === '0' ? 'selected' : ''}>石头</option>
                        <option value="1" ${settings.rockPaperScissors.fixedGesture === '1' ? 'selected' : ''}>剪刀</option>
                        <option value="2" ${settings.rockPaperScissors.fixedGesture === '2' ? 'selected' : ''}>布</option>
                    </select>
                </div>
                <div class="arp-note">摇骰子红包当前服务端本身就不支持领取，这里不提供开关；自己发的红包会自动跳过。</div>
            </div>

            <div class="arp-card">
                <div class="arp-card-title">感谢文案</div>
                <label class="arp-option">
                    <span>
                        抢到后自动致谢
                        <span class="arp-sub">仅在抢到正积分时发送</span>
                    </span>
                    <input type="checkbox" data-setting="autoReplyEnabled" ${settings.autoReplyEnabled ? 'checked' : ''}>
                </label>
                <label class="arp-option arp-option--stack">
                    <span>
                        自定义感谢文案
                        <span class="arp-sub">支持占位符：{points}、{user}</span>
                    </span>
                    <textarea data-setting="thankTemplate" rows="3">${escapeHtml(settings.thankTemplate)}</textarea>
                </label>
                <div class="arp-note">发送格式：普通文字感谢文案 + 空行 + Markdown 引用。固定引用内容：${escapeHtml(OFFICIAL_EXTENSION_QUOTE)}</div>
                <label class="arp-option">
                    <span>
                        同一人感谢频控
                        <span class="arp-sub">限制每个人在一段时间内只感谢前几次</span>
                    </span>
                    <input type="checkbox" data-setting="thankLimitEnabled" ${settings.thankLimitEnabled ? 'checked' : ''}>
                </label>
                <div class="arp-inline-grid">
                    <label class="arp-option">
                        <span>窗口分钟数</span>
                        <input type="number" min="1" max="1440" step="1" data-setting="thankLimitWindowMinutes" value="${settings.thankLimitWindowMinutes}" ${settings.thankLimitEnabled ? '' : 'disabled'}>
                    </label>
                    <label class="arp-option">
                        <span>每人前几次</span>
                        <input type="number" min="1" max="20" step="1" data-setting="thankLimitMaxPerUser" value="${settings.thankLimitMaxPerUser}" ${settings.thankLimitEnabled ? '' : 'disabled'}>
                    </label>
                </div>
            </div>

            <div class="arp-card">
                <div class="arp-card-title">快捷操作</div>
                <div class="arp-actions-row">
                    <button type="button" class="arp-inline-button" data-action="scan">立即扫描</button>
                    <button type="button" class="arp-inline-button" data-action="stop-all">清空等待队列</button>
                </div>
                <div class="arp-note">支持聊天室“经典”和“简约”两种样式；右下角入口按钮和设置面板都支持拖拽。</div>
            </div>
        `;

        bindPanelInputs(body);
    }

    function bindPanelInputs(body) {
        body.querySelectorAll('[data-setting="enabled"]').forEach((node) => {
            node.addEventListener('change', function () {
                state.settings.enabled = node.checked;
                saveSettings();
                applySettingsChange();
            });
        });

        body.querySelectorAll('[data-setting="delaySeconds"]').forEach((node) => {
            node.addEventListener('change', function () {
                state.settings.delaySeconds = normalizeDelay(node.value);
                node.value = String(state.settings.delaySeconds);
                saveSettings();
                applySettingsChange();
            });
        });

        body.querySelectorAll('[data-setting="autoReplyEnabled"]').forEach((node) => {
            node.addEventListener('change', function () {
                state.settings.autoReplyEnabled = node.checked;
                saveSettings();
                updateStatus('已更新感谢开关');
            });
        });

        body.querySelectorAll('[data-setting="thankTemplate"]').forEach((node) => {
            node.addEventListener('change', function () {
                state.settings.thankTemplate = normalizeThankTemplate(node.value);
                node.value = state.settings.thankTemplate;
                saveSettings();
                updateStatus('已更新感谢文案模板');
            });
        });

        body.querySelectorAll('[data-setting="thankLimitEnabled"]').forEach((node) => {
            node.addEventListener('change', function () {
                state.settings.thankLimitEnabled = node.checked;
                saveSettings();
                updateStatus('已更新感谢频控开关');
                renderPanelBody();
            });
        });

        body.querySelectorAll('[data-setting="thankLimitWindowMinutes"]').forEach((node) => {
            node.addEventListener('change', function () {
                state.settings.thankLimitWindowMinutes = normalizeThankLimitWindow(node.value);
                node.value = String(state.settings.thankLimitWindowMinutes);
                saveSettings();
                updateStatus('已更新感谢频控窗口');
                renderPanelBody();
            });
        });

        body.querySelectorAll('[data-setting="thankLimitMaxPerUser"]').forEach((node) => {
            node.addEventListener('change', function () {
                state.settings.thankLimitMaxPerUser = normalizeThankLimitMax(node.value);
                node.value = String(state.settings.thankLimitMaxPerUser);
                saveSettings();
                updateStatus('已更新感谢频控次数');
                renderPanelBody();
            });
        });

        body.querySelectorAll('[data-type]').forEach((node) => {
            node.addEventListener('change', function () {
                const type = node.getAttribute('data-type');
                state.settings.types[type] = node.checked;
                saveSettings();
                applySettingsChange();
            });
        });

        body.querySelectorAll('[data-setting="rpsMode"]').forEach((node) => {
            node.addEventListener('change', function () {
                state.settings.rockPaperScissors.randomGesture = node.value === 'random';
                saveSettings();
                applySettingsChange();
            });
        });

        body.querySelectorAll('[data-setting="fixedGesture"]').forEach((node) => {
            node.addEventListener('change', function () {
                state.settings.rockPaperScissors.fixedGesture = ['0', '1', '2'].includes(node.value) ? node.value : '0';
                saveSettings();
                applySettingsChange();
            });
        });
    }

    function handlePanelAction(event) {

        const actionNode = event.target.closest('[data-action]');
        if (!actionNode) {
            return;
        }

        const action = actionNode.getAttribute('data-action');
        if (action === 'close') {
            closePanel();
            return;
        }
        if (action === 'scan') {
            scanRecentMessages(true);
            return;
        }
        if (action === 'stop-all') {
            clearAllSchedules('已清空等待队列');
        }
    }

    function togglePanel() {
        if (state.settings.panelOpen) {
            closePanel();
        } else {
            openPanel();
        }
    }

    function openPanel() {
        const panel = document.getElementById(PANEL_ID);
        if (!panel) {
            return;
        }

        state.settings.panelOpen = true;
        panel.classList.remove('arp-hidden');
        saveSettings();
        renderPanelBody();
    }

    function closePanel() {
        const panel = document.getElementById(PANEL_ID);
        if (!panel) {
            return;
        }

        state.settings.panelOpen = false;
        panel.classList.add('arp-hidden');
        saveSettings();
    }

    function startDrag(event) {
        if (event.button !== 0) {
            return;
        }

        if (event.target.closest('button')) {
            return;
        }

        const panel = document.getElementById(PANEL_ID);
        if (!panel) {
            return;
        }

        const rect = panel.getBoundingClientRect();
        state.dragState = {
            offsetX: event.clientX - rect.left,
            offsetY: event.clientY - rect.top
        };

        document.addEventListener('pointermove', onDrag);
        document.addEventListener('pointerup', stopDrag);
        document.body.style.userSelect = 'none';
    }

    function onDrag(event) {
        if (!state.dragState) {
            return;
        }

        const panel = document.getElementById(PANEL_ID);
        if (!panel) {
            return;
        }

        const left = event.clientX - state.dragState.offsetX;
        const top = event.clientY - state.dragState.offsetY;
        applyPanelPosition(left, top);
    }

    function stopDrag() {
        const panel = document.getElementById(PANEL_ID);
        if (panel) {
            const rect = panel.getBoundingClientRect();
            state.settings.panelPosition = {
                left: Math.round(rect.left),
                top: Math.round(rect.top)
            };
            saveSettings();
        }

        state.dragState = null;
        document.removeEventListener('pointermove', onDrag);
        document.removeEventListener('pointerup', stopDrag);
        document.body.style.userSelect = '';
    }

    function applyPanelPosition(left, top) {
        const panel = document.getElementById(PANEL_ID);
        if (!panel) {
            return;
        }

        const maxLeft = Math.max(8, window.innerWidth - panel.offsetWidth - 8);
        const maxTop = Math.max(8, window.innerHeight - panel.offsetHeight - 8);
        const finalLeft = Math.min(maxLeft, Math.max(8, Math.round(left)));
        const finalTop = Math.min(maxTop, Math.max(8, Math.round(top)));

        panel.style.left = `${finalLeft}px`;
        panel.style.top = `${finalTop}px`;
        panel.style.right = 'auto';
        panel.style.bottom = 'auto';
    }

    function startLauncherDrag(event) {
        if (event.button !== 0) {
            return;
        }

        const launcher = document.getElementById(LAUNCHER_ID);
        if (!launcher) {
            return;
        }

        const rect = launcher.getBoundingClientRect();
        state.launcherDragState = {
            offsetX: event.clientX - rect.left,
            offsetY: event.clientY - rect.top,
            startX: event.clientX,
            startY: event.clientY,
            moved: false
        };

        document.addEventListener('pointermove', onLauncherDrag);
        document.addEventListener('pointerup', stopLauncherDrag);
        document.body.style.userSelect = 'none';
    }

    function onLauncherDrag(event) {
        if (!state.launcherDragState) {
            return;
        }

        const launcher = document.getElementById(LAUNCHER_ID);
        if (!launcher) {
            return;
        }

        if (Math.abs(event.clientX - state.launcherDragState.startX) > 3 || Math.abs(event.clientY - state.launcherDragState.startY) > 3) {
            state.launcherDragState.moved = true;
        }

        const left = event.clientX - state.launcherDragState.offsetX;
        const top = event.clientY - state.launcherDragState.offsetY;
        applyLauncherPosition(left, top);
    }

    function stopLauncherDrag() {
        const launcher = document.getElementById(LAUNCHER_ID);
        if (launcher && state.launcherDragState) {
            const rect = launcher.getBoundingClientRect();
            state.settings.launcherPosition = {
                left: Math.round(rect.left),
                top: Math.round(rect.top)
            };
            saveSettings();

            if (state.launcherDragState.moved) {
                state.launcherClickSuppress = true;
                window.setTimeout(function () {
                    state.launcherClickSuppress = false;
                }, 80);
            }
        }

        state.launcherDragState = null;
        document.removeEventListener('pointermove', onLauncherDrag);
        document.removeEventListener('pointerup', stopLauncherDrag);
        document.body.style.userSelect = '';
    }

    function applyLauncherPosition(left, top) {
        const launcher = document.getElementById(LAUNCHER_ID);
        if (!launcher) {
            return;
        }

        const maxLeft = Math.max(8, window.innerWidth - launcher.offsetWidth - 8);
        const maxTop = Math.max(8, window.innerHeight - launcher.offsetHeight - 8);
        const finalLeft = Math.min(maxLeft, Math.max(8, Math.round(left)));
        const finalTop = Math.min(maxTop, Math.max(8, Math.round(top)));

        launcher.style.left = `${finalLeft}px`;
        launcher.style.top = `${finalTop}px`;
        launcher.style.right = 'auto';
        launcher.style.bottom = 'auto';
    }

    function escapeHtml(text) {
        return String(text || '')
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function formatPoints(points, withSign) {
        const value = Number(points) || 0;
        if (withSign && value > 0) {
            return `+${value}`;
        }
        return String(value);
    }

    function pruneStats() {
        if (!state.stats) {
            return;
        }
        const now = Date.now();
        const historyCutoff = now - 90 * DAY_MS;
        const thankCutoff = now - Math.max(7 * DAY_MS, normalizeThankLimitWindow(state.settings.thankLimitWindowMinutes) * 60 * 1000);
        state.stats.history = state.stats.history
            .filter((item) => item && item.time >= historyCutoff)
            .slice(-5000);
        state.stats.thanksHistory = state.stats.thanksHistory
            .filter((item) => item && item.time >= thankCutoff)
            .slice(-2000);
    }

    function getStatsSummary() {
        pruneStats();
        const now = Date.now();
        const last7Days = state.stats.history.filter((item) => now - Number(item.time || 0) <= 7 * DAY_MS);
        return {
            last7DaysPoints: last7Days.reduce((sum, item) => sum + (Number(item.points) || 0), 0),
            last7DaysCount: last7Days.length,
            totalPoints: Number(state.stats.totalPoints) || 0,
            totalCount: Number(state.stats.totalCount) || 0
        };
    }

    function recordGrab(message, points) {
        const oId = String(message.oId || '');
        if (!oId) {
            return;
        }
        const exists = state.stats.history.some((item) => String(item.oId) === oId);
        if (exists) {
            return;
        }

        state.stats.totalPoints = (Number(state.stats.totalPoints) || 0) + points;
        state.stats.totalCount = (Number(state.stats.totalCount) || 0) + 1;
        state.stats.history.push({
            oId: oId,
            time: Date.now(),
            points: points,
            senderId: String(message.packet && message.packet.senderId || ''),
            senderName: String(message.raw && message.raw.userName || ''),
            type: String(message.packet && message.packet.type || '')
        });
        saveStats();
        renderPanelBody();
    }

    function getSenderKey(message) {
        if (!message) {
            return '';
        }
        return String(message.packet && message.packet.senderId || message.raw && message.raw.userName || '');
    }

    function canSendThankYou(message) {
        if (!state.settings.thankLimitEnabled) {
            return {allowed: true, reason: ''};
        }

        pruneStats();
        const senderKey = getSenderKey(message);
        const windowMinutes = normalizeThankLimitWindow(state.settings.thankLimitWindowMinutes);
        const maxPerUser = normalizeThankLimitMax(state.settings.thankLimitMaxPerUser);
        const windowMs = windowMinutes * 60 * 1000;
        const now = Date.now();
        const sentCount = state.stats.thanksHistory.filter((item) => String(item.senderId) === senderKey && now - Number(item.time || 0) <= windowMs).length;
        if (sentCount >= maxPerUser) {
            return {
                allowed: false,
                reason: `命中感谢频控：同一人 ${windowMinutes} 分钟内仅感谢前 ${maxPerUser} 次`
            };
        }
        return {allowed: true, reason: ''};
    }

    function recordThankYou(message) {
        state.stats.thanksHistory.push({
            oId: String(message.oId || ''),
            time: Date.now(),
            senderId: getSenderKey(message),
            senderName: String(message.raw && message.raw.userName || '')
        });
        saveStats();
    }

    function formatThankTemplate(points, message) {
        const senderName = String(message && message.raw && message.raw.userName || '老板');
        return normalizeThankTemplate(state.settings.thankTemplate)
            .replace(/\{points\}/g, String(points))
            .replace(/\{user\}/g, senderName);
    }

    function buildThankYouContent(points, message) {
        return `${formatThankTemplate(points, message)}\n\n${OFFICIAL_EXTENSION_QUOTE}`;
    }

    function detectManualGrab() {

        document.addEventListener('click', function (event) {
            const trigger = event.target.closest('.hongbao__item, .hongbao__finger_guessing_icon');
            if (!trigger) {
                return;
            }

            const item = event.target.closest('[id^="chatroom"]');
            if (!item) {
                return;
            }

            const oId = item.id.replace('chatroom', '');
            state.manualHandled.add(oId);
            cancelScheduled(oId, '检测到你手动点了红包，已取消自动抢');
        }, true);
    }

    function parseRedPacket(content) {
        if (typeof content !== 'string') {
            return null;
        }

        const normalized = content.trim().replace(/^<p>/, '').replace(/<\/p>$/, '');
        if (!normalized) {
            return null;
        }

        try {
            const packet = JSON.parse(normalized);
            if (packet && packet.msgType === 'redPacket') {
                packet.type = packet.type || 'random';
                packet.who = Array.isArray(packet.who) ? packet.who : [];
                return packet;
            }
        } catch (error) {
        }

        return null;
    }

    function normalizeReceivers(value) {
        if (Array.isArray(value)) {
            return value;
        }
        if (typeof value !== 'string' || !value.trim()) {
            return [];
        }

        try {
            const parsed = JSON.parse(value);
            return Array.isArray(parsed) ? parsed : [];
        } catch (error) {
            return [];
        }
    }

    function hasSelfOpened(packet) {
        return Array.isArray(packet.who) && packet.who.some((item) => String(item.userId) === state.currentUserId);
    }

    function isSupportedBySettings(packet) {
        if (!state.settings.enabled) {
            return false;
        }
        if (!packet || !packet.type) {
            return false;
        }
        if (String(packet.senderId) === state.currentUserId) {
            return false;
        }
        if (Number(packet.got) >= Number(packet.count)) {
            return false;
        }
        if (hasSelfOpened(packet)) {
            return false;
        }

        switch (packet.type) {
            case 'random':
                return state.settings.types.random;
            case 'average':
                return state.settings.types.average;
            case 'specify': {
                if (!state.settings.types.specify) {
                    return false;
                }
                const receivers = normalizeReceivers(packet.recivers);
                return receivers.includes(state.currentUserName);
            }
            case 'heartbeat':
                return state.settings.types.heartbeat;
            case 'rockPaperScissors':
                return state.settings.types.rockPaperScissors;
            default:
                return false;
        }
    }

    function cacheMessage(message) {
        state.messageCache.set(String(message.oId), message);
        while (state.messageCache.size > 200) {
            const oldestKey = state.messageCache.keys().next().value;
            state.messageCache.delete(oldestKey);
        }
    }

    function handleMessageData(data) {
        if (!data || !data.oId) {
            return;
        }

        const packet = parseRedPacket(data.content);
        if (!packet) {
            return;
        }

        const message = {
            oId: String(data.oId),
            packet: packet,
            raw: data
        };

        cacheMessage(message);
        scheduleIfNeeded(message);
    }

    function scheduleIfNeeded(message) {
        if (!message || !message.oId || !message.packet) {
            return;
        }

        const oId = String(message.oId);
        if (state.manualHandled.has(oId) || state.scheduled.has(oId)) {
            return;
        }

        if (!isSupportedBySettings(message.packet)) {
            return;
        }

        const delayMs = normalizeDelay(state.settings.delaySeconds) * 1000;
        const typeText = packetTypeLabel(message.packet.type);
        const timerId = window.setTimeout(function () {
            grabPacket(oId).catch((error) => {
                console.error('[ARP] 抢红包异常', error);
                updateStatus(`抢红包异常：${error.message || '未知错误'}`);
            });
        }, delayMs);

        state.scheduled.set(oId, {
            timerId: timerId,
            message: message,
            createdAt: Date.now()
        });
        updateStatus(`已加入队列：${typeText} #${oId}，${delayMs / 1000} 秒后尝试`);
    }

    function packetTypeLabel(type) {
        switch (type) {
            case 'random':
                return '拼手气红包';
            case 'average':
                return '普通红包';
            case 'specify':
                return '专属红包';
            case 'heartbeat':
                return '心跳红包';
            case 'rockPaperScissors':
                return '猜拳红包';
            default:
                return type || '红包';
        }
    }

    function cancelScheduled(oId, message) {
        const entry = state.scheduled.get(String(oId));
        if (!entry) {
            return;
        }
        window.clearTimeout(entry.timerId);
        state.scheduled.delete(String(oId));
        if (message) {
            updateStatus(message);
        }
    }

    function clearAllSchedules(message) {
        for (const entry of state.scheduled.values()) {
            window.clearTimeout(entry.timerId);
        }
        state.scheduled.clear();
        if (message) {
            updateStatus(message);
        }
    }

    function chooseGesture() {
        if (state.settings.rockPaperScissors.randomGesture) {
            return String(Math.floor(Math.random() * 3));
        }
        return state.settings.rockPaperScissors.fixedGesture;
    }

    async function postJSON(url, payload) {
        const response = await fetch(url, {
            method: 'POST',
            credentials: 'same-origin',
            headers: {
                'Content-Type': 'application/json;charset=UTF-8'
            },
            body: JSON.stringify(payload)
        });

        const contentType = response.headers.get('content-type') || '';
        if (!contentType.includes('application/json')) {
            throw new Error(`接口返回异常：${response.status}`);
        }

        return response.json();
    }

    async function getJSON(url) {
        const response = await fetch(url, {
            method: 'GET',
            credentials: 'same-origin'
        });

        const contentType = response.headers.get('content-type') || '';
        if (!contentType.includes('application/json')) {
            throw new Error(`接口返回异常：${response.status}`);
        }

        return response.json();
    }

    function findSelfRecord(whoList) {
        if (!Array.isArray(whoList)) {
            return null;
        }
        return whoList.find((item) => String(item.userId) === state.currentUserId) || null;
    }

    async function grabPacket(oId) {
        const entry = state.scheduled.get(String(oId));
        state.scheduled.delete(String(oId));
        if (!entry) {
            return;
        }

        const packet = entry.message.packet;
        if (!isSupportedBySettings(packet)) {
            updateStatus(`已跳过红包 #${oId}：当前设置不允许抢这个类型`);
            return;
        }
        if (state.manualHandled.has(String(oId))) {
            updateStatus(`已跳过红包 #${oId}：你已经手动处理过了`);
            return;
        }

        const payload = {oId: String(oId)};
        if (packet.type === 'rockPaperScissors') {
            payload.gesture = chooseGesture();
        }

        updateStatus(`正在尝试抢红包 #${oId}...`);

        const result = await postJSON(`${state.servePath}/chat-room/red-packet/open`, payload);
        if (!result || result.code === -1) {
            const message = result && result.msg ? result.msg : '未抢到';
            updateStatus(`红包 #${oId} 失败：${message}`, `失败：#${oId}`);
            notify('error', `红包 #${oId} 未抢到：${message}`);
            return;
        }

        const selfRecord = findSelfRecord(result.who);
        if (!selfRecord || hasSelfOpened(packet)) {
            updateStatus(`红包 #${oId} 没有新增收益`, `未新增：#${oId}`);
            return;
        }

        const points = Number(selfRecord.userMoney || 0);
        const typeText = packetTypeLabel(packet.type);
        const grabText = `${typeText} ${formatPoints(points, true)} 积分`;
        packet.who = Array.isArray(result.who) ? result.who : packet.who;
        packet.got = result.info && result.info.got !== undefined ? result.info.got : packet.got;
        cacheMessage(entry.message);
        recordGrab(entry.message, points);

        updateStatus(`抢到红包：${grabText}`, grabText);
        notify(points >= 0 ? 'success' : 'error', `抢到 ${formatPoints(points, true)} 积分（${typeText}）`);

        if (state.settings.autoReplyEnabled && points > 0) {
            const thankDecision = canSendThankYou(entry.message);
            if (thankDecision.allowed) {
                await sendThankYou(entry.message, points);
            } else {
                updateStatus(`抢到红包：${grabText}；${thankDecision.reason}`, grabText);
            }
        }
    }

    async function sendThankYou(message, points) {
        const content = buildThankYouContent(points, message);
        const previewText = formatThankTemplate(points, message);
        const client = String(pageWindow.thisClient || (pageWindow.ChatRoom && pageWindow.ChatRoom.isMobile && pageWindow.ChatRoom.isMobile() ? 'Mobile/移动网页端' : 'Web/PC网页端'));

        try {
            const result = await postJSON(`${state.servePath}/chat-room/send`, {
                content: content,
                client: client
            });

            if (result && result.code === 0) {
                recordThankYou(message);
                updateStatus(`已自动发送致谢：${previewText}`);
                return;
            }

            const messageText = result && result.msg ? result.msg : '发送失败';
            updateStatus(`致谢发送失败：${messageText}`);
        } catch (error) {
            updateStatus(`致谢发送失败：${error.message || '未知错误'}`);
        }
    }

    async function scanRecentMessages(showNotice) {

        if (!ensureUserContext()) {
            updateStatus('未检测到登录态，无法自动抢红包');
            return;
        }

        const ids = Array.from(document.querySelectorAll('[id^="chatroom"]'))
            .map((node) => Number(String(node.id).replace('chatroom', '')))
            .filter((id) => Number.isFinite(id) && id > 0);

        if (!ids.length) {
            updateStatus('当前页面还没有可扫描的聊天记录');
            return;
        }

        const latestOId = String(Math.max.apply(null, ids));
        const size = Math.max(40, ids.length + 5);
        try {
            const result = await getJSON(`${state.servePath}/chat-room/getMessage?size=${size}&mode=1&type=md&oId=${latestOId}`);
            if (!result || !Array.isArray(result.data)) {
                updateStatus('扫描失败：聊天记录接口返回异常');
                return;
            }

            result.data.forEach(handleMessageData);
            if (showNotice) {
                notify('success', '已重新扫描当前聊天室红包');
            }
            updateStatus(`扫描完成，当前等待队列 ${state.scheduled.size} 个`);
        } catch (error) {
            console.error('[ARP] 扫描聊天记录失败', error);
            updateStatus(`扫描失败：${error.message || '未知错误'}`);
        }
    }

    function patchRenderMsg() {
        if (state.renderPatched || !pageWindow.ChatRoom || typeof pageWindow.ChatRoom.renderMsg !== 'function') {
            return;
        }

        const original = pageWindow.ChatRoom.renderMsg;
        pageWindow.ChatRoom.renderMsg = function () {
            try {
                handleMessageData(arguments[0]);
            } catch (error) {
                console.error('[ARP] 解析红包消息失败', error);
            }
            return original.apply(this, arguments);
        };
        state.renderPatched = true;
        updateStatus('已接管聊天室消息流，等待红包出现');
    }

    function applySettingsChange() {
        renderPanelBody();
        clearAllSchedules();
        for (const message of state.messageCache.values()) {
            scheduleIfNeeded(message);
        }
        updateStatus(state.settings.enabled ? `设置已更新，当前等待队列 ${state.scheduled.size} 个` : '自动抢红包已关闭');
    }

    function bootstrapWhenReady() {
        createUI();
        detectManualGrab();

        const tryReady = function () {
            createUI();
            if (!ensureUserContext() || !pageWindow.ChatRoom || typeof pageWindow.ChatRoom.renderMsg !== 'function') {
                updateStatus('等待聊天室脚本加载...');
                return;
            }

            patchRenderMsg();
            scanRecentMessages(false);
            if (state.readyTimer) {
                window.clearInterval(state.readyTimer);
                state.readyTimer = null;
            }
        };

        tryReady();
        if (!state.readyTimer) {
            state.readyTimer = window.setInterval(tryReady, 1000);
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', bootstrapWhenReady, {once: true});
    } else {
        bootstrapWhenReady();
    }
})();
