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
/* 职业设置即时保存。 */
(function (window, document) {
  'use strict'

  function status(root, message) { root.querySelector('[data-profession-status]').textContent = message }

  function request(path, body) {
    return window.ProfessionProfile.request(path, {method: 'POST', headers: {'csrfToken': Label.csrfToken || ''}, body: JSON.stringify(body)})
  }

  function save(root, saving, saved, operation) {
    var previous = root._professionSave || Promise.resolve()
    root._professionSave = previous.catch(function () {}).then(function () {
      status(root, saving)
      return operation()
    })
    root._professionSave.then(function () { status(root, saved) }).catch(function (error) { status(root, error.message) })
  }

  function customVisibility(root) {
    var result = {}
    root.querySelectorAll('[data-profession-visibility]').forEach(function (item) { result[item.dataset.professionVisibility] = item.value })
    return result
  }

  function savePrimary(root, choice) {
    save(root, '正在保存主职业', '主职业已保存', function () {
      return request('/api/profession/me/primary', {professionId: choice.dataset.professionId})
    })
  }

  function savePrivacy(root) {
    var preset = root.querySelector('[name="professionPreset"]:checked').value
    var body = {preset: preset}
    if (preset === 'CUSTOM') body.moduleVisibility = customVisibility(root)
    save(root, '正在保存展示范围', '展示范围已保存', function () { return request('/api/profession/me/privacy', body) })
  }

  function bind(root) {
    root.addEventListener('click', function (event) {
      var choice = event.target.closest('[data-profession-id]')
      if (choice) savePrimary(root, choice)
    })
    root.addEventListener('change', function (event) {
      if (event.target.name === 'professionPreset' || event.target.dataset.professionVisibility) savePrivacy(root)
    })
  }

  document.addEventListener('DOMContentLoaded', function () {
    var root = document.getElementById('professionSettings')
    if (root) bind(root)
  })
})(window, document)
