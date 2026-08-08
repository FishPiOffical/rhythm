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
/* 职业配置的单个、全部导出与导入预检查。 */
(function (window, document) {
  'use strict'

  var MAX_FILE_SIZE = 16000000
  var approvedConfigJson = ''

  function root() { return document.getElementById('professionAdmin') }
  function dialog() { return document.getElementById('professionTransferDialog') }
  function result(text) { dialog().querySelector('[data-profession-transfer-result]').textContent = text }
  function status(text) { root().querySelector('[data-profession-admin-status]').textContent = text }
  function importButton() { return dialog().querySelector('[data-profession-import]') }
  function previewHolder() { return dialog().querySelector('[data-profession-transfer-preview]') }

  function currentName(item) { return item.displayName || item.professionCode }

  function fillSelect(catalog) {
    var select = dialog().querySelector('[data-profession-export-select]')
    select.replaceChildren()
    catalog.forEach(function (item) {
      var option = document.createElement('option')
      option.value = item.oId
      option.textContent = currentName(item)
      select.appendChild(option)
    })
  }

  function open() {
    resetImport()
    dialog().showModal()
    window.ProfessionAdmin.loadAllCatalogSummaries().then(fillSelect).catch(showError)
  }

  function resetImport() {
    approvedConfigJson = ''
    importButton().disabled = true
    result('')
    previewHolder().replaceChildren()
  }

  function download(data, name) {
    var blob = new Blob([JSON.stringify(data, null, 2)], {type: 'application/json'})
    var link = document.createElement('a')
    link.href = URL.createObjectURL(blob)
    link.download = name + '.json'
    link.click()
    URL.revokeObjectURL(link.href)
  }

  function exportConfig(professionId) {
    var query = professionId ? '?professionId=' + encodeURIComponent(professionId) : ''
    return window.ProfessionAdmin.request('/api/profession/admin/config/export' + query, 'GET').then(function (data) {
      var name = data.scope === 'SINGLE' ? data.professions[0].professionCode : 'profession-config'
      download(data, name)
      status('配置文件已导出')
      result('配置文件已导出')
    }).catch(showError)
  }

  function fileText() {
    var file = dialog().querySelector('[data-profession-import-file]').files[0]
    if (!file) throw new Error('请选择配置文件')
    if (file.size > MAX_FILE_SIZE) throw new Error('配置文件不能超过 16 MB')
    return file.text()
  }

  function textConfig() {
    var value = dialog().querySelector('[data-profession-import-text]').value.trim()
    var file = dialog().querySelector('[data-profession-import-file]').files[0]
    if (value && file) throw new Error('请选择配置文件或粘贴配置，不能同时使用')
    if (!value) return fileText()
    if (new Blob([value]).size > MAX_FILE_SIZE) throw new Error('配置内容不能超过 16 MB')
    return Promise.resolve(value)
  }

  function precheck() {
    resetImport()
    Promise.resolve().then(textConfig).then(function (configJson) {
      return window.ProfessionAdmin.request('/api/profession/admin/config/precheck', 'POST', {configJson: configJson})
        .then(function (data) { return {configJson: configJson, data: data} })
    }).then(function (value) {
      renderPreview(value.data)
      if (value.data.valid && !value.data.conflicts.length) approvedConfigJson = value.configJson
      importButton().disabled = !approvedConfigJson
    }).catch(showError)
  }

  function renderPreview(data) {
    var holder = previewHolder()
    holder.replaceChildren()
    if (!data.valid) {
      result(data.error || '配置文件不合法')
      return
    }
    result('检查完成：可导入 ' + data.ready.length + ' 个，冲突 ' + data.conflicts.length + ' 个')
    summary(holder, '可导入', data.ready, false)
    summary(holder, '存在冲突', data.conflicts, true)
  }

  function summary(holder, title, values, conflict) {
    var section = document.createElement('section')
    section.className = 'profession-admin__transfer-summary' + (conflict ? ' is-conflict' : '')
    var heading = document.createElement('strong')
    heading.textContent = title + ' · ' + values.length
    section.appendChild(heading)
    if (values.length) {
      var list = document.createElement('ul')
      values.forEach(function (value) {
        var item = document.createElement('li')
        item.textContent = value.professionCode + (value.reason ? ' · ' + value.reason : '')
        list.appendChild(item)
      })
      section.appendChild(list)
    }
    holder.appendChild(section)
  }

  function importConfig() {
    if (!approvedConfigJson) {
      showError(new Error('请先检查无冲突的配置文件'))
      return
    }
    importButton().disabled = true
    window.ProfessionAdmin.request('/api/profession/admin/config/import', 'POST', {configJson: approvedConfigJson}).then(function (data) {
      result('已导入 ' + data.importedCount + ' 个职业')
      status('职业库已更新')
      approvedConfigJson = ''
      window.dispatchEvent(new Event('profession-catalog-reload'))
    }).catch(showError).finally(function () {
      importButton().disabled = !approvedConfigJson
    })
  }

  function showError(error) {
    result(error.message)
    status(error.message)
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!root()) return
    root().addEventListener('click', function (event) {
      var button = event.target.closest('button')
      if (!button) return
      if (button.dataset.professionTransferOpen !== undefined) open()
      if (button.dataset.professionExportAll !== undefined) exportConfig()
      if (button.dataset.professionExportOne !== undefined) exportConfig(dialog().querySelector('[data-profession-export-select]').value)
      if (button.dataset.professionImportPrecheck !== undefined) precheck()
      if (button.dataset.professionImport !== undefined) importConfig()
    })
    dialog().querySelector('[data-profession-import-file]').addEventListener('change', resetImport)
    dialog().querySelector('[data-profession-import-text]').addEventListener('input', resetImport)
    window.addEventListener('profession-export-one', function (event) { exportConfig(event.detail) })
  })
})(window, document)
