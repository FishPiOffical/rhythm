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
/* 职业自动化执行器与经验计算器编辑器。 */
(function (window, document) {
  'use strict'

  var binary = ['ADD', 'SUBTRACT', 'MULTIPLY', 'DIVIDE', 'MIN', 'MAX']

  function create(holder, trigger, types, actionTypes) {
    var actions = [action(actionTypes)]
    function render() { holder.replaceChildren.apply(holder, actions.map(function (item, index) { return actionView(item, index, trigger, types, actionTypes) }).concat([button('添加执行器', 'data-add-action')])) }
    holder.addEventListener('change', function (event) { change(event, trigger, actionTypes, render) })
    holder.addEventListener('input', function (event) { inputChange(event) })
    holder.addEventListener('click', function (event) { click(event, trigger, actions, actionTypes, render) })
    render()
    return {value: function () { return actions.map(function (item, index) { return actionValue(item, index, actions, actionTypes) }) },
      setTrigger: function (next) { trigger = next; actions.forEach(function (item) { resetFields(item, trigger) }); render() },
      load: function (next) { actions.splice(0, actions.length); next.forEach(function (item) { actions.push(loadAction(item, actionTypes)) }); render() }}
  }

  function action(actionTypes) { return actionFor(typeFor(actionTypes, 'profession.experience.adjust')) }
  function actionFor(type) {
    if (type.configType === 'NOTIFICATION') return {actionType: type.actionType, notificationContent: '', notificationWhen: 'ALWAYS'}
    if (type.configType === 'CONTRIBUTION') return {actionType: type.actionType}
    return {actionType: type.actionType, calculator: calculator('FIXED')}
  }
  function loadAction(value, actionTypes) {
    var model = actionFor(typeFor(actionTypes, value.actionType))
    if (model.calculator) model.calculator = JSON.parse(JSON.stringify(value.calculator || calculator('FIXED')))
    if (model.notificationContent !== undefined) {
      model.notificationContent = value.notificationContent || ''
      model.notificationWhen = value.notificationWhen || 'ALWAYS'
    }
    return model
  }

  function typeFor(actionTypes, actionType) { return actionTypes.find(function (item) { return item.actionType === actionType }) || actionTypes[0] }
  function calculator(type) { return {calculatorType: type, value: 1} }

  function actionView(model, index, trigger, types, actionTypes) {
    var element = document.createElement('section')
    element.className = 'profession-automation__action'
    element._action = model
    element.appendChild(actionTypeSelect(model, actionTypes))
    if (model.calculator) element.appendChild(calculatorView(model.calculator, trigger, types))
    if (model.notificationContent !== undefined) element.appendChild(notificationView(model))
    if (index > 0) element.appendChild(button('删除', 'data-remove-action'))
    return element
  }

  function actionTypeSelect(model, actionTypes) {
    var label = document.createElement('label')
    label.textContent = '执行内容'
    var select = document.createElement('select')
    select.dataset.actionType = 'true'
    actionTypes.forEach(function (type) { select.appendChild(option(type.actionType, type.displayName, model.actionType)) })
    label.appendChild(select)
    return label
  }

  function notificationView(model) {
    var holder = document.createElement('section')
    holder.className = 'profession-automation__notification'
    holder.appendChild(notificationContent(model))
    holder.appendChild(notificationWhen(model))
    var hint = document.createElement('small')
    hint.textContent = '可用：{职业} {等级} {经验} {变化} {奖励}'
    holder.appendChild(hint)
    return holder
  }

  function notificationContent(model) {
    var label = document.createElement('label')
    label.textContent = '通知内容'
    var area = document.createElement('textarea')
    area.maxLength = 4096
    area.placeholder = '恭喜升级至 {职业} 的 {等级}，奖励：{奖励}'
    area.value = model.notificationContent
    area.dataset.notificationContent = 'true'
    label.appendChild(area)
    return label
  }

  function notificationWhen(model) {
    var label = document.createElement('label')
    label.textContent = '发送时机'
    var select = document.createElement('select')
    select.dataset.notificationWhen = 'true'
    select.appendChild(option('ALWAYS', '每次触发', model.notificationWhen))
    select.appendChild(option('LEVEL_UP', '仅升级时发送', model.notificationWhen))
    label.appendChild(select)
    return label
  }

  function calculatorView(model, trigger, types) {
    var element = document.createElement('section')
    element.className = 'profession-automation__calculator'
    element._calculator = model
    element.appendChild(typeSelect(model, types, trigger))
    if (model.calculatorType === 'FIXED') element.appendChild(input('value', '经验值', model.value))
    if (model.calculatorType === 'FIELD') element.appendChild(fieldSelect(trigger, model.fieldCode))
    if (binary.includes(model.calculatorType)) element.appendChild(pairView(model, trigger, types))
    if (model.calculatorType === 'CLAMP') element.appendChild(clampView(model, trigger, types))
    if (model.calculatorType === 'TIERED') element.appendChild(tieredView(model, trigger, types))
    if (model.calculatorType === 'WEIGHTED_SUM') element.appendChild(weightedView(model, trigger, types))
    return element
  }

  function typeSelect(model, types, trigger) {
    var select = document.createElement('select')
    select.dataset.calculatorType = 'true'
    types.filter(function (type) { return type !== 'FIELD' || numericFields(trigger).length > 0 }).forEach(function (type) { select.appendChild(option(type, calculatorName(type), model.calculatorType)) })
    return select
  }

  function fieldSelect(trigger, selected) {
    var select = document.createElement('select')
    select.dataset.calculatorField = 'true'
    numericFields(trigger).forEach(function (field) { select.appendChild(option(field.fieldCode, fieldName(field.fieldCode), selected)) })
    return select
  }

  function pairView(model, trigger, types) {
    var holder = group()
    holder.appendChild(calculatorView(model.left, trigger, types))
    holder.appendChild(calculatorView(model.right, trigger, types))
    if (model.calculatorType === 'DIVIDE') holder.appendChild(input('zeroValue', '除零值', model.zeroValue))
    return holder
  }

  function clampView(model, trigger, types) {
    var holder = group()
    holder.appendChild(calculatorView(model.value, trigger, types))
    holder.appendChild(input('min', '下限', model.min))
    holder.appendChild(input('max', '上限', model.max))
    return holder
  }

  function tieredView(model, trigger, types) {
    var holder = group()
    holder.appendChild(calculatorView(model.input, trigger, types))
    holder.appendChild(input('defaultValue', '默认值', model.defaultValue))
    model.tiers.forEach(function (tier) { holder.appendChild(tierView(tier)) })
    holder.appendChild(button('添加阶梯', 'data-add-tier'))
    return holder
  }

  function weightedView(model, trigger, types) {
    var holder = group()
    model.items.forEach(function (item) { holder.appendChild(weightedItem(item, trigger, types)) })
    holder.appendChild(button('添加项', 'data-add-weight'))
    return holder
  }

  function tierView(tier) {
    var holder = group()
    holder.dataset.tier = 'true'
    holder._tier = tier
    holder.appendChild(tierInput('minInclusive', '达到经验', tier.minInclusive))
    holder.appendChild(tierInput('value', '结果经验', tier.value))
    holder.appendChild(button('删除', 'data-remove-tier'))
    return holder
  }

  function weightedItem(item, trigger, types) {
    var holder = group()
    holder.dataset.weighted = 'true'
    holder._weighted = item
    holder.appendChild(calculatorView(item.value, trigger, types))
    holder.appendChild(weightInput(item.weight))
    holder.appendChild(button('删除', 'data-remove-weight'))
    return holder
  }

  function change(event, trigger, actionTypes, render) {
    var actionNode = event.target.closest('.profession-automation__action')
    var calculatorNode = event.target.closest('.profession-automation__calculator')
    if (event.target.dataset.actionType) {
      replaceAction(actionNode._action, actionFor(typeFor(actionTypes, event.target.value)))
      return render()
    }
    if (event.target.dataset.notificationWhen) actionNode._action.notificationWhen = event.target.value
    if (event.target.dataset.calculatorType) replace(calculatorNode._calculator, event.target.value, trigger)
    if (event.target.dataset.calculatorField) calculatorNode._calculator.fieldCode = event.target.value
    if (event.target.dataset.calculatorValue) calculatorNode._calculator[event.target.dataset.calculatorValue] = Number(event.target.value)
    if (event.target.dataset.weight && event.target.closest('[data-weighted]')) event.target.closest('[data-weighted]')._weighted.weight = Number(event.target.value)
    if (event.target.dataset.tierValue && event.target.closest('[data-tier]')) event.target.closest('[data-tier]')._tier[event.target.dataset.tierValue] = Number(event.target.value)
    render()
  }

  function inputChange(event) {
    if (!event.target.dataset.notificationContent) return
    event.target.closest('.profession-automation__action')._action.notificationContent = event.target.value
  }

  function click(event, trigger, actions, actionTypes, render) {
    var element = event.target.closest('button')
    if (!element) return
    if (element.dataset.addAction) actions.push(action(actionTypes))
    if (element.dataset.removeAction) actions.splice(actions.indexOf(element.closest('.profession-automation__action')._action), 1)
    if (element.dataset.addTier) element.closest('.profession-automation__calculator')._calculator.tiers.push({minInclusive: 0, value: 1})
    if (element.dataset.removeTier) removeItem(element.closest('[data-tier]')._tier, element.closest('.profession-automation__calculator')._calculator.tiers)
    if (element.dataset.addWeight) element.closest('.profession-automation__calculator')._calculator.items.push({value: calculator('FIXED'), weight: 1})
    if (element.dataset.removeWeight) removeItem(element.closest('[data-weighted]')._weighted, element.closest('.profession-automation__calculator')._calculator.items)
    render()
  }

  function replace(model, type, trigger) { Object.keys(model).forEach(function (key) { delete model[key] }); Object.assign(model, initialCalculator(type, trigger)) }
  function replaceAction(model, next) { Object.keys(model).forEach(function (key) { delete model[key] }); Object.assign(model, next) }
  function initialCalculator(type, trigger) {
    if (type === 'FIELD') return {calculatorType: type, fieldCode: numericFields(trigger)[0].fieldCode}
    if (binary.includes(type)) return {calculatorType: type, left: calculator('FIXED'), right: calculator('FIXED'), zeroValue: 0}
    if (type === 'CLAMP') return {calculatorType: type, value: calculator('FIXED'), min: 0, max: 1}
    if (type === 'TIERED') return {calculatorType: type, input: calculator('FIXED'), defaultValue: 0, tiers: [{minInclusive: 0, value: 1}]}
    if (type === 'WEIGHTED_SUM') return {calculatorType: type, items: [{value: calculator('FIXED'), weight: 1}]}
    return calculator('FIXED')
  }

  function resetFields(model, trigger) {
    if (!model.calculator) return
    if (model.calculator.calculatorType === 'FIELD' && numericFields(trigger).length === 0) replace(model.calculator, 'FIXED', trigger)
    if (model.calculator.calculatorType === 'FIELD') model.calculator.fieldCode = numericFields(trigger)[0].fieldCode
    resetCalculatorFields(model.calculator, trigger)
  }

  function resetCalculatorFields(model, trigger) {
    if (binary.includes(model.calculatorType)) { resetCalculatorFields(model.left, trigger); resetCalculatorFields(model.right, trigger) }
    if (model.calculatorType === 'CLAMP') resetCalculatorFields(model.value, trigger)
    if (model.calculatorType === 'TIERED') resetCalculatorFields(model.input, trigger)
    if (model.calculatorType === 'WEIGHTED_SUM') model.items.forEach(function (item) { resetCalculatorFields(item.value, trigger) })
  }

  function actionValue(model, index, actions, actionTypes) {
    var type = typeFor(actionTypes, model.actionType)
    var duplicate = actions.slice(0, index).filter(function (item) { return item.actionType === model.actionType }).length
    var value = {actionType: model.actionType, actionCode: type.actionCode + (duplicate ? '.' + (duplicate + 1) : '')}
    if (model.calculator) value.calculator = JSON.parse(JSON.stringify(model.calculator))
    if (model.notificationContent !== undefined) { value.notificationContent = model.notificationContent; value.notificationWhen = model.notificationWhen }
    return value
  }

  function numericFields(trigger) { return trigger.fields.filter(function (field) { return field.fieldType === 'NUMBER' }) }
  function removeItem(item, values) { values.splice(values.indexOf(item), 1) }
  function input(key, placeholder, value) { var element = document.createElement('input'); element.type = 'number'; element.placeholder = placeholder; element.value = value; element.dataset.calculatorValue = key; return element }
  function tierInput(key, placeholder, value) { var element = document.createElement('input'); element.type = 'number'; element.placeholder = placeholder; element.value = value; element.dataset.tierValue = key; return element }
  function weightInput(value) { var element = document.createElement('input'); element.type = 'number'; element.placeholder = '权重'; element.value = value; element.dataset.weight = 'true'; return element }
  function option(value, label, selected) { var element = document.createElement('option'); element.value = value; element.textContent = label; element.selected = value === selected; return element }
  function button(label, data) { var element = document.createElement('button'); element.type = 'button'; element.textContent = label; element.setAttribute(data, 'true'); return element }
  function group() { var element = document.createElement('div'); element.className = 'profession-automation__calculator-group'; return element }
  function calculatorName(value) { return ({FIXED: '固定经验', FIELD: '使用字段数值', ADD: '相加', SUBTRACT: '相减', MULTIPLY: '相乘', DIVIDE: '相除', MIN: '取较小值', MAX: '取较大值', CLAMP: '限定范围', TIERED: '分段计算', WEIGHTED_SUM: '加权合计'})[value] || value }
  function fieldName(value) { return ({windowStart: '统计开始时间', windowEnd: '统计结束时间', registeredReaderCount: '登录读者数', anonymousReaderCount: '匿名读者数', countedAnonymousReaderCount: '计入读者数', rewardPoint: '阅读积分', articleType: '帖子类型', tagCount: '标签数', wordCount: '字数', publishedAt: '发布时间'})[value] || value }

  window.ProfessionAutomationCalculatorEditor = {create: create}
})(window, document)
