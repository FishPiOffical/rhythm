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
                self.scrollToComments();
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
        });
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

    scrollToTop: function () {
        window.scrollTo({top: 0, behavior: 'smooth'});
    },

    scrollToComments: function () {
        var comments = document.getElementById('comments');
        if (comments) {
            comments.scrollIntoView({behavior: 'smooth'});
        }
    }
};
