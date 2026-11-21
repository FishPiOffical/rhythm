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
    if (!$('.repeater-station').length) return
    HomePersonalize.bindRepeater()
    HomePersonalize.loadRepeater()
  },

  bindRepeater: function () {
    $('.repeater-station').off('click.repeater')
      .on('click.repeater', '[data-repeater-type]', function () {
        HomePersonalize.switchRepeaterType($(this).data('repeater-type'))
      })
      .on('click.repeater', '[data-repeater-action]', function () {
        HomePersonalize.handleRepeaterAction($(this).data('repeater-action'))
      })
      .on('click.repeater', '[data-rank-tab]', function () {
        HomePersonalize.renderRepeaterLeaderboard($(this).data('rank-tab'))
      })
      .on('click.repeater', '[data-rank-copy]', function () {
        var rows = (HomePersonalize.repeater.leaderboard || {}).items || []
        var row = rows[Number($(this).attr('data-rank-copy'))]
        if (row) HomePersonalize.copyRepeaterText(row.content, true)
      })
  },

  repeaterRequest: function (options) {
    var success = options.success
    return $.ajax($.extend({}, options, {
      cache: false,
      success: function (result) {
        if (!result || result.code !== 0) {
          if (options.failure) options.failure()
          Util.alert(result && result.msg ? result.msg : '请求失败')
          return
        }
        success(result.data || {})
      },
      error: function (xhr) {
        if (xhr.statusText === 'abort') return
        if (options.failure) options.failure()
        HomePersonalize.handleError(xhr)
      },
    }))
  },

  switchRepeaterType: function (type) {
    HomePersonalize.repeater.type = type
    HomePersonalize.repeater.exhausted = false
    $('[data-repeater-type]').removeClass('repeater-station__tab--active').attr('aria-pressed', 'false')
      .filter('[data-repeater-type="' + type + '"]').addClass('repeater-station__tab--active').attr('aria-pressed', 'true')
    HomePersonalize.loadRepeater()
  },

  handleRepeaterAction: function (action) {
    var handlers = {
      copy: HomePersonalize.copyRepeater, next: HomePersonalize.nextRepeater,
      like: HomePersonalize.likeRepeater, openCreate: HomePersonalize.openRepeaterCreate,
      closeCreate: HomePersonalize.closeRepeaterCreate, submit: HomePersonalize.submitRepeater,
      leaderboard: HomePersonalize.openRepeaterLeaderboard, report: HomePersonalize.openRepeaterReport,
      submitReport: HomePersonalize.submitRepeaterReport, closeDialog: HomePersonalize.closeRepeaterDialog,
      remove: HomePersonalize.removeRepeater,
    }
    if (handlers[action]) handlers[action]()
  },

  nextRepeater: function () {
    var state = HomePersonalize.repeater
    if (state.loading) return
    if (state.exhausted || state.seen[state.type].length >= 250) {
      state.seen[state.type] = []
      state.exhausted = false
    }
    HomePersonalize.loadRepeater()
  },

  rememberRepeater: function (item) {
    var seen = HomePersonalize.repeater.seen[item.repeaterContentType]
    if (seen && item.oId && seen.indexOf(item.oId) === -1) seen.push(item.oId)
  },

  loadRepeater: function (id) {
    var state = HomePersonalize.repeater
    var type = state.type
    if (!id && state.seen[type].length >= 250) state.seen[type] = []
    var sequence = (state.sequence || 0) + 1
    state.sequence = sequence
    if (state.request) state.request.abort()
    state.loading = true
    HomePersonalize.renderRepeater({repeaterContent: '加载中…'})
    $('[data-repeater-action="next"]').prop('disabled', true)
    state.request = HomePersonalize.repeaterRequest({
      url: Label.servePath + (id ? '/api/repeater/items/' + encodeURIComponent(id) : '/api/repeater/next'),
      type: 'GET',
      data: id ? {} : {repeaterContentType: type, excludeIds: state.seen[type].join(',')},
      success: function (data) {
        if (sequence !== state.sequence) return
        var item = data.item || {}
        state.exhausted = !item.oId
        if (!item.oId) {
          HomePersonalize.renderRepeater({repeaterContent: state.seen[type].length ? '这一轮看完了，再换一轮。' : '暂无内容，来投稿吧。'})
          return
        }
        if (id) {
          state.type = item.repeaterContentType
          $('[data-repeater-type]').removeClass('repeater-station__tab--active').attr('aria-pressed', 'false')
            .filter('[data-repeater-type="' + state.type + '"]').addClass('repeater-station__tab--active').attr('aria-pressed', 'true')
        }
        HomePersonalize.rememberRepeater(item)
        HomePersonalize.renderRepeater(item)
      },
      failure: function () {
        if (sequence === state.sequence) HomePersonalize.renderRepeater({repeaterContent: '加载失败，请点“换一个”重试。'})
      },
      complete: function () {
        if (sequence !== state.sequence) return
        state.loading = false
        $('[data-repeater-action="next"]').prop('disabled', false)
      },
    })
  },

  renderRepeater: function (item) {
    HomePersonalize.repeater.item = item
    $('#repeaterContent').text(item.repeaterContent || '暂无内容').scrollTop(0)
    $('#repeaterAuthor').empty()
    if (item.oId) HomePersonalize.repeaterAuthorLink(item.repeaterContentAuthorName).appendTo('#repeaterAuthor')
    $('#repeaterLikeCount').text(item.repeaterContentLikeCount || 0)
    $('[data-repeater-action="copy"], [data-repeater-action="report"], [data-repeater-action="remove"]')
      .prop('disabled', !item.oId)
    var ownContent = HomePersonalize.isOwnRepeater(item)
    $('[data-repeater-action="report"]').prop('disabled', !item.oId || ownContent)
      .attr('title', ownContent ? '不能举报自己的内容' : '举报内容')
    $('.repeater-station__btn--like').toggleClass('repeater-station__btn--liked', !!item.repeaterContentLiked)
      .attr('aria-pressed', !!item.repeaterContentLiked).prop('disabled', !item.oId || !!HomePersonalize.repeater.liking)
    $('[data-repeater-action="next"]').text(HomePersonalize.repeater.seen[HomePersonalize.repeater.type].length >= 250 ? '再一轮' : '换一个')
  },

  currentRepeaterId: function () {
    return HomePersonalize.repeater.item ? HomePersonalize.repeater.item.oId : ''
  },

  isOwnRepeater: function (item) {
    var actor = $('.repeater-station').attr('data-current-user-id') || ''
    return !!actor && !!item && actor === item.repeaterContentAuthorId
  },

  repeaterAuthorLink: function (name) {
    if (!name) return $('<span>').addClass('repeater-station__author-empty').text('转录站')
    return $('<a>').addClass('repeater-station__author-link')
      .attr('href', Label.servePath + '/member/' + encodeURIComponent(name)).text(name)
  },

  openRepeaterLeaderboard: function () {
    var dialog = document.getElementById('repeaterLeaderboardDialog')
    if (!dialog || dialog.open) return
    dialog.showModal()
    $('#repeaterRankStatus').text('')
    $('#repeaterRankList').empty().append($('<li>').text('加载中…'))
    HomePersonalize.repeater.leaderboard = null
    var sequence = (HomePersonalize.repeater.rankSequence || 0) + 1
    HomePersonalize.repeater.rankSequence = sequence
    HomePersonalize.repeaterRequest({
      url: Label.servePath + '/api/repeater/leaderboard', type: 'GET',
      success: function (data) {
        if (sequence !== HomePersonalize.repeater.rankSequence) return
        HomePersonalize.repeater.leaderboard = data
        HomePersonalize.renderRepeaterLeaderboard('authors')
      },
      failure: function () { $('#repeaterRankList').empty().append($('<li>').text('加载失败，请重新打开。')) },
    })
  },

  renderRepeaterLeaderboard: function (type) {
    var board = HomePersonalize.repeater.leaderboard
    if (!board) return
    $('#repeaterLeaderboardDialog [data-rank-tab]').removeClass('repeater-station__tab--active').attr('aria-pressed', 'false')
      .filter('[data-rank-tab="' + type + '"]').addClass('repeater-station__tab--active').attr('aria-pressed', 'true')
    var rows = type === 'items' ? board.items : board.authors
    var $list = $('#repeaterRankList').empty()
    $('#repeaterRankStatus').text('')
    ;(rows || []).forEach(function (row, index) {
      var $entry = $('<li>').addClass('repeater-station__rank-entry')
      var $head = $('<div>').addClass('repeater-station__rank-head').appendTo($entry)
      $('<span>').addClass('repeater-station__rank-number').attr('data-rank', index + 1).text(index + 1).appendTo($head)
      HomePersonalize.repeaterAuthorLink(row.authorName).appendTo($head)
      $('<span>').addClass('repeater-station__rank-count').text((row.likeCount || 0) + ' 赞').appendTo($head)
      if (type === 'authors') {
        $entry.addClass('repeater-station__rank-entry--author')
      } else {
        $('<p>').addClass('repeater-station__rank-content').text(row.content || '').appendTo($entry)
        $('<button type="button">').addClass('repeater-station__text-btn')
          .attr('data-rank-copy', index).text('复制').appendTo($entry)
      }
      $list.append($entry)
    })
    if (!rows || !rows.length) $list.append($('<li>').text('暂无排行'))
  },

  openRepeaterReport: function () {
    if (!Label.isLoggedIn) { Util.needLogin(); return }
    if (HomePersonalize.repeater.reporting) return
    if (HomePersonalize.isOwnRepeater(HomePersonalize.repeater.item)) { Util.alert('不能举报自己的内容'); return }
    var id = HomePersonalize.currentRepeaterId()
    if (!id) return
    if (HomePersonalize.repeater.reportId !== id) $('#repeaterReportMemo').val('')
    HomePersonalize.repeater.reportId = id
    var dialog = document.getElementById('repeaterReportDialog')
    if (dialog && !dialog.open) dialog.showModal()
  },

  submitRepeaterReport: function () {
    var state = HomePersonalize.repeater
    if (!state.reportId || state.reporting) return
    state.reporting = true
    $('[data-repeater-action="submitReport"]').prop('disabled', true)
    HomePersonalize.repeaterRequest({
      url: Label.servePath + '/report', type: 'POST',
      data: JSON.stringify({reportDataId: state.reportId, reportDataType: 4,
        reportType: parseInt($('#repeaterReportReason').val(), 10), reportMemo: $('#repeaterReportMemo').val() || ''}),
      contentType: 'application/json;charset=UTF-8',
      success: function () {
        HomePersonalize.closeRepeaterDialog()
        $('#repeaterReportMemo').val('')
        Util.alert('举报已提交')
      },
      complete: function () { state.reporting = false; $('[data-repeater-action="submitReport"]').prop('disabled', false) },
    })
  },

  closeRepeaterDialog: function () {
    $('.repeater-station__dialog').each(function () { if (this.open) this.close() })
  },

  copyRepeater: function () {
    var item = HomePersonalize.repeater.item
    if (!item || !item.oId) return
    HomePersonalize.copyRepeaterText(item.repeaterContent, false)
  },

  copyRepeaterText: function (text, inLeaderboard) {
    var feedback = function (message) {
      if (inLeaderboard) $('#repeaterRankStatus').text(message)
      else Util.alert(message)
    }
    if (!navigator.clipboard) { feedback('当前浏览器不支持复制'); return }
    navigator.clipboard.writeText(text).then(function () { feedback('已复制') })
      .catch(function () { feedback('复制失败，请选中文字复制') })
  },

  likeRepeater: function () {
    var state = HomePersonalize.repeater
    var id = HomePersonalize.currentRepeaterId()
    if (!id || state.liking) return
    if (!Label.isLoggedIn) { Util.needLogin(); return }
    state.liking = true
    $('.repeater-station__btn--like').prop('disabled', true)
    HomePersonalize.repeaterRequest({
      url: Label.servePath + '/api/repeater/' + encodeURIComponent(id) + '/like', type: 'POST',
      data: JSON.stringify({}), contentType: 'application/json;charset=UTF-8',
      success: function (data) {
        if (id !== HomePersonalize.currentRepeaterId()) return
        state.item.repeaterContentLiked = data.liked
        state.item.repeaterContentLikeCount = data.repeaterContentLikeCount
        $('#repeaterLikeCount').text(data.repeaterContentLikeCount)
        $('.repeater-station__btn--like').toggleClass('repeater-station__btn--liked', !!data.liked).attr('aria-pressed', !!data.liked)
      },
      complete: function () { state.liking = false; $('.repeater-station__btn--like').prop('disabled', !HomePersonalize.currentRepeaterId()) },
    })
  },

  openRepeaterCreate: function () {
    if (!Label.isLoggedIn) { Util.needLogin(); return }
    $('#repeaterCreatePanel').prop('hidden', false)
    $('#repeaterCreateContent').trigger('focus')
  },

  closeRepeaterCreate: function () { $('#repeaterCreatePanel').prop('hidden', true) },

  submitRepeater: function () {
    var state = HomePersonalize.repeater
    if (!Label.isLoggedIn) { Util.needLogin(); return }
    if (state.submitting) return
    var text = $.trim($('#repeaterCreateContent').val())
    if (text.length < 2 || text.length > 500) { Util.alert('内容需为 2 到 500 字'); return }
    state.submitting = true
    $('[data-repeater-action="submit"]').prop('disabled', true).text('审核中')
    $('#repeaterCreateContent').prop('disabled', true)
    var type = state.type
    HomePersonalize.repeaterRequest({
      url: Label.servePath + '/api/repeater', type: 'POST',
      data: JSON.stringify({repeaterContentType: type, repeaterContent: text}), contentType: 'application/json;charset=UTF-8',
      success: function (data) {
        $('#repeaterCreateContent').val('')
        HomePersonalize.closeRepeaterCreate()
        var item = data.item || {}
        HomePersonalize.rememberRepeater(item)
        if (type === state.type) {
          state.sequence = (state.sequence || 0) + 1
          if (state.request) state.request.abort()
          state.loading = false
          state.exhausted = false
          $('[data-repeater-action="next"]').prop('disabled', false)
          HomePersonalize.renderRepeater(item)
        }
        Util.alert('投稿成功')
      },
      complete: function () {
        state.submitting = false
        $('[data-repeater-action="submit"]').prop('disabled', false).text('提交')
        $('#repeaterCreateContent').prop('disabled', false)
      },
    })
  },

  removeRepeater: function () {
    var id = HomePersonalize.currentRepeaterId()
    if (!id || HomePersonalize.repeater.removing || !window.confirm('确认删除这条复读内容？')) return
    HomePersonalize.repeater.removing = true
    $('[data-repeater-action="remove"]').prop('disabled', true)
    HomePersonalize.repeaterRequest({
      url: Label.servePath + '/admin/repeater/' + encodeURIComponent(id) + '/remove', type: 'POST',
      headers: {csrfToken: $('.repeater-station').attr('data-csrf')},
      success: function () { if (id === HomePersonalize.currentRepeaterId()) HomePersonalize.loadRepeater() },
      complete: function () {
        HomePersonalize.repeater.removing = false
        $('[data-repeater-action="remove"]').prop('disabled', !HomePersonalize.currentRepeaterId())
      },
    })
  },
})
