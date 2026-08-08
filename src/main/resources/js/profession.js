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
/* 职业选择、隐私设置、主页摘要与用户名片。 */
(function (window, document) {
  'use strict'
  var modules = ['primaryProfession', 'allProfessions', 'experience', 'rarity', 'majorContributions',
    'contributionStats', 'activityFeed', 'levelHistory', 'rewards', 'ranking']
  var moduleLabels = {
    primaryProfession: '主职业', allProfessions: '全部职业', experience: '经验', rarity: '稀有度',
    majorContributions: '主要贡献', contributionStats: '贡献统计', activityFeed: '职业动态',
    levelHistory: '等级记录', rewards: '职业奖励', ranking: '排行信息'
  }
  function request(url, options) {
    var config = Object.assign({}, options || {})
    config.headers = Object.assign({'Content-Type': 'application/json;charset=UTF-8'}, config.headers || {})
    return fetch((window.Label && Label.servePath ? Label.servePath : '') + url, config).then(function (response) {
      if (!response.ok) throw new Error('职业请求失败：' + response.status)
      return response.json()
    }).then(function (response) {
      if (response.code !== 0) throw new Error(response.msg || '职业请求失败')
      return response.data
    })
  }
  function post(url, body) {
    return request(url, {method: 'POST', headers: {'csrfToken': Label.csrfToken || ''}, body: JSON.stringify(body)})
  }
  function darkMode() {
    return document.body.classList.contains('night') ? 'true' : 'false'
  }
  function publicProfile(userName, position) {
    return request('/api/user/' + encodeURIComponent(userName) + '/profession?position=' +
      encodeURIComponent(position) + '&dark=' + darkMode())
  }
  function professionMark(profession, presentation) {
    var mark = document.createElement('span')
    mark.className = 'profession-badge__mark'
    var imageUrl = presentation.iconUrl || presentation.imageUrl || presentation.badgeUrl
    if (imageUrl) {
      var image = document.createElement('img')
      image.className = 'profession-badge__image'
      image.alt = ''
      image.src = imageUrl
      mark.appendChild(image)
    } else {
      mark.textContent = (profession.shortName || profession.displayName || '职').trim().charAt(0)
    }
    return mark
  }
  function applyPresentation(element, mark, presentation) {
    if (presentation.backgroundColor) mark.style.backgroundColor = presentation.backgroundColor
    if (presentation.textColor) mark.style.color = presentation.textColor
    if (presentation.borderColor) mark.style.borderColor = presentation.borderColor
    if (presentation.primaryColor) element.style.setProperty('--profession-primary', presentation.primaryColor)
    if (presentation.borderWidth && presentation.borderWidth !== 'none') {
      element.classList.add('profession-badge--border-' + presentation.borderWidth)
    }
    ;['shadow', 'glow'].forEach(function (name) {
      if (presentation[name] && presentation[name] !== 'none') element.classList.add('profession-badge--' + name + '-' + presentation[name])
    })
  }
  function card(profession) {
    var presentation = profession.presentation || {}
    var element = document.createElement('span')
    element.className = 'profession-badge profession-badge--' + (presentation.shape || 'pill')
    element.title = profession.displayName + (profession.levelName ? ' · ' + profession.levelName : '')
    var mark = professionMark(profession, presentation)
    var copy = document.createElement('span')
    copy.className = 'profession-badge__copy'
    var name = document.createElement('strong')
    name.className = 'profession-badge__name'
    name.textContent = profession.displayName
    copy.appendChild(name)
    if (profession.levelName) {
      var level = document.createElement('span')
      level.className = 'profession-badge__level'
      level.textContent = profession.levelName
      copy.appendChild(level)
    }
    applyPresentation(element, mark, presentation)
    element.appendChild(mark)
    element.appendChild(copy)
    return element
  }
  function appendPrimary(holder, profession) {
    if (!profession) return
    holder.appendChild(card(profession))
  }
  function profileLink(userName, profession) {
    var link = document.createElement('a')
    link.className = 'profession-profile__link'
    link.href = (Label.servePath || '') + '/member/' + encodeURIComponent(userName) + '/profession'
    link.setAttribute('aria-label', '查看' + profession.displayName + '职业资料')
    link.appendChild(card(profession))
    return link
  }
  function appendHome(target, data, userName) {
    if (!data.primaryProfession) {
      target.replaceChildren()
      return
    }
    var holder = document.createElement('div')
    holder.className = 'profession-profile__primary'
    holder.appendChild(profileLink(userName, data.primaryProfession))
    target.replaceChildren(holder)
  }
  function loadHome(target, userName) {
    return publicProfile(userName, 'homeProfile').then(function (data) {
      appendHome(target, data, userName)
    }).catch(function (error) {
      target.replaceChildren()
      console.error(error)
    })
  }
  function loadUserCard(target, userName) {
    return publicProfile(userName, 'userCard').then(function (data) {
      if (!data.primaryProfession || !target) return
      var meta = target.querySelector('.user-card__meta')
      if (!meta) throw new Error('用户名片结构异常')
      var holder = document.createElement('div')
      holder.className = 'user-card__profession'
      holder.appendChild(profileLink(userName, data.primaryProfession))
      target.querySelectorAll('.user-card__profession').forEach(function (item) { item.remove() })
      meta.appendChild(holder)
    }).catch(function (error) { console.error(error) })
  }
  function loadProfilePage(target, userName) {
    return publicProfile(userName, 'professionPage').then(function (data) {
      window.ProfessionProfile.renderPublicPage(target, data)
    }).catch(function (error) {
      target.textContent = '暂无公开职业资料'
      console.error(error)
    })
  }
  function settingRoot() { return document.getElementById('professionSettings') }
  function option(profession, selected, triggers, rewards) {
    var item = document.createElement('button')
    item.type = 'button'
    item.className = 'profession-choice' + (selected ? ' is-selected' : '')
    item.dataset.professionId = profession.professionId
    item.appendChild(card(profession))
    if (profession.shortName) {
      var identity = document.createElement('small')
      identity.className = 'profession-choice__identity'
      identity.textContent = profession.shortName
      item.appendChild(identity)
    }
    var description = document.createElement('span')
    description.className = 'profession-choice__description'
    description.textContent = profession.description || '职业记录持续累计'
    item.appendChild(description)
    item.appendChild(growthGuide(triggers))
    item.appendChild(rewardGuide(rewards))
    return item
  }
  function growthGuide(triggers) {
    var item = document.createElement('small')
    item.className = 'profession-choice__growth'
    item.textContent = triggers && triggers.length ? '经验来源：' + triggers.map(triggerName).join('、') : '经验来源：暂未配置'
    return item
  }
  function rewardGuide(rewards) {
    var item = document.createElement('small')
    item.className = 'profession-choice__rewards'
    var points = (rewards || []).filter(function (reward) { return reward.rewardType === 'POINT' })
      .map(function (reward) { return Number(reward.amount || 0) }).filter(Boolean).sort(function (left, right) { return left - right })
    var labels = []
    if (points.length) labels.push(numberRange(points) + ' 积分')
    if ((rewards || []).some(function (reward) { return reward.rewardType === 'MEDAL' })) labels.push('勋章')
    item.textContent = '部分阶段奖励：' + (labels.join('、') || '按等级方案发放')
    return item
  }
  function numberRange(values) {
    var format = new Intl.NumberFormat('zh-CN')
    if (values.length === 1) return format.format(values[0])
    return format.format(values[0]) + '–' + format.format(values[values.length - 1])
  }
  function triggerName(value) {
    return ({'long_article.read_settled': '长篇阅读结算', 'article.published': '发布帖子',
      'comment.published': '发表评论', 'breezemoon.published': '发布清风明月',
      'repeater.published': '发布复读机内容', 'chatroom.message.published': '聊天室发言',
      'user.online.settled': '在线时长结算'})[value] || value
  }
  function triggersFor(data, profession) {
    return data.growthGuides && data.growthGuides[profession.professionId] || []
  }
  function rewardsFor(data, profession) {
    return data.rewardGuides && data.rewardGuides[profession.professionId] || []
  }
  function visibility(data) {
    try { return JSON.parse(data.moduleVisibilityJson || '{}') } catch (error) { throw new Error('职业隐私配置异常') }
  }
  function renderCustomVisibility(root, values) {
    var holder = root.querySelector('[data-profession-custom-visibility]')
    holder.replaceChildren()
    modules.forEach(function (name) {
      var label = document.createElement('label')
      label.className = 'profession-settings__visibility'
      label.textContent = moduleLabels[name]
      var select = document.createElement('select')
      select.dataset.professionVisibility = name
      ;[['PUBLIC', '公开'], ['LOGGED_IN', '登录可见'], ['SELF', '仅自己'], ['HIDDEN', '隐藏']].forEach(function (entry) {
        var option = document.createElement('option')
        option.value = entry[0]
        option.textContent = entry[1]
        select.appendChild(option)
      })
      select.value = values[name]
      label.appendChild(select)
      holder.appendChild(label)
    })
  }
  function selectedProfession(root, data) {
    var progress = data.progress || []
    var configured = data.primaryProfessionId || ''
    var available = (data.availableProfessions || []).some(function (item) { return item.professionId === configured })
    var current = available ? configured : (progress[0] && progress[0].professionId) || ''
    root.dataset.selectedProfessionId = current
    return current
  }
  function renderSettings(data) {
    var root = settingRoot()
    if (!root) return
    root.__professionProfileData = data
    var selected = selectedProfession(root, data)
    var choices = root.querySelector('[data-profession-choices]')
    choices.replaceChildren()
    data.availableProfessions.forEach(function (profession) {
      choices.appendChild(option(profession, profession.professionId === selected, triggersFor(data, profession), rewardsFor(data, profession)))
    })
    root.querySelector('[name="professionPreset"][value="' + data.privacyPreset + '"]').checked = true
    renderCustomVisibility(root, visibility(data))
    root.querySelector('[data-profession-custom-visibility]').hidden = data.privacyPreset !== 'CUSTOM'
    root.querySelector('[data-profession-skip-wrap]').hidden = data.onboardingState !== 'UNDECIDED'
    root.querySelector('[data-profession-status]').textContent = settingsStatus(data, selected)
    showOwnDetail(root, selected)
  }
  function settingsStatus(data, selected) {
    if (data.primaryProfessionState === 'RETIRED') return '原主职业已停用，请重新选择'
    if (data.primaryProfessionState === 'DELETED') return '原主职业已删除，请重新选择'
    if (!data.primaryProfessionId) return '尚未设置主职业'
    var profession = data.availableProfessions.find(function (item) { return item.professionId === selected })
    return profession ? '当前主职业：' + profession.displayName : '主职业已设置'
  }
  function showOwnDetail(root, professionId) {
    var target = root.querySelector('[data-profession-detail]')
    if (!professionId) {
      target.textContent = '暂无职业记录'
      return
    }
    request('/api/profession/me/' + encodeURIComponent(professionId) + '/detail?dark=' + darkMode()).then(function (data) {
      window.ProfessionProfile.renderOwnDetail(target, data)
    }).catch(function (error) {
      target.textContent = '暂无职业记录'
      console.error(error)
    })
  }
  function chooseProfession(root, professionId) {
    root.dataset.selectedProfessionId = professionId
    root.querySelectorAll('[data-profession-id]').forEach(function (item) {
      item.classList.toggle('is-selected', item.dataset.professionId === professionId)
    })
    showOwnDetail(root, professionId)
  }
  function showError(root) {
    return function (error) { root.querySelector('[data-profession-status]').textContent = error.message }
  }
  function createOnboardingPanel(title, notice, preview) {
    var panel = document.createElement('div')
    panel.className = 'profession-onboarding__panel'
    var eyebrow = document.createElement('span')
    eyebrow.className = 'profession-onboarding__eyebrow'
    eyebrow.textContent = '社区职业'
    var heading = document.createElement('h2')
    heading.textContent = title
    var description = document.createElement('p')
    description.textContent = '职业记录创作、交流与回应。主职业用于主页和名片展示，随时可换；其他职业仍会累计。'
    var noticeElement = document.createElement('p')
    noticeElement.className = 'profession-onboarding__notice'
    noticeElement.textContent = notice
    var choices = document.createElement('div')
    choices.dataset.professionChoices = ''
    var actions = document.createElement('div')
    actions.className = 'profession-onboarding__actions'
    var button = document.createElement('button')
    button.type = 'button'
    button.className = 'profession-button'
    if (preview) button.dataset.professionOnboardingClose = ''
    else button.dataset.professionSkip = ''
    button.textContent = preview ? '关闭' : '暂不设置'
    actions.appendChild(button)
    var status = document.createElement('div')
    status.dataset.professionStatus = ''
    status.setAttribute('role', 'status')
    status.setAttribute('aria-live', 'polite')
    panel.append(eyebrow, heading, description, noticeElement, choices, actions, status)
    return {panel: panel, choices: choices}
  }
  function bindSettings(root) {
    root.addEventListener('change', function (event) {
      if (event.target.name === 'professionPreset') {
        root.querySelector('[data-profession-custom-visibility]').hidden = event.target.value !== 'CUSTOM'
      }
    })
    root.addEventListener('click', function (event) {
      var choice = event.target.closest('[data-profession-id]')
      if (choice) chooseProfession(root, choice.dataset.professionId)
      if (event.target.matches('[data-profession-reopen-onboarding]')) onboarding(root.__professionProfileData, true)
      if (event.target.matches('[data-profession-skip]')) {
        root.querySelector('[data-profession-status]').textContent = '正在保存'
        post('/api/profession/me/skip', {}).then(function () {
          root.querySelector('[data-profession-skip-wrap]').hidden = true
          root.querySelector('[data-profession-status]').textContent = '已暂不设置主职业'
        }).catch(showError(root))
      }
    })
  }
  function onboarding(data, preview) {
    var primaryUnavailable = data.primaryProfessionState === 'RETIRED' || data.primaryProfessionState === 'DELETED'
    if ((!preview && data.onboardingState !== 'UNDECIDED' && !primaryUnavailable) || document.getElementById('professionOnboarding')) return
    var root = document.createElement('section')
    root.id = 'professionOnboarding'
    root.className = 'profession-onboarding'
    var title = primaryUnavailable ? '重新选择主职业' : '选择主职业'
    var notice = data.primaryProfessionState === 'RETIRED' ? '原主职业已停用，历史经验会保留。' : data.primaryProfessionState === 'DELETED' ? '原主职业已删除，请选择新的主职业。' : '撤回内容时，对应经验与贡献会同步回退。'
    var parts = createOnboardingPanel(title, notice, preview)
    root.appendChild(parts.panel)
    document.body.appendChild(root)
    data.availableProfessions.forEach(function (profession) {
      parts.choices.appendChild(option(profession, false, triggersFor(data, profession), rewardsFor(data, profession)))
    })
    parts.panel.addEventListener('click', function (event) {
      var choice = event.target.closest('[data-profession-id]')
      if (choice) post('/api/profession/me/primary', {professionId: choice.dataset.professionId}).then(function () { closeOnboarding(root) }).catch(showError(parts.panel))
      if (event.target.matches('[data-profession-skip]')) post('/api/profession/me/skip', {}).then(function () { closeOnboarding(root) }).catch(showError(parts.panel))
      if (event.target.matches('[data-profession-onboarding-close]')) root.remove()
    })
  }
  function closeOnboarding(root) {
    root.remove()
    var settings = settingRoot()
    if (settings) request('/api/profession/me').then(renderSettings).catch(showError(settings))
  }
  function loadProfilePages(scope) {
    scope.querySelectorAll('[data-user-profession-detail]').forEach(function (target) {
      if (target.dataset.professionProfileLoaded) return
      target.dataset.professionProfileLoaded = 'true'
      loadProfilePage(target.querySelector('[data-profession-page-content]'), target.dataset.userProfessionDetail)
    })
  }
  function observePjaxContent() {
    var container = document.getElementById('home-pjax-container')
    if (!container || container.dataset.professionObserver || !window.MutationObserver) return
    container.dataset.professionObserver = 'true'
    var observer = new MutationObserver(function () { loadProfilePages(container) })
    observer.observe(container, {childList: true, subtree: true})
  }
  function initialize() {
    document.querySelectorAll('[data-user-profession]').forEach(function (target) { loadHome(target, target.dataset.userProfession) })
    loadProfilePages(document)
    observePjaxContent()
    var root = settingRoot()
    if (!Label.isLoggedIn) return
    if (root) bindSettings(root)
    request('/api/profession/me').then(function (data) {
      if (root) renderSettings(data)
      onboarding(data)
    }).catch(function (error) {
      if (root) return showError(root)(error)
      console.error(error)
    })
  }
  window.ProfessionProfile = {card: card, request: request, loadHome: loadHome, loadUserCard: loadUserCard,
    loadProfilePage: loadProfilePage, renderPublicPage: function () { throw new Error('职业资料渲染器未加载') },
    renderOwnDetail: function () { throw new Error('职业详情渲染器未加载') }}
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', initialize)
  else initialize()
})(window, document)
