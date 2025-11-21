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
 * @fileoverview article page and add comment.
 *
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @version 1.43.0.3, Apr 30, 2020
 */
let isNight = false;
let pageWidth = 800;
let fontSize = 18;

function scrollToTop() {
    window.scrollTo({
        top: 0,
        behavior: 'smooth'
    });
}

function listenScroll() {
    document.addEventListener('scroll', function () {
        const scrollY = window.scrollY || document.documentElement.scrollTop;
        const scrollThreshold = 300;
        if (scrollY > scrollThreshold) {
            // 显示按钮
            $('.top').show(200);
        } else {
            $('.top').hide(200);
        }
    })
}

function toggleNight() {
    isNight = !isNight;
    changeNight();
}

function changeNight() {
    if (isNight) {
        $('.article').removeClass().addClass('article night');
        $('.right-nav-item .night').hide();
        $('.right-nav-item .day').show();
        $('.day-night-label').html("日间");
        localStorage.setItem("isNight", "true");
    } else {
        $('.article').removeClass().addClass('article');
        $('.right-nav-item .night').show();
        $('.right-nav-item .day').hide();
        $('.day-night-label').html("夜间");
        localStorage.removeItem("isNight");
    }
    $('.color-item').removeClass('active');
    $(`.color-item.${isNight ? 'night' : 'light'}`).addClass('active');
}

function loadLocalSetting() {
    isNight = !!localStorage.getItem('isNight');
    changeNight();
    pageWidth = parseInt(localStorage.getItem("pageWidth") ?? "800");
    changeWidth(pageWidth);
    fontSize = parseInt(localStorage.getItem("fontSize") ?? "18");
    changeFontSize(0);
}

function changeTheme(theme) {
    $('.color-item').removeClass('active');
    $(`.color-item.${theme}`).addClass('active');
    switch (theme) {
        case "night":
            isNight = true;
            changeNight();
            break;
        default:
            isNight = false;
            changeNight();
            break;
    }
}

function changeWidth(width) {
    $('.page-width').removeClass('active');
    $(`.page-width.w-${width}`).addClass('active');
    document.documentElement.style.setProperty('--article-body-width', width === 0 ? '80%' : width + 'px');
    localStorage.setItem("pageWidth", width);
}

function changeFontSize(size) {
    fontSize += size;
    $('.font-size-input').html(fontSize);
    document.documentElement.style.setProperty('--article-font-size', fontSize + 'px');
    localStorage.setItem("fontSize",fontSize);
}

function openSetting() {
    $('.setting-box').toggle(200);
}

function listenDocumentClick() {
    $(document).click(function (e) {
        if ($(e.target).closest('.setting-box,.right-nav-item').length === 0) {
            $('.setting-box').hide(200);
        }
    });
}


$(document).ready(function () {
    // 监听滚动
    listenScroll();
    loadLocalSetting();
    listenDocumentClick();
})
