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
/* 职业公开资料与本人职业详情渲染。 */
(function (window, document) {
  'use strict'

  var profile = window.ProfessionProfile || {}

  function element(tag, className, text) {
    var node = document.createElement(tag)
    if (className) node.className = className
    if (text !== undefined) node.textContent = text
    return node
  }

  function number(value) {
    return new Intl.NumberFormat('zh-CN').format(Number(value || 0))
  }

  function time(value) {
    if (!value) return '暂无记录'
    return new Date(Number(value)).toLocaleString('zh-CN', {hour12: false})
  }

  function experience(value) {
    var amount = Number(value || 0)
    return (amount > 0 ? '+' : '') + number(amount) + ' 经验'
  }

  function sourceName(item) {
    return item.sourceLabel || '职业活动'
  }

  function section(title, className) {
    var container = element('section', 'profession-detail__section ' + (className || ''))
    container.appendChild(element('h2', 'profession-detail__title', title))
    return container
  }

  function professionIndex(data) {
    var result = {}
    ;(data.professions || []).forEach(function (item) { result[item.professionId] = item })
    if (data.primaryProfession) result[data.primaryProfession.professionId] = data.primaryProfession
    return result
  }

  function summaryCard(profession, rarity) {
    var card = element('div', 'profession-detail__summary')
    var presentation = profession.presentation || {}
    var animation = presentation.levelUpAnimation
    if (animation && animation !== 'none') card.classList.add('profession-detail__summary--animation-' + animation)
    if (presentation.textureUrl) card.appendChild(cover(presentation.textureUrl))
    card.appendChild(profile.card(profession))
    if (profession.description) card.appendChild(element('p', '', profession.description))
    var metrics = element('div', 'profession-detail__metrics')
    if (profession.totalExperience !== undefined) metrics.appendChild(element('span', 'profession-detail__metric',
      number(profession.totalExperience) + ' 经验'))
    if (rarity) metrics.appendChild(element('span', 'profession-detail__metric', '超越 ' + number(rarity.surpassedPercent) + '% 用户'))
    if (metrics.childNodes.length) card.appendChild(metrics)
    return card
  }

  function cover(url) {
    var image = element('img', 'profession-detail__cover')
    image.src = url
    image.alt = ''
    image.decoding = 'async'
    return image
  }

  function contributionItem(item, profession) {
    var value = element('li', 'profession-detail__row')
    value.appendChild(element('strong', '', profession ? profession.displayName : '职业贡献'))
    value.appendChild(element('span', '', number(item.eventCount) + ' 次'))
    value.appendChild(element('span', 'profession-detail__positive', experience(item.experienceSum)))
    return value
  }

  function appendContributions(parent, values, professions, title) {
    if (!values || !values.length) return
    var block = section(title, 'profession-detail__contributions')
    var list = element('ul', 'profession-detail__list')
    values.forEach(function (item) { list.appendChild(contributionItem(item, professions[item.professionId])) })
    block.appendChild(list)
    parent.appendChild(block)
  }

  function appendActivity(parent, values, professions) {
    if (!values || !values.length) return
    var block = section('职业动态', 'profession-detail__activity')
    block.appendChild(detailHeader(['职业', '来源', '经验变化', '时间'], 'profession-detail__activity-header'))
    var list = element('ul', 'profession-detail__list')
    values.forEach(function (item) {
      var row = element('li', 'profession-detail__row profession-detail__activity-row')
      var profession = professions[item.professionId]
      row.appendChild(detailField('职业', '', profession ? profession.displayName : '职业'))
      row.appendChild(detailField('来源', 'profession-detail__activity-source', sourceName(item)))
      row.appendChild(detailField('经验变化', 'profession-detail__positive', experience(item.experienceDelta)))
      row.appendChild(detailField('时间', 'profession-detail__time', time(item.occurredAt)))
      list.appendChild(row)
    })
    block.appendChild(list)
    parent.appendChild(block)
  }

  function appendLevelHistory(parent, values, professions) {
    if (!values || !values.length) return
    var block = section('等级变化')
    var list = element('ul', 'profession-detail__list')
    values.forEach(function (item) {
      var profession = professions[item.professionId]
      var row = element('li', 'profession-detail__row')
      row.appendChild(element('strong', '', profession ? profession.displayName : '职业'))
      row.appendChild(element('span', '', '等级已更新'))
      row.appendChild(element('time', '', time(item.occurredAt)))
      list.appendChild(row)
    })
    block.appendChild(list)
    parent.appendChild(block)
  }

  function appendRewards(parent, values, professions) {
    if (!values || !values.length) return
    var block = section('职业奖励')
    var list = element('ul', 'profession-detail__list')
    values.forEach(function (item) {
      var profession = professions[item.professionId]
      var row = element('li', 'profession-detail__row')
      row.appendChild(element('strong', '', profession ? profession.displayName : '职业'))
      row.appendChild(element('span', '', item.rewardType === 'MEDAL' ? '已发放勋章' : '已发放积分'))
      row.appendChild(element('time', '', time(item.grantedAt)))
      list.appendChild(row)
    })
    block.appendChild(list)
    parent.appendChild(block)
  }

  function appendRanking(parent, values, professions) {
    if (!values || !values.length) return
    var block = section('职业排行')
    var list = element('ul', 'profession-detail__list')
    values.forEach(function (item) {
      var profession = professions[item.professionId]
      var row = element('li', 'profession-detail__row')
      row.appendChild(element('strong', '', profession ? profession.displayName : '职业'))
      row.appendChild(element('span', '', '第 ' + number(item.rank) + ' 名'))
      row.appendChild(element('span', '', '超越 ' + number(item.surpassedPercent) + '% 用户'))
      list.appendChild(row)
    })
    block.appendChild(list)
    parent.appendChild(block)
  }

  function appendPublicProfessions(root, data) {
    if (!data.professions || !data.professions.length) return
    var block = section('全部职业')
    var grid = element('div', 'profession-detail__grid')
    data.professions.forEach(function (item) { grid.appendChild(summaryCard(item, item.rarity)) })
    block.appendChild(grid)
    root.appendChild(block)
  }

  function renderPublicPage(target, data) {
    var root = element('div', 'profession-detail profession-detail--public')
    var professions = professionIndex(data)
    if (data.primaryProfession) root.appendChild(summaryCard(data.primaryProfession, data.primaryProfession.rarity))
    appendPublicProfessions(root, data)
    var sections = element('div', 'profession-detail__sections')
    appendContributions(sections, data.majorContributions, professions, '主要贡献')
    appendContributions(sections, data.contributionStats, professions, '贡献统计')
    appendActivity(sections, data.activityFeed, professions)
    appendLevelHistory(sections, data.levelHistory, professions)
    appendRewards(sections, data.rewards, professions)
    appendRanking(sections, data.ranking, professions)
    if (sections.childNodes.length) root.appendChild(sections)
    if (!root.childNodes.length) root.appendChild(element('p', '', '暂无公开职业资料'))
    target.replaceChildren(root)
  }

  function appendLevels(root, data) {
    var block = section('等级路线')
    var list = element('ol', 'profession-detail__levels')
    data.levels.forEach(function (item) {
      var row = element('li', 'profession-detail__level')
      if (item.levelCode === data.profession.levelCode) row.classList.add('is-current')
      row.appendChild(element('strong', '', item.displayName))
      row.appendChild(element('span', '', number(item.requiredExperience) + ' 经验'))
      if (item.description) row.appendChild(element('p', '', item.description))
      list.appendChild(row)
    })
    block.appendChild(list)
    root.appendChild(block)
  }

  function progressInfo(data) {
    var levels = data.levels || []
    var index = levels.findIndex(function (item) { return item.levelCode === data.profession.levelCode })
    var current = levels[index] || {requiredExperience: 0}
    var next = levels[index + 1]
    var total = Number(data.profession.totalExperience || 0)
    var start = Number(current.requiredExperience || 0)
    var required = next ? Number(next.requiredExperience || start) : start
    var percent = next ? Math.max(0, Math.min(100, (total - start) * 100 / Math.max(1, required - start))) : 100
    return {current: current, next: next, total: total, required: required, percent: percent}
  }

  function appendProgress(root, data) {
    var info = progressInfo(data)
    var block = section('职业进展', 'profession-detail__progress')
    var progressStyle = data.profession.presentation && data.profession.presentation.progressStyle
    if (progressStyle) block.classList.add('profession-detail__progress--' + progressStyle)
    var header = element('div', 'profession-detail__progress-head')
    header.appendChild(element('strong', '', info.current.displayName || data.profession.levelName || '当前等级'))
    header.appendChild(element('span', '', number(info.total) + ' 经验'))
    var track = element('div', 'profession-detail__progress-track')
    var fill = element('span', 'profession-detail__progress-fill')
    fill.style.setProperty('--profession-progress', info.percent + '%')
    track.appendChild(fill)
    block.appendChild(header)
    block.appendChild(track)
    block.appendChild(element('p', 'profession-detail__progress-note', info.next
      ? '距 ' + info.next.displayName + ' 还差 ' + number(Math.max(0, info.required - info.total)) + ' 经验'
      : '已达当前方案最高等级'))
    root.appendChild(block)
  }

  function appendOwnContributions(root, values) {
    if (!values || !values.length) return
    var block = section('贡献统计')
    var list = element('ul', 'profession-detail__list')
    values.forEach(function (item) { list.appendChild(contributionItem(item)) })
    block.appendChild(list)
    root.appendChild(block)
  }

  function recordRow(item) {
    var row = element('li', 'profession-detail__row profession-detail__record')
    row.appendChild(detailField('行为', '', '获得经验'))
    row.appendChild(detailField('来源', 'profession-detail__activity-source', sourceName(item)))
    row.appendChild(detailField('经验变化', 'profession-detail__positive', experience(item.experienceDelta)))
    row.appendChild(detailField('累计经验', '', number(item.beforeExperience) + ' → ' + number(item.afterExperience)))
    row.appendChild(detailField('时间', 'profession-detail__time', time(item.occurredAt)))
    return row
  }

  function detailHeader(labels, className) {
    var header = element('div', 'profession-detail__record-header ' + className)
    labels.forEach(function (label) { header.appendChild(element('span', '', label)) })
    return header
  }

  function detailField(label, className, value) {
    var field = element('span', 'profession-detail__record-field ' + className)
    field.appendChild(element('b', 'profession-detail__record-label', label))
    field.appendChild(element('span', 'profession-detail__record-value', value))
    return field
  }

  function appendRecords(list, values) {
    values.forEach(function (item) { list.appendChild(recordRow(item)) })
  }

  function recordUrl(professionId, cursor) {
    var path = '/api/profession/me/' + encodeURIComponent(professionId) + '/records?limit=30'
    if (!cursor) return path
    return path + '&beforeOccurredAt=' + encodeURIComponent(cursor.beforeOccurredAt) +
      '&beforeEffectId=' + encodeURIComponent(cursor.beforeEffectId)
  }

  function loadRecords(list, button, professionId, cursor) {
    button.disabled = true
    button.textContent = '读取中'
    profile.request(recordUrl(professionId, cursor)).then(function (data) {
      appendRecords(list, data.records || [])
      if (!data.nextCursor) return button.remove()
      button.disabled = false
      button.textContent = '更多记录'
      button.onclick = function () { loadRecords(list, button, professionId, data.nextCursor) }
    }).catch(function () {
      button.disabled = false
      button.textContent = '重新读取'
    })
  }

  function appendRecordSection(root, professionId) {
    var block = section('最近 90 天记录')
    block.appendChild(detailHeader(['行为', '来源', '经验变化', '累计经验', '时间'], 'profession-detail__record-header--five'))
    var list = element('ul', 'profession-detail__list')
    var button = element('button', 'profession-detail__more', '读取记录')
    button.type = 'button'
    button.onclick = function () { loadRecords(list, button, professionId) }
    block.appendChild(list)
    block.appendChild(button)
    root.appendChild(block)
    button.click()
  }

  function renderOwnDetail(target, data) {
    var root = element('div', 'profession-detail profession-detail--own')
    root.appendChild(summaryCard(data.profession, data.rarity))
    appendProgress(root, data)
    appendLevels(root, data)
    appendOwnContributions(root, data.contributions)
    appendRecordSection(root, data.profession.professionId)
    target.replaceChildren(root)
  }

  profile.renderPublicPage = renderPublicPage
  profile.renderOwnDetail = renderOwnDetail
  window.ProfessionProfile = profile
})(window, document)
