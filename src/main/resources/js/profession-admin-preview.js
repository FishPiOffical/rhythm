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
/* 职业资料编辑时同步更新主题色和用户名片预览。 */
(function (window, document) {
  'use strict'

  var palettes = [
    ['#2457d6', '#edf3ff', '#102a63'], ['#0f766e', '#e8faf6', '#134e4a'], ['#9f3a58', '#fff1f5', '#6b1831'],
    ['#854d0e', '#fff7e6', '#713f12'], ['#6d3bb8', '#f5f0ff', '#42206d'], ['#0f6074', '#eaf9fc', '#164e63']
  ]

  function root() { return document.getElementById('professionAdmin') }
  function form() { return root().querySelector('#professionDefinitionForm') }
  function preview() { return root().querySelector('[data-profession-live-preview]') }

  function refresh() {
    var source = form()
    var target = preview()
    if (!target) return
    var primary = source.primaryColor.value || palettes[0][0]
    var background = source.backgroundColor.value || palettes[0][1]
    var text = source.textColor.value || palettes[0][2]
    var icon = source.imageUrl.value.trim()
    var texture = source.textureUrl.value.trim()
    target.style.setProperty('--preview-primary', primary)
    target.style.setProperty('--preview-background', background)
    target.style.setProperty('--preview-text', text)
    target.style.backgroundImage = texture ? 'linear-gradient(135deg, rgba(255,255,255,.78), rgba(255,255,255,.18)),url("' + safeUrl(texture) + '")' : ''
    var iconNode = target.querySelector('[data-profession-live-preview-icon]')
    iconNode.textContent = icon ? '' : (source.shortName.value.trim() || source.displayName.value.trim() || '职').slice(0, 2)
    iconNode.style.backgroundImage = icon ? 'url("' + safeUrl(icon) + '")' : ''
    target.querySelector('[data-profession-live-preview-name]').textContent = source.displayName.value.trim() || '职业名称'
    target.querySelector('[data-profession-live-preview-level]').textContent = (source.shortName.value.trim() || '展示简称') + ' · 主题预览'
  }

  function randomize() {
    var next = palettes[Math.floor(Math.random() * palettes.length)]
    form().primaryColor.value = next[0]
    form().backgroundColor.value = next[1]
    form().textColor.value = next[2]
    refresh()
  }

  function safeUrl(value) { return value.replace(/"/g, '%22').replace(/\\/g, '%5C') }

  document.addEventListener('DOMContentLoaded', function () {
    if (!root()) return
    root().addEventListener('input', function (event) { if (event.target.form === form()) refresh() })
    root().addEventListener('change', function (event) { if (event.target.form === form()) refresh() })
    root().addEventListener('click', function (event) { if (event.target.closest('[data-profession-random-theme]')) randomize() })
    refresh()
  })

  window.ProfessionAdminPreview = {refresh: refresh}
})(window, document)
