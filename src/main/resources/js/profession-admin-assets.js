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
/* 职业图标与封面的上传、预览。 */
(function (window, document) {
  'use strict'

  var MAX_IMAGE_SIZE = 5242880
  var IMAGE_TYPES = ['image/gif', 'image/jpeg', 'image/png', 'image/webp']

  function adminRoot() { return document.getElementById('professionAdmin') }

  function status(message) {
    var target = adminRoot().querySelector('[data-profession-admin-status]')
    target.textContent = message
  }

  function previewTarget(input) {
    return input.form.querySelector('[data-profession-asset-preview-target="' + input.dataset.professionAssetPreview + '"]')
  }

  function refresh(input) {
    var target = previewTarget(input)
    var url = input.value.trim()
    target.replaceChildren()
    if (!url) return
    var image = document.createElement('img')
    image.src = url
    image.alt = input.dataset.professionAssetPreview === 'icon' ? '职业图标预览' : '职业封面预览'
    target.appendChild(image)
  }

  function refreshAll(root) {
    root.querySelectorAll('[data-profession-asset-preview]').forEach(refresh)
  }

  function fileInput() {
    var input = document.createElement('input')
    input.type = 'file'
    input.accept = IMAGE_TYPES.join(',')
    input.hidden = true
    document.body.appendChild(input)
    return input
  }

  function validate(file) {
    if (!IMAGE_TYPES.includes(file.type)) throw new Error('仅支持 GIF、JPG、PNG 或 WebP 图片')
    if (file.size > MAX_IMAGE_SIZE) throw new Error('图片不能超过 5 MB')
  }

  function upload(file) {
    var body = new FormData()
    body.append('file[]', file)
    return fetch((Label.servePath || '') + '/upload', {method: 'POST', body: body, credentials: 'same-origin'})
      .then(function (response) { if (!response.ok) throw new Error('上传失败：' + response.status); return response.json() })
      .then(function (response) {
        if (response.code !== 0) throw new Error(response.msg || '上传失败')
        var map = response.data && response.data.succMap
        var key = map && Object.keys(map)[0]
        if (!key || !map[key]) throw new Error('上传未返回图片地址')
        return map[key]
      })
  }

  function choose(button) {
    var input = fileInput()
    input.addEventListener('change', function () {
      var file = input.files[0]
      input.remove()
      if (!file) return
      submit(button, file)
    })
    input.click()
  }

  function submit(button, file) {
    var field = button.form.elements[button.dataset.professionAssetUpload]
    try { validate(file) } catch (error) { status(error.message); return }
    button.disabled = true
    status('正在上传图片')
    upload(file).then(function (url) {
      field.value = url
      refresh(field)
      status('图片已上传')
    }).catch(function (error) {
      status(error.message)
    }).finally(function () {
      button.disabled = false
    })
  }

  function bind(root) {
    root.addEventListener('input', function (event) {
      if (event.target.matches('[data-profession-asset-preview]')) refresh(event.target)
    })
    root.addEventListener('click', function (event) {
      var button = event.target.closest('[data-profession-asset-upload]')
      if (button) choose(button)
    })
  }

  window.ProfessionAdminAssets = {refreshAll: refreshAll}
  document.addEventListener('DOMContentLoaded', function () {
    var root = adminRoot()
    if (!root) return
    bind(root)
    refreshAll(root)
  })
})(window, document)
