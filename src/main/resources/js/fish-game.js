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
var FishGame = {
  request: function (url, method, data) {
    return $.ajax({ url: Label.servePath + url, type: method, data: data ? JSON.stringify(data) : null, contentType: 'application/json', headers: { csrfToken: Label.csrfToken || '' } }).fail(function (xhr) {
      var message = xhr.responseJSON && xhr.responseJSON.msg ? xhr.responseJSON.msg : '请求失败';
      FishGame.adminMessage(message);
      if (!$('[data-fish-game-admin-result]').length) Util.alert(message);
    });
  },
  formData: function (form) {
    var data = {};
    $(form).serializeArray().forEach(function (item) { data[item.name] = item.value; });
    return data;
  },
  adminMessage: function (message) { $('[data-fish-game-admin-result]').text(message); },
  downloadJson: function (data) {
    var blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    var link = document.createElement('a');
    link.href = URL.createObjectURL(blob);
    link.download = 'fish-games.json';
    link.click();
    URL.revokeObjectURL(link.href);
  },
  submit: function (form) {
    FishGame.request('/api/fish-games', 'POST', FishGame.formData(form)).done(function (res) {
      Util.alert(res.msg || '已提交审核');
      if (res.code === 0) { FishGame.closeModal(); window.location.reload(); }
    });
  },
  vote: function (gameId, value, button) {
    FishGame.request('/api/fish-games/' + gameId + '/vote', 'POST', { fishGameVoteValue: value }).done(function (res) {
      if (res.code === 0 && res.data) {
        FishGame.updateVoteUI(gameId, res.data);
      }
    });
  },
  updateVoteUI: function (gameId, data) {
    var vote = data.fishGameUserVote || '';
    $('[data-fish-game-id="' + gameId + '"]').each(function () {
      var current = $(this).data('fish-game-vote');
      $(this).toggleClass('is-selected', current === vote);
      $(this).attr('aria-pressed', current === vote ? 'true' : 'false');
      if (current === 'like') {
        var count = $(this).find('.fish-game-vote__count');
        if (count.length) count.text(data.fishGameLikeCount || 0);
        else $(this).find('span').first().text(data.fishGameLikeCount || 0);
      }
      if (current === 'dislike') {
        $(this).find('.fish-game-dislike-indicator').text(vote === 'dislike' ? '1' : '');
      }
    });
  },
  comment: function (form) {
    FishGame.request('/api/fish-games/' + window.fishGameId + '/comments', 'POST', { content: $(form).find('[name=content]').val() }).done(function (res) {
      Util.alert(res.msg || '评论已发布');
      if (res.code === 0) window.location.reload();
    });
  },
  adminAdd: function (form) {
    FishGame.request('/api/admin/fish-games', 'POST', FishGame.formData(form)).done(function (res) {
      FishGame.adminMessage(res.msg || '已添加');
      if (res.code === 0) window.location.reload();
    });
  },
  adminEdit: function (form) {
    FishGame.request('/api/admin/fish-games/' + $(form).data('id') + '/edit', 'POST', FishGame.formData(form)).done(function (res) {
      FishGame.adminMessage(res.msg || '已保存');
      if (res.code === 0) window.location.reload();
    });
  },
  adminExport: function () {
    FishGame.request('/api/admin/fish-games/export', 'GET').done(function (res) {
      if (res.games) { FishGame.downloadJson(res); FishGame.adminMessage('JSON 已导出'); }
      else FishGame.adminMessage(res.msg || '导出失败');
    });
  },
  adminImport: function () {
    var value = $.trim($('[data-fish-game-import]').val());
    if (!value) { FishGame.adminMessage('请粘贴 JSON'); return; }
    var data;
    try { data = JSON.parse(value); } catch (e) { FishGame.adminMessage('JSON 格式不合法'); return; }
    FishGame.request('/api/admin/fish-games/import', 'POST', data).done(function (res) {
      var result = res.data || {};
      FishGame.adminMessage(res.msg || ('已导入 ' + (result.imported || 0) + ' 个鱼游，跳过 ' + (result.skipped || 0) + ' 个重复网址'));
      if (res.code === 0) window.location.reload();
    });
  },
  readImportFile: function (input) {
    var file = input.files && input.files[0];
    if (!file) return;
    $('[data-fish-game-import-file-name]').text(file.name || '已选择文件');
    var reader = new FileReader();
    reader.onload = function () { $('[data-fish-game-import]').val(reader.result); };
    reader.onerror = function () { FishGame.adminMessage('读取文件失败'); };
    reader.readAsText(file);
  },
  edit: function (form) {
    FishGame.request('/api/fish-games/' + window.fishGameId + '/edit', 'POST', FishGame.formData(form)).done(function (res) {
      Util.alert(res.msg || '已提交审核');
      if (res.code === 0) window.location.reload();
    });
  },
  openModal: function () {
    var modal = $('[data-fish-game-modal]')[0];
    if (!modal) return;
    modal.hidden = false;
    document.body.classList.add('fish-game-modal-open');
    $(modal).find('input, textarea').first().trigger('focus');
  },
  closeModal: function () {
    var modal = $('[data-fish-game-modal]')[0];
    if (!modal) return;
    modal.hidden = true;
    document.body.classList.remove('fish-game-modal-open');
  }
};
$(function () {
  $('[data-fish-game-open]').on('click', FishGame.openModal);
  $('[data-fish-game-close]').on('click', FishGame.closeModal);
  $(document).on('keydown', function (event) { if (event.key === 'Escape') FishGame.closeModal(); });
  $('[data-fish-game-submit]').on('submit', function (e) { e.preventDefault(); FishGame.submit(this); });
  $(document).on('click', '[data-fish-game-vote]', function (event) {
    event.preventDefault();
    event.stopPropagation();
    var button = this;
    FishGame.vote($(button).data('fish-game-id') || window.fishGameId, $(button).data('fish-game-vote'), button);
  });
  $('.fish-game-card[data-fish-game-url]').on('click', function (event) {
    if ($(event.target).closest('a, button').length) return;
    window.open($(this).data('fish-game-url'), '_blank', 'noopener');
  });
  $('[data-fish-game-comment]').on('submit', function (e) { e.preventDefault(); FishGame.comment(this); });
  $('[data-fish-game-edit-form]').on('submit', function (e) { e.preventDefault(); FishGame.edit(this); });
  $('[data-admin-review]').on('click', function () {
    FishGame.request('/api/admin/fish-games/' + $(this).data('admin-review') + '/review', 'POST', { fishGameStatus: $(this).data('status') }).done(function (res) {
      Util.alert(res.msg || '已更新');
      if (res.code === 0) window.location.reload();
    });
  });
  $('[data-admin-add]').on('submit', function (e) { e.preventDefault(); FishGame.adminAdd(this); });
  $('[data-admin-edit-form]').on('submit', function (e) { e.preventDefault(); FishGame.adminEdit(this); });
  $('[data-fish-game-export]').on('click', FishGame.adminExport);
  $('[data-fish-game-import-file]').on('change', function () { FishGame.readImportFile(this); });
  $('[data-fish-game-import-submit]').on('click', FishGame.adminImport);
});
