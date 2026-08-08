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
/* 职业等级、等级奖励与按位置覆盖样式编辑器。 */
(function (window, document) {
  'use strict'

  var presentationKeys = ['primaryColor', 'secondaryColor', 'backgroundColor', 'textColor', 'borderColor']
  var resourceKeys = ['imageUrl', 'iconUrl', 'badgeUrl', 'avatarFrameUrl', 'textureUrl', 'decorationUrl']
  var colorLabels = ['主色', '辅色', '背景', '文字', '边框']
  var resourceLabels = ['职业图', '图标', '徽记', '头像框', '纹理', '装饰']

  function create(holder, metadata) {
    holder.addEventListener('click', function (event) { click(event, holder, metadata) })
    holder.addEventListener('change', function (event) { change(event) })
    holder.addEventListener('input', function (event) { updateSummary(event.target.closest('.profession-level-editor')) })
    return {add: function () { holder.appendChild(level(metadata)); renumber(holder) }, value: function () { return values(holder) },
      refreshReward: function (item) { toggleReward(item) }}
  }

  function level(metadata) {
    var item = element('details', 'profession-admin__level profession-level-editor')
    item.open = true
    item.appendChild(summary())
    var body = element('div', 'profession-level-editor__body')
    body.append(levelFields(), presentationGroup(metadata), rewardGroup(), button('删除等级', 'data-remove-level', 'remove'))
    item.appendChild(body)
    return item
  }

  function summary() {
    var item = element('summary', 'profession-level-editor__summary')
    item.append(element('span', 'profession-level-editor__index', '等级 1'), element('strong', 'profession-level-editor__name', '未命名等级'),
      element('small', 'profession-level-editor__experience', '0 EXP'), disclosure())
    return item
  }

  function levelFields() {
    var holder = element('div', 'profession-level-editor__fields')
    holder.append(input('levelCode', '等级编号', 'text', '', '例如：apprentice'))
    holder.append(input('levelName', '等级名称', 'text', '', '例如：初学者'))
    holder.append(input('experience', '累计经验', 'number', '0', '例如：100'))
    holder.append(input('shortName', '等级简称', 'text', '', '例如：初学'))
    holder.append(input('description', '等级说明', 'text', '', '例如：已完成第一阶段'))
    holder.append(input('achievementDescription', '达成说明', 'text', '', '例如：累计获得 100 经验'))
    return holder
  }

  function presentationGroup(metadata) {
    var holder = element('section', 'profession-level-editor__group')
    holder.append(element('h4', '', '局部展示（可选）'), element('p', 'profession-level-editor__hint', '职业主题已用于所有常用位置。仅在某个等级需要例外外观时设置。'))
    var details = element('details', 'profession-level-editor__advanced')
    details.appendChild(element('summary', '', '添加位置覆盖'))
    details.appendChild(element('div', 'profession-level-editor__presentations'))
    details.appendChild(button('新增位置', 'data-add-presentation', 'addfile'))
    holder.appendChild(details)
    return holder
  }

  function presentation(metadata) {
    var item = element('section', 'profession-level-editor__presentation')
    item.append(position(metadata), style('明亮模式', 'light', metadata, '#f1f5f9', '#334155'),
      style('深色模式', 'dark', metadata, '#111827', '#e2e8f0'), button('删除覆盖', 'data-remove-presentation', 'remove'))
    return item
  }

  function position(metadata) {
    var holder = label('展示位置')
    var select = document.createElement('select')
    select.dataset.presentationPosition = 'true'
    metadata.positions.forEach(function (value) {
      select.appendChild(option(value, positionName(value)))
    })
    holder.appendChild(select)
    return holder
  }

  function style(title, theme, metadata, background, text) {
    var holder = element('fieldset', 'profession-level-editor__style')
    holder.appendChild(element('legend', '', title))
    presentationKeys.forEach(function (key, index) {
      var initial = key === 'backgroundColor' ? background : key === 'textColor' ? text : '#334155'
      holder.appendChild(styleInput(colorLabels[index], key, theme, 'color', initial))
    })
    resourceKeys.forEach(function (key, index) { holder.appendChild(styleInput(resourceLabels[index], key, theme, 'url', '')) })
    Object.keys(metadata.presets).forEach(function (key) { holder.appendChild(preset(key, theme, metadata.presets[key])) })
    return holder
  }

  function styleInput(text, key, theme, type, initial) {
    var holder = label(text)
    var control = document.createElement('input')
    control.type = type === 'color' ? 'color' : 'url'
    control.value = initial
    control.placeholder = type === 'url' ? '图片地址' : ''
    control.dataset['style' + capitalize(theme)] = key
    holder.appendChild(control)
    return holder
  }

  function preset(key, theme, choices) {
    var holder = label(presetName(key))
    var select = document.createElement('select')
    select.dataset['style' + capitalize(theme)] = key
    choices.forEach(function (choice) { select.appendChild(option(choice, presetValueName(choice))) })
    holder.appendChild(select)
    return holder
  }

  function rewardGroup() {
    var holder = element('section', 'profession-level-editor__group')
    holder.append(element('h4', '', '升级奖励'), element('div', 'profession-level-editor__rewards'), button('新增奖励', 'data-add-reward', 'addfile'))
    return holder
  }

  function reward() {
    var item = element('section', 'profession-level-editor__reward')
    var type = label('奖励类型')
    var select = document.createElement('select')
    select.dataset.rewardType = 'true'
    select.append(option('POINT', '积分'), option('MEDAL', '勋章'))
    type.appendChild(select)
    item.append(type, input('rewardPoint', '积分数量', 'number', '1', '例如：50'), medal(),
      input('medalDuration', '有效期（小时）', 'number', '0', '0 代表永久'), input('medalData', '附加数据', 'text', '', '可留空'), button('删除奖励', 'data-remove-reward', 'remove'))
    toggleReward(item)
    return item
  }

  function medal() {
    var holder = label('勋章')
    holder.appendChild(window.ProfessionMedalPicker.create())
    return holder
  }

  function click(event, holder, metadata) {
    var target = event.target.closest('button')
    if (!target) return
    if (target.dataset.addPresentation) target.closest('.profession-level-editor').querySelector('.profession-level-editor__presentations').appendChild(presentation(metadata))
    if (target.dataset.removePresentation) target.closest('.profession-level-editor__presentation').remove()
    if (target.dataset.addReward) target.closest('.profession-level-editor').querySelector('.profession-level-editor__rewards').appendChild(reward())
    if (target.dataset.removeReward) target.closest('.profession-level-editor__reward').remove()
    if (target.dataset.removeLevel) target.closest('.profession-level-editor').remove()
    renumber(holder)
  }

  function change(event) {
    if (event.target.dataset.rewardType) toggleReward(event.target.closest('.profession-level-editor__reward'))
  }

  function toggleReward(item) {
    var medal = item.querySelector('[name="medalId"]').closest('label')
    var duration = item.querySelector('[name="medalDuration"]').closest('label')
    var data = item.querySelector('[name="medalData"]').closest('label')
    var point = item.querySelector('[name="rewardPoint"]').closest('label')
    var isMedal = item.querySelector('[data-reward-type]').value === 'MEDAL'
    medal.hidden = !isMedal
    duration.hidden = !isMedal
    data.hidden = !isMedal
    point.hidden = isMedal
  }

  function renumber(holder) { Array.from(holder.querySelectorAll('.profession-level-editor')).forEach(function (item, index) { updateSummary(item, index + 1) }) }
  function updateSummary(item, index) {
    if (!item) return
    var displayIndex = index || Array.from(item.parentElement.querySelectorAll('.profession-level-editor')).indexOf(item) + 1
    item.querySelector('.profession-level-editor__index').textContent = '等级 ' + displayIndex
    item.querySelector('.profession-level-editor__name').textContent = value(item, 'levelName') || '未命名等级'
    item.querySelector('.profession-level-editor__experience').textContent = number(item, 'experience') + ' EXP'
  }

  function values(holder) {
    return Array.from(holder.querySelectorAll('.profession-level-editor')).map(function (item, index, all) { return levelValue(item, index, all.length) })
  }

  function levelValue(item, index, total) {
    return {levelCode: value(item, 'levelCode'), sortOrder: index, requiredTotalExperience: number(item, 'experience'), displayName: value(item, 'levelName'),
      shortName: value(item, 'shortName'), description: value(item, 'description'), achievementDescription: value(item, 'achievementDescription'),
      isTopLevel: index === total - 1, presentations: presentationValues(item), rewards: rewardValues(item, value(item, 'levelCode'))}
  }

  function presentationValues(level) {
    var positions = new Set()
    return Array.from(level.querySelectorAll('.profession-level-editor__presentation')).map(function (item) {
      var position = item.querySelector('[data-presentation-position]').value
      if (positions.has(position)) throw new Error('展示位置不能重复')
      positions.add(position)
      return {positionCode: position, lightConfigJson: JSON.stringify(styleValue(item, 'light')), darkConfigJson: JSON.stringify(styleValue(item, 'dark'))}
    })
  }

  function styleValue(item, theme) {
    var result = {}
    item.querySelectorAll('[data-style-' + theme + ']').forEach(function (control) { if (control.value) result[control.dataset['style' + capitalize(theme)]] = control.value })
    return result
  }

  function rewardValues(level, code) {
    return Array.from(level.querySelectorAll('.profession-level-editor__reward')).map(function (item, index) {
      var type = item.querySelector('[data-reward-type]').value
      return {rewardCode: code + '.' + type.toLowerCase() + '.' + index, rewardType: type, rewardConfigJson: JSON.stringify(rewardConfig(item, type)),
        grantPolicy: 'FIRST_REACH', downgradePolicy: 'KEEP', sortOrder: index}
    })
  }

  function rewardConfig(item, type) {
    if (type === 'POINT') return {amount: number(item, 'rewardPoint'), memo: '职业升级奖励'}
    return {medalId: value(item, 'medalId'), durationMillis: number(item, 'medalDuration') * 3600000, data: value(item, 'medalData')}
  }

  function input(name, text, type, initial, placeholder) { var holder = label(text); var control = document.createElement('input'); control.name = name; control.type = type || 'text'; control.value = initial || ''; control.placeholder = placeholder || ''; holder.appendChild(control); return holder }
  function label(text) { var holder = document.createElement('label'); holder.textContent = text; return holder }
  function element(tag, className, text) { var item = document.createElement(tag); item.className = className; if (text) item.textContent = text; return item }
  function disclosure() { return icon('chevron-down', 'profession-editor__disclosure') }
  function button(text, key, iconName) { var item = document.createElement('button'); item.type = 'button'; if (iconName) item.append(icon(iconName, 'profession-editor__button-icon'), document.createTextNode(text)); else item.textContent = text; item.setAttribute(key, 'true'); return item }
  function icon(symbol, className) { var item = document.createElementNS('http://www.w3.org/2000/svg', 'svg'); item.setAttribute('aria-hidden', 'true'); item.setAttribute('class', className); var use = document.createElementNS('http://www.w3.org/2000/svg', 'use'); use.setAttributeNS('http://www.w3.org/1999/xlink', 'href', '#' + symbol); item.appendChild(use); return item }
  function value(item, name) { return item.querySelector('[name="' + name + '"]').value.trim() }
  function number(item, name) { return Number(item.querySelector('[name="' + name + '"]').value || 0) }
  function capitalize(value) { return value.charAt(0).toUpperCase() + value.slice(1) }
  function option(value, text) { var item = document.createElement('option'); item.value = value; item.textContent = text; return item }
  function positionName(value) { return ({default: '所有默认位置', selectionCard: '选择卡片', homeProfile: '个人主页', userCard: '用户名片', professionPage: '职业资料页', ranking: '职业排行', levelUpDialog: '升级提示', compactMobile: '移动端紧凑展示'})[value] || value }
  function presetName(value) { return ({borderWidth: '边框', shape: '形状', shadow: '阴影', glow: '光晕', progressStyle: '进度样式', levelUpAnimation: '升级动效'})[value] || value }
  function presetValueName(value) { return ({none: '无', thin: '细', medium: '中', thick: '粗', square: '直角', rounded: '圆角', pill: '胶囊', circle: '圆形', soft: '柔和', strong: '强', solid: '实色', gradient: '渐变', segmented: '分段', ring: '环形', fade: '淡入', scale: '缩放', burst: '闪现'})[value] || value }

  window.ProfessionLevelEditor = {create: create}
})(window, document)
