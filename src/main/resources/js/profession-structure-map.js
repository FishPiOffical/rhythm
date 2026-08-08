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
/* 从当前编辑内容生成职业、等级和自动化的关系图。 */
(function (window, document) {
  'use strict'

  function root() { return document.getElementById('professionAdmin') }
  function dialog() { return root().querySelector('#professionStructureDialog') }
  function holder() { return root().querySelector('[data-profession-structure-map]') }

  function open() {
    try {
      render(window.ProfessionAdmin.editorState())
      dialog().showModal()
    } catch (error) {
      root().querySelector('[data-profession-admin-status]').textContent = error.message
    }
  }

  function render(state) {
    var tree = element('ul', 'profession-structure-map__tree')
    var profession = branch((state.definition.displayName || state.definition.professionCode || '职业') + ' · 当前草稿')
    profession.appendChild(levels(state.levels))
    profession.appendChild(automations(state.rules))
    tree.appendChild(profession)
    holder().replaceChildren(tree)
  }

  function levels(values) {
    var root = branch('等级方案 · ' + values.length + ' 个等级')
    values.forEach(function (level) {
      var entry = branch(level.displayName + ' · ' + level.requiredTotalExperience + ' EXP')
      var rewards = level.rewards.map(rewardText).filter(Boolean)
      entry.appendChild(leaf(rewards.length ? '奖励：' + rewards.join('、') : '未设置升级奖励'))
      root.appendChild(entry)
    })
    return root
  }

  function automations(values) {
    var root = branch('自动化 · ' + values.length + ' 条规则')
    values.forEach(function (rule) {
      var configuration = JSON.parse(rule.configurationJson)
      var entry = branch(rule.automationCode + ' · ' + triggerName(configuration.trigger.triggerType))
      entry.appendChild(leaf(conditionText(configuration.condition)))
      configuration.actions.forEach(function (action) { entry.appendChild(leaf(actionText(action))) })
      root.appendChild(entry)
    })
    return root
  }

  function rewardText(reward) {
    var config = JSON.parse(reward.rewardConfigJson || '{}')
    if (reward.rewardType === 'POINT') return '+' + config.amount + ' 积分'
    if (reward.rewardType === 'MEDAL') return '勋章 #' + (config.medalId || '未选择')
    return ''
  }

  function conditionText(condition) {
    if (!condition) return '条件：不限'
    if (condition.type === 'LEAF') return '条件：' + condition.fieldCode + ' ' + operatorName(condition.operator)
    if (condition.type === 'NOT') return '条件：不满足子条件'
    return '条件：' + (condition.type === 'ALL' ? '全部满足' : '任一满足') + '（' + condition.children.length + ' 项）'
  }

  function actionText(action) {
    if (action.actionType === 'profession.experience.adjust') return '执行：增加职业经验'
    if (action.actionType === 'profession.system.notify') return '执行：发送系统通知'
    if (action.actionType === 'profession.contribution.record') return '执行：记录贡献'
    return '执行：' + action.actionType
  }

  function branch(text) { var item = element('li', 'profession-structure-map__branch'); item.appendChild(element('strong', '', text)); return item }
  function leaf(text) { var item = element('li', 'profession-structure-map__leaf', text); return item }
  function element(tag, className, text) { var item = document.createElement(tag); item.className = className; if (text) item.textContent = text; return item }
  function triggerName(value) { return ({'long_article.read_settled': '长篇阅读结算', 'article.published': '发布帖子', 'comment.published': '发表评论', 'breezemoon.published': '发布清风明月', 'repeater.published': '发布复读机内容', 'chatroom.message.published': '聊天室发言'})[value] || value }
  function operatorName(value) { return ({EQ: '等于', NE: '不等于', GT: '大于', GTE: '不少于', LT: '小于', LTE: '不超过', BETWEEN: '介于', IN: '属于', PREFIX: '开头是', SUFFIX: '结尾是', CONTAINS: '包含', IS_EMPTY: '为空', IN_WINDOW: '位于区间', OLDER_THAN: '早于', NEWER_THAN: '晚于'})[value] || value }

  document.addEventListener('DOMContentLoaded', function () {
    if (!root()) return
    root().addEventListener('click', function (event) { if (event.target.closest('[data-profession-structure-open]')) open() })
  })
})(window, document)
