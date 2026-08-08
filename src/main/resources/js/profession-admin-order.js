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
/* 职业库展示顺序编辑。 */
(function (window, document) {
  'use strict'

  var professions = []

  function root() { return document.getElementById('professionAdmin') }
  function dialog() { return document.getElementById('professionOrderDialog') }
  function list() { return dialog().querySelector('[data-profession-order-list]') }
  function status(message) { root().querySelector('[data-profession-admin-status]').textContent = message }

  function open() {
    window.ProfessionAdmin.loadAllCatalogSummaries().then(function (values) {
      professions = values.slice()
      render()
      dialog().showModal()
    }).catch(function (error) { status(error.message) })
  }

  function render() {
    list().replaceChildren.apply(list(), professions.map(item))
  }

  function item(profession, index) {
    var value = document.createElement('li')
    var label = document.createElement('strong')
    label.textContent = profession.displayName || profession.professionCode
    var code = document.createElement('small')
    code.textContent = profession.professionCode
    var actions = document.createElement('span')
    actions.className = 'profession-admin__order-actions'
    actions.append(button('上移', profession.oId, -1, index === 0), button('下移', profession.oId, 1, index === professions.length - 1))
    value.append(label, code, actions)
    return value
  }

  function button(label, id, move, disabled) {
    var value = document.createElement('button')
    value.type = 'button'
    value.textContent = label
    value.dataset.professionOrderMove = move
    value.dataset.professionId = id
    value.disabled = disabled
    return value
  }

  function move(id, direction) {
    var index = professions.findIndex(function (profession) { return String(profession.oId) === String(id) })
    var next = index + direction
    if (index < 0 || next < 0 || next >= professions.length) return
    var current = professions[index]
    professions[index] = professions[next]
    professions[next] = current
    render()
  }

  function save(button) {
    window.ProfessionAdmin.setPending(dialog(), true)
    return window.ProfessionAdmin.request('/api/profession/admin/catalog/order', 'POST', {
      professionIds: professions.map(function (profession) { return profession.oId })
    }).then(function () {
      dialog().close()
      status('展示顺序已保存')
      window.dispatchEvent(new CustomEvent('profession-catalog-reload', {detail: {message: '展示顺序已保存'}}))
    }).catch(function (error) {
      status(error.message)
    }).finally(function () {
      window.ProfessionAdmin.setPending(dialog(), false)
    })
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!root()) return
    root().addEventListener('click', function (event) {
      var button = event.target.closest('button')
      if (!button) return
      if (button.dataset.professionOrderOpen !== undefined) open()
      if (button.dataset.professionOrderMove !== undefined) move(button.dataset.professionId, Number(button.dataset.professionOrderMove))
      if (button.dataset.professionOrderSave !== undefined) save(button)
    })
  })
})(window, document)
