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
/* 职业管理数据、保存和操作控制。 */
(function (window, document) {
  'use strict'

  var catalog = []
  var activeView = 'definition'
  var editingProfession
  var editingRevisionId = ''
  var automationCollection
  var levelEditor

  function root() { return document.getElementById('professionAdmin') }
  function status(message) { root().querySelector('[data-profession-admin-status]').textContent = message }
  function id(value) { return String(value || '') }
  function name(item) { return item.displayName || item.professionCode }
  function editingName() {
    var revisions = editingProfession ? editingProfession.revisions || [] : []
    var revision = revisions.find(function (item) { return id(item.oId) === editingRevisionId })
    return revision ? name(revision) : name(editingProfession || {})
  }

  function request(url, method, body) {
    return fetch((Label.servePath || '') + url, {
      method: method,
      credentials: 'same-origin',
      headers: {'Content-Type': 'application/json;charset=UTF-8', 'csrfToken': Label.csrfToken || ''},
      body: body ? JSON.stringify(body) : undefined
    }).then(function (response) {
      if (!response.ok) throw new Error('请求失败：' + response.status)
      return response.json()
    }).then(function (response) {
      if (response.code !== 0) throw new Error(response.msg || '操作失败')
      return response.data
    })
  }

  function catalogUrl(options) {
    var query = new URLSearchParams()
    Object.keys(options || {}).forEach(function (key) {
      if (options[key] !== '' && options[key] !== undefined && options[key] !== null) query.set(key, options[key])
    })
    return '/api/profession/admin/catalog-summary' + (query.size ? '?' + query.toString() : '')
  }

  function loadCatalog(options) {
    return request(catalogUrl(options), 'GET').then(function (data) {
      replaceCatalog(data.items || [])
      window.dispatchEvent(new CustomEvent('profession-catalog-loaded', {detail: catalog}))
      window.dispatchEvent(new CustomEvent('profession-catalog-page', {detail: data}))
      return data
    })
  }

  function detail(professionId) {
    return request('/api/profession/admin/catalog-detail/' + encodeURIComponent(professionId), 'GET')
  }

  function loadAllCatalogSummaries() {
    var pageSize = 50
    function next(page, values) {
      return request(catalogUrl({sort: 'manual', page: page, pageSize: pageSize}), 'GET').then(function (data) {
        var result = values.concat(data.items || [])
        return page * pageSize < data.total ? next(page + 1, result) : result
      })
    }
    return next(1, [])
  }

  function replaceCatalog(values) {
    catalog = values.slice()
    refreshWizard()
  }

  function setEditingProfession(profession, revisionId) {
    editingProfession = profession || null
    editingRevisionId = profession ? id(revisionId || profession.currentRevisionId) : ''
    root().querySelectorAll('[data-profession-owner]').forEach(function (owner) {
      owner.querySelector('[name="professionId"]').value = editingProfession ? editingProfession.oId : ''
      owner.querySelector('[data-profession-owner-name]').textContent = editingProfession
        ? editingName() : '请先保存职业资料'
    })
    refreshWizard()
  }

  function hasEditingProfession() { return Boolean(editingProfession && editingRevisionId) }

  function refreshWizard() {
    if (!hasEditingProfession() && activeView !== 'definition') activeView = 'definition'
    root().querySelectorAll('[data-admin-view]').forEach(function (button) {
      var enabled = button.dataset.adminView === 'definition' || hasEditingProfession()
      button.disabled = !enabled
      button.classList.toggle('is-active', button.dataset.adminView === activeView)
      button.setAttribute('aria-selected', button.dataset.adminView === activeView ? 'true' : 'false')
    })
    selectView(activeView, true)
  }

  function selectView(view, quiet) {
    if (view !== 'definition' && !hasEditingProfession()) {
      if (!quiet) status('请先保存职业资料，再继续设置等级和经验规则')
      return
    }
    activeView = view
    root().querySelectorAll('[data-admin-panel]').forEach(function (panel) { panel.hidden = panel.dataset.adminPanel !== view })
    root().querySelectorAll('[data-admin-view]').forEach(function (button) {
      var selected = button.dataset.adminView === view
      button.classList.toggle('is-active', selected)
      button.setAttribute('aria-selected', selected ? 'true' : 'false')
    })
  }

  function compact(source) {
    return Object.keys(source).reduce(function (result, key) {
      if (source[key]) result[key] = source[key]
      return result
    }, {})
  }

  function definitionPayload(form) {
    return {professionCode: form.professionCode.value, displayName: form.displayName.value, shortName: form.shortName.value,
      description: form.description.value, defaultPresentationJson: JSON.stringify(compact({primaryColor: form.primaryColor.value,
        backgroundColor: form.backgroundColor.value, textColor: form.textColor.value, imageUrl: form.imageUrl.value, textureUrl: form.textureUrl.value}))}
  }

  function migrationConfig(form) {
    var result = {}
    if (form.migrationPolicy.value === 'SCHEDULED_SWITCH') {
      var switchAt = new Date(form.scheduledSwitchAt.value).getTime()
      if (!switchAt) throw new Error('请设置切换时间')
      result.scheduledSwitchAt = switchAt
    }
    if (form.rewardMigrationPolicy.value === 'GRANT_SELECTED') {
      result.selectedRewardCodes = form.selectedRewardCodes.value.split(',').map(function (value) { return value.trim() }).filter(Boolean)
    }
    return JSON.stringify(result)
  }

  function schemePayload(form) {
    if (!hasEditingProfession()) throw new Error('请先保存职业资料')
    return {professionId: editingProfession.oId, professionRevisionId: editingRevisionId,
      migrationPolicy: form.migrationPolicy.value, rewardMigrationPolicy: form.rewardMigrationPolicy.value,
      migrationConfigJson: migrationConfig(form), levels: levelEditor.value()}
  }

  function automationPayloads(form) {
    return automationCollection.changed().map(function (rule) { return {rule: rule, body: automationPayload(form, rule)} })
  }

  function automationPayload(form, rule) {
    if (!hasEditingProfession()) throw new Error('请先保存职业资料')
    return {professionId: editingProfession.oId, automationCode: rule.automationCode, configurationJson: rule.configurationJson}
  }

  function saveAutomations(form) {
    var payloads = automationPayloads(form)
    if (!payloads.length) return Promise.resolve({changed: false})
    return payloads.reduce(function (chain, payload) {
      return chain.then(function () {
        return request('/api/profession/admin/automation/draft', 'POST', payload.body).then(function () {
          automationCollection.markSaved([payload.rule])
        })
      })
    }, Promise.resolve()).then(function () { return {changed: true} })
  }

  function save(form) {
    if (!form.reportValidity()) return Promise.resolve()
    if (form.id === 'professionDefinitionForm') return request('/api/profession/admin/definition/draft', 'POST', definitionPayload(form))
    if (form.id === 'professionSchemeForm') return request('/api/profession/admin/scheme/draft', 'POST', schemePayload(form))
    return saveAutomations(form)
  }

  function savedMessage(form) {
    if (form.id === 'professionDefinitionForm') return '职业资料已保存，可继续设置等级和规则'
    if (form.id === 'professionSchemeForm') return '草稿已保存，可在历史记录中启用等级方案'
    return '规则草稿已保存'
  }

  function setPending(holder, pending) {
    holder.setAttribute('aria-busy', pending ? 'true' : 'false')
    holder.querySelectorAll('button').forEach(function (button) { button.disabled = pending })
  }

  function actionBody(route, button) {
    if (route === 'definition/retire') return {professionId: button.dataset.itemId}
    if (route === 'definition/rollback') return {professionId: button.dataset.professionId, revisionId: button.dataset.itemId}
    if (route.indexOf('definition/') === 0) return {revisionId: button.dataset.itemId}
    if (route === 'scheme/rollback') return {professionId: button.dataset.professionId, schemeId: button.dataset.itemId}
    if (route.indexOf('scheme/') === 0) return {schemeId: button.dataset.itemId}
    if (route === 'automation/rollback') return {automationId: button.dataset.professionId, revisionId: button.dataset.itemId}
    if (route === 'automation/publish' || route === 'automation/copy') return {revisionId: button.dataset.itemId}
    return {automationId: button.dataset.itemId}
  }

  function actionMessage(button) {
    var route = button.dataset.professionAction
    if (route === 'definition/retire') return Promise.resolve('停用后，新经验不再累计到这个职业。确认继续？')
    if (route === 'scheme/retire') return Promise.resolve('停用后，这套等级方案不再生效。确认继续？')
    if (route === 'automation/retire') return Promise.resolve('停用后，这条经验规则不再处理新事件。确认继续？')
    if (route.indexOf('rollback') > -1) return Promise.resolve('还原会创建并立即启用一个新版本。确认继续？')
    if (route === 'definition/publish') return Promise.resolve('启用后，用户可以选择这个职业。确认继续？')
    if (route !== 'scheme/publish') return Promise.resolve('确认启用这个版本？')
    return request('/api/profession/admin/scheme-impact/' + button.dataset.professionId + '/' + button.dataset.itemId, 'GET').then(function (impact) {
      return '将影响 ' + impact.totalCount + ' 位用户：升级 ' + impact.upgradeCount + '，降级 ' + impact.downgradeCount + '。确认启用？'
    })
  }

  function confirmAction(message) {
    var dialog = document.createElement('dialog')
    var copy = document.createElement('p')
    var actions = document.createElement('footer')
    var cancel = document.createElement('button')
    var confirm = document.createElement('button')
    dialog.className = 'profession-admin__confirm'
    copy.textContent = message
    cancel.type = 'button'
    cancel.textContent = '取消'
    confirm.type = 'button'
    confirm.className = 'profession-admin__button--primary'
    confirm.textContent = '确认'
    actions.append(cancel, confirm)
    dialog.append(copy, actions)
    document.body.appendChild(dialog)
    return new Promise(function (resolve) {
      function finish(confirmed) {
        dialog.close()
        dialog.remove()
        resolve(confirmed)
      }
      cancel.addEventListener('click', function () { finish(false) }, {once: true})
      confirm.addEventListener('click', function () { finish(true) }, {once: true})
      dialog.addEventListener('cancel', function (event) {
        event.preventDefault()
        finish(false)
      }, {once: true})
      dialog.showModal()
      confirm.focus()
    })
  }

  function runAction(button) {
    setPending(button.parentElement, true)
    return actionMessage(button).then(function (message) {
      return confirmAction(message)
    }).then(function (confirmed) {
      if (!confirmed) return null
      return request('/api/profession/admin/' + button.dataset.professionAction, 'POST', actionBody(button.dataset.professionAction, button))
    }).then(function (result) {
      if (result === null) return null
      window.dispatchEvent(new CustomEvent('profession-catalog-reload', {detail: {message: '操作已完成'}}))
      return result
    }).finally(function () { setPending(button.parentElement, false) })
  }

  function toggleMigrationFields() {
    var form = root().querySelector('#professionSchemeForm')
    form.querySelector('[data-scheduled-switch]').hidden = form.migrationPolicy.value !== 'SCHEDULED_SWITCH'
    form.querySelector('[data-selected-rewards]').hidden = form.rewardMigrationPolicy.value !== 'GRANT_SELECTED'
  }

  function bind() {
    root().addEventListener('submit', function (event) {
      event.preventDefault()
      var form = event.target
      setPending(form, true)
      save(form).then(function (result) {
        form.dataset.dirty = 'false'
        if (result && result.changed === false) {
          status('没有可保存的更改')
          return
        }
        status(savedMessage(form))
        if (form.id === 'professionDefinitionForm' && result) {
          window.dispatchEvent(new CustomEvent('profession-definition-saved', {
            detail: {professionCode: form.professionCode.value, revisionId: result.id}
          }))
        }
        window.dispatchEvent(new CustomEvent('profession-catalog-reload', {detail: {message: savedMessage(form)}}))
      }).catch(function (error) { status(error.message) }).finally(function () { setPending(form, false) })
    })
    root().addEventListener('input', function (event) { if (event.target.form) event.target.form.dataset.dirty = 'true' })
    root().addEventListener('change', function (event) {
      if (event.target.form) event.target.form.dataset.dirty = 'true'
      if (event.target.name === 'migrationPolicy' || event.target.name === 'rewardMigrationPolicy') toggleMigrationFields()
    })
    root().addEventListener('click', function (event) {
      var button = event.target.closest('button')
      if (!button) return
      if (button.matches('[data-admin-view]')) selectView(button.dataset.adminView)
      if (button.matches('[data-add-level]')) levelEditor.add()
      if (button.matches('[data-add-automation]')) automationCollection.add()
      if (button.dataset.professionHistory) window.dispatchEvent(new CustomEvent('profession-history-open', {detail: button.dataset.professionHistory}))
      if (button.dataset.professionExport) window.dispatchEvent(new CustomEvent('profession-export-one', {detail: button.dataset.professionExport}))
      if (button.matches('[data-profession-action]') && button.dataset.professionAction.indexOf('edit-') !== 0) {
        runAction(button).catch(function (error) { status(error.message) })
      }
    })
  }

  function editorState() {
    var definition = definitionPayload(root().querySelector('#professionDefinitionForm'))
    return {definition: definition, levels: levelEditor.value(), rules: automationCollection.snapshot()}
  }

  window.ProfessionAdmin = {request: request, detail: detail, loadCatalog: loadCatalog, loadAllCatalogSummaries: loadAllCatalogSummaries,
    replaceCatalog: replaceCatalog, catalog: function () { return catalog.slice() }, name: name, status: status, selectView: selectView,
    setEditingProfession: setEditingProfession, setPending: setPending, editorState: editorState,
    resetDirty: function (form) { form.dataset.dirty = 'false' }}

  document.addEventListener('DOMContentLoaded', function () {
    if (!root()) return
    bind()
    window.addEventListener('profession-catalog-reload', function (event) {
      window.dispatchEvent(new CustomEvent('profession-catalog-request', {detail: event.detail || {}}))
    })
    Promise.all([request('/api/profession/admin/automation/metadata', 'GET'), request('/api/profession/admin/definition/metadata', 'GET')]).then(function (values) {
      automationCollection = window.ProfessionAutomationCollection.create(root().querySelector('[data-automation-list]'), values[0])
      levelEditor = window.ProfessionLevelEditor.create(root().querySelector('[data-level-list]'), values[1])
      levelEditor.add()
      window.dispatchEvent(new CustomEvent('profession-admin-ready', {detail: {levelEditor: levelEditor, automationCollection: automationCollection}}))
      toggleMigrationFields()
    }).catch(function (error) { status(error.message) })
  })
})(window, document)
