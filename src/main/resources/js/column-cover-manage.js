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
  coverUploadMaxSize: 5242880,

  bindColumnManage: function () {
    HomePersonalize.filterCurrentColumnManageItem()
    $('.column-manage').off('click.columnManage')
      .on('click.columnManage', '.column-manage__save', HomePersonalize.saveColumnCoverFromItem)
      .on('click.columnManage', '.column-manage__clear', HomePersonalize.clearColumnCoverFromItem)
      .on('input.columnManage', '.column-manage__input', HomePersonalize.previewColumnCoverFromInput)
  },

  filterCurrentColumnManageItem: function () {
    var columnId = HomePersonalize.getCurrentManageColumnId()
    if (!columnId) {
      return
    }
    var $items = $('.column-manage__item')
    var $current = $items.filter(function () {
      return String($(this).data('column-id')) === columnId
    })
    if (!$current.length) {
      return
    }
    $items.not($current).remove()
    $('.column-manage__heading').text('全书封面')
  },

  getCurrentManageColumnId: function () {
    if (!window.URLSearchParams) {
      return ''
    }
    return new URLSearchParams(window.location.search).get('columnId') || ''
  },

  saveColumnCoverFromItem: function () {
    var $item = $(this).closest('.column-manage__item')
    HomePersonalize.saveColumnCover($item, $item.find('.column-manage__input').val())
  },

  clearColumnCoverFromItem: function () {
    var $item = $(this).closest('.column-manage__item')
    $item.find('.column-manage__input').val('')
    HomePersonalize.saveColumnCover($item, '')
  },

  previewColumnCoverFromInput: function () {
    HomePersonalize.previewColumnCover($(this).closest('.column-manage__item'), $(this).val())
  },

  saveColumnCover: function ($item, coverURL) {
    var columnId = $item.data('column-id')
    HomePersonalize.request({
      url: Label.servePath + '/api/columns/' + encodeURIComponent(columnId) + '/cover',
      type: 'POST',
      data: JSON.stringify({columnCoverURL: $.trim(coverURL)}),
      contentType: 'application/json;charset=UTF-8',
      success: function (data) {
        HomePersonalize.renderSavedColumnCover($item, data.column || {})
      },
    })
  },

  renderSavedColumnCover: function ($item, column) {
    $item.find('.column-manage__input').val(column.columnHasCover ? column.columnCoverURL : '')
    HomePersonalize.previewColumnCover($item, column.columnCoverURL)
    Util.alert('已保存')
  },

  previewColumnCover: function ($item, coverURL) {
    var $cover = $item.find('.column-manage__cover')
    var url = $.trim(coverURL || '')
    if (!url) {
      $cover.addClass('column-cover--default').css('background-image', '')
      return
    }
    $cover.css('background-image', HomePersonalize.formatCoverBackground(url))
    $cover.removeClass('column-cover--default')
  },

  formatCoverBackground: function (url) {
    return 'url("' + String(url || '').replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '")'
  },

  bindColumnUpload: function () {
    if (!$.fn || !$.fn.fileupload) {
      return
    }
    HomePersonalize.ensureColumnUploadForm()
    HomePersonalize.bindColumnUploadClick()
    HomePersonalize.initColumnFileUpload()
  },

  ensureColumnUploadForm: function () {
    if ($('#columnCoverUploadForm').length) {
      return
    }
    $('body').append('<form id="columnCoverUploadForm" style="display:none" method="POST"'
      + ' enctype="multipart/form-data"><input id="columnCoverUploadInput" type="file"'
      + ' name="file" accept="image/gif,image/jpeg,image/png,image/webp"></form>')
  },

  bindColumnUploadClick: function () {
    $('.column-manage').off('click.columnUpload')
      .on('click.columnUpload', '.column-manage__upload', function () {
        HomePersonalize.columnUploadTarget = $(this).closest('.column-manage__item')
        $('#columnCoverUploadInput').val('').trigger('click')
      })
  },

  initColumnFileUpload: function () {
    $('#columnCoverUploadForm').fileupload({
      acceptFileTypes: /(\.|\/)(gif|jpe?g|png|webp)$/i,
      maxFileSize: HomePersonalize.coverUploadMaxSize,
      multipart: true,
      pasteZone: null,
      dropZone: null,
      url: Label.servePath + '/upload',
      paramName: 'file[]',
      add: function (e, data) {
        HomePersonalize.validateUpload(data)
      },
      formData: function (form) {
        return form.serializeArray()
      },
      done: HomePersonalize.handleUploadDone,
      fail: HomePersonalize.handleUploadFail,
    })
  },

  handleUploadDone: function (e, data) {
    var url = HomePersonalize.readUploadedURL(data)
    if (!url || !HomePersonalize.columnUploadTarget) {
      Util.alert('上传失败')
      return
    }
    HomePersonalize.columnUploadTarget.find('.column-manage__input').val(url)
    HomePersonalize.previewColumnCover(HomePersonalize.columnUploadTarget, url)
  },

  readUploadedURL: function (data) {
    var succMap = data && data.result && data.result.data && data.result.data.succMap
    var key = succMap ? Object.keys(succMap)[0] : ''
    return key ? succMap[key] : ''
  },

  handleUploadFail: function (e, data) {
    Util.alert('上传失败: ' + (data && data.errorThrown ? data.errorThrown : '未知错误'))
  },

  validateUpload: function (data) {
    var file = data.files && data.files[0]
    if (!HomePersonalize.hasUploadFile(file)) {
      return
    }
    if (!window.File || !window.FileReader || !window.FileList || !window.Blob) {
      data.submit()
      return
    }
    HomePersonalize.validateUploadByReader(file, data)
  },

  hasUploadFile: function (file) {
    if (!file) {
      Util.alert('未选择图片')
      return false
    }
    return true
  },

  validateUploadByReader: function (file, data) {
    var reader = new FileReader()
    reader.readAsArrayBuffer(file)
    reader.onload = function (evt) {
      HomePersonalize.submitValidUpload(file, data, evt.target.result)
    }
    reader.onerror = function () {
      Util.alert('读取图片失败')
    }
  },

  submitValidUpload: function (file, data, buffer) {
    if (!HomePersonalize.isValidImage(file, buffer)) {
      Util.alert('只允许上传图片')
      return
    }
    if (buffer.byteLength > HomePersonalize.coverUploadMaxSize) {
      Util.alert('图片过大')
      return
    }
    data.submit()
  },

  isValidImage: function (file, buffer) {
    var fileBuf = new Uint8Array(buffer.slice(0, 11))
    if (typeof isImage === 'function') {
      return isImage(fileBuf)
    }
    return /^image\//.test(file.type || '')
  },
})
