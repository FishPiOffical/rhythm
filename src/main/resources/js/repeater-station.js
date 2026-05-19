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
$.extend(HomePersonalize, {
  initRepeater: function () {
    HomePersonalize.bindRepeater()
    HomePersonalize.loadRepeater()
  },

  bindRepeater: function () {
    $('.repeater-station__tab').off('click.repeater').on('click.repeater', function () {
      HomePersonalize.switchRepeaterType($(this))
    })
    $('.repeater-station').off('click.repeater')
      .on('click.repeater', '[data-repeater-action]', function () {
        HomePersonalize.handleRepeaterAction($(this).data('repeater-action'))
      })
  },

  switchRepeaterType: function ($tab) {
    $('.repeater-station__tab').removeClass('repeater-station__tab--active')
    $tab.addClass('repeater-station__tab--active')
    HomePersonalize.repeater.type = $tab.data('repeater-type')
    HomePersonalize.loadRepeater()
  },

  handleRepeaterAction: function (action) {
    var handlers = {
      copy: HomePersonalize.copyRepeater,
      next: HomePersonalize.nextRepeater,
      like: HomePersonalize.likeRepeater,
      openCreate: HomePersonalize.openRepeaterCreate,
      closeCreate: HomePersonalize.closeRepeaterCreate,
      submit: HomePersonalize.submitRepeater,
    }
    if (handlers[action]) {
      handlers[action]()
    }
  },

  nextRepeater: function () {
    HomePersonalize.loadRepeater(HomePersonalize.currentRepeaterId())
  },

  openRepeaterCreate: function () {
    $('#repeaterCreatePanel').prop('hidden', false)
  },

  closeRepeaterCreate: function () {
    $('#repeaterCreatePanel').prop('hidden', true)
  },

  loadRepeater: function (excludeId) {
    HomePersonalize.request({
      url: Label.servePath + '/api/repeater/next',
      type: 'GET',
      data: HomePersonalize.buildRepeaterQuery(excludeId),
      success: function (data) {
        HomePersonalize.renderRepeater(data.item || {})
      },
    })
  },

  buildRepeaterQuery: function (excludeId) {
    return {
      repeaterContentType: HomePersonalize.repeater.type,
      excludeId: excludeId || '',
    }
  },

  renderRepeater: function (item) {
    HomePersonalize.repeater.item = item
    $('#repeaterContent').text(item.repeaterContent || '暂无内容')
    $('#repeaterTypeLabel').text(item.repeaterContentTypeLabel || HomePersonalize.typeLabel(HomePersonalize.repeater.type))
    $('#repeaterAuthor').text(item.repeaterContentAuthorName ? '来自 ' + item.repeaterContentAuthorName : '')
    $('#repeaterLikeCount').text(item.repeaterContentLikeCount || 0)
    $('.repeater-station__btn--like').toggleClass('repeater-station__btn--liked', !!item.repeaterContentLiked)
  },

  currentRepeaterId: function () {
    return HomePersonalize.repeater.item ? HomePersonalize.repeater.item.oId : ''
  },

  copyRepeater: function () {
    var text = HomePersonalize.repeater.item ? HomePersonalize.repeater.item.repeaterContent : ''
    if (!HomePersonalize.canCopyRepeater(text)) {
      return
    }
    navigator.clipboard.writeText(text).then(function () {
      Util.alert('已复制')
    }).catch(function () {
      Util.alert('复制失败')
    })
  },

  canCopyRepeater: function (text) {
    if (!text) {
      Util.alert('没有可复制内容')
      return false
    }
    if (!navigator.clipboard) {
      Util.alert('当前浏览器不支持复制')
      return false
    }
    return true
  },

  likeRepeater: function () {
    var id = HomePersonalize.currentRepeaterId()
    if (!HomePersonalize.canLikeRepeater(id)) {
      return
    }
    HomePersonalize.request({
      url: Label.servePath + '/api/repeater/' + encodeURIComponent(id) + '/like',
      type: 'POST',
      data: JSON.stringify({}),
      contentType: 'application/json;charset=UTF-8',
      success: HomePersonalize.applyRepeaterLike,
    })
  },

  canLikeRepeater: function (id) {
    if (!id) {
      Util.alert('没有可点赞内容')
      return false
    }
    if (!Label.isLoggedIn) {
      Util.needLogin()
      return false
    }
    return true
  },

  applyRepeaterLike: function (data) {
    HomePersonalize.repeater.item.repeaterContentLiked = data.liked
    HomePersonalize.repeater.item.repeaterContentLikeCount = data.repeaterContentLikeCount
    HomePersonalize.renderRepeater(HomePersonalize.repeater.item)
  },

  submitRepeater: function () {
    if (!HomePersonalize.canCreateRepeater()) {
      return
    }
    HomePersonalize.request({
      url: Label.servePath + '/api/repeater',
      type: 'POST',
      data: JSON.stringify(HomePersonalize.buildRepeaterBody()),
      contentType: 'application/json;charset=UTF-8',
      success: HomePersonalize.renderCreatedRepeater,
    })
  },

  canCreateRepeater: function () {
    if (!Label.isLoggedIn) {
      Util.needLogin()
      return false
    }
    return true
  },

  buildRepeaterBody: function () {
    return {
      repeaterContentType: HomePersonalize.repeater.type,
      repeaterContent: $.trim($('#repeaterCreateContent').val()),
    }
  },

  renderCreatedRepeater: function (data) {
    $('#repeaterCreateContent').val('')
    HomePersonalize.closeRepeaterCreate()
    HomePersonalize.renderRepeater(data.item || {})
  },

})
