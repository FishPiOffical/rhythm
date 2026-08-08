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
/* 一个职业可编排多条独立自动化规则。 */
(function (window, document) {
  'use strict'

  var previewTimers = new WeakMap()

  function create(holder, metadata) {
    holder.addEventListener('click', function (event) { click(event, holder, metadata) })
    holder.addEventListener('input', function (event) { updateAndPreview(event.target.closest('.profession-automation-card')) })
    holder.addEventListener('change', function (event) { updateAndPreview(event.target.closest('.profession-automation-card')) })
    return {add: function () { return add(holder, metadata) }, load: function (values) { load(holder, metadata, values) }, values: function () { return values(holder, true) }, changed: function () { return changed(holder) }, markSaved: markSaved, snapshot: function () { return values(holder, false) }, itemFor: itemFor}
  }

  function add(holder, metadata, seed) {
    var card = ruleCard(metadata, seed || {})
    holder.appendChild(card)
    update(card)
    return card
  }

  function load(holder, metadata, items) {
    holder.replaceChildren()
    items.forEach(function (item) { add(holder, metadata, item) })
  }

  function ruleCard(metadata, seed) {
    var card = element('details', 'profession-automation-card')
    card.open = !seed.automationId
    card._automationRule = {automationId: seed.automationId || '', persisted: Boolean(seed.automationId), baseline: ''}
    card.appendChild(summary())
    var body = element('div', 'profession-automation-card__body')
    var fields = element('div', 'profession-automation-card__fields')
    var code = textInput('规则代号', '例如：long_read_reward')
    var codeInput = code.querySelector('input')
    codeInput.dataset.automationCode = 'true'
    codeInput.value = seed.automationCode || 'rule_' + Date.now().toString().slice(-6)
    fields.append(code)
    var trigger = triggerField()
    fields.append(trigger)
    body.appendChild(fields)
    body.appendChild(conditionSection())
    body.appendChild(actionSection())
    body.appendChild(testSection())
    if (!seed.automationId) body.appendChild(button('删除规则', 'data-remove-automation', 'remove'))
    card.appendChild(body)
    card._automationRule.editor = window.ProfessionAutomationEditor.create(card, metadata)
    if (seed.configuration) card._automationRule.editor.load(seed.configuration)
    if (seed.automationId) card._automationRule.baseline = signature(ruleData(card))
    return card
  }

  function summary() {
    var item = element('summary', 'profession-automation-card__summary')
    item.append(element('span', 'profession-automation-card__index', '规则'), element('strong', 'profession-automation-card__name', '新规则'),
      element('small', 'profession-automation-card__trigger', '选择触发时机'), disclosure())
    return item
  }

  function triggerField() {
    var holder = label('触发时机')
    var select = document.createElement('select')
    select.dataset.automationTrigger = 'true'
    holder.appendChild(select)
    return holder
  }

  function conditionSection() {
    var section = element('section', 'profession-automation')
    var header = document.createElement('header')
    header.append(element('h3', '', '生效条件'), switchControl())
    section.append(header, element('div', '', '', {'data-automation-conditions': 'true'}))
    return section
  }

  function switchControl() {
    var holder = label('不限条件')
    holder.className = 'profession-automation__switch'
    var input = document.createElement('input')
    input.type = 'checkbox'
    input.dataset.automationNoCondition = 'true'
    holder.prepend(input)
    return holder
  }

  function actionSection() {
    var section = element('section', 'profession-automation')
    section.append(element('h3', '', '执行内容'), element('div', '', '', {'data-automation-actions': 'true'}))
    return section
  }

  function testSection() {
    var section = element('details', 'profession-automation profession-automation__test')
    section.appendChild(element('summary', '', '试运行'))
    var content = element('div', 'profession-automation__test-content')
    var header = document.createElement('header')
    header.append(element('h3', '', '模拟本条规则'), button('试运行', 'data-test-automation', 'playgame'))
    var copy = element('p', 'profession-automation__hint', '传入示例事件，查看条件与执行结果；不会写入数据。')
    var count = label('模拟次数')
    count.className = 'profession-automation__test-count'
    var input = document.createElement('input')
    input.type = 'number'
    input.min = '1'
    input.step = '1'
    input.value = '1'
    input.dataset.automationEventCount = 'true'
    count.appendChild(input)
    var payload = element('div')
    payload.dataset.automationPayload = 'true'
    var result = element('div', 'profession-automation__result')
    result.dataset.automationResult = 'true'
    result.setAttribute('aria-live', 'polite')
    content.append(header, copy, count, payload, result)
    section.appendChild(content)
    return section
  }

  function click(event, holder, metadata) {
    var button = event.target.closest('button')
    if (!button) return
    if (button.dataset.addAutomation !== undefined) add(holder, metadata)
    if (button.dataset.removeAutomation !== undefined) button.closest('.profession-automation-card').remove()
  }

  function update(card) {
    if (!card) return
    var code = card.querySelector('[data-automation-code]').value.trim()
    var trigger = card.querySelector('[data-automation-trigger]')
    var title = trigger.options[trigger.selectedIndex]
    card.querySelector('.profession-automation-card__name').textContent = code || '未命名规则'
    card.querySelector('.profession-automation-card__trigger').textContent = title ? title.textContent : '选择触发时机'
    var index = Array.from(card.parentElement.querySelectorAll('.profession-automation-card')).indexOf(card) + 1
    card.querySelector('.profession-automation-card__index').textContent = '规则 ' + index
  }

  function updateAndPreview(card) {
    update(card)
    schedulePreview(card)
  }

  function schedulePreview(card) {
    if (!card || !card.querySelector('.profession-automation__test[open]')) return
    window.clearTimeout(previewTimers.get(card))
    previewTimers.set(card, window.setTimeout(function () {
      window.dispatchEvent(new CustomEvent('profession-automation-preview-request', {detail: {card: card}}))
    }, 180))
  }

  function values(holder, validate) {
    var cards = Array.from(holder.querySelectorAll('.profession-automation-card'))
    if (!cards.length && validate) throw new Error('请新增至少一条规则')
    var codes = new Set()
    return cards.map(function (card) {
      var code = card.querySelector('[data-automation-code]').value.trim()
      if (validate && !/^[a-z][a-z0-9._-]{0,63}$/.test(code)) throw new Error('规则代号仅支持小写字母、数字、点、下划线和连字符')
      if (validate && codes.has(code)) throw new Error('规则代号不能重复')
      codes.add(code)
      return ruleData(card, code)
    })
  }

  function ruleData(card, code) {
    var ruleCode = code === undefined ? card.querySelector('[data-automation-code]').value.trim() : code
    return {automationCode: ruleCode, configurationJson: JSON.stringify(card._automationRule.editor.configuration())}
  }

  function changed(holder) {
    var cards = Array.from(holder.querySelectorAll('.profession-automation-card'))
    return values(holder, true).map(function (rule, index) { return Object.assign({card: cards[index]}, rule) })
      .filter(function (rule) { return signature(rule) !== rule.card._automationRule.baseline })
  }

  function markSaved(rules) {
    rules.forEach(function (rule) {
      rule.card._automationRule.baseline = signature(rule)
      rule.card._automationRule.persisted = true
    })
  }

  function signature(rule) { return JSON.stringify({automationCode: rule.automationCode, configurationJson: rule.configurationJson}) }

  function itemFor(button) {
    var card = button.closest('.profession-automation-card')
    if (!card) throw new Error('规则不存在')
    var eventCount = Number(card.querySelector('[data-automation-event-count]').value)
    if (!Number.isSafeInteger(eventCount) || eventCount < 1) throw new Error('模拟次数需为正整数')
    return {configurationJson: JSON.stringify(card._automationRule.editor.configuration()), payloadJson: JSON.stringify(card._automationRule.editor.payload()),
      eventCount: eventCount, result: card.querySelector('[data-automation-result]')}
  }

  function element(tag, className, text, attributes) {
    var item = document.createElement(tag)
    item.className = className || ''
    if (text) item.textContent = text
    Object.keys(attributes || {}).forEach(function (key) { item.setAttribute(key, attributes[key]) })
    return item
  }

  function label(text) { var item = document.createElement('label'); item.textContent = text; return item }
  function textInput(text, placeholder) { var item = label(text); var input = document.createElement('input'); input.type = 'text'; input.placeholder = placeholder; input.autocomplete = 'off'; item.appendChild(input); return item }
  function disclosure() { return icon('chevron-down', 'profession-editor__disclosure') }
  function button(text, attribute, iconName) { var item = document.createElement('button'); item.type = 'button'; if (iconName) item.append(icon(iconName, 'profession-editor__button-icon'), document.createTextNode(text)); else item.textContent = text; item.setAttribute(attribute, 'true'); return item }
  function icon(symbol, className) { var item = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); item.setAttribute('aria-hidden', 'true'); item.setAttribute('class', className); var use = document.createElementNS('http://www.w3.org/2000/svg', 'use'); use.setAttributeNS('http://www.w3.org/1999/xlink', 'href', '#' + symbol); item.appendChild(use); return item }

  window.ProfessionAutomationCollection = {create: create}
})(window, document)
