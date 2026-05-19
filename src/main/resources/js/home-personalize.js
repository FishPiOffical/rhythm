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
var HomePersonalize = {
  storageKey: 'fishpi.home.modules.v1',
  repeater: {
    type: 'joke',
    item: null,
  },
  visiblePresetSizes: {
    compact: 6,
    standard: 8,
  },
  modules: [],
  zones: {},
  zoneIndex: 0,

  initIndex: function () {
    HomePersonalize.mountConfigToolbar()
    HomePersonalize.initHomeModules()
    HomePersonalize.initRepeater()
  },

  mountConfigToolbar: function () {
    var $toolbar = $('.home-personalize-toolbar')
    var $anchor = $('.user-nav a[href="https://ext.adventext.fun/"]').first()
    if ($toolbar.length === 0 || $anchor.length === 0) {
      return
    }

    $toolbar.addClass('home-personalize-toolbar--nav')
    $toolbar.find('.home-personalize-toolbar__btn').attr('title', '配置首页模块')
    $anchor.after($toolbar)
    $anchor.parent().addClass('home-personalize-nav')
  },

  initColumnManage: function () {
    HomePersonalize.bindColumnManage()
    HomePersonalize.bindColumnUpload()
  },

  getState: function () {
    var state = {hidden: {}, order: [], zones: {}}
    try {
      state = $.extend(state, JSON.parse(localStorage.getItem(HomePersonalize.storageKey) || '{}'))
    } catch (ignored) {
      state = {hidden: {}, order: [], zones: {}}
    }
    return state
  },

  setState: function (state) {
    localStorage.setItem(HomePersonalize.storageKey, JSON.stringify(state))
  },

  request: function (options) {
    var success = options.success
    $.ajax($.extend({}, options, {
      cache: false,
      success: function (result) {
        HomePersonalize.handleSuccess(result, success)
      },
      error: HomePersonalize.handleError,
    }))
  },

  handleSuccess: function (result, success) {
    if (!result || result.code !== 0) {
      Util.alert(result && result.msg ? result.msg : '请求失败')
      return
    }
    if (success) {
      success(result.data || {})
    }
  },

  handleError: function (xhr) {
    if (xhr && xhr.status === 401) {
      Util.needLogin()
      return
    }
    Util.alert('请求失败')
  },

  typeLabel: function (type) {
    if (type === 'kfc') {
      return '疯狂星期四'
    }
    if (type === 'fish') {
      return '鱼类科普'
    }
    return '段子'
  },

  escape: function (text) {
    return $('<div>').text(text || '').html()
  },
}
