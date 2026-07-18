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
 * Mobile long article reading page functionality.
 */
window.MLongArticle = {
    storageKey: 'longArticleSettings',
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
            settings.width = ['auto', '600', '800', '1000', '1200'].indexOf(String(saved.width || '800')) >= 0 ? String(saved.width || '800') : '800';
            settings.desktopFontSize = this.normalizeFontSize(saved.desktopFontSize || saved.fontSize, 18, 32, 18);
            settings.mobileFontSize = this.normalizeFontSize(saved.mobileFontSize || saved.fontSize, 16, 24, 16);
        }

        try {
            var legacyFontSize = parseInt(window.localStorage.getItem('longArticleFontSize'), 10);
            var oldSettings = JSON.parse(window.localStorage.getItem('mLongArticleSettings') || '{}');
            if (!saved.mobileFontSize && oldSettings && !isNaN(parseInt(oldSettings.fontSize, 10))) {
                settings.mobileFontSize = this.normalizeFontSize(parseInt(oldSettings.fontSize, 10), 16, 24, 16);
            } else if (!saved.mobileFontSize && !isNaN(legacyFontSize)) {
                settings.mobileFontSize = this.normalizeFontSize(legacyFontSize, 16, 24, 16);
            }
            window.localStorage.removeItem('longArticleFontSize');
            window.localStorage.removeItem('mLongArticleSettings');
        } catch (e) {
            // Ignore unavailable or malformed legacy storage.
        }

        this.settings = settings;
        this.saveSettings();
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
        document.documentElement.style.setProperty('--long-article-font-size', this.settings.mobileFontSize + 'px');
    },

    bindEvents: function () {
        var self = this;
        var toolbar = document.querySelector('[data-long-article-toolbar]');
        if (!toolbar) {
            return;
        }
        toolbar.addEventListener('click', function (event) {
            var target = event.target;
            while (target && target !== toolbar && !target.hasAttribute('data-long-article-action')) {
                target = target.parentNode;
            }
            if (!target || target === toolbar) {
                return;
            }
            var action = target.getAttribute('data-long-article-action');
            if (action === 'toggle') {
                var open = !toolbar.classList.contains('is-open');
                toolbar.classList.toggle('is-open', open);
                target.classList.toggle('active', open);
                target.setAttribute('aria-expanded', open ? 'true' : 'false');
            } else if (action === 'top') {
                window.scrollTo({top: 0, behavior: 'smooth'});
            } else if (action === 'comments') {
                var comments = document.getElementById('comments');
                if (comments) {
                    comments.scrollIntoView({behavior: 'smooth'});
                }
            } else if (action === 'font-decrease') {
                self.setFontSize(-2);
            } else if (action === 'font-increase') {
                self.setFontSize(2);
            }
        });
    },

    setFontSize: function (delta) {
        this.settings.mobileFontSize = this.normalizeFontSize(this.settings.mobileFontSize + delta, 12, 24, 16);
        this.saveSettings();
        this.applySettings();
    }
};
