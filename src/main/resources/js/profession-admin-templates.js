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
/* 常用职业模板。模板只填充表单，不写入职业数据。 */
(function (window, document) {
  'use strict'

  var templates = {
    author: {displayName: '著述家', professionCode: 'longform_author', shortName: '著述', description: '连载长篇与章节作品', primaryColor: '#5b4b8a', backgroundColor: '#f1effb', textColor: '#2d2450'},
    life: {displayName: '生活家', professionCode: 'life_writer', shortName: '生活', description: '记录知识、兴趣和日常', primaryColor: '#156b61', backgroundColor: '#eaf7f3', textColor: '#123c37'},
    social: {displayName: '交流家', professionCode: 'community_host', shortName: '交流', description: '连接讨论、聊天和短文', primaryColor: '#9a4d22', backgroundColor: '#fff3e8', textColor: '#5e2b10'},
    activity: {displayName: '参与家', professionCode: 'community_participant', shortName: '参与', description: '围绕活动持续投入', primaryColor: '#285b9b', backgroundColor: '#edf5ff', textColor: '#183b67'}
  }

  function root() { return document.getElementById('professionAdmin') }
  function dialog() { return document.getElementById('professionTemplateDialog') }

  function open() {
    dialog().showModal()
  }

  function select(code) {
    var template = templates[code]
    if (!template) throw new Error('职业模板不存在')
    dialog().close()
    window.ProfessionAdminWorkflow.createDefinition(template)
  }

  document.addEventListener('DOMContentLoaded', function () {
    if (!root()) return
    root().addEventListener('click', function (event) {
      var button = event.target.closest('button')
      if (!button) return
      if (button.dataset.professionTemplateOpen !== undefined) open()
      if (button.dataset.professionTemplate) select(button.dataset.professionTemplate)
    })
  })
})(window, document)
