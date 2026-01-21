/**
 * 长文章阅读设置控制器
 */
var LongArticle = {
    STORAGE_KEY: 'longArticleSettings',
    defaultSettings: {
        theme: 'light',
        fontSize: 18
    },

    init: function () {
        this.loadSettings();
        this.bindEvents();
        this.applySettings();
    },

    loadSettings: function () {
        try {
            var saved = localStorage.getItem(this.STORAGE_KEY);
            if (saved) {
                this.settings = JSON.parse(saved);
            } else {
                this.settings = Object.assign({}, this.defaultSettings);
            }
        } catch (e) {
            this.settings = Object.assign({}, this.defaultSettings);
        }
    },

    saveSettings: function () {
        try {
            localStorage.setItem(this.STORAGE_KEY, JSON.stringify(this.settings));
        } catch (e) {
            console.warn('无法保存阅读设置:', e);
        }
    },

    bindEvents: function () {
        var self = this;

        // 点击外部关闭设置面板
        $(document).on('click', function (e) {
            if (!$(e.target).closest('.long-article-settings').length &&
                !$(e.target).closest('.long-article-panel').length) {
                $('#longArticlePanel').removeClass('show');
            }
        });
    },

    applySettings: function () {
        // 应用主题
        this.setTheme(this.settings.theme, false);

        // 应用字号
        this.setFontSize(0, false);
    },

    toggleSettings: function () {
        $('#longArticlePanel').toggleClass('show');
    },

    toggleNight: function () {
        var newTheme = this.settings.theme === 'light' ? 'night' : 'light';
        this.setTheme(newTheme);
    },

    setTheme: function (theme, save) {
        if (save === undefined) save = true;

        this.settings.theme = theme;

        if (theme === 'night') {
            $('body').addClass('night');
        } else {
            $('body').removeClass('night');
        }

        // 更新按钮状态
        $('.long-article-theme-btn').removeClass('active');
        $('.long-article-theme-btn').each(function () {
            if ($(this).text().indexOf(theme === 'light' ? '浅色' : '深色') !== -1) {
                $(this).addClass('active');
            }
        });

        if (save) {
            this.saveSettings();
        }
    },

    setFontSize: function (delta, save) {
        if (save === undefined) save = true;

        var currentSize = this.settings.fontSize;
        var newSize = currentSize + delta;

        if (newSize < 12) newSize = 12;
        if (newSize > 28) newSize = 28;

        this.settings.fontSize = newSize;

        // 应用字号到长文章内容
        $('.long-article-content').css('font-size', newSize + 'px');

        // 更新显示
        $('#longArticleFontSize').text(newSize + 'px');

        if (save) {
            this.saveSettings();
        }
    },

    scrollToTop: function () {
        $('html, body').animate({
            scrollTop: 0
        }, 300);
    }
};
