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
const isDarkModeInSystem = window.matchMedia("(prefers-color-scheme: dark)").matches;
const mode = localStorage.getItem('color-scheme') || 'auto';
DarkReader.setFetchMethod(window.fetch)
setColorMode(mode);
function toggleColorMode() {
  let mode = localStorage.getItem('color-scheme'); // light / dark / auto
  if (mode === 'dark') {
    mode = isDarkModeInSystem ? 'light' : 'auto';
  } else if (mode === 'light') {
    mode = !isDarkModeInSystem ? 'dark' : 'auto';
  } else {
    mode = isDarkModeInSystem ? 'light' : 'dark';
  }
  setColorMode(mode)
}

function setColorMode(mode) {
    const isDark = mode == 'dark' || (mode == 'auto' && isDarkModeInSystem);
    if (isDark) {
        DarkReader.enable({
            brightness: 100,
            contrast: 90,
            sepia: 10
        });
    } else {
        DarkReader.auto(false);
    }
    localStorage.setItem('color-scheme', mode);
    updateColorModeButtons(isDark);
}

function updateColorModeButtons(isDark) {
    const headerButton = document.querySelector('#color-mode svg use');
    headerButton?.setAttribute('xlink:href', isDark ? '#color-moon' : '#color-sun');
    document.querySelectorAll('[data-color-mode-toggle]').forEach((button) => {
        const icon = button.querySelector('svg use');
        icon?.setAttribute('xlink:href', isDark ? '#color-sun' : '#color-moon');
        const label = isDark ? '白天模式' : '黑夜模式';
        button.setAttribute('aria-label', label);
        button.setAttribute('title', label);
    });
}

document.addEventListener("DOMContentLoaded", () => {
    const colorModeButton = document.querySelector('#color-mode');
    if (colorModeButton) {
        colorModeButton.addEventListener('click', toggleColorMode);
    }
    updateColorModeButtons(localStorage.getItem('color-scheme') == 'dark' || (localStorage.getItem('color-scheme') == 'auto' && isDarkModeInSystem));
});
