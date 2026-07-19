/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
/**
 * Long article reading page functionality.
 */
window.LongArticle = {
    storageKey: 'longArticleSettings',
    allowedWidths: ['auto', '600', '800', '1000', '1200'],
    settings: {
        width: '800',
        desktopFontSize: 18,
        mobileFontSize: 16
    },

    init: function () {
        if (this.initialized) {
            return;
        }
        this.initialized = true;
        this.loadSettings();
        this.bindEvents();
        this.applySettings();
        this.formatParagraphIndentation();
        this.updateTopButton();
        if (this.shouldOpenCommentsFromLocation()) {
            this.openComments();
            this.focusLocationComment();
        }
    },

    loadSettings: function () {
        var settings = {
            width: '800',
            desktopFontSize: 18,
            mobileFontSize: 16
        };
        var saved;

        try {
            saved = JSON.parse(window.localStorage.getItem(this.storageKey) || '{}');
        } catch (e) {
            saved = {};
        }

        if (saved && typeof saved === 'object') {
            settings.width = this.normalizeWidth(saved.width);
            settings.desktopFontSize = this.normalizeFontSize(saved.desktopFontSize || saved.fontSize, 18, 32, 18);
            settings.mobileFontSize = this.normalizeFontSize(saved.mobileFontSize, 16, 24, 16);
        }

        try {
            var legacyFontSize = parseInt(window.localStorage.getItem('longArticleFontSize'), 10);
            if (!saved.desktopFontSize && !saved.fontSize && !isNaN(legacyFontSize)) {
                settings.desktopFontSize = this.normalizeFontSize(legacyFontSize, 18, 32, 18);
                settings.mobileFontSize = this.normalizeFontSize(legacyFontSize, 16, 24, 16);
            }
            var mobileSettings = JSON.parse(window.localStorage.getItem('mLongArticleSettings') || '{}');
            if (!saved.mobileFontSize && mobileSettings && !isNaN(parseInt(mobileSettings.fontSize, 10))) {
                settings.mobileFontSize = this.normalizeFontSize(parseInt(mobileSettings.fontSize, 10), 16, 24, 16);
            }
            window.localStorage.removeItem('longArticleFontSize');
            window.localStorage.removeItem('mLongArticleSettings');
        } catch (e) {
            // Ignore unavailable or malformed legacy storage.
        }

        this.settings = settings;
        this.saveSettings();
    },

    normalizeWidth: function (width) {
        width = String(width || '800');
        return this.allowedWidths.indexOf(width) >= 0 ? width : '800';
    },

    normalizeFontSize: function (size, min, max, fallback) {
        size = parseInt(size, 10);
        if (isNaN(size)) {
            return fallback;
        }
        return Math.max(min, Math.min(max, size));
    },

    formatParagraphIndentation: function () {
        document.querySelectorAll('.long-article-content > p').forEach(function (paragraph) {
            var hasDirectBreak = Array.prototype.some.call(paragraph.childNodes, function (node) {
                return node.nodeType === 1 && node.tagName === 'BR';
            });
            if (!hasDirectBreak || paragraph.querySelector('img, picture, video, iframe')) {
                return;
            }

            var fragment = document.createDocumentFragment();
            var line = document.createElement('span');
            line.className = 'long-article-paragraph-line';
            Array.prototype.slice.call(paragraph.childNodes).forEach(function (node) {
                if (node.nodeType === 1 && node.tagName === 'BR') {
                    fragment.appendChild(line);
                    line = document.createElement('span');
                    line.className = 'long-article-paragraph-line';
                } else {
                    line.appendChild(node);
                }
            });
            fragment.appendChild(line);
            paragraph.textContent = '';
            paragraph.classList.add('long-article-paragraph-lines');
            paragraph.appendChild(fragment);
        });
    },

    saveSettings: function () {
        try {
            window.localStorage.setItem(this.storageKey, JSON.stringify(this.settings));
        } catch (e) {
            // Ignore unavailable storage.
        }
    },

    applySettings: function () {
        var root = document.documentElement;
        var width = this.settings.width;
        var halfWidth = width === 'auto' ? 'min(40vw, 600px)' : (parseInt(width, 10) / 2) + 'px';

        root.style.setProperty('--long-article-width', width === 'auto' ? 'min(80vw, 1200px)' : width + 'px');
        root.style.setProperty('--long-article-half-width', halfWidth);
        root.style.setProperty('--long-article-font-size', this.settings.desktopFontSize + 'px');
        this.updateWidthButtons();
        this.updateLayoutMetrics();
    },

    bindEvents: function () {
        var self = this;
        var toolbar = document.querySelector('[data-long-article-toolbar]');
        if (!toolbar) {
            return;
        }

        toolbar.addEventListener('click', function (event) {
            var actionElement = self.findActionElement(event.target, toolbar);
            if (!actionElement) {
                return;
            }
            var action = actionElement.getAttribute('data-long-article-action');
            if (action === 'top') {
                self.scrollToTop();
            } else if (action === 'comments') {
                if (window.LongArticleParagraphComments && window.LongArticleParagraphComments.getActiveParagraphId()) {
                    window.LongArticleParagraphComments.showChapterComments();
                    self.openComments();
                } else {
                    self.toggleComments();
                }
            } else if (action === 'font-decrease') {
                self.setFontSize(-2);
            } else if (action === 'font-increase') {
                self.setFontSize(2);
            } else if (action === 'layout') {
                self.toggleLayoutPanel();
            } else if (actionElement.hasAttribute('data-long-article-width')) {
                self.setWidth(actionElement.getAttribute('data-long-article-width'));
            }
        });

        document.addEventListener('click', function (event) {
            var panel = document.getElementById('longArticleLayoutPanel');
            var layoutButton = toolbar.querySelector('[data-long-article-action="layout"]');
            if (panel && panel.classList.contains('is-open') && !panel.contains(event.target) && !layoutButton.contains(event.target)) {
                self.closeLayoutPanel();
            }
            if (event.target.closest && event.target.closest('[data-long-article-comments-close]')) {
                self.closeComments();
            }
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && self.isCommentsOpen()) {
                self.closeComments();
            }
        });

        window.addEventListener('resize', function () {
            self.updateLayoutMetrics();
        });
        window.addEventListener('scroll', function () {
            self.updateTopButton();
        }, {passive: true});
    },

    findActionElement: function (target, boundary) {
        while (target && target !== boundary) {
            if (target.nodeType === 1 && (target.hasAttribute('data-long-article-action') || target.hasAttribute('data-long-article-width'))) {
                return target;
            }
            target = target.parentNode;
        }
        return target && target !== boundary && target.nodeType === 1 ? target : null;
    },

    setFontSize: function (delta) {
        this.settings.desktopFontSize = this.normalizeFontSize(this.settings.desktopFontSize + delta, 12, 32, 18);
        this.saveSettings();
        this.applySettings();
    },

    setWidth: function (width) {
        this.settings.width = this.normalizeWidth(width);
        this.saveSettings();
        this.applySettings();
        this.closeLayoutPanel();
    },

    updateWidthButtons: function () {
        var width = this.settings.width;
        document.querySelectorAll('[data-long-article-width]').forEach(function (button) {
            button.classList.toggle('is-active', button.getAttribute('data-long-article-width') === width);
        });
        var fontSize = document.querySelector('[data-long-article-font-size]');
        if (fontSize) {
            fontSize.textContent = this.settings.desktopFontSize + 'px';
        }
    },

    updateTopButton: function () {
        var button = document.querySelector('[data-long-article-action="top"]');
        if (button) {
            button.classList.toggle('is-visible', (window.pageYOffset || document.documentElement.scrollTop || 0) > 320);
        }
    },

    toggleLayoutPanel: function () {
        var panel = document.getElementById('longArticleLayoutPanel');
        var button = document.querySelector('[data-long-article-action="layout"]');
        if (!panel || !button) {
            return;
        }
        var open = !panel.classList.contains('is-open');
        panel.classList.toggle('is-open', open);
        panel.setAttribute('aria-hidden', open ? 'false' : 'true');
        button.setAttribute('aria-expanded', open ? 'true' : 'false');
    },

    closeLayoutPanel: function () {
        var panel = document.getElementById('longArticleLayoutPanel');
        var button = document.querySelector('[data-long-article-action="layout"]');
        if (panel) {
            panel.classList.remove('is-open');
            panel.setAttribute('aria-hidden', 'true');
        }
        if (button) {
            button.setAttribute('aria-expanded', 'false');
        }
    },

    updateLayoutMetrics: function () {
        var root = document.documentElement;
        var viewportWidth = document.documentElement.clientWidth || window.innerWidth || 0;
        var selectedWidth = this.settings.width === 'auto' ? Math.min(viewportWidth * 0.8, 1200) : parseInt(this.settings.width, 10);
        var closedArticleWidth = Math.min(selectedWidth, Math.max(320, viewportWidth - 28));
        var drawerWidth = viewportWidth <= 768
            ? Math.max(280, viewportWidth - 20)
            : 320;
        var articleDrawerGap = 0;
        var toolbarGap = 14;
        var toolbarWidth = 64;
        var safeMargin = 14;
        var inline = viewportWidth >= 960;
        var openArticleWidth = closedArticleWidth;

        if (inline) {
            var maxInlineStageWidth = viewportWidth - (toolbarWidth + toolbarGap + safeMargin) * 2;
            var maxInlineArticleWidth = maxInlineStageWidth - drawerWidth - articleDrawerGap;
            openArticleWidth = Math.min(closedArticleWidth, Math.max(480, maxInlineArticleWidth));
            inline = openArticleWidth >= 480;
        }

        var stageWidth = inline ? openArticleWidth + articleDrawerGap + drawerWidth : closedArticleWidth;
        var stageLeft = Math.max(safeMargin, (viewportWidth - stageWidth) / 2);
        var drawerLeft = inline ? stageLeft + openArticleWidth + articleDrawerGap : viewportWidth - drawerWidth;
        var closedToolbarLeft = Math.min(viewportWidth - toolbarWidth - safeMargin, (viewportWidth - closedArticleWidth) / 2 + closedArticleWidth + toolbarGap);
        var openToolbarLeft = inline ? stageLeft + stageWidth + toolbarGap : closedToolbarLeft;

        root.style.setProperty('--long-article-stage-width', stageWidth + 'px');
        root.style.setProperty('--long-article-stage-left', stageLeft + 'px');
        root.style.setProperty('--long-article-open-article-width', openArticleWidth + 'px');
        document.body.style.setProperty('--long-article-drawer-width', drawerWidth + 'px');
        root.style.setProperty('--long-article-drawer-left', drawerLeft + 'px');
        root.style.setProperty('--long-article-toolbar-closed-left', closedToolbarLeft + 'px');
        root.style.setProperty('--long-article-toolbar-open-left', openToolbarLeft + 'px');
        document.body.classList.toggle('long-article-comments-inline', inline);
    },

    scrollToTop: function () {
        window.scrollTo({top: 0, behavior: 'smooth'});
    },

    getCommentsPanel: function () {
        return document.getElementById('articleCommentsPanel');
    },

    isCommentsOpen: function () {
        return document.body.classList.contains('long-article-comments-open');
    },

    toggleComments: function () {
        if (this.isCommentsOpen()) {
            this.closeComments();
        } else {
            this.openComments();
        }
    },

    openComments: function () {
        var panel = this.getCommentsPanel();
        var button = document.querySelector('[data-long-article-action="comments"]');
        if (!panel) {
            return;
        }
        this.closeLayoutPanel();
        this.updateLayoutMetrics();
        document.body.classList.add('long-article-comments-open');
        panel.setAttribute('aria-hidden', 'false');
        if (button) {
            button.classList.add('is-active');
            button.setAttribute('aria-expanded', 'true');
        }
    },

    closeComments: function () {
        var panel = this.getCommentsPanel();
        var button = document.querySelector('[data-long-article-action="comments"]');
        var editorPanel = document.querySelector('.editor-panel');
        if (editorPanel && window.getComputedStyle(editorPanel).display !== 'none' && window.Comment && typeof window.Comment._hideReplyPanel === 'function') {
            window.Comment._hideReplyPanel();
        }
        if (window.LongArticleParagraphComments && window.LongArticleParagraphComments.getActiveParagraphId()) {
            window.LongArticleParagraphComments.showChapterComments(true, false);
        }
        document.body.classList.remove('long-article-comments-open');
        if (panel) {
            panel.setAttribute('aria-hidden', 'true');
        }
        if (button) {
            button.classList.remove('is-active');
            button.setAttribute('aria-expanded', 'false');
        }
    },

    shouldOpenCommentsFromLocation: function () {
        var panel = this.getCommentsPanel();
        var hash = window.location.hash.replace(/^#/, '');
        var target = hash ? document.getElementById(hash) : null;
        if (panel && target && panel.contains(target)) {
            return true;
        }
        return /(?:\?|&)(?:p|m|sort|author|paragraph)=/.test(window.location.search);
    },

    focusLocationComment: function () {
        var self = this;
        window.setTimeout(function () {
            var hash = window.location.hash.replace(/^#/, '');
            var target = hash ? document.getElementById(hash) : null;
            var panel = self.getCommentsPanel();
            if (panel && target && panel.contains(target)) {
                target.scrollIntoView({block: 'center'});
            }
        }, 0);
    }
};
