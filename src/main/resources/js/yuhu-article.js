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

function changeNight(){
    let isNight = localStorage.getItem('isNight');
    if(isNight != null){
        localStorage.removeItem('isNight')
    }else{

        localStorage.setItem('isNight','true');
    }
    loadLocalSetting();
}

function loadLocalSetting(){
    let isNight = !!localStorage.getItem('isNight');
    if(isNight){
        $('.article').addClass('night')
        $('.right-nav-item .night').hide();
        $('.right-nav-item .day').show();
        $('.day-night-label').html("日间");
    }else{
        $('.article').removeClass('night')
        $('.right-nav-item .night').show();
        $('.right-nav-item .day').hide();
        $('.day-night-label').html("夜间");
    }
}

$(document).ready(function () {
    // 监听滚动
    listenScroll();
    loadLocalSetting();
})
