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
/**
 * @fileoverview add-article.
 *
 * @author <a href="http://vanessa.b3log.org">Liyuan Li</a>
 * @author <a href="http://88250.b3log.org">Liang Ding</a>
 * @author <a href="https://ld246.com/member/ZephyrJung">Zephyr</a>
 * @author <a href="https://qiankunpingtai.cn">qiankunpingtai</a>
 */

/**
 * @description Add article function.
 * @static
 */
var AddArticle = {
  editor: undefined,
  rewardEditor: undefined,
  currentDraftId: '',
  draftCsrfToken: '',
  _collectTags: function () {
    var articleTags = ''
    $('.tags-input .tag .text').each(function () {
      articleTags += $(this).text() + ','
    })
    var inputTag = $.trim($('#articleTags').val() || '')
    if (inputTag !== '') {
      articleTags += inputTag.replace(/[,，、；;]/g, '') + ','
    }
    return articleTags
  },
  _getArticleType: function () {
    var articleTypeRadio = $('input[type=\'radio\'][name=\'articleType\']:checked')
    if (articleTypeRadio.length > 0) {
      return parseInt(articleTypeRadio.val())
    }
    return Label.articleType
  },
  _collectDraftData: function () {
    var postData = JSON.parse(localStorage.postData || '{}')
    var draft = {
      articleDraftId: AddArticle.currentDraftId,
      articleTitle: $('#articleTitle').val().replace(/(^\s*)|(\s*$)/g, ''),
      articleContent: AddArticle.editor ? AddArticle.editor.getValue() : '',
      articleDraftThoughtContent: postData.thoughtContent || '',
      articleTags: AddArticle._collectTags(),
      articleType: AddArticle._getArticleType(),
      articleCommentable: $('#articleCommentable').prop('checked') !== false,
      articleNotifyFollowers: $('#articleNotifyFollowers').prop('checked') === true,
      articleShowInList: $('#articleShowInList').prop('checked') ? 1 : 0,
      articleStatement: $('#articleStatement').val() || 0,
      articleAnonymous: $('#articleAnonymous').prop('checked') === true,
    }
    draft = $.extend(draft, AddArticle._collectLongArticleDraftData())
    if (AddArticle.rewardEditor) {
      draft.articleRewardContent = AddArticle.rewardEditor.getValue()
    }
    if ($('#articleRewardPoint').length > 0) {
      draft.articleRewardPoint = $('#articleRewardPoint').val()
    }
    if ($('#articleAskPoint').length > 0) {
      draft.articleQnAOfferPoint = $('#articleAskPoint').val()
    }
    return draft
  },
  _collectLongArticleDraftData: function () {
    if ($('#longArticleColumnId').length === 0) {
      return {}
    }
    var columnId = $('#longArticleColumnId').val() || ''
    var columnTitle = ''
    if (columnId === '__NEW__' && $('#longArticleColumnTitle').length > 0) {
      columnTitle = $('#longArticleColumnTitle').val().replace(/(^\s*)|(\s*$)/g, '')
    }
    var chapterNo = ''
    if (columnId !== '' && $('#longArticleChapterNo').length > 0) {
      chapterNo = $('#longArticleChapterNo').val().replace(/(^\s*)|(\s*$)/g, '')
    }
    return {
      columnId: columnId,
      columnTitle: columnTitle,
      chapterNo: chapterNo,
      columnCoverURL: $('#longArticleColumnCoverURL').val() || '',
    }
  },
  _hasDraftContent: function (draft) {
    return $.trim(draft.articleTitle) !== '' ||
      $.trim(draft.articleContent) !== '' ||
      $.trim(draft.articleTags.replace(/,/g, '')) !== '' ||
      $.trim(draft.articleDraftThoughtContent || '') !== '' ||
      $.trim(draft.articleRewardContent || '') !== ''
  },
  _showDraftTip: function (message, error) {
    $('#addArticleTip').toggleClass('error', error === true).empty().
      append($('<ul></ul>').append($('<li></li>').text(message)))
  },
  saveDraft: function (csrfToken, it) {
    var draft = AddArticle._collectDraftData()
    if (!AddArticle._hasDraftContent(draft)) {
      AddArticle._showDraftTip('草稿为空', true)
      return
    }
    $.ajax({
      url: Label.servePath + '/api/article-drafts',
      type: 'POST',
      headers: {'csrfToken': csrfToken},
      cache: false,
      data: JSON.stringify(draft),
      beforeSend: function () {
        $(it).attr('disabled', 'disabled').css('opacity', '0.3')
      },
      error: function (jqXHR, textStatus, errorThrown) {
        AddArticle._showDraftTip(errorThrown, true)
      },
      success: function (result) {
        if (0 === result.code) {
          AddArticle.currentDraftId = result.data.draft.oId
          AddArticle._showDraftTip('草稿已保存', false)
        } else {
          AddArticle._showDraftTip(result.msg, true)
        }
      },
      complete: function () {
        $(it).removeAttr('disabled').css('opacity', '1')
      },
    })
  },
  openDraftBox: function (csrfToken) {
    AddArticle.draftCsrfToken = csrfToken
    AddArticle._ensureDraftBox().removeClass('fn-none')
    AddArticle.loadDrafts()
  },
  closeDraftBox: function () {
    AddArticle._ensureDraftBox().addClass('fn-none')
  },
  _ensureDraftBox: function () {
    if ($('#articleDraftBox').length === 1) {
      return $('#articleDraftBox')
    }
    $('body').append(
      '<div id="articleDraftBox" class="article-draft-box fn-none">' +
      '<div class="article-draft-box__panel">' +
      '<div class="article-draft-box__head"><strong>草稿箱</strong>' +
      '<button type="button" class="article-draft-box__close" aria-label="关闭" ' +
      'onclick="AddArticle.closeDraftBox()">&times;</button></div>' +
      '<div class="article-draft-box__body">' +
      '<div class="article-draft-box__list">' +
      '<div class="article-draft-box__state">加载中...</div></div></div>' +
      '</div></div>')
    return $('#articleDraftBox')
  },
  loadDrafts: function () {
    $('.article-draft-box__list').html('<div class="article-draft-box__state">加载中...</div>')
    $.ajax({
      url: Label.servePath + '/api/article-drafts',
      type: 'GET',
      cache: false,
      success: function (result) {
        if (0 === result.code) {
          AddArticle._renderDrafts(result.data.drafts || [])
        } else {
          $('.article-draft-box__list').empty().
            append($('<div class="article-draft-box__state"></div>').text(result.msg))
        }
      },
    })
  },
  _renderDrafts: function (drafts) {
    var $list = $('.article-draft-box__list').empty()
    var currentType = AddArticle._getArticleType()
    var count = 0
    $.each(drafts, function (index, draft) {
      if (parseInt(draft.articleDraftType) !== currentType) {
        return
      }
      count++
      $list.append(AddArticle._buildDraftItem(draft))
    })
    if (count === 0) {
      $list.append($('<div class="article-draft-box__empty"></div>').text('暂无草稿'))
    }
  },
  _buildDraftItem: function (draft) {
    var $item = $('<div class="article-draft-box__item"></div>')
    var title = draft.articleDraftTitle || '未命名'
    var typeText = parseInt(draft.articleDraftType) === 6 ? '长文章' : '帖子'
    var $main = $('<div class="article-draft-box__item-main"></div>').appendTo($item)
    $('<div class="article-draft-box__title"></div>').text(title).appendTo($main)
    $('<div class="article-draft-box__summary"></div>').
      text(draft.articleDraftSummary || '无正文').appendTo($main)
    $('<div class="article-draft-box__meta"></div>').
      text(typeText + ' · ' + new Date(draft.articleDraftUpdatedTime).toLocaleString()).appendTo($main)
    var $actions = $('<div class="article-draft-box__actions"></div>').appendTo($item)
    $('<button type="button" class="article-draft-box__restore">恢复</button>').on('click', function () {
      AddArticle.restoreDraft(draft.oId)
    }).appendTo($actions)
    $('<button type="button" class="article-draft-box__remove">删除</button>').on('click', function () {
      AddArticle.removeDraft(draft.oId)
    }).appendTo($actions)
    return $item
  },
  restoreDraft: function (draftId) {
    if (!confirm('恢复草稿会覆盖当前内容，继续？')) {
      return
    }
    $.ajax({
      url: Label.servePath + '/api/article-drafts/' + draftId,
      type: 'GET',
      cache: false,
      success: function (result) {
        if (0 === result.code) {
          AddArticle._fillDraft(result.data.draft)
          AddArticle.closeDraftBox()
          AddArticle._showDraftTip('草稿已恢复', false)
        } else {
          $('.article-draft-box__list').empty().
            append($('<div class="article-draft-box__state"></div>').text(result.msg))
        }
      },
    })
  },
  removeDraft: function (draftId) {
    if (!confirm('删除这个草稿？')) {
      return
    }
    $.ajax({
      url: Label.servePath + '/api/article-drafts/' + draftId,
      type: 'DELETE',
      headers: {'csrfToken': AddArticle.draftCsrfToken},
      cache: false,
      success: function (result) {
        if (0 === result.code) {
          if (AddArticle.currentDraftId === draftId) {
            AddArticle.currentDraftId = ''
          }
          AddArticle.loadDrafts()
        } else {
          $('.article-draft-box__list').empty().
            append($('<div class="article-draft-box__state"></div>').text(result.msg))
        }
      },
    })
  },
  _fillDraft: function (draft) {
    AddArticle.currentDraftId = draft.oId
    $('#articleTitle').val(draft.articleDraftTitle || '')
    if (AddArticle.editor) {
      AddArticle.editor.setValue(draft.articleDraftContent || '')
    }
    AddArticle._setTags(draft.articleDraftTags || '')
    $('input[type=\'radio\'][name=\'articleType\'][value=\'' + draft.articleDraftType + '\']').
      prop('checked', true)
    Label.articleType = parseInt(draft.articleDraftType)
    $('#articleStatement').val(draft.articleDraftStatement || 0)
    $('#articleCommentable').prop('checked', draft.articleDraftCommentable !== false)
    $('#articleNotifyFollowers').prop('checked', draft.articleDraftNotifyFollowers === true)
    $('#articleShowInList').prop('checked', parseInt(draft.articleDraftShowInList) !== 0)
    $('#articleAnonymous').prop('checked', draft.articleDraftAnonymous === true)
    AddArticle._fillLongArticleDraft(draft)
    AddArticle._fillRewardDraft(draft)
    AddArticle._syncPostData(draft)
  },
  _fillLongArticleDraft: function (draft) {
    if ($('#longArticleColumnId').length === 0) {
      return
    }
    $('#longArticleColumnId').val(draft.articleDraftColumnId || '')
    $('#longArticleColumnTitle').val(draft.articleDraftColumnTitle || '')
    $('#longArticleChapterNo').val(draft.articleDraftChapterNo || '')
    AddArticle.toggleLongColumnCreate()
  },
  _fillRewardDraft: function (draft) {
    if (AddArticle.rewardEditor) {
      if (draft.articleDraftRewardContent || draft.articleDraftRewardPoint) {
        $('#showReward').click()
      }
      AddArticle.rewardEditor.setValue(draft.articleDraftRewardContent || '')
    }
    $('#articleRewardPoint').val(draft.articleDraftRewardPoint || '')
    $('#articleAskPoint').val(draft.articleDraftQnAOfferPoint || '')
  },
  _setTags: function (tags) {
    $('.tags-input .tag').remove()
    $('#articleTags').val('')
    $.each(tags.split(','), function (index, tag) {
      var text = $.trim(tag).replace(/,/g, '')
      if (text === '') {
        return
      }
      var $tag = $('<span class="tag"><span class="text"></span><span class="close">x</span></span>')
      $tag.find('.text').text(text)
      $('.post .tags-selected').append($tag)
    })
  },
  _syncPostData: function (draft) {
    var postData = JSON.parse(localStorage.postData || '{}')
    postData.title = draft.articleDraftTitle || ''
    postData.content = draft.articleDraftContent || ''
    postData.thoughtContent = draft.articleDraftThoughtContent || ''
    postData.thoughtTime = (new Date()).getTime() -
      AddArticle._getThoughtLastTime(postData.thoughtContent)
    postData.tags = draft.articleDraftTags || ''
    postData.rewardContent = draft.articleDraftRewardContent || ''
    postData.rewardPoint = draft.articleDraftRewardPoint || ''
    postData.QnAOfferPoint = draft.articleDraftQnAOfferPoint || ''
    localStorage.postData = JSON.stringify(postData)
  },
  _getThoughtLastTime: function (thoughtContent) {
    if (!thoughtContent) {
      return 0
    }
    var unitSep = String.fromCharCode(31)
    var recordSep = String.fromCharCode(30)
    var records = thoughtContent.split(recordSep).filter(function (record) {
      return record !== ''
    })
    if (records.length === 0) {
      return 0
    }
    var units = records[records.length - 1].split(unitSep)
    if (units.length < 2) {
      return 0
    }
    return parseInt(units[1]) || 0
  },
  /**
   * 记录思绪过程
   * @param {string} currentValue 当前内容
   * @param {string} prevValue 输入前的上一次值
   * @param {Object} postData 发贴本地缓存数据
   */
  recordThought: function (currentValue, prevValue, postData) {
    var diff = JsDiff.diffChars(prevValue, currentValue)
    var unitSep = String.fromCharCode(31) // Unit Separator (单元分隔符)
    var change = ''
    var fromCh = 0
    var fromLine = 0
    var toCh = 0
    var toLine = 0
    for (var iMax = diff.length, i = 0; i < iMax; i++) {
      var time = (new Date()).getTime() - postData.thoughtTime
      var valueArr = diff[i].value.split('\n')

      if (!diff[i].removed && !diff[i].added) {
        toLine = fromLine = valueArr.length - 1 + fromLine
        if (valueArr.length === 1) {
          toCh = fromCh = fromCh + valueArr[valueArr.length - 1].length
        } else {
          toCh = fromCh = valueArr[valueArr.length - 1].length
        }
      }

      if (diff[i].removed) {
        toCh += diff[i].count
        toLine += valueArr.length - 1

        change += String.fromCharCode(24) + unitSep + time // cancel
          + unitSep + fromCh + '-' + fromLine
          + unitSep + toCh + '-' + toLine
          + String.fromCharCode(30)  // Record Separator (记录分隔符)
      } else if (diff[i].added) {
        change += diff[i].value + unitSep + time
          + unitSep + fromCh + '-' + fromLine
          + unitSep + toCh + '-' + toLine
          + String.fromCharCode(30)  // Record Separator (记录分隔符)

        if (valueArr[valueArr.length - 1].length === 0) {
          toCh = fromCh = 0
        } else {
          toCh = fromCh = valueArr[valueArr.length - 1].length + fromCh
        }
        toLine = fromLine = valueArr.length - 1 + fromLine
      }
    }

    postData.thoughtContent += change
  },

  /**
   * @description 删除文章
   * @csrfToken [string] CSRF 令牌
   * @it [bom] 调用事件的元素
   */
  remove: function (csrfToken, it) {
    if (!confirm(Label.confirmRemoveLabel)) {
      return
    }

    $.ajax({
      url: Label.servePath + '/article/' + Label.articleOId + '/remove',
      type: 'POST',
      headers: {'csrfToken': csrfToken},
      cache: false,
      beforeSend: function () {
        $(it).attr('disabled', 'disabled').css('opacity', '0.3')
      },
      error: function (jqXHR, textStatus, errorThrown) {
        $('#addArticleTip').
          addClass('error').
          html('<ul><li>' + errorThrown + '</li></ul>')
      },
      success: function (result, textStatus) {
        $(it).removeAttr('disabled').css('opacity', '1')
        if (0 === result.code) {
          window.location.href = Label.servePath + '/member/' + Label.userName
        } else {
          $('#addArticleTip').
            addClass('error').
            html('<ul><li>' + result.msg + '</li></ul>')
        }
      },
      complete: function () {
        $(it).removeAttr('disabled').css('opacity', '1')
      },
    })
  },
  /**
   * @description 发布文章
   * @csrfToken [string] CSRF 令牌
   * @it [Bom] 触发事件的元素
   */
  confirmAdd: function (csrfToken, it) {
    // 长文章不需要标签
    if ($('.tags-input').length > 0 && $('.tags-input .tag .text').length === 0) {
      $('#addArticleTip').
      addClass('error').
      html('<ul><li>为了让你的文章更好地被归类和展示，请至少填写一个标签哦 :)</li></ul>')
      return
    }

    // 长文章（articleType=6）默认视为好帖，直接发放奖励
    if (Label.articleType === 6) {
      AddArticle.add(csrfToken, it, true)
      return
    }

    if ($('.tags-input').length === 0 || ($('.tags-input .tag .text').text().indexOf('新人报道') != -1 || $('.tags-input .tag .text').text().indexOf('新人报到') != -1)) {
      AddArticle.add(csrfToken, it)
    } else {
      Swal.fire({
        html: "根据 <a href='https://fishpi.cn/article/1684378758315' target='_blank'>摸鱼派发帖规范细则</a>，请对您的帖子进行自觉分类，自行选择是否获得<a target='_blank' href='https://fishpi.cn/article/1683775497629'>好帖积分和活跃度奖励</a> (300积分和45%活跃度，每日仅第一次有效)<br><br>" +
            "<b>有积分奖励的帖子：</b>原创的技术文章 / 原创且用心的美食、旅游、生活内容 / 发自内心的分享 / 有营养的问答 / 原创且用心的长短篇小说、故事、纪实创作<br><br>" +
            "<b>没有积分奖励的帖子：</b>发牢骚、感慨 / 非原创的内容 / 无意义内容帖、单纯的水帖 / 新人报道帖 / 广告帖 / 不用心、无价值的长短篇小说、故事、纪实创作<br><br>" +
            "<b>请注意：</b><b>帖子发布后24小时内无法删除！</b>如果不知道该选哪个，请选“我就随便写写”，选择领取奖励后，我们将对帖子进行检查，如不符合规则，您的积分奖励将会被扣除。",
        icon: 'question',
        showCancelButton: true,
        confirmButtonColor: '#3085d6',
        cancelButtonColor: '#707070',
        confirmButtonText: '这是好帖，给我奖励！',
        cancelButtonText: '我就随便写写，不需要奖励',
      }).then((result) => {
        if (result.isConfirmed) {
          AddArticle.add(csrfToken, it, true)
        } else if (result.dismiss === Swal.DismissReason.cancel) {
          AddArticle.add(csrfToken, it)
        }
      })
    }
  },
  add: function (csrfToken, it, isGoodArticle) {
    // 长文章不需要标签
    var hasTagsInput = $('.tags-input').length > 0;
    if (hasTagsInput && $('.tags-input .tag .text').length === 0) {
      $('#addArticleTip').
      addClass('error').
      html('<ul><li>为了让你的文章更好地被归类和展示，请至少填写一个标签哦 :)</li></ul>')
      return;
    }

    if (Validate.goValidate({
      target: $('#addArticleTip'),
      data: [
        {
          'type': 'string',
          'max': 256,
          'msg': Label.articleTitleErrorLabel,
          'target': $('#articleTitle'),
        }],
    })) {
      var articleType = 0;
      var articleTypeRadio = $('input[type=\'radio\'][name=\'articleType\']:checked');
      if (articleTypeRadio.length > 0) {
        articleType = parseInt(articleTypeRadio.val());
      }

      if (articleType !== 5) {
        if ($('#articleRewardPoint').length > 0 && $('#articleRewardPoint').data('orval')
            && !/^\+?[1-9][0-9]*$/.test($('#articleRewardPoint').val())) {
          $('#addArticleTip').addClass('error').html('<ul><li>'
              + Label.articleRewardPointErrorLabel + '</li></ul>')
          return false
        }
      }

      var articleTags = ''
      if (hasTagsInput) {
        $('.tags-input .tag .text').each(function () {
          articleTags += $(this).text() + ','
        })
      }

      // 长文章使用 Label.articleType，否则从 radio 获取
      var finalArticleType = articleTypeRadio.length > 0 ? articleType : Label.articleType;

      var requestJSONObject = {
        articleTitle: $('#articleTitle').val().replace(/(^\s*)|(\s*$)/g, ''),
        articleContent: this.editor.getValue(),
        articleTags: articleTags,
        articleType: finalArticleType,
      }

      // 长文章不需要这些选项
      if ($('#articleCommentable').length > 0) {
        requestJSONObject.articleCommentable = $('#articleCommentable').prop('checked');
      } else {
        requestJSONObject.articleCommentable = true;
      }

      if ($('#articleNotifyFollowers').length > 0) {
        requestJSONObject.articleNotifyFollowers = $('#articleNotifyFollowers').prop('checked');
      }

      if ($('#articleShowInList').length > 0) {
        requestJSONObject.articleShowInList = Boolean($('#articleShowInList').prop('checked')) ? 1 : 0;
      }

      if ($('#longArticleColumnId').length > 0) {
        var longArticleColumnId = $('#longArticleColumnId').val()
        var longArticleColumnTitle = ''
        if (longArticleColumnId === '__NEW__') {
          longArticleColumnTitle = $('#longArticleColumnTitle').val().replace(/(^\s*)|(\s*$)/g, '')
          if (longArticleColumnTitle === '') {
            $('#addArticleTip').addClass('error').html('<ul><li>请输入新专栏名称</li></ul>')
            return false
          }
        }
        requestJSONObject.columnId = longArticleColumnId
        requestJSONObject.columnTitle = longArticleColumnTitle
        requestJSONObject.columnCoverURL = $('#longArticleColumnCoverURL').val() || ''

        var chapterNo = ''
        if (longArticleColumnId !== '' && $('#longArticleChapterNo').length > 0) {
          chapterNo = $('#longArticleChapterNo').val().replace(/(^\s*)|(\s*$)/g, '')
        }
        if (chapterNo !== '' && !/^[1-9][0-9]*$/.test(chapterNo)) {
          $('#addArticleTip').addClass('error').html('<ul><li>章节号必须是正整数</li></ul>')
          return false
        }
        requestJSONObject.chapterNo = chapterNo
      }

      if ($('#articleStatement').length > 0) {
        requestJSONObject.articleStatement = $('#articleStatement').val();
      }

      if (undefined !== isGoodArticle && isGoodArticle === true) {
        requestJSONObject.isGoodArticle = 'yes';
      }

      if (AddArticle.currentDraftId !== '') {
        requestJSONObject.articleDraftId = AddArticle.currentDraftId
      }

      if (finalArticleType !== 5) {
        if ($('#articleRewardContent').length > 0) {
          requestJSONObject.articleRewardContent = this.rewardEditor.getValue()
        }
        if ($('#articleRewardPoint').length > 0) {
          requestJSONObject.articleRewardPoint = $('#articleRewardPoint').
          val().
          replace(/(^\s*)|(\s*$)/g, '')
        }
        if ($('#articleAnonymous').length > 0) {
          requestJSONObject.articleAnonymous = $('#articleAnonymous').
          prop('checked')
        }
      } else {
        requestJSONObject.articleQnAOfferPoint = $('#articleAskPoint').
        val().
        replace(/(^\s*)|(\s*$)/g, '')
      }

      var url = Label.servePath + '/article', type = 'POST'

      if (3 === parseInt(requestJSONObject.articleType)) {
        requestJSONObject.articleContent = JSON.parse(
            window.localStorage.postData).thoughtContent
      }

      if (Label.articleOId) {
        url = url + '/' + Label.articleOId
        type = 'PUT'
      }

      $.ajax({
        url: url,
        type: type,
        headers: {'csrfToken': csrfToken},
        cache: false,
        data: JSON.stringify(requestJSONObject),
        beforeSend: function () {
          $(it).attr('disabled', 'disabled').css('opacity', '0.3')
        },
        error: function (jqXHR, textStatus, errorThrown) {
          $('#addArticleTip').
          addClass('error').
          html('<ul><li>' + errorThrown + '</li></ul>')
        },
        success: function (result, textStatus) {
          $(it).removeAttr('disabled').css('opacity', '1')
          if (0 === result.code) {
            if (result.draftRemoveMsg) {
              alert(result.draftRemoveMsg)
            }
            window.location.href = Label.servePath + '/article/' +
                result.articleId
            localStorage.removeItem('postData')
            AddArticle.editor.clearCache()
            if (AddArticle.rewardEditor) {
              AddArticle.rewardEditor.clearCache()
            }
          } else {
            $('#addArticleTip').
            addClass('error').
            html('<ul><li>' + result.msg + '</li></ul>')
          }
        },
        complete: function () {
          $(it).removeAttr('disabled').css('opacity', '1')
        },
      })
    }
  },
  /**
   * @description 初始化发文
   */
  init: function () {
    $.ua.set(navigator.userAgent)

    // local data
    if (location.search.indexOf('?id=') > -1) {
      localStorage.removeItem('postData')
    }

    var postData = undefined
    if (!localStorage.postData) {
      postData = {
        title: '',
        content: '',
        tags: '',
        thoughtContent: '',
        rewardContent: '',
        rewardPoint: '',
        thoughtTime: (new Date()).getTime(),
      }
      localStorage.postData = JSON.stringify(postData)
    } else {
      postData = JSON.parse(localStorage.postData)
    }

    // init content editor
    if ('' !== postData.content) {
      $('#articleContent').val(postData.content)
    }

    var prevValue = postData.content
    // 初始化文章编辑器
    AddArticle.editor = Util.newVditor({
      outline: { enable: true, position: "left" },
      typewriterMode: true,
      id: 'articleContent',
      cache: Label.articleOId ? false : true,
      preview: {
        mode: 'both',
      },
      resize: {
        enable: true,
        position: 'bottom'
      },
      after: function () {
        if ($('#articleContent').next().val() !== '') {
          AddArticle.editor.setValue($('#articleContent').next().val())
        }
      },
      height: 500,
      placeholder: $('#articleContent').data('placeholder'),
      input: function () {
        if (Label.articleType === 3) {
          var postData = JSON.parse(localStorage.postData)
          prevValue = localStorage.getItem('vditorarticleContent') || ''
          AddArticle.recordThought(AddArticle.editor.getValue(), prevValue,
            postData)
          localStorage.postData = JSON.stringify(postData)
        }
      },
    })

    // 私信 at 默认值
    var atIdx = location.href.indexOf('at=')
    if (-1 !== atIdx) {
      if ('' == postData.content) {
        var at = AddArticle.editor.getValue()
        AddArticle.editor.setValue('\n\n\n' + at)
      }

      if ('' == postData.title) {
        var username = Util.getParameterByName('at')
        $('#articleTitle').val('Hi, ' + username)
      }
      if ('' !== postData.tags) {
        var tagTitles = Label.discussionLabel
        var tags = Util.getParameterByName('tags')
        if ('' !== tags) {
          tagTitles += ',' + tags
        }
        $('#articleTags').val(tagTitles)
      }
    }

    // set url title
    if ('' == postData.title) {
      var title = Util.getParameterByName('title')
      if (title && title.length > 0) {
        $('#articleTitle').val(title)
      }
    }

    // set localStorage
    if ('' !== postData.title) {
      $('#articleTitle').val(postData.title)
    }
    $('#articleTitle').keyup(function () {
      var postData = JSON.parse(localStorage.postData)
      postData.title = $(this).val()
      localStorage.postData = JSON.stringify(postData)
    })

    if ('' !== postData.tags) {
      $('#articleTags').val(postData.tags)
    }

    this._initTag()
    this._initLongArticleColumn()

    // focus
    if ($('#articleTitle').val().length <= 0) {
      $('#articleTitle').focus()
    }

    // check title is repeat
    $('#articleTitle').blur(function () {
      if ($.trim($(this).val()) === '') {
        return false
      }

      if (1 === Label.articleType) { // 机要不检查
        return
      }

      $.ajax({
        url: Label.servePath + '/article/check-title',
        type: 'POST',
        data: JSON.stringify({
          'articleTitle': $.trim($(this).val()),
          'articleId': Label.articleOId, // 更新时才有值
        }),
        success: function (result, textStatus) {
          if (0 !== result.code) {
            if ($('#articleTitleTip').length === 1) {
              $('#articleTitleTip').html(result.msg)
            } else {
              $('#articleTitle').
                after('<div class="module" id="articleTitleTip">' +
                  result.msg +
                  '</div>')
            }

          } else {
            $('#articleTitleTip').remove()
          }
        },
      })
    })

    // 快捷发文
    $('#articleTags, #articleRewardPoint, #articleAskPoint').
      keypress(function (event) {
        if (event.ctrlKey && 10 === event.charCode) {
          AddArticle.add()
          return false
        }
      })

    if ($('#articleAskPoint').length === 0 && $('#articleRewardContent').length > 0) {
      // 初始化打赏区编辑器
      if (0 < $('#articleRewardPoint').val().replace(/(^\s*)|(\s*$)/g, '')) {
        $('#showReward').click()
      }

      AddArticle.rewardEditor = Util.newVditor({
        id: 'articleRewardContent',
        cache: Label.articleOId ? false : true,
        preview: {
          mode: 'editor',
        },
        resize: {
          enable: false,
        },
        height: 200,
        placeholder: $('#articleRewardContent').data('placeholder'),
        after: function () {
          if ($('#articleRewardContent').next().val() !== '') {
            $('#showReward').click()
            AddArticle.rewardEditor.setValue(
              $('#articleRewardContent').next().val())
          }
        },
      })
    }

    if ($('#articleAskPoint').length === 0 && $('#articleRewardPoint').length > 0) {
      if ('' !== postData.rewardContent) {
        $('#showReward').click()
        AddArticle.rewardEditor.setValue(postData.rewardContent)
      }

      if ('' !== postData.rewardPoint) {
        $('#showReward').click()
        $('#articleRewardPoint').val(postData.rewardPoint)
      }
      $('#articleRewardPoint').keyup(function () {
        var postData = JSON.parse(localStorage.postData)
        postData.rewardPoint = $(this).val()
        localStorage.postData = JSON.stringify(postData)
      })
    } else if ($('#articleAskPoint').length > 0) {
      $('#articleAskPoint').keyup(function () {
        var postData = JSON.parse(localStorage.postData)
        postData.QnAOfferPoint = $(this).val()
        localStorage.postData = JSON.stringify(postData)
      })
      if ('' !== postData.QnAOfferPoint && $('#articleAskPoint').val() === '') {
        $('#articleAskPoint').val(postData.QnAOfferPoint)
      }
    }
  },
  /**
   * @description 初始化长文专栏信息
   */
  _initLongArticleColumn: function () {
    if ($('#longArticleColumnId').length === 0) {
      return
    }

    $('#longArticleColumnId').change(function () {
      AddArticle.toggleLongColumnCreate()
    })

    AddArticle.toggleLongColumnCreate()
  },
  /**
   * @description 切换新建专栏输入框
   */
  toggleLongColumnCreate: function () {
    if ($('#longArticleColumnId').length === 0) {
      return
    }

    var selectedColumnId = $('#longArticleColumnId').val()
    var isCreateColumn = selectedColumnId === '__NEW__'
    var isColumnEnabled = selectedColumnId !== ''

    if ($('#longArticleColumnTitleWrap').length > 0) {
      if (isCreateColumn) {
        $('#longArticleColumnTitleWrap').show()
        $('#longArticleColumnTitle').focus()
      } else {
        $('#longArticleColumnTitleWrap').hide()
      }
    }

    if ($('#longArticleChapterWrap').length > 0 && $('#longArticleChapterNo').length > 0) {
      if (isColumnEnabled) {
        $('#longArticleChapterWrap').show()
        $('#longArticleChapterNo').prop('disabled', false)
      } else {
        $('#longArticleChapterWrap').hide()
        $('#longArticleChapterNo').prop('disabled', true).val('')
      }
    }

    AddArticle.syncLongColumnCoverValue(selectedColumnId)

    if ($('#longArticleColumnManage').length > 0) {
      if (isColumnEnabled) {
        $('#longArticleColumnManage')
          .attr('href', 'javascript:void(0)')
          .text('封面管理')
          .show()
      } else {
        $('#longArticleColumnManage').hide()
      }
    }
  },
  syncLongColumnCoverValue: function (selectedColumnId) {
    if ($('#longArticleColumnCoverURL').length === 0) {
      return
    }
    if (selectedColumnId === '') {
      $('#longArticleColumnCoverURL').val('')
      return
    }
    if (selectedColumnId === '__NEW__') {
      if (window.LongArticleCoverDialog) {
        $('#longArticleColumnCoverURL').val(LongArticleCoverDialog.getPendingCoverFromPostData())
      } else {
        $('#longArticleColumnCoverURL').val('')
      }
      return
    }
    $('#longArticleColumnCoverURL').val(AddArticle.getLongColumnCoverURL(selectedColumnId))
  },
  getLongColumnCoverURL: function (selectedColumnId) {
    var coverURL = ''
    $('#longArticleColumnCoverData span').each(function () {
      var $item = $(this)
      if (String($item.attr('data-column-id')) !== String(selectedColumnId)) {
        return
      }
      if ($item.attr('data-has-cover') === 'true') {
        coverURL = $item.attr('data-cover-url') || ''
      }
    })
    return coverURL
  },
  /**
   * @description 初始化标签编辑器
   * @returns {undefined}
   */
  _initTag: function () {
    if ($('#articleTags').length === 0 || $('.tags-input').length === 0) {
      return
    }

    $.ua.set(navigator.userAgent)

    // 添加 tag 到输入框
    var addTag = function (text) {
      if (text.replace(/\s/g, '') === '') {
        return false
      }
      var hasTag = false
      text = text.replace(/\s/g, '').replace(/,/g, '')
      $('#articleTags').val('')

      // 重复添加处理
      $('.tags-input .text').each(function () {
        var $it = $(this)
        if (text === $it.text()) {
          $it.parent().addClass('haved')
          setTimeout(function () {
            $it.parent().removeClass('haved')
          }, 900)
          hasTag = true
        }
      })

      if (hasTag) {
        return false
      }

      // 长度处理
      if ($('.tags-input .tag').length >= 4) {
        $('#articleTags').val('').data('val', '')
        return false
      }

      $('.post .tags-selected').append('<span class="tag"><span class="text">'
        + text + '</span><span class="close">x</span></span>')

      // set tags to localStorage
      if (location.search.indexOf('?id=') === -1) {
        var articleTags = ''
        $('.tags-input .tag .text').each(function () {
          articleTags += $(this).text() + ','
        })

        var postData = JSON.parse(localStorage.postData)
        postData.tags = articleTags
        localStorage.postData = JSON.stringify(postData)
      }

      if ($('.tags-input .tag').length >= 4) {
        $('#articleTags').val('').data('val', '')
      }
    }

    // domains 切换
    $('.domains-tags .btn').click(function () {
      $('.domains-tags .btn.current').removeClass('current green')
      $(this).addClass('current').addClass('green')
      $('.domains-tags .domain-tags').hide()
      $('#tags' + $(this).data('id')).show()
    })

    // tag 初始化渲染
    var initTags = $('#articleTags').val().split(',')
    for (var j = 0, jMax = initTags.length; j < jMax; j++) {
      addTag(initTags[j])
    }

    // 领域 tag 选择
    $('.domain-tags .tag').click(function () {
      addTag($(this).text())
    })

    // 移除 tag
    $('.tags-input').on('click', '.tag > span.close', function () {
      $(this).parent().remove()

      // set tags to localStorage
      if (location.search.indexOf('?id=') === -1) {
        var articleTags = ''
        $('.tags-input .tag .text').each(function () {
          articleTags += $(this).text() + ','
        })

        var postData = JSON.parse(localStorage.postData)
        postData.tags = articleTags
        localStorage.postData = JSON.stringify(postData)
      }
    })

    // 展现领域 tag 选择面板
    $('#articleTags').click(function () {
      $('.post .domains-tags').show()
      if ($.ua.device.type !== 'mobile') {
        $('.post .domains-tags').
          css('left', ($('.post .tags-selected').width() + 10) + 'px')
      }
      $('#articleTagsSelectedPanel').hide()
    }).blur(function () {
      if ($('#articleTagsSelectedPanel').css('display') === 'block') {
        // 鼠标点击 completed 面板时避免把输入框的值加入到 tag 中
        return false
      }
      addTag($(this).val())
    })

    // 关闭领域 tag 选择面板
    $('body').click(function (event) {
      if ($(event.target).closest('.tags-input').length === 1 ||
        $(event.target).closest('.domains-tags').length === 1) {
      } else {
        $('.post .domains-tags').hide()
      }
    })

    // 自动补全 tag
    $('#articleTags').completed({
      height: 170,
      onlySelect: true,
      data: [],
      afterSelected: function ($it) {
        addTag($it.text())
      },
      afterKeyup: function (event) {
        $('.post .domains-tags').hide()
        // 遇到分词符号自动添加标签
        if (event.key === ',' || event.key === '，' ||
          event.key === '、' || event.key === '；' || event.key === ';') {
          var text = $('#articleTags').val()
          addTag(text.substr(0, text.length - 1))
          return false
        }

        // 回车，自动添加标签
        if (event.keyCode === 13) {
          addTag($('#articleTags').val())
          return false
        }

        // 上下左右
        if (event.keyCode === 37 || event.keyCode === 39 ||
          event.keyCode === 38 || event.keyCode === 40) {
          return false
        }

        // ECS 隐藏面板
        if (event.keyCode === 27) {
          $('#articleTagsSelectedPanel').hide()
          return false
        }

        // 删除 tag
        if (event.keyCode === 8 && event.data.settings.chinese === 8
          && event.data.settings.keydownVal.replace(/\s/g, '') === '') {
          $('.tags-input .tag .close:last').click()
          return false
        }

        if ($('#articleTags').val().replace(/\s/g, '') === '') {
          return false
        }

        $.ajax({
          url: Label.servePath + '/tags/query?title=' + $('#articleTags').val(),
          error: function (jqXHR, textStatus, errorThrown) {
            $('#addArticleTip').
              addClass('error').
              html('<ul><li>' + errorThrown + '</li></ul>')
          },
          success: function (result, textStatus) {
            if (0 === result.code) {
              if ($.ua.device.type !== 'mobile') {
                $('#articleTagsSelectedPanel').
                  css('left', ($('.post .tags-selected').width() + 10) + 'px')
              }
              $('#articleTags').completed('updateData', result.tags)
            } else {
              console.log(result)
            }
          },
        })
      },
    })
  },
}

AddArticle.init()
