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
/* 职业经验模拟，不写入用户经验、奖励或通知。 */
(function (window, document) {
  'use strict'

  var summaries = []
  var details = {}
  var entries = []

  function root() { return document.getElementById('professionAdmin') }
  function dialog() { return root().querySelector('#professionSimulationDialog') }
  function field(name) { return root().querySelector('[data-profession-simulation-' + name + ']') }
  function number(value) { return Number(value) }
  function currentProfession() { return details[field('profession').value] }

  function loadSummaries() {
    return window.ProfessionAdmin.loadAllCatalogSummaries().then(function (values) {
      summaries = values.filter(function (item) { return item.currentLevelSchemeId })
      field('profession').replaceChildren()
      summaries.forEach(function (item) {
        var option = document.createElement('option')
        option.value = item.oId
        option.textContent = item.displayName || item.professionCode
        field('profession').appendChild(option)
      })
      if (!summaries.length) throw new Error('没有已启用等级方案的职业')
      return summaries
    })
  }

  function loadDetail(professionId) {
    if (details[professionId]) return Promise.resolve(details[professionId])
    return window.ProfessionAdmin.detail(professionId).then(function (profession) {
      details[professionId] = profession
      return profession
    })
  }

  function levels(profession) {
    var scheme = profession && profession.schemes.find(function (item) { return String(item.oId) === String(profession.currentLevelSchemeId) })
    return scheme ? scheme.levels.slice().sort(function (left, right) { return left.requiredTotalExperience - right.requiredTotalExperience }) : []
  }

  function levelFor(values, experience) {
    return values.reduce(function (current, level) {
      return level.requiredTotalExperience <= experience ? level : current
    }, values[0])
  }

  function simulation() {
    var initial = number(field('current').value)
    if (!Number.isSafeInteger(initial) || initial < 0) throw new Error('当前经验需为非负整数')
    var profession = currentProfession()
    var values = levels(profession)
    if (!profession || !values.length) throw new Error('正在读取等级方案')
    return entries.reduce(function (result, entry) {
      var before = result.experience
      var after = Math.max(0, before + entry.delta)
      result.timeline.push(timelineEntry(entry, values, before, after))
      result.experience = after
      return result
    }, {levels: values, experience: initial, timeline: []})
  }

  function timelineEntry(entry, values, before, after) {
    return {entry: entry, before: before, after: after, beforeLevel: levelFor(values, before), afterLevel: levelFor(values, after),
      crossed: values.filter(function (level) { return level.requiredTotalExperience > before && level.requiredTotalExperience <= after })}
  }

  function render() {
    try {
      var value = simulation()
      var level = levelFor(value.levels, value.experience)
      field('experience').textContent = value.experience + ' EXP'
      field('level').textContent = level.displayName + ' · ' + level.levelCode
      field('timeline').replaceChildren.apply(field('timeline'), value.timeline.map(timelineItem))
    } catch (error) {
      field('experience').textContent = '—'
      field('level').textContent = error.message
      field('timeline').replaceChildren()
    }
  }

  function timelineItem(value) {
    var item = document.createElement('li')
    var time = document.createElement('time')
    var trigger = document.createElement('strong')
    var experience = document.createElement('span')
    var detail = document.createElement('small')
    time.textContent = value.entry.time
    trigger.textContent = value.entry.trigger
    experience.textContent = signed(value.entry.delta) + ' EXP · ' + value.after + ' EXP'
    detail.textContent = detailText(value)
    item.append(time, trigger, experience, detail)
    return item
  }

  function detailText(value) {
    var parts = []
    if (value.beforeLevel.levelCode !== value.afterLevel.levelCode) parts.push('等级：' + value.beforeLevel.displayName + ' → ' + value.afterLevel.displayName)
    var rewards = value.crossed.flatMap(function (level) { return level.rewards.map(rewardText) })
    if (rewards.length) parts.push('奖励：' + rewards.join('、'))
    if (value.entry.notifications.length) parts.push('通知：' + value.entry.notifications.map(notificationText).join('、'))
    if (value.entry.contributionCount) parts.push('贡献：记录 ' + value.entry.contributionCount + ' 次')
    return parts.join('；') || '等级不变'
  }

  function notificationText(value) {
    return value.count > 1 ? value.content + ' ×' + value.count : value.content
  }

  function rewardText(reward) {
    var config = JSON.parse(reward.rewardConfigJson || '{}')
    if (reward.rewardType === 'POINT') return '+' + config.amount + ' 积分'
    if (reward.rewardType === 'MEDAL') return '勋章 ' + (config.medalId || '')
    return reward.rewardType
  }

  function signed(value) { return value > 0 ? '+' + value : String(value) }

  function add() {
    var delta = number(field('delta').value)
    if (!Number.isSafeInteger(delta) || delta < 0) throw new Error('经验值需为非负整数')
    entries.push({delta: delta * number(field('direction').value), trigger: field('trigger').value.trim() || '手动调整',
      time: field('time').value || localTime(), notifications: []})
    render()
  }

  function localTime() {
    var value = new Date()
    value.setMinutes(value.getMinutes() - value.getTimezoneOffset())
    return value.toISOString().slice(0, 16).replace('T', ' ')
  }

  function open() {
    loadSummaries().then(function () {
      entries = []
      field('time').value = localTime().replace(' ', 'T')
      return loadDetail(field('profession').value)
    }).then(function () {
      dialog().showModal()
      render()
    }).catch(showError)
  }

  function changeProfession() {
    entries = []
    loadDetail(field('profession').value).then(render).catch(showError)
  }

  function showError(error) {
    field('level').textContent = error.message
    root().querySelector('[data-profession-admin-status]').textContent = error.message
  }

  function ruleTest(detail) {
    var notifications = detail.effects.filter(function (effect) { return effect.notificationContent }).map(function (effect) {
      return {content: effect.notificationContent, count: Number(effect.eventCount || 1)}
    })
    var delta = detail.effects.reduce(function (sum, effect) { return sum + Number(effect.experienceDelta || 0) }, 0)
    loadSummaries().then(function () {
      field('profession').value = detail.professionId
      return loadDetail(detail.professionId)
    }).then(function () {
      entries = [{delta: delta, trigger: detail.trigger, time: localTime(), notifications: notifications,
        contributionCount: detail.effects.filter(function (effect) { return effect.contributionRecorded }).reduce(function (sum, effect) {
          return sum + Number(effect.eventCount || 1)
        }, 0)}]
      if (!dialog().open) dialog().showModal()
      render()
    }).catch(showError)
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!root()) return
    root().addEventListener('click', function (event) {
      var button = event.target.closest('button')
      if (!button) return
      if (button.dataset.professionSimulationOpen !== undefined) open()
      if (button.dataset.professionSimulationAdd !== undefined) {
        try { add() } catch (error) { showError(error) }
      }
    })
    root().addEventListener('input', function (event) {
      if (event.target.matches('[data-profession-simulation-current]')) render()
    })
    root().addEventListener('change', function (event) {
      if (event.target.matches('[data-profession-simulation-profession]')) changeProfession()
    })
    window.addEventListener('profession-automation-tested', function (event) { ruleTest(event.detail) })
  })
})(window, document)
