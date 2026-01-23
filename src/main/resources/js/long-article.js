/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
/**
 * Long article reading page functionality.
 */
window.LongArticle = {
    settings: {
        theme: 'light',
        fontSize: 18,
        width: 900
    },

    init: function() {
        this.loadSettings();
        this.bindEvents();
        this.applySettings();
    },

    loadSettings: function() {
        var saved = localStorage.getItem('longArticleSettings');
        if (saved) {
            try {
                this.settings = JSON.parse(saved);
            } catch (e) {
                console.error('Failed to parse settings', e);
            }
        }
    },

    saveSettings: function() {
        localStorage.setItem('longArticleSettings', JSON.stringify(this.settings));
    },

    applySettings: function() {
        var body = document.body;
        var container = document.querySelector('.long-article-container');
        var content = document.querySelector('.long-article-content');

        // Theme
        if (this.settings.theme === 'night') {
            body.classList.add('night');
        } else {
            body.classList.remove('night');
        }

        // Font size
        if (content) {
            content.style.fontSize = this.settings.fontSize + 'px';
        }
        document.getElementById('fontSizeValue').textContent = this.settings.fontSize + 'px';

        // Width
        if (container) {
            container.style.width = this.settings.width + 'px';
        }
        document.documentElement.style.setProperty('--la-content-width', this.settings.width + 'px');
    },

    bindEvents: function() {
        var self = this;

        // Close settings panel when clicking outside
        document.addEventListener('click', function(e) {
            var panel = document.getElementById('settingsPanel');
            var toggle = document.querySelector('.sidebar-item[onclick*="openSettings"]');
            if (panel && panel.classList.contains('active')) {
                if (!panel.contains(e.target) && (!toggle || !toggle.contains(e.target))) {
                    panel.classList.remove('active');
                }
            }
        });
    },

    openSettings: function() {
        var panel = document.getElementById('settingsPanel');
        if (panel) {
            panel.classList.toggle('active');
        }
    },

    setTheme: function(theme) {
        this.settings.theme = theme;
        this.saveSettings();
        this.applySettings();

        // Update active state
        var lightBtn = document.querySelector('.theme-btn.light');
        var nightBtn = document.querySelector('.theme-btn.night');
        if (lightBtn) lightBtn.classList.toggle('active', theme === 'light');
        if (nightBtn) nightBtn.classList.toggle('active', theme === 'night');
    },

    setFontSize: function(delta) {
        var newSize = this.settings.fontSize + delta;
        if (newSize >= 12 && newSize <= 32) {
            this.settings.fontSize = newSize;
            this.saveSettings();
            this.applySettings();
        }
    },

    setWidth: function(width) {
        this.settings.width = width;
        this.saveSettings();
        this.applySettings();

        // Update active state
        var widthBtns = document.querySelectorAll('.width-btn');
        widthBtns.forEach(function(btn) {
            var btnWidth = btn.getAttribute('onclick').match(/\d+/);
            if (btnWidth && parseInt(btnWidth[0]) === width) {
                btn.classList.add('active');
            } else {
                btn.classList.remove('active');
            }
        });
    },

    toggleNight: function() {
        this.setTheme(this.settings.theme === 'night' ? 'light' : 'night');
    },

    scrollToTop: function() {
        window.scrollTo({
            top: 0,
            behavior: 'smooth'
        });
    }
};
