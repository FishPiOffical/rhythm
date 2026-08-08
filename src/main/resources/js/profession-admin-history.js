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
/* 职业、等级方案和经验规则的按需历史记录。 */
(function (window, document) {
  'use strict'

  function root() { return document.getElementById('professionAdmin') }
  function dialog() { return document.getElementById('professionHistoryDialog') }
  function state(value) { return ({DRAFT: '草稿', PUBLISHED: '已启用', RETIRED: '已停用'})[value] || value }

  function name(profession) {
    var current = profession.revisions.find(function (item) { return String(item.oId) === String(profession.currentRevisionId) })
    return current ? current.displayName : latest(profession.revisions).displayName || profession.professionCode
  }

  function latest(values) { return ordered(values)[0] || {} }
  function ordered(values) { return values.slice().sort(function (left, right) { return right.revisionNo - left.revisionNo }) }

  function button(holder, label, action, itemId, professionId) {
    var value = document.createElement('button')
    value.type = 'button'
    value.textContent = label
    value.dataset.professionAction = action
    value.dataset.itemId = itemId
    if (professionId) value.dataset.professionId = professionId
    holder.appendChild(value)
  }

  function group(title) {
    var value = document.createElement('section')
    value.className = 'profession-admin__group'
    var heading = document.createElement('h3')
    heading.textContent = title
    value.appendChild(heading)
    return value
  }

  function record(title, revision, previous, names) {
    var value = document.createElement('article')
    value.className = 'profession-admin__history-record'
    var header = document.createElement('header')
    var heading = document.createElement('strong')
    heading.textContent = title
    var badge = document.createElement('span')
    badge.className = 'profession-admin__state profession-admin__state--' + String(revision.status || '').toLowerCase()
    badge.textContent = state(revision.status)
    header.append(heading, badge)
    var meta = document.createElement('small')
    meta.textContent = '保存于 ' + date(revision.publishedAt || revision.createdAt) + ' · 操作人 ' + operator(revision, names)
    value.append(header, meta)
    if (previous) value.appendChild(changes(previous, revision))
    return value
  }

  function date(value) {
    return value ? new Date(Number(value)).toLocaleString('zh-CN', {hour12: false}) : '未记录时间'
  }

  function operator(value, names) {
    var id = value.publishedBy || value.createdBy
    return id ? names[id] || id : '未记录'
  }

  function changes(previous, current) {
    var fields = [
      ['displayName', '名称'], ['shortName', '简称'], ['description', '说明'],
      ['migrationPolicy', '生效方式'], ['rewardMigrationPolicy', '奖励迁移'],
      ['levelCount', '等级数量'], ['configurationJson', '规则配置']
    ]
    var rows = fields.filter(function (field) { return previous[field[0]] !== current[field[0]] })
    var details = document.createElement('details')
    var summary = document.createElement('summary')
    summary.textContent = rows.length ? '查看变更' : '没有字段变更'
    details.appendChild(summary)
    rows.forEach(function (field) {
      var row = document.createElement('p')
      row.textContent = field[1] + '：' + value(previous[field[0]]) + ' → ' + value(current[field[0]])
      details.appendChild(row)
    })
    return details
  }

  function value(input) {
    var text = String(input || '')
    return text.length > 120 ? text.slice(0, 120) + '…' : text || '未设置'
  }

  function definitionHistory(profession, names) {
    var holder = group('职业资料')
    var revisions = ordered(profession.revisions)
    revisions.forEach(function (revision, index) {
      var entry = record('职业资料', revision, revisions[index + 1], names)
      if (revision.status === 'DRAFT') button(entry, '启用', 'definition/publish', revision.oId)
      button(entry, '还原', 'definition/rollback', revision.oId, profession.oId)
      holder.appendChild(entry)
    })
    if (profession.status !== 'RETIRED') button(holder, '停用职业', 'definition/retire', profession.oId)
    return holder
  }

  function schemeHistory(profession, names) {
    var holder = group('等级方案')
    var schemes = ordered(profession.schemes)
    schemes.forEach(function (scheme, index) {
      var summary = Object.assign({}, scheme, {levelCount: scheme.levels.length})
      var previous = schemes[index + 1]
      if (previous) previous = Object.assign({}, previous, {levelCount: previous.levels.length})
      var entry = record('等级 ' + scheme.levels.length + ' 个', summary, previous, names)
      button(entry, '编辑', 'edit-scheme', scheme.oId, profession.oId)
      if (scheme.status === 'DRAFT') button(entry, '启用', 'scheme/publish', scheme.oId, profession.oId)
      button(entry, '另存', 'scheme/copy', scheme.oId, profession.oId)
      button(entry, '还原', 'scheme/rollback', scheme.oId, profession.oId)
      if (scheme.status !== 'RETIRED') button(entry, '停用', 'scheme/retire', scheme.oId, profession.oId)
      holder.appendChild(entry)
    })
    return holder
  }

  function automationHistory(profession, names) {
    var holder = group('经验规则')
    profession.automations.forEach(function (automation) {
      var revisions = ordered(automation.revisions)
      revisions.forEach(function (revision, index) {
        var entry = record(automation.automationCode, revision, revisions[index + 1], names)
        button(entry, '编辑', 'edit-automation', automation.oId, profession.oId)
        if (revision.status === 'DRAFT') button(entry, '启用', 'automation/publish', revision.oId, profession.oId)
        button(entry, '另存', 'automation/copy', revision.oId, profession.oId)
        button(entry, '还原', 'automation/rollback', revision.oId, automation.oId)
        holder.appendChild(entry)
      })
      if (automation.status !== 'RETIRED') button(holder, '停用 ' + automation.automationCode, 'automation/retire', automation.oId)
    })
    return holder
  }

  function render(profession) {
    var holder = dialog().querySelector('[data-profession-history]')
    dialog().querySelector('#professionHistoryTitle').textContent = name(profession) + '的历史记录'
    var names = profession.operatorNames || {}
    holder.replaceChildren(definitionHistory(profession, names), schemeHistory(profession, names), automationHistory(profession, names))
  }

  function open(professionId) {
    window.ProfessionAdmin.detail(professionId).then(function (profession) {
      render(profession)
      dialog().showModal()
    }).catch(function (error) { root().querySelector('[data-profession-admin-status]').textContent = error.message })
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!root()) return
    window.addEventListener('profession-history-open', function (event) { open(event.detail) })
    document.addEventListener('click', function (event) {
      var button = event.target.closest('[data-profession-dialog-close]')
      if (button) button.closest('dialog').close()
    })
  })
})(window, document)
