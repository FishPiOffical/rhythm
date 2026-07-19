/** Long article paragraph comment interactions shared by PC and mobile. */
window.LongArticleParagraphComments = {
  activeParagraphId: '',
  activeSnapshot: '',
  activeParagraphOrphaned: false,
  currentPage: 1,
  currentSort: '',
  counts: {},
  initialized: false,

  init: function () {
    if (this.initialized) {
      return
    }
    this.controller = window.LongArticle || window.MLongArticle
    this.commentsPanel = this.controller && this.controller.getCommentsPanel()
    if (!this.commentsPanel) {
      return
    }
    this.initialized = true
    this.ensurePanel()
    this.decorateParagraphs()
    this.bindEvents()
    this.loadSummary()
    var paragraphId = this.getQueryValue('paragraph')
    if (paragraphId) {
      this.open(paragraphId)
    }
  },

  ensurePanel: function () {
    if (document.getElementById('longArticleParagraphComments')) {
      this.paragraphPanel = document.getElementById('longArticleParagraphComments')
      return
    }
    var section = document.createElement('section')
    section.id = 'longArticleParagraphComments'
    section.className = 'long-article-paragraph-comments'
    section.setAttribute('aria-hidden', 'true')
    section.innerHTML = '<header class="long-article-paragraph-comments__header">' +
      '<div><strong>段评</strong><span data-paragraph-comment-count></span></div>' +
      '<button type="button" data-paragraph-comments-close aria-label="关闭评论">&times;</button>' +
      '<p data-paragraph-comment-snapshot></p>' +
      '<div class="long-article-paragraph-comments__sort">' +
      '<button type="button" data-paragraph-sort="" class="is-active">正序</button>' +
      '<button type="button" data-paragraph-sort="hot">热门</button></div></header>' +
      '<div class="long-article-paragraph-comments__reply commentToggleEditorBtn">写段评</div>' +
      '<div class="list comments"><ul data-paragraph-comment-list></ul></div>' +
      '<div class="long-article-paragraph-comments__empty" data-paragraph-comment-empty>暂无段评</div>' +
      '<div class="long-article-paragraph-comments__pager" data-paragraph-comment-pager></div>'
    this.commentsPanel.appendChild(section)
    this.paragraphPanel = section
  },

  decorateParagraphs: function () {
    var self = this
    document.querySelectorAll('[data-long-paragraph-id]').forEach(function (paragraph) {
      if (self.getDirectCommentButton(paragraph)) {
        return
      }
      paragraph.classList.add('long-article-commentable-block')
      var button = document.createElement('button')
      button.type = 'button'
      button.className = 'long-article-paragraph-comment-btn'
      button.setAttribute('data-open-paragraph-comments', paragraph.getAttribute('data-long-paragraph-id'))
      button.setAttribute('aria-label', '查看段评')
      button.innerHTML = '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M20 2H4a2 2 0 0 0-2 2v18l4-4h14a2 2 0 0 0 2-2V4a2 2 0 0 0-2-2z"/></svg><span>0</span>'
      paragraph.appendChild(button)
      self.updateParagraphButton(paragraph.getAttribute('data-long-paragraph-id'))
    })
  },

  bindEvents: function () {
    var self = this
    document.addEventListener('click', function (event) {
      var openButton = event.target.closest && event.target.closest('[data-open-paragraph-comments]')
      if (openButton) {
        event.preventDefault()
        event.stopPropagation()
        self.open(openButton.getAttribute('data-open-paragraph-comments'))
        return
      }
      if (event.target.closest && event.target.closest('[data-paragraph-comments-close]')) {
        self.close()
        return
      }
      var sortButton = event.target.closest && event.target.closest('[data-paragraph-sort]')
      if (sortButton) {
        self.currentSort = sortButton.getAttribute('data-paragraph-sort')
        self.paragraphPanel.querySelectorAll('[data-paragraph-sort]').forEach(function (button) {
          button.classList.toggle('is-active', button === sortButton)
        })
        self.load(1)
        return
      }
      if (event.target.closest && event.target.closest('.long-article-paragraph-comments__reply')) {
        Comment._toggleReply()
      }
    })
  },

  loadSummary: function () {
    var self = this
    $.ajax({
      url: Label.servePath + '/comment/paragraph/summary',
      type: 'POST',
      cache: false,
      data: JSON.stringify({articleId: Label.articleOId}),
      success: function (result) {
        if (result.code !== 0) {
          return
        }
        self.counts = {}
        ;(result.paragraphComments || []).forEach(function (item) {
          self.counts[item.paragraphId] = parseInt(item.commentCount || 0)
          self.updateParagraphButton(item.paragraphId)
        })
      },
    })
  },

  updateParagraphButton: function (paragraphId) {
    var button = document.querySelector('[data-open-paragraph-comments="' + paragraphId + '"]')
    if (!button) {
      return
    }
    var count = parseInt(this.counts[paragraphId] || 0)
    button.classList.toggle('has-comments', count > 0)
    button.querySelector('span').textContent = count > 0 ? count : ''
  },

  open: function (paragraphId) {
    var paragraph = document.querySelector('[data-long-paragraph-id="' + paragraphId + '"]')
    if (!paragraph) {
      this.showChapterComments(true)
      this.openOrphanedCommentFromHash()
      return
    }
    this.showChapterComments(false)
    this.activeParagraphId = paragraphId
    this.activeParagraphOrphaned = false
    this.activeSnapshot = this.getSnapshot(paragraph)
    document.querySelectorAll('.long-article-commentable-block.is-commenting').forEach(function (element) {
      element.classList.remove('is-commenting')
    })
    paragraph.classList.add('is-commenting')
    this.paragraphPanel.querySelector('[data-paragraph-comment-snapshot]').textContent = this.activeSnapshot
    this.controller.openComments()
    this.updateURL(paragraphId)
    this.load(1)
  },

  close: function () {
    this.showChapterComments(true)
    this.controller.closeComments()
  },

  showChapterComments: function (show, updateURL) {
    if (show === undefined) {
      show = true
    }
    this.activeParagraphId = show ? '' : this.activeParagraphId
    this.activeParagraphOrphaned = show ? false : this.activeParagraphOrphaned
    this.commentsPanel.classList.toggle('is-paragraph-view', !show)
    this.paragraphPanel.classList.toggle('is-active', !show)
    this.paragraphPanel.setAttribute('aria-hidden', show ? 'true' : 'false')
    if (show) {
      document.querySelectorAll('.long-article-commentable-block.is-commenting').forEach(function (element) {
        element.classList.remove('is-commenting')
      })
      if (updateURL !== false) {
        this.updateURL('')
      }
    }
  },

  load: function (page) {
    var self = this
    this.currentPage = page || 1
    this.paragraphPanel.classList.add('is-loading')
    $.ajax({
      url: Label.servePath + '/comment/paragraph/thread/parents',
      type: 'POST',
      cache: false,
      data: JSON.stringify({
        articleId: Label.articleOId,
        paragraphId: this.activeParagraphId,
        paginationCurrentPageNum: this.currentPage,
        userCommentViewMode: Label.userCommentViewMode,
        sort: this.currentSort,
      }),
      success: function (result) {
        if (result.code !== 0) {
          self.renderError(result.msg || '加载失败')
          return
        }
        if (result.paragraphSnapshot) {
          self.activeSnapshot = result.paragraphSnapshot
          self.paragraphPanel.querySelector('[data-paragraph-comment-snapshot]').textContent = self.activeSnapshot
        }
        self.render(result.commentThreadParents || [], result.pagination || {})
      },
      error: function () {
        self.renderError('加载失败')
      },
      complete: function () {
        self.paragraphPanel.classList.remove('is-loading')
      },
    })
  },

  render: function (comments, pagination) {
    var list = this.paragraphPanel.querySelector('[data-paragraph-comment-list]')
    list.innerHTML = ''
    for (var i = 0; i < comments.length; i++) {
      list.insertAdjacentHTML('beforeend', this.renderRoot(comments[i]))
    }
    this.paragraphPanel.querySelector('[data-paragraph-comment-empty]').style.display = comments.length ? 'none' : 'block'
    this.paragraphPanel.querySelector('[data-paragraph-comment-count]').textContent = ' ' + parseInt(this.counts[this.activeParagraphId] || 0) + ' 条'
    this.renderPager(pagination)
    Comment.initReactionWidgets($(this.paragraphPanel))
    Util.parseMarkdown()
    Util.parseHljs()
    Util.listenUserCard()
    this.focusLocationComment()
  },

  renderRoot: function (data) {
    var replies = data.commentThreadReplies || []
    var thread = replies.length ? '<div class="comment-thread" data-root-id="' + Comment.escapeHTML(data.oId) + '"><div class="comment-thread__list">' + Comment.renderThreadReplies(replies) + '</div></div>' : ''
    return '<li id="' + Comment.escapeHTML(data.oId) + '" data-author="' + Comment.escapeHTML(data.commentAuthorName) +
      '" data-comment-type="1" data-comment-paragraph-id="' + Comment.escapeHTML(data.commentParagraphId || this.activeParagraphId) +
      '" data-comment-paragraph-status="' + parseInt(data.commentParagraphStatus || 0) +
      '" data-is-author="' + (data.commentIsArticleAuthor ? 'true' : 'false') + '" class="' + (replies.length ? 'cmt-selected' : '') + '">' +
      '<div class="fn-flex"><a rel="nofollow" href="' + Label.servePath + '/member/' + Comment.escapeHTML(data.commentAuthorName) + '">' +
      '<span class="avatar" style="background-image:url(\'' + Comment.escapeHTML(data.commentAuthorThumbnailURL) + '\')"></span></a>' +
      '<div class="fn-flex-1"><div class="comment-info"><a rel="nofollow" href="' + Label.servePath + '/member/' + Comment.escapeHTML(data.commentAuthorName) + '">' +
      Comment.renderCommentAuthorName(data) + '</a><span class="ft-fade"> · ' + Comment.escapeHTML(data.timeAgo) + '</span></div>' +
      '<div class="vditor-reset comment">' + data.commentContent + '</div>' + thread +
      '<div class="comment-action">' + Comment.renderThreadAction(data) + '</div></div></div></li>'
  },

  renderPager: function (pagination) {
    var self = this
    var pager = this.paragraphPanel.querySelector('[data-paragraph-comment-pager]')
    var page = parseInt(pagination.paginationCurrentPageNum || 1)
    var pages = parseInt(pagination.paginationPageCount || 1)
    pager.innerHTML = ''
    if (pages <= 1) {
      return
    }
    if (page > 1) {
      pager.appendChild(this.pagerButton('上一页', page - 1))
    }
    var info = document.createElement('span')
    info.textContent = page + '/' + pages
    pager.appendChild(info)
    if (page < pages) {
      pager.appendChild(this.pagerButton('下一页', page + 1))
    }
  },

  pagerButton: function (label, page) {
    var self = this
    var button = document.createElement('button')
    button.type = 'button'
    button.textContent = label
    button.addEventListener('click', function () { self.load(page) })
    return button
  },

  renderError: function (message) {
    this.paragraphPanel.querySelector('[data-paragraph-comment-list]').innerHTML = ''
    var empty = this.paragraphPanel.querySelector('[data-paragraph-comment-empty]')
    empty.textContent = message
    empty.style.display = 'block'
  },

  getSnapshot: function (paragraph) {
    var clone = paragraph.cloneNode(true)
    var button = this.getDirectCommentButton(clone)
    if (button) {
      button.parentNode.removeChild(button)
    }
    return String(clone.textContent || '').replace(/\s+/g, ' ').trim().substring(0, 160)
  },

  getDirectCommentButton: function (paragraph) {
    for (var i = 0; i < paragraph.children.length; i++) {
      if (paragraph.children[i].classList.contains('long-article-paragraph-comment-btn')) {
        return paragraph.children[i]
      }
    }
    return null
  },

  getActiveParagraphId: function () {
    return this.activeParagraphId
  },

  getSubmissionParagraphId: function (originalCommentId) {
    if (this.activeParagraphOrphaned && !originalCommentId) {
      return ''
    }
    return this.activeParagraphId
  },

  activateFromComment: function (element) {
    var root = element && element.closest ? element.closest('[data-comment-paragraph-id]') : null
    if (root && root.getAttribute('data-comment-paragraph-id')) {
      this.activeParagraphId = root.getAttribute('data-comment-paragraph-id')
      this.activeParagraphOrphaned = root.getAttribute('data-comment-paragraph-status') === '1'
    }
  },

  handleSubmitSuccess: function () {
    if (!this.activeParagraphId) {
      return
    }
    if (this.activeParagraphOrphaned) {
      window.setTimeout(function () { window.location.reload() }, 250)
      return
    }
    var self = this
    window.setTimeout(function () {
      self.loadSummary()
      self.load(self.currentPage)
    }, 250)
  },

  handleRemove: function (paragraphId) {
    if (!paragraphId) {
      return
    }
    this.counts[paragraphId] = Math.max(0, parseInt(this.counts[paragraphId] || 0) - 1)
    this.updateParagraphButton(paragraphId)
    if (this.activeParagraphId === paragraphId) {
      this.paragraphPanel.querySelector('[data-paragraph-comment-count]').textContent =
        ' ' + this.counts[paragraphId] + ' 条'
    }
  },

  handleRealtime: function (data) {
    var paragraphId = data.commentParagraphId || ''
    if (!paragraphId) {
      return
    }
    this.counts[paragraphId] = parseInt(this.counts[paragraphId] || 0) + 1
    this.updateParagraphButton(paragraphId)
    if (this.activeParagraphId === paragraphId) {
      var self = this
      window.setTimeout(function () { self.load(self.currentPage) }, 100)
    }
  },

  getQueryValue: function (key) {
    var match = window.location.search.match(new RegExp('(?:^|[?&])' + key + '=([^&]*)'))
    return match ? decodeURIComponent(match[1].replace(/\+/g, ' ')) : ''
  },

  updateURL: function (paragraphId) {
    var search = String(window.location.search || '').replace(/^\?/, '').split('&').filter(function (part) {
      return part && part.split('=')[0] !== 'paragraph'
    })
    if (paragraphId) {
      search.push('paragraph=' + encodeURIComponent(paragraphId))
    }
    window.history.replaceState({}, '', window.location.pathname + (search.length ? '?' + search.join('&') : '') + window.location.hash)
  },

  focusLocationComment: function () {
    var self = this
    window.setTimeout(function () {
      var id = window.location.hash.replace(/^#/, '')
      var target = id ? document.getElementById(id) : null
      if (target && self.paragraphPanel.contains(target)) {
        target.scrollIntoView({block: 'center'})
        Comment._bgFade($(target), {scroll: false})
      }
    }, 0)
  },

  openOrphanedCommentFromHash: function () {
    var id = window.location.hash.replace(/^#/, '')
    var target = id ? document.getElementById(id) : null
    if (!target) {
      return
    }
    var details = target.closest ? target.closest('.long-article-orphaned-comments') : null
    if (!details) {
      return
    }
    details.open = true
    this.controller.openComments()
    window.setTimeout(function () {
      target.scrollIntoView({block: 'center'})
      Comment._bgFade($(target), {scroll: false})
    }, 0)
  },
}
