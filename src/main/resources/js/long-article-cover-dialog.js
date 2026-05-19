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
var LongArticleCoverDialog = {
  coverUploadMaxSize: 5242880,
  saveTimer: 0,
  init: function () {
    if ($('#longArticleColumnId').length === 0) return
    LongArticleCoverDialog.bindOpen()
    LongArticleCoverDialog.ensureUploadForm()
    LongArticleCoverDialog.restorePendingCover()
  },
  bindOpen: function () {
    $('#longArticleColumnManage').off('click.longCover').on('click.longCover', function () {
      LongArticleCoverDialog.open()
      return false
    })
  },
  open: function () {
    if ($('#longArticleColumnId').val() === '') return
    LongArticleCoverDialog.ensureDialog()
    LongArticleCoverDialog.fillDialog()
    LongArticleCoverDialog.setSaving(false)
    LongArticleCoverDialog.setStatus('')
    $('#longArticleCoverDialog').removeClass('fn-none')
  },
  close: function () {
    clearTimeout(LongArticleCoverDialog.saveTimer)
    LongArticleCoverDialog.setSaving(false)
    LongArticleCoverDialog.setStatus('')
    $('#longArticleCoverDialog').addClass('fn-none')
  },
  ensureDialog: function () {
    if ($('#longArticleCoverDialog').length > 0) return
    $('body').append(
      '<div id="longArticleCoverDialog" class="long-article-cover-dialog fn-none">' +
      '<div class="long-article-cover-dialog__mask"></div><div class="long-article-cover-dialog__panel">' +
      '<div class="long-article-cover-dialog__head"><strong>全书封面</strong>' +
      '<button type="button" class="long-article-cover-dialog__close">×</button></div>' +
      '<div class="long-article-cover-dialog__body"><div class="long-article-cover-dialog__preview column-cover column-cover--default"><span></span></div>' +
      '<input id="longArticleCoverDialogInput" type="hidden" maxlength="1024"/></div>' +
      '<div class="long-article-cover-dialog__status" aria-live="polite"></div><div class="long-article-cover-dialog__actions">' +
      '<button type="button" class="long-article-cover-dialog__upload">上传</button><button type="button" class="green long-article-cover-dialog__save">保存</button>' +
      '<button type="button" class="long-article-cover-dialog__clear">删除</button>' +
      '</div></div></div>')
    LongArticleCoverDialog.bindDialog()
  },
  bindDialog: function () {
    $('#longArticleCoverDialog')
      .on('click', '.long-article-cover-dialog__mask, .long-article-cover-dialog__close',
        LongArticleCoverDialog.close)
      .on('input', '#longArticleCoverDialogInput', LongArticleCoverDialog.previewInput)
      .on('click', '.long-article-cover-dialog__upload', LongArticleCoverDialog.openUpload)
      .on('click', '.long-article-cover-dialog__save', LongArticleCoverDialog.save)
      .on('click', '.long-article-cover-dialog__clear', LongArticleCoverDialog.clear)
  },
  fillDialog: function () {
    var title = LongArticleCoverDialog.getSelectedColumnTitle()
    var coverURL = LongArticleCoverDialog.getSelectedCoverURL()
    $('#longArticleCoverDialog .long-article-cover-dialog__preview span').text(title)
    $('#longArticleCoverDialogInput').val(coverURL)
    LongArticleCoverDialog.preview(coverURL)
  },
  getSelectedColumnTitle: function () {
    if ($('#longArticleColumnId').val() === '__NEW__') {
      return $.trim($('#longArticleColumnTitle').val()) || '新专栏'
    }
    var selected = $.trim($('#longArticleColumnId option:selected').text())
    return $.trim(selected.replace(/（\d+ 章）$/, '')) || '新专栏'
  },
  getSelectedCoverURL: function () {
    var columnId = $('#longArticleColumnId').val()
    if (columnId === '__NEW__') {
      return $('#longArticleColumnCoverURL').val() ||
        LongArticleCoverDialog.getPendingCoverFromPostData()
    }
    var $data = LongArticleCoverDialog.findColumnData(columnId)
    return $data.attr('data-has-cover') === 'true'
      ? $data.attr('data-cover-url') || ''
      : ''
  },
  findColumnData: function (columnId) {
    return $('#longArticleColumnCoverData span').filter(function () {
      return String($(this).attr('data-column-id')) === String(columnId)
    }).first()
  },
  previewInput: function () {
    LongArticleCoverDialog.preview($(this).val())
  },
  preview: function (coverURL) {
    var $preview = $('#longArticleCoverDialog .long-article-cover-dialog__preview')
    var url = $.trim(coverURL || '')
    if (url === '') {
      $preview.addClass('column-cover--default').css('background-image', '')
      return
    }
    $preview.removeClass('column-cover--default').css('background-image',
      LongArticleCoverDialog.formatCoverBackground(url))
  },
  formatCoverBackground: function (url) {
    return 'url("' + String(url || '').replace(/\\/g, '\\\\').replace(/"/g, '\\"') + '")'
  },
  openUpload: function () {
    LongArticleCoverDialog.ensureUploadForm()
    if ($('#longArticleCoverUploadInput').length === 0) {
      LongArticleCoverDialog.setStatus('上传不可用', true)
      return
    }
    LongArticleCoverDialog.setStatus('')
    $('#longArticleCoverUploadInput').val('').trigger('click')
  },
  save: function () {
    var columnId = $('#longArticleColumnId').val()
    var coverURL = $.trim($('#longArticleCoverDialogInput').val())
    LongArticleCoverDialog.setSaving(true)
    LongArticleCoverDialog.setStatus('')
    if (columnId === '__NEW__') {
      LongArticleCoverDialog.savePending(coverURL)
      return
    }
    LongArticleCoverDialog.saveExisting(columnId, coverURL)
  },
  savePending: function (coverURL) {
    $('#longArticleColumnCoverURL').val(coverURL)
    LongArticleCoverDialog.savePendingToPostData(coverURL)
    LongArticleCoverDialog.finishSave()
  },
  savePendingToPostData: function (coverURL) {
    if (!localStorage.postData) {
      return
    }
    var postData = JSON.parse(localStorage.postData)
    postData.longArticleColumnCoverURL = coverURL
    localStorage.postData = JSON.stringify(postData)
  },
  restorePendingCover: function () {
    if ($('#longArticleColumnId').val() !== '__NEW__' ||
      $('#longArticleColumnCoverURL').val() !== '') {
      return
    }
    $('#longArticleColumnCoverURL').val(LongArticleCoverDialog.getPendingCoverFromPostData())
  },
  getPendingCoverFromPostData: function () {
    return localStorage.postData
      ? JSON.parse(localStorage.postData).longArticleColumnCoverURL || ''
      : ''
  },
  saveExisting: function (columnId, coverURL) {
    $.ajax({
      url: Label.servePath + '/api/columns/' + encodeURIComponent(columnId) + '/cover',
      type: 'POST',
      data: JSON.stringify({columnCoverURL: coverURL}),
      contentType: 'application/json;charset=UTF-8',
      cache: false,
      success: function (result) {
        LongArticleCoverDialog.handleSaveResult(result, columnId)
      },
      error: function () {
        LongArticleCoverDialog.failSave('保存失败')
      },
    })
  },
  handleSaveResult: function (result, columnId) {
    if (!result || result.code !== 0) {
      LongArticleCoverDialog.failSave(result && result.msg ? result.msg : '保存失败')
      return
    }
    var column = result.data && result.data.column ? result.data.column : {}
    LongArticleCoverDialog.updateColumnData(columnId, column)
    LongArticleCoverDialog.finishSave()
  },
  updateColumnData: function (columnId, column) {
    var $data = LongArticleCoverDialog.findColumnData(columnId)
    $data.attr('data-cover-url', column.columnCoverURL || '')
    $data.attr('data-has-cover', column.columnHasCover ? 'true' : 'false')
    $('#longArticleColumnCoverURL').val(column.columnHasCover ? column.columnCoverURL : '')
  },
  clear: function () {
    $('#longArticleCoverDialogInput').val('')
    LongArticleCoverDialog.preview('')
    LongArticleCoverDialog.setStatus('待保存')
  },
  setStatus: function (message, isError) {
    $('#longArticleCoverDialog .long-article-cover-dialog__status')
      .text(message || '')
      .toggleClass('long-article-cover-dialog__status--error', isError === true)
  },
  setSaving: function (isSaving) {
    var $dialog = $('#longArticleCoverDialog')
    $dialog.find('.long-article-cover-dialog__save')
      .prop('disabled', isSaving)
      .text(isSaving ? '保存中' : '保存')
    $dialog.find('.long-article-cover-dialog__upload, .long-article-cover-dialog__clear')
      .prop('disabled', isSaving)
  },
  failSave: function (message) {
    LongArticleCoverDialog.setSaving(false)
    LongArticleCoverDialog.setStatus(message, true)
  },
  finishSave: function () {
    LongArticleCoverDialog.setSaving(false)
    LongArticleCoverDialog.setStatus('已保存')
    LongArticleCoverDialog.saveTimer = setTimeout(LongArticleCoverDialog.close, 650)
  },
  ensureUploadForm: function () {
    if (!$.fn || !$.fn.fileupload || $('#longArticleCoverUploadForm').length > 0) {
      return
    }
    $('body').append('<form id="longArticleCoverUploadForm" style="display:none" method="POST"'
      + ' enctype="multipart/form-data"><input id="longArticleCoverUploadInput" type="file"'
      + ' name="file" accept="image/gif,image/jpeg,image/png,image/webp"></form>')
    LongArticleCoverDialog.initUpload()
  },
  initUpload: function () {
    $('#longArticleCoverUploadForm').fileupload({
      acceptFileTypes: /(\.|\/)(gif|jpe?g|png|webp)$/i,
      maxFileSize: LongArticleCoverDialog.coverUploadMaxSize,
      multipart: true,
      pasteZone: null,
      dropZone: null,
      url: Label.servePath + '/upload',
      paramName: 'file[]',
      add: function (e, data) {
        LongArticleCoverDialog.validateUpload(data)
      },
      formData: function (form) {
        return form.serializeArray()
      },
      done: LongArticleCoverDialog.handleUploadDone,
      fail: LongArticleCoverDialog.handleUploadFail,
    })
  },
  validateUpload: function (data) {
    var file = data.files && data.files[0]
    if (!file) {
      LongArticleCoverDialog.setStatus('未选择图片', true)
      return
    }
    if (!window.File || !window.FileReader || !window.FileList || !window.Blob) {
      data.submit()
      return
    }
    LongArticleCoverDialog.validateUploadByReader(file, data)
  },
  validateUploadByReader: function (file, data) {
    var reader = new FileReader()
    reader.readAsArrayBuffer(file)
    reader.onload = function (evt) {
      LongArticleCoverDialog.submitValidUpload(file, data, evt.target.result)
    }
    reader.onerror = function () {
      LongArticleCoverDialog.setStatus('读取失败', true)
    }
  },
  submitValidUpload: function (file, data, buffer) {
    if (!LongArticleCoverDialog.isValidImage(file, buffer)) {
      LongArticleCoverDialog.setStatus('只允许图片', true)
      return
    }
    if (buffer.byteLength > LongArticleCoverDialog.coverUploadMaxSize) {
      LongArticleCoverDialog.setStatus('图片过大', true)
      return
    }
    LongArticleCoverDialog.setStatus('上传中')
    data.submit()
  },
  isValidImage: function (file, buffer) {
    var fileBuf = new Uint8Array(buffer.slice(0, 11))
    if (typeof isImage === 'function') {
      return isImage(fileBuf)
    }
    return /^image\//.test(file.type || '')
  },
  handleUploadDone: function (e, data) {
    var url = LongArticleCoverDialog.readUploadedURL(data)
    if (!url) {
      LongArticleCoverDialog.setStatus('上传失败', true)
      return
    }
    $('#longArticleCoverDialogInput').val(url)
    LongArticleCoverDialog.preview(url)
    LongArticleCoverDialog.setStatus('已上传')
  },
  readUploadedURL: function (data) {
    var succMap = data && data.result && data.result.data && data.result.data.succMap
    var key = succMap ? Object.keys(succMap)[0] : ''
    return key ? succMap[key] : ''
  },
  handleUploadFail: function (e, data) {
    LongArticleCoverDialog.setStatus(data && data.errorThrown
      ? '上传失败: ' + data.errorThrown
      : '上传失败', true)
  },
}
$(function () {
  LongArticleCoverDialog.init()
})
