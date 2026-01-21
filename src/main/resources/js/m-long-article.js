/**
 * Mobile long article reading page functionality.
 */
window.MLongArticle = {
    settings: {
        theme: 'light',
        fontSize: 16
    },

    init: function() {
        this.loadSettings();
        this.bindEvents();
        this.applySettings();
    },

    loadSettings: function() {
        var saved = localStorage.getItem('mLongArticleSettings');
        if (saved) {
            try {
                this.settings = JSON.parse(saved);
            } catch (e) {
                console.error('Failed to parse settings', e);
            }
        }
    },

    saveSettings: function() {
        localStorage.setItem('mLongArticleSettings', JSON.stringify(this.settings));
    },

    applySettings: function() {
        var body = document.body;
        var content = document.querySelector('.m-long-article-content');

        if (this.settings.theme === 'night') {
            body.classList.add('night');
        } else {
            body.classList.remove('night');
        }

        if (content) {
            content.style.fontSize = this.settings.fontSize + 'px';
        }
        var fontSizeEl = document.getElementById('mFontSizeValue');
        if (fontSizeEl) {
            fontSizeEl.textContent = this.settings.fontSize + 'px';
        }
    },

    bindEvents: function() {
        var self = this;

        document.addEventListener('click', function(e) {
            var panel = document.getElementById('mSettingsPanel');
            var toggle = document.querySelector('.m-action-btn[onclick*="openSettings"]');
            if (panel && panel.classList.contains('active')) {
                if (!panel.contains(e.target) && (!toggle || !toggle.contains(e.target))) {
                    panel.classList.remove('active');
                }
            }
        });
    },

    openSettings: function() {
        var panel = document.getElementById('mSettingsPanel');
        if (panel) {
            panel.classList.toggle('active');
        }
    },

    setTheme: function(theme) {
        this.settings.theme = theme;
        this.saveSettings();
        this.applySettings();

        var lightBtn = document.querySelector('.m-theme-btn.light');
        var nightBtn = document.querySelector('.m-theme-btn.night');
        if (lightBtn) lightBtn.classList.toggle('active', theme === 'light');
        if (nightBtn) nightBtn.classList.toggle('active', theme === 'night');
    },

    setFontSize: function(delta) {
        var newSize = this.settings.fontSize + delta;
        if (newSize >= 12 && newSize <= 24) {
            this.settings.fontSize = newSize;
            this.saveSettings();
            this.applySettings();
        }
    },

    toggleNight: function() {
        this.setTheme(this.settings.theme === 'night' ? 'light' : 'night');
    }
};
