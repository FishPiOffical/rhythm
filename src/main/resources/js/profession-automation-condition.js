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
/* 职业自动化条件树编辑器。 */
(function (window, document) {
  'use strict'

  var types = ['LEAF', 'ALL', 'ANY', 'NOT']
  var labels = {LEAF: '条件', ALL: '全部满足', ANY: '任一满足', NOT: '不满足'}

  function create(holder, trigger) {
    var state = initial(trigger)
    function render() { holder.replaceChildren(nodeView(state, trigger, false)) }
    holder.addEventListener('change', function (event) { change(event, trigger, render) })
    holder.addEventListener('input', function (event) { updateValue(event) })
    holder.addEventListener('click', function (event) { click(event, trigger, render) })
    render()
    return {value: function () { return value(state) }, setTrigger: function (next) { trigger = next; state = initial(trigger); render() },
      load: function (next) { state = JSON.parse(JSON.stringify(next)); render() }}
  }

  function initial(trigger) { return {type: 'LEAF', fieldCode: trigger.fields[0].fieldCode, operator: trigger.fields[0].operators[0], value: ''} }

  function nodeView(model, trigger, removable) {
    var element = document.createElement('section')
    element.className = 'profession-automation__condition'
    element._condition = model
    element.appendChild(typeControl(model))
    if (model.type === 'LEAF') element.appendChild(leafView(model, trigger))
    if (model.type === 'NOT') element.appendChild(nodeView(model.child, trigger, false))
    if (model.type === 'ALL' || model.type === 'ANY') element.appendChild(groupView(model, trigger))
    if (removable) element.appendChild(button('删除', 'data-remove-condition'))
    return element
  }

  function typeControl(model) {
    var select = document.createElement('select')
    select.dataset.conditionType = 'true'
    types.forEach(function (type) { select.appendChild(option(type, labels[type], model.type)) })
    return select
  }

  function leafView(model, trigger) {
    var holder = document.createElement('div')
    holder.className = 'profession-automation__condition-fields'
    holder.appendChild(fieldSelect(trigger.fields, model.fieldCode))
    var field = fieldFor(trigger, model.fieldCode)
    holder.appendChild(operatorSelect(field.operators, model.operator))
    valueControls(holder, model)
    return holder
  }

  function fieldSelect(fields, selected) {
    var select = document.createElement('select')
    select.dataset.conditionField = 'true'
    fields.forEach(function (field) { select.appendChild(option(field.fieldCode, fieldName(field.fieldCode), selected)) })
    return select
  }

  function operatorSelect(operators, selected) {
    var select = document.createElement('select')
    select.dataset.conditionOperator = 'true'
    operators.forEach(function (operator) { select.appendChild(option(operator, operatorName(operator), selected)) })
    return select
  }

  function valueControls(holder, model) {
    if (model.operator === 'BETWEEN') return rangeControls(holder, model, 'min', 'max')
    if (model.operator === 'IN') return input(holder, 'values', '多个值用逗号分隔', model.values || '')
    if (model.operator === 'IN_WINDOW') return rangeControls(holder, model, 'from', 'to')
    if (model.operator === 'IS_EMPTY') return
    input(holder, 'value', '条件值', model.value || '')
  }

  function rangeControls(holder, model, first, second) {
    input(holder, first, first, model[first] || '')
    input(holder, second, second, model[second] || '')
  }

  function input(holder, key, placeholder, current) {
    var element = document.createElement('input')
    element.type = 'text'
    element.placeholder = placeholder
    element.value = current
    element.dataset.conditionValue = key
    holder.appendChild(element)
  }

  function groupView(model, trigger) {
    var holder = document.createElement('div')
    holder.className = 'profession-automation__condition-group'
    model.children.forEach(function (child) { holder.appendChild(nodeView(child, trigger, true)) })
    holder.appendChild(button('添加条件', 'data-add-condition'))
    return holder
  }

  function change(event, trigger, render) {
    var node = event.target.closest('.profession-automation__condition')
    if (!node) return
    var model = node._condition
    if (event.target.dataset.conditionType) replaceType(model, event.target.value, trigger)
    if (event.target.dataset.conditionField) replaceField(model, event.target.value, trigger)
    if (event.target.dataset.conditionOperator) model.operator = event.target.value
    updateValue(event)
    render()
  }

  function updateValue(event) {
    if (!event.target.dataset.conditionValue) return
    var node = event.target.closest('.profession-automation__condition')
    if (node) node._condition[event.target.dataset.conditionValue] = event.target.value
  }

  function replaceType(model, type, trigger) {
    var next = type === 'LEAF' ? initial(trigger) : type === 'NOT' ? {type: type, child: initial(trigger)} : {type: type, children: [initial(trigger)]}
    Object.keys(model).forEach(function (key) { delete model[key] })
    Object.assign(model, next)
  }

  function replaceField(model, fieldCode, trigger) {
    var field = fieldFor(trigger, fieldCode)
    model.fieldCode = fieldCode
    model.operator = field.operators[0]
    model.value = ''
    delete model.min; delete model.max; delete model.values; delete model.from; delete model.to
  }

  function click(event, trigger, render) {
    var button = event.target.closest('button')
    if (!button) return
    var node = button.closest('.profession-automation__condition')
    if (button.dataset.addCondition) node._condition.children.push(initial(trigger))
    if (button.dataset.removeCondition) removeChild(holderFor(node), node._condition)
    render()
  }

  function holderFor(node) { return node.parentElement.closest('.profession-automation__condition') }

  function removeChild(parent, child) {
    if (!parent || !parent._condition.children) return
    parent._condition.children.splice(parent._condition.children.indexOf(child), 1)
  }

  function value(model) {
    if (model.type === 'LEAF') return leafValue(model)
    if (model.type === 'NOT') return {type: 'NOT', child: value(model.child)}
    return {type: model.type, children: model.children.map(value)}
  }

  function leafValue(model) {
    var result = {type: 'LEAF', fieldCode: model.fieldCode, operator: model.operator}
    if (model.operator === 'BETWEEN') return Object.assign(result, {min: number(model.min), max: number(model.max)})
    if (model.operator === 'IN') return Object.assign(result, {values: model.values.split(',').map(valueFor).filter(Boolean)})
    if (model.operator === 'IN_WINDOW') return Object.assign(result, {from: number(model.from), to: number(model.to)})
    if (model.operator === 'IS_EMPTY') return result
    return Object.assign(result, {value: valueFor(model.value)})
  }

  function valueFor(value) { return /^-?\d+(\.\d+)?$/.test(String(value).trim()) ? Number(value) : String(value).trim() }
  function number(value) { return Number(value) }
  function fieldFor(trigger, code) { return trigger.fields.find(function (field) { return field.fieldCode === code }) }
  function option(value, label, selected) { var element = document.createElement('option'); element.value = value; element.textContent = label; element.selected = value === selected; return element }
  function button(label, data) { var element = document.createElement('button'); element.type = 'button'; element.textContent = label; element.setAttribute(data, 'true'); return element }
  function fieldName(value) { return ({settlementId: '结算编号', articleId: '帖子编号', authorUserId: '作者', windowStart: '统计开始时间', windowEnd: '统计结束时间', registeredReaderCount: '登录读者数', anonymousReaderCount: '匿名读者数', countedAnonymousReaderCount: '计入读者数', rewardPoint: '阅读积分', articleType: '帖子类型', tagCount: '标签数', wordCount: '字数', publishedAt: '发布时间', commentId: '评论编号', parentCommentId: '原评论编号', breezemoonId: '清风明月编号', contentId: '复读机内容编号', contentType: '内容类型', messageId: '消息编号', messageType: '消息类型', contentLength: '消息字数', sentAt: '发送时间'})[value] || value }
  function operatorName(value) { return ({EQ: '等于', NE: '不等于', GT: '大于', GTE: '不少于', LT: '小于', LTE: '不超过', BETWEEN: '介于', IN: '属于', PREFIX: '开头是', SUFFIX: '结尾是', CONTAINS: '包含', IS_EMPTY: '为空', IN_WINDOW: '位于区间', OLDER_THAN: '早于', NEWER_THAN: '晚于'})[value] || value }

  window.ProfessionAutomationConditionEditor = {create: create}
})(window, document)
