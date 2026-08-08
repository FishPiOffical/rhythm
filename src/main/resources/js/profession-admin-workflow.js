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
/* 职业库与分步编辑流程。 */
(function (window, document) {
  'use strict'

  var editors
  var activeProfession
  var activeRevisionId = ''

  function root() { return document.getElementById('professionAdmin') }
  function status(message) { root().querySelector('[data-profession-admin-status]').textContent = message }
  function workspaceTitle(value) { root().querySelector('[data-profession-workspace-title]').textContent = value }
  function editorDialog() { return root().querySelector('#professionEditorDialog') }
  function sameId(left, right) { return String(left) === String(right) }

  function setMode(value) {
    var target = root()
    target.dataset.adminMode = value
    if (value === 'compose' && !editorDialog().open) editorDialog().showModal()
    if (value === 'catalog' && editorDialog().open) editorDialog().close()
  }

  function showStep(step) { window.ProfessionAdmin.selectView(step) }

  function selectedRevision(profession) {
    var revision = profession.revisions.find(function (item) { return sameId(item.oId, profession.currentRevisionId) })
    if (revision) return revision
    var drafts = profession.revisions.filter(function (item) { return item.status === 'DRAFT' })
    if (!drafts.length) throw new Error('职业没有可编辑的草稿')
    return drafts.sort(function (left, right) { return right.revisionNo - left.revisionNo })[0]
  }

  function setActiveProfession(profession, revisionId) {
    activeProfession = profession || null
    activeRevisionId = activeProfession ? String(revisionId || selectedRevision(activeProfession).oId) : ''
    window.ProfessionAdmin.setEditingProfession(activeProfession, activeRevisionId)
  }

  function activeRevision(profession) {
    var revision = (profession.revisions || []).find(function (item) { return sameId(item.oId, activeRevisionId) })
    return revision || selectedRevision(profession)
  }

  function selectedAutomationRevision(automation) {
    var current = automation.revisions.find(function (item) { return sameId(item.oId, automation.currentRevisionId) })
    if (current) return current
    var drafts = automation.revisions.filter(function (item) { return item.status === 'DRAFT' })
    if (!drafts.length) throw new Error('经验规则没有可编辑的版本')
    return drafts.sort(function (left, right) { return right.revisionNo - left.revisionNo })[0]
  }

  function setField(form, name, value) {
    var field = form.elements ? form.elements[name] : form.querySelector('[name="' + name + '"]')
    field.value = value || ''
  }

  function updateDefinitionCopy(editing) {
    var form = root().querySelector('#professionDefinitionForm')
    form.querySelector('[data-definition-title]').textContent = editing ? '编辑职业' : '职业信息'
    form.querySelector('[data-definition-description]').textContent = editing ? '保存后可在历史记录中还原。' : '保存后启用职业，再继续设置等级与经验规则。'
    form.querySelector('[data-definition-save]').textContent = editing ? '保存更改' : '保存职业'
  }

  function createDefinition(seed) {
    var form = root().querySelector('#professionDefinitionForm')
    form.reset()
    setActiveProfession(null)
    Object.keys(seed || {}).forEach(function (key) { setField(form, key, seed[key]) })
    form.elements.professionCode.readOnly = false
    updateDefinitionCopy(false)
    workspaceTitle('新建职业')
    window.ProfessionAdminAssets.refreshAll(root())
    window.ProfessionAdminPreview.refresh()
    setMode('compose')
    showStep('definition')
    window.ProfessionAdmin.resetDirty(form)
  }

  function editDefinition(profession) {
    var form = root().querySelector('#professionDefinitionForm')
    var revision = selectedRevision(profession)
    setActiveProfession(profession, revision.oId)
    var presentation = JSON.parse(revision.defaultPresentationJson)
    setField(form, 'professionCode', profession.professionCode)
    setField(form, 'displayName', revision.displayName)
    setField(form, 'shortName', revision.shortName)
    setField(form, 'description', revision.description)
    ;['primaryColor', 'backgroundColor', 'textColor', 'imageUrl', 'textureUrl'].forEach(function (name) { setField(form, name, presentation[name]) })
    form.elements.professionCode.readOnly = true
    updateDefinitionCopy(true)
    workspaceTitle(revision.displayName)
    window.ProfessionAdminAssets.refreshAll(root())
    window.ProfessionAdminPreview.refresh()
    setMode('compose')
    showStep('definition')
    window.ProfessionAdmin.resetDirty(form)
  }

  function updateSelect(select, value) {
    select.value = value
    select.dispatchEvent(new Event('change', {bubbles: true}))
  }

  function styleValue(holder, theme, json) {
    var values = JSON.parse(json)
    Object.keys(values).forEach(function (key) {
      var control = holder.querySelector('[data-style-' + theme + '="' + key + '"]')
      if (control) control.value = values[key]
    })
  }

  function presentations(holder, values) {
    values.forEach(function (value) {
      holder.querySelector('[data-add-presentation]').click()
      var target = holder.querySelector('.profession-level-editor__presentations').lastElementChild
      target.querySelector('[data-presentation-position]').value = value.positionCode
      styleValue(target, 'light', value.lightConfigJson)
      styleValue(target, 'dark', value.darkConfigJson)
    })
  }

  function rewards(holder, values) {
    values.forEach(function (value) {
      holder.querySelector('[data-add-reward]').click()
      var target = holder.querySelector('.profession-level-editor__rewards').lastElementChild
      var config = JSON.parse(value.rewardConfigJson)
      var rewardType = target.querySelector('[data-reward-type]')
      updateSelect(rewardType, value.rewardType)
      editors.levelEditor.refreshReward(target)
      if (value.rewardType === 'POINT') target.querySelector('[name="rewardPoint"]').value = config.amount
      if (value.rewardType === 'MEDAL') {
        var medal = target.querySelector('[name="medalId"]')
        medal.value = config.medalId || ''
        if (medal.value) window.ProfessionMedalPicker.sync(medal)
        target.querySelector('[name="medalDuration"]').value = Number(config.durationMillis || 0) / 3600000
        target.querySelector('[name="medalData"]').value = config.data || ''
      }
    })
  }

  function level(holder, value) {
    setField(holder, 'levelCode', value.levelCode)
    setField(holder, 'levelName', value.displayName)
    setField(holder, 'experience', value.requiredTotalExperience)
    setField(holder, 'shortName', value.shortName)
    setField(holder, 'description', value.description)
    setField(holder, 'achievementDescription', value.achievementDescription)
    presentations(holder, value.presentations)
    rewards(holder, value.rewards)
  }

  function editScheme(profession, scheme) {
    if (!editors) throw new Error('等级编辑器尚未准备完成')
    var form = root().querySelector('#professionSchemeForm')
    setActiveProfession(profession, scheme.professionRevisionId)
    var config = JSON.parse(scheme.migrationConfigJson)
    updateSelect(form.elements.migrationPolicy, scheme.migrationPolicy)
    updateSelect(form.elements.rewardMigrationPolicy, scheme.rewardMigrationPolicy)
    if (config.scheduledSwitchAt) form.elements.scheduledSwitchAt.value = new Date(config.scheduledSwitchAt).toISOString().slice(0, 16)
    form.elements.selectedRewardCodes.value = (config.selectedRewardCodes || []).join(',')
    var holder = root().querySelector('[data-level-list]')
    holder.replaceChildren()
    scheme.levels.forEach(function (value) { editors.levelEditor.add(); level(holder.lastElementChild, value) })
    holder.querySelectorAll('[name="levelName"]').forEach(function (input) { input.dispatchEvent(new Event('input', {bubbles: true})) })
    workspaceTitle(activeRevision(profession).displayName)
    setMode('compose')
    showStep('scheme')
    window.ProfessionAdmin.resetDirty(form)
  }

  function editAutomation(profession) {
    if (!editors) throw new Error('经验规则编辑器尚未准备完成')
    var form = root().querySelector('#professionAutomationForm')
    var revision = selectedRevision(profession)
    setActiveProfession(profession, revision.oId)
    var rules = profession.automations.map(function (automation) {
      var revision = selectedAutomationRevision(automation)
      return {automationId: automation.oId, automationCode: automation.automationCode, configuration: JSON.parse(revision.configurationJson)}
    })
    editors.automationCollection.load(rules)
    workspaceTitle(revision.displayName)
    setMode('compose')
    showStep('automation')
    window.ProfessionAdmin.resetDirty(form)
  }

  function find(catalogue, id, collection) {
    for (var index = 0; index < catalogue.length; index++) {
      var match = catalogue[index][collection || 'revisions'].find(function (item) { return sameId(item.oId, id) })
      if (match || sameId(catalogue[index].oId, id)) return {profession: catalogue[index], item: match || catalogue[index]}
    }
    throw new Error('职业资料不存在')
  }

  function run(button) {
    var professionId = button.dataset.professionId || button.dataset.itemId
    Promise.all([window.ProfessionAdmin.detail(professionId), window.ProfessionAdmin.loadAllCatalogSummaries()]).then(function (values) {
      var profession = values[0]
      window.ProfessionAdmin.replaceCatalog(values[1])
      if (button.dataset.professionAction === 'edit-definition') return editDefinition(profession)
      if (button.dataset.professionAction === 'edit-scheme') {
        var scheme = find([profession], button.dataset.itemId, 'schemes')
        return editScheme(scheme.profession, scheme.item)
      }
      var automation = find([profession], button.dataset.itemId, 'automations')
      editAutomation(automation.profession)
    }).catch(function (error) { status(error.message) })
  }

  function hasUnsavedChanges() {
    return Array.from(root().querySelectorAll('.profession-admin__form')).some(function (form) { return form.dataset.dirty === 'true' })
  }

  function closeEditor() {
    if (hasUnsavedChanges() && !window.confirm('尚有未保存内容。确认放弃更改？')) return
    root().querySelectorAll('.profession-admin__form').forEach(function (form) { window.ProfessionAdmin.resetDirty(form) })
    setMode('catalog')
  }

  function testAutomation(button) { return window.ProfessionAutomationTester.test(editors, button) }

  function openActiveStep(step) {
    if (!activeProfession) return
    if (step === 'scheme') {
      var scheme = activeProfession.schemes.find(function (item) { return sameId(item.oId, activeProfession.currentLevelSchemeId) })
      if (scheme) return editScheme(activeProfession, scheme)
      return createScheme(activeProfession)
    }
    if (step === 'automation') editAutomation(activeProfession)
  }

  function createScheme(profession) {
    var form = root().querySelector('#professionSchemeForm')
    form.reset()
    var revision = activeRevision(profession)
    setActiveProfession(profession, revision.oId)
    var holder = root().querySelector('[data-level-list]')
    holder.replaceChildren()
    editors.levelEditor.add()
    workspaceTitle(revision.displayName)
    setMode('compose')
    showStep('scheme')
    window.ProfessionAdmin.resetDirty(form)
  }

  function activateSavedDefinition(detail) {
    return window.ProfessionAdmin.loadAllCatalogSummaries().then(function (catalog) {
      var summary = catalog.find(function (item) { return item.professionCode === detail.professionCode })
      if (!summary) throw new Error('已保存的职业未进入职业库')
      return window.ProfessionAdmin.detail(summary.oId)
    }).then(function (profession) {
      setActiveProfession(profession, detail.revisionId)
      workspaceTitle(selectedRevision(profession).displayName)
      status('职业资料已保存，可继续设置等级和规则')
    }).catch(function (error) { status(error.message) })
  }

  function bind(target) {
    target.addEventListener('click', function (event) {
      var button = event.target.closest('button')
      if (!button) return
      if (button.dataset.adminMode === 'catalog') setMode('catalog')
      if (button.dataset.adminMode === 'compose') createDefinition()
      if (button.dataset.adminView) openActiveStep(button.dataset.adminView)
      if (button.dataset.professionEditorClose !== undefined) closeEditor()
      if (button.dataset.professionAction && button.dataset.professionAction.indexOf('edit-') === 0) run(button)
      if (button.dataset.testAutomation !== undefined) testAutomation(button)
    })
    window.addEventListener('profession-automation-preview-request', function (event) {
      var button = event.detail.card.querySelector('[data-test-automation]')
      if (button) testAutomation(button)
    })
  }

  window.addEventListener('profession-admin-ready', function (event) { editors = event.detail })
  window.addEventListener('profession-definition-saved', function (event) { activateSavedDefinition(event.detail) })
  window.ProfessionAdminWorkflow = {createDefinition: createDefinition}
  document.addEventListener('DOMContentLoaded', function () {
    var target = root()
    if (!target) return
    bind(target)
    editorDialog().addEventListener('cancel', function (event) {
      if (!hasUnsavedChanges()) return
      event.preventDefault()
      closeEditor()
    })
    editorDialog().addEventListener('close', function () { target.dataset.adminMode = 'catalog' })
    setMode('catalog')
  })
})(window, document)
