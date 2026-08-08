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
/* 职业自动化可视化编排入口。 */
(function (window, document) {
  'use strict'

  function create(form, metadata) {
    var triggers = metadata.triggers
    var select = form.querySelector('[data-automation-trigger]')
    var noCondition = form.querySelector('[data-automation-no-condition]')
    var condition = window.ProfessionAutomationConditionEditor.create(form.querySelector('[data-automation-conditions]'), triggers[0])
    var actions = window.ProfessionAutomationCalculatorEditor.create(form.querySelector('[data-automation-actions]'),
      triggers[0], metadata.calculatorTypes, metadata.actions)
    fillTriggers(select, triggers)
    renderPayload(form, triggers[0])
    select.addEventListener('change', function () { update(form, triggers, condition, actions) })
    noCondition.addEventListener('change', function () { form.querySelector('[data-automation-conditions]').hidden = noCondition.checked })
    return {configuration: function () { return configuration(select, triggers, condition, actions, noCondition.checked) }, payload: function () { return payload(form, selected(select, triggers)) },
      load: function (value) { load(form, value, triggers, select, condition, actions, noCondition) }}
  }

  function load(form, value, triggers, select, condition, actions, noCondition) {
    var trigger = triggers.find(function (item) { return item.triggerType === value.trigger.triggerType })
    if (!trigger) throw new Error('已保存的触发时机不可用')
    select.value = trigger.triggerType
    condition.setTrigger(trigger)
    actions.setTrigger(trigger)
    noCondition.checked = !value.condition
    form.querySelector('[data-automation-conditions]').hidden = noCondition.checked
    if (value.condition) condition.load(value.condition)
    actions.load(value.actions)
    renderPayload(form, trigger)
  }

  function fillTriggers(select, triggers) {
    select.replaceChildren()
    triggers.forEach(function (trigger) { select.appendChild(option(trigger.triggerType, triggerName(trigger.triggerType))) })
  }

  function update(form, triggers, condition, actions) {
    var trigger = selected(form.querySelector('[data-automation-trigger]'), triggers)
    condition.setTrigger(trigger)
    actions.setTrigger(trigger)
    renderPayload(form, trigger)
  }

  function renderPayload(form, trigger) {
    var holder = form.querySelector('[data-automation-payload]')
    holder.replaceChildren()
    trigger.fields.forEach(function (field) { holder.appendChild(payloadField(field)) })
  }

  function payloadField(field) {
    var label = document.createElement('label')
    label.textContent = fieldName(field.fieldCode)
    var input = document.createElement('input')
    input.type = field.fieldType === 'TEXT' ? 'text' : 'number'
    input.value = field.fieldType === 'TEXT' ? '' : '0'
    input.dataset.payloadField = field.fieldCode
    input.dataset.payloadType = field.fieldType
    label.appendChild(input)
    return label
  }

  function configuration(select, triggers, condition, actions, noCondition) {
    var trigger = selected(select, triggers)
    return {trigger: {triggerType: trigger.triggerType, schemaVersion: trigger.schemaVersion}, condition: noCondition ? null : condition.value(), actions: actions.value()}
  }

  function payload(form, trigger) {
    var result = {}
    trigger.fields.forEach(function (field) {
      var input = form.querySelector('[data-payload-field="' + field.fieldCode + '"]')
      result[field.fieldCode] = field.fieldType === 'TEXT' ? input.value : Number(input.value)
    })
    return result
  }

  function selected(select, triggers) { return triggers.find(function (trigger) { return trigger.triggerType === select.value }) }
  function option(value, label) { var element = document.createElement('option'); element.value = value; element.textContent = label; return element }
  function triggerName(value) { return ({'long_article.read_settled': '长篇阅读结算', 'article.published': '发布帖子', 'comment.published': '发表评论', 'breezemoon.published': '发布清风明月', 'repeater.published': '发布复读机内容', 'chatroom.message.published': '聊天室发言', 'user.online.settled': '在线时长结算'})[value] || value }
  function fieldName(value) { return ({settlementId: '结算编号', articleId: '帖子编号', authorUserId: '作者', windowStart: '统计开始时间', windowEnd: '统计结束时间', registeredReaderCount: '登录读者数', anonymousReaderCount: '匿名读者数', countedAnonymousReaderCount: '计入读者数', rewardPoint: '阅读积分', articleType: '帖子类型', tagCount: '标签数', wordCount: '字数', publishedAt: '发布时间', commentId: '评论编号', parentCommentId: '原评论编号', breezemoonId: '清风明月编号', contentId: '复读机内容编号', contentType: '内容类型', messageId: '消息编号', messageType: '消息类型', contentLength: '消息字数', sentAt: '发送时间', userId: '用户', onlineMinuteBefore: '结算前在线分钟', onlineMinuteAfter: '结算后在线分钟', onlineMinuteDelta: '本次在线分钟', settledAt: '结算时间'})[value] || value }

  window.ProfessionAutomationEditor = {create: create}
})(window, document)
