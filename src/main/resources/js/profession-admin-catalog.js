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
/* 职业库筛选、分页与摘要卡片。 */
(function (window, document) {
  'use strict'

  var filters = {keyword: '', status: '', sort: 'recent', page: 1, pageSize: 24}
  var searchTimer

  function root() { return document.getElementById('professionAdmin') }
  function list() { return root().querySelector('[data-profession-admin-list]') }
  function text(value) { return value || '' }
  function state(value) { return ({DRAFT: '草稿', PUBLISHED: '已启用', RETIRED: '已停用'})[value] || value }

  function presentation(item) {
    return JSON.parse(item.defaultPresentationJson || '{}')
  }

  function preview(item) {
    var data = presentation(item)
    var holder = document.createElement('span')
    holder.className = 'profession-admin__preview'
    holder.style.setProperty('--profession-primary', data.primaryColor || '#2563eb')
    holder.style.setProperty('--profession-background', data.backgroundColor || '#eff6ff')
    holder.style.setProperty('--profession-text', data.textColor || '#1e293b')
    if (data.textureUrl) holder.style.backgroundImage = 'linear-gradient(135deg, rgba(255,255,255,.16), rgba(15,23,42,.08)),url("' + data.textureUrl.replace(/"/g, '%22') + '")'
    if (data.imageUrl) {
      var image = document.createElement('img')
      image.src = data.imageUrl
      image.alt = text(item.displayName) + '图标'
      holder.appendChild(image)
    } else {
      holder.textContent = text(item.shortName || item.displayName || item.professionCode).slice(0, 2)
    }
    return holder
  }

  function button(label, attribute, value, name) {
    var item = document.createElement('button')
    item.type = 'button'
    item.textContent = label
    item.dataset[attribute] = value
    item.setAttribute('aria-label', name + label)
    return item
  }

  function card(item) {
    var article = document.createElement('article')
    article.className = 'profession-admin__item'
    article.appendChild(preview(item))
    var content = document.createElement('div')
    content.className = 'profession-admin__item-content'
    var heading = document.createElement('h3')
    heading.textContent = text(item.displayName || item.professionCode)
    var code = document.createElement('p')
    code.textContent = item.professionCode
    var description = document.createElement('small')
    description.textContent = text(item.description || '尚未填写职业说明')
    content.append(heading, code, description)
    article.appendChild(content)
    var badge = document.createElement('span')
    badge.className = 'profession-admin__state profession-admin__state--' + item.status.toLowerCase()
    badge.textContent = state(item.status)
    article.appendChild(badge)
    var metrics = document.createElement('div')
    metrics.className = 'profession-admin__metrics'
    metrics.textContent = '等级 ' + item.levelCount + ' · 规则 ' + item.automationCount
    article.appendChild(metrics)
    var actions = document.createElement('div')
    actions.className = 'profession-admin__actions'
    var itemName = text(item.displayName || item.professionCode)
    actions.append(button('编辑', 'professionAction', 'edit-definition', itemName), button('历史', 'professionHistory', item.oId, itemName))
    actions.querySelector('[data-profession-action]').dataset.itemId = item.oId
    article.appendChild(actions)
    return article
  }

  function render(page) {
    var holder = list()
    holder.replaceChildren()
    page.items.forEach(function (item) { holder.appendChild(card(item)) })
    root().querySelector('[data-profession-catalog-count]').textContent = '共 ' + page.total + ' 个职业'
    root().querySelector('[data-profession-catalog-page]').textContent = '第 ' + page.page + ' 页'
    root().querySelector('[data-profession-catalog-previous]').disabled = page.page <= 1
    root().querySelector('[data-profession-catalog-next]').disabled = page.page * page.pageSize >= page.total
    root().querySelector('[data-profession-catalog-empty]').hidden = page.items.length > 0
  }

  function load(message) {
    root().dataset.catalogLoading = 'true'
    window.ProfessionAdmin.status('读取职业库')
    return window.ProfessionAdmin.loadCatalog(filters).then(function (page) {
      render(page)
      window.ProfessionAdmin.status(message || '职业库已更新')
      return page
    }).catch(function (error) {
      window.ProfessionAdmin.status(error.message)
      throw error
    }).finally(function () { delete root().dataset.catalogLoading })
  }

  function updateFilter() {
    filters.keyword = root().querySelector('[data-profession-catalog-search]').value.trim()
    filters.status = root().querySelector('[data-profession-catalog-status]').value
    filters.sort = root().querySelector('[data-profession-catalog-sort]').value
    filters.page = 1
    load()
  }

  function bind() {
    root().querySelector('[data-profession-catalog-search]').addEventListener('input', function () {
      window.clearTimeout(searchTimer)
      searchTimer = window.setTimeout(updateFilter, 220)
    })
    root().querySelector('[data-profession-catalog-status]').addEventListener('change', updateFilter)
    root().querySelector('[data-profession-catalog-sort]').addEventListener('change', updateFilter)
    root().querySelector('[data-profession-catalog-previous]').addEventListener('click', function () { filters.page--; load() })
    root().querySelector('[data-profession-catalog-next]').addEventListener('click', function () { filters.page++; load() })
    window.addEventListener('profession-catalog-request', function (event) { load(event.detail && event.detail.message) })
    window.addEventListener('profession-catalog-page', function (event) { render(event.detail) })
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!root()) return
    bind()
    load()
  })
})(window, document)
