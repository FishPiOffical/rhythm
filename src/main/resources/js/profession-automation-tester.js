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
/* 职业自动化试运行与累计效果展示。 */
(function (window) {
  'use strict'

  var sequences = new WeakMap()

  function test(editors, button) {
    var form = button.form
    var item = editors.automationCollection.itemFor(button)
    var card = button.closest('.profession-automation-card')
    var sequence = nextSequence(card)
    window.ProfessionAdmin.setPending(card, true)
    return window.ProfessionAdmin.request('/api/profession/admin/automation/test', 'POST', {
      configurationJson: item.configurationJson,
      payloadJson: item.payloadJson
    }).then(function (value) {
      if (!isCurrent(card, sequence)) return
      var effects = scaleEffects(value.effects || [], item.eventCount)
      item.result.textContent = testText(value, effects, item.eventCount)
      if (value.matched) dispatch(form.professionId.value, item.eventCount, effects)
    }).catch(function (error) {
      if (isCurrent(card, sequence)) item.result.textContent = error.message
    }).finally(function () {
      if (isCurrent(card, sequence)) window.ProfessionAdmin.setPending(card, false)
    })
  }

  function nextSequence(card) {
    var sequence = (sequences.get(card) || 0) + 1
    sequences.set(card, sequence)
    return sequence
  }

  function isCurrent(card, sequence) { return sequences.get(card) === sequence }

  function scaleEffects(effects, count) {
    return effects.map(function (effect) {
      var result = Object.assign({}, effect)
      if (result.experienceDelta !== undefined) result.experienceDelta = Number(result.experienceDelta) * count
      result.eventCount = count
      return result
    })
  }

  function dispatch(professionId, count, effects) {
    window.dispatchEvent(new CustomEvent('profession-automation-tested', {detail: {
      professionId: professionId, trigger: count === 1 ? '规则测试' : '规则测试 · ' + count + ' 次', effects: effects
    }}))
  }

  function testText(value, effects, count) {
    if (!value.matched) return '条件不满足，未产生经验或通知。'
    if (!effects.length) return '规则符合，但没有执行内容。'
    var perEvent = value.effects.map(function (effect) { return effectText(effect, 1) }).join('；')
    if (count === 1) return perEvent
    return '每次：' + perEvent + '；共 ' + count + ' 次：' + effects.map(function (effect) { return effectText(effect, count) }).join('；')
  }

  function effectText(effect, count) {
    if (effect.experienceDelta !== undefined) return '增加 ' + effect.experienceDelta + ' 经验'
    if (effect.contributionRecorded) return count === 1 ? '记录一次贡献' : '记录 ' + count + ' 次贡献'
    return count === 1 ? '发送系统通知' : '发送系统通知 ' + count + ' 次'
  }

  window.ProfessionAutomationTester = {test: test}
})(window)
