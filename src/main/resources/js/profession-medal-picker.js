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
/* 职业升级奖励复用新版勋章管理的名称检索。 */
(function (window, document) {
  'use strict'

  var DEFAULT_PAGE = 1
  var DEFAULT_PAGE_SIZE = 50
  var PANEL_GUTTER = 12
  var PANEL_OFFSET = 8
  var PANEL_WIDTH = 360
  var searchTimer

  function create() {
    var holder = element('div', 'profession-medal-picker')
    var value = input('hidden')
    value.name = 'medalId'
    value.dataset.medalId = 'true'
    var trigger = button('选择勋章', 'data-medal-picker-open')
    var panel = element('section', 'profession-medal-picker__panel')
    panel.hidden = true
    var search = input('search')
    search.placeholder = '搜索勋章名称或编号'
    search.autocomplete = 'off'
    search.dataset.medalPickerSearch = 'true'
    var results = element('div', 'profession-medal-picker__results')
    results.setAttribute('role', 'listbox')
    panel.append(search, results)
    holder.append(value, trigger, panel)
    holder._medalPicker = {value: value, trigger: trigger, panel: panel, search: search, results: results, loaded: false, placeholder: null}
    bind(holder)
    return holder
  }

  function bind(holder) {
    holder.addEventListener('click', function (event) {
      var target = event.target.closest('button')
      if (!target) return
      if (target.dataset.medalPickerOpen !== undefined) open(holder)
      if (target.dataset.medalPickerOption !== undefined) select(holder, target._medal)
    })
    holder.addEventListener('input', function (event) {
      if (!event.target.matches('[data-medal-picker-search]')) return
      window.clearTimeout(searchTimer)
      searchTimer = window.setTimeout(function () { query(holder, event.target.value.trim()) }, 180)
    })
    document.addEventListener('pointerdown', function (event) {
      if (!holder.contains(event.target) && !holder._medalPicker.panel.contains(event.target)) close(holder)
    })
    document.addEventListener('keydown', function (event) {
      if (event.key === 'Escape') close(holder)
    })
    window.addEventListener('resize', function () {
      if (!holder._medalPicker.panel.hidden) place(holder)
    })
  }

  function open(holder) {
    var picker = holder._medalPicker
    if (!picker.panel.hidden) return close(holder)
    mount(holder)
    picker.panel.hidden = false
    place(holder)
    if (picker.loaded) {
      picker.search.focus()
      return
    }
    request('/api/medal/admin/list', {page: DEFAULT_PAGE, pageSize: DEFAULT_PAGE_SIZE}).then(function (items) {
      picker.loaded = true
      render(holder, items)
      place(holder)
      picker.search.focus()
    }).catch(function (error) { failure(holder, error) })
  }

  function close(holder) {
    var picker = holder._medalPicker
    picker.panel.hidden = true
    restore(picker)
  }

  function mount(holder) {
    var picker = holder._medalPicker
    if (picker.placeholder) return
    picker.placeholder = document.createComment('profession-medal-picker')
    picker.panel.parentNode.insertBefore(picker.placeholder, picker.panel)
    ;(holder.closest('dialog') || document.body).appendChild(picker.panel)
  }

  function restore(picker) {
    if (!picker.placeholder) return
    picker.placeholder.parentNode.insertBefore(picker.panel, picker.placeholder)
    picker.placeholder.remove()
    picker.placeholder = null
    picker.panel.removeAttribute('style')
  }

  function place(holder) {
    var picker = holder._medalPicker
    var panel = picker.panel
    var rect = picker.trigger.getBoundingClientRect()
    var width = Math.min(PANEL_WIDTH, window.innerWidth - PANEL_GUTTER * 2)
    panel.style.width = width + 'px'
    panel.style.maxHeight = Math.max(180, window.innerHeight - PANEL_GUTTER * 2) + 'px'
    panel.style.left = Math.max(PANEL_GUTTER, Math.min(rect.right - width, window.innerWidth - width - PANEL_GUTTER)) + 'px'
    var below = rect.bottom + PANEL_OFFSET
    panel.style.top = below + 'px'
    if (below + panel.offsetHeight > window.innerHeight - PANEL_GUTTER && rect.top - panel.offsetHeight - PANEL_OFFSET >= PANEL_GUTTER) {
      panel.style.top = (rect.top - panel.offsetHeight - PANEL_OFFSET) + 'px'
    }
  }

  function query(holder, keyword) {
    if (!keyword) return request('/api/medal/admin/list', {page: DEFAULT_PAGE, pageSize: DEFAULT_PAGE_SIZE})
      .then(function (items) { render(holder, items) }).catch(function (error) { failure(holder, error) })
    return request('/api/medal/admin/search', {keyword: keyword})
      .then(function (items) { render(holder, items) }).catch(function (error) { failure(holder, error) })
  }

  function request(path, body) {
    if (!window.ProfessionAdmin) throw new Error('职业管理尚未准备完成')
    return window.ProfessionAdmin.request(path, 'POST', body)
  }

  function render(holder, items) {
    var picker = holder._medalPicker
    picker.results.replaceChildren()
    if (!items.length) {
      picker.results.appendChild(element('p', 'profession-medal-picker__empty', '没有匹配的勋章'))
      return
    }
    items.forEach(function (item) {
      var option = button('', 'data-medal-picker-option')
      option.type = 'button'
      option.setAttribute('role', 'option')
      option._medal = item
      option.append(element('strong', '', name(item)), element('small', '', identifier(item)))
      picker.results.appendChild(option)
    })
    place(holder)
  }

  function select(holder, medal) {
    var picker = holder._medalPicker
    picker.value.value = identifier(medal)
    picker.trigger.textContent = name(medal)
    picker.trigger.dataset.medalSelected = 'true'
    close(holder)
    picker.value.dispatchEvent(new Event('change', {bubbles: true}))
  }

  function sync(value) {
    var holder = value.closest('.profession-medal-picker')
    if (!holder || !value.value) return
    var picker = holder._medalPicker
    picker.trigger.textContent = '读取勋章名称'
    request('/api/medal/admin/detail', {medalId: value.value}).then(function (item) {
      picker.trigger.textContent = name(item)
      picker.trigger.dataset.medalSelected = 'true'
    }).catch(function (error) {
      picker.trigger.textContent = '勋章 #' + value.value + ' 未找到'
      picker.trigger.dataset.medalSelected = 'false'
      failure(holder, error)
    })
  }

  function failure(holder, error) {
    var root = document.getElementById('professionAdmin')
    if (root) root.querySelector('[data-profession-admin-status]').textContent = error.message
    holder._medalPicker.results.replaceChildren(element('p', 'profession-medal-picker__empty', error.message))
  }

  function name(item) { return item.medal_name || item.name || identifier(item) }
  function identifier(item) { return String(item.medal_id || item.medalId || item.oId || '') }
  function input(type) { var item = document.createElement('input'); item.type = type; return item }
  function button(text, attribute) { var item = document.createElement('button'); item.type = 'button'; item.textContent = text; item.setAttribute(attribute, 'true'); return item }
  function element(tag, className, text) { var item = document.createElement(tag); item.className = className; if (text) item.textContent = text; return item }

  window.ProfessionMedalPicker = {create: create, sync: sync}
})(window, document)
