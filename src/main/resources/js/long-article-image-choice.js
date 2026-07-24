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
/** 长文正文图片操作菜单，供 PC 与移动端共用。 */
window.LongArticleImageActions = {
  edgeGap: 12,
  touchMoveThreshold: 12,
  touchClickGuard: 700,
  activeImage: null,
  activeParagraph: null,
  touchStart: null,
  touchHandledAt: 0,
  initialized: false,

  init: function () {
    if (this.initialized) {
      return
    }
    this.initialized = true
    this.ensureMenu()
    this.decorateImages()
    this.bindEvents()
  },

  ensureMenu: function () {
    var menu = document.createElement('div')
    menu.className = 'long-article-image-actions'
    menu.id = 'longArticleImageActions'
    menu.setAttribute('role', 'group')
    menu.setAttribute('aria-label', '图片操作')
    menu.setAttribute('aria-hidden', 'true')
    menu.innerHTML = '<button type="button" data-image-action="comment">段评</button>' +
      '<button type="button" data-image-action="preview">放大</button>'
    document.body.appendChild(menu)
    this.menu = menu
  },

  decorateImages: function () {
    var self = this
    document.querySelectorAll('.long-article-content img').forEach(function (image) {
      if (!self.getImage(image)) {
        return
      }
      if (!image.hasAttribute('tabindex')) {
        image.setAttribute('tabindex', '0')
      }
      image.setAttribute('aria-haspopup', 'true')
      image.setAttribute('aria-controls', self.menu.id)
      image.setAttribute('aria-expanded', 'false')
    })
  },

  bindEvents: function () {
    var self = this
    document.addEventListener('click', function (event) {
      self.handleClick(event)
    }, true)
    document.addEventListener('dblclick', function (event) {
      self.handleImageEvent(event)
    }, true)
    document.addEventListener('touchstart', function (event) {
      self.handleTouchStart(event)
    }, true)
    document.addEventListener('touchend', function (event) {
      self.handleTouchEnd(event)
    }, true)
    document.addEventListener('touchcancel', function () {
      self.touchStart = null
    }, true)
    document.addEventListener('keydown', function (event) {
      self.handleKeydown(event)
    })
    window.addEventListener('resize', function () { self.close() })
  },

  getImage: function (target) {
    var image = target && target.closest
      ? target.closest('.long-article-content img')
      : null
    if (!image || image.classList.contains('emoji') || image.closest('.ad')) {
      return null
    }
    return image.closest('[data-long-paragraph-id]') ? image : null
  },

  handleClick: function (event) {
    var action = event.target.closest && event.target.closest('[data-image-action]')
    if (action && this.menu.contains(action)) {
      this.stopEvent(event)
      this.runAction(action.getAttribute('data-image-action'))
      return
    }
    var image = this.getImage(event.target)
    if (!image) {
      this.close()
      return
    }
    this.stopEvent(event)
    if (Date.now() - this.touchHandledAt >= this.touchClickGuard) {
      this.open(image)
    }
  },

  handleImageEvent: function (event) {
    if (!this.getImage(event.target)) {
      return
    }
    this.stopEvent(event)
  },

  handleTouchStart: function (event) {
    var image = this.getImage(event.target)
    if (!image || !event.changedTouches.length) {
      this.touchStart = null
      return
    }
    var touch = event.changedTouches[0]
    this.touchStart = {image: image, x: touch.clientX, y: touch.clientY}
  },

  handleTouchEnd: function (event) {
    if (!this.touchStart || !event.changedTouches.length) {
      return
    }
    var touch = event.changedTouches[0]
    var distance = Math.hypot(
      touch.clientX - this.touchStart.x,
      touch.clientY - this.touchStart.y)
    var image = this.touchStart.image
    this.touchStart = null
    if (distance > this.touchMoveThreshold) {
      return
    }
    this.stopEvent(event)
    this.touchHandledAt = Date.now()
    this.open(image)
  },

  handleKeydown: function (event) {
    if (event.key === 'Escape' && this.menu.classList.contains('is-open')) {
      event.preventDefault()
      this.close()
      this.activeImage.focus()
      return
    }
    var image = this.getImage(event.target)
    if (image && (event.key === 'Enter' || event.key === ' ')) {
      this.stopEvent(event)
      this.open(image)
    }
  },

  open: function (image) {
    this.activeImage = image
    this.activeParagraph = image.closest('[data-long-paragraph-id]')
    image.setAttribute('aria-expanded', 'true')
    this.menu.classList.add('is-open')
    this.menu.setAttribute('aria-hidden', 'false')
    this.position(image)
    this.menu.querySelector('button').focus()
  },

  position: function (image) {
    var rect = image.getBoundingClientRect()
    var menuRect = this.menu.getBoundingClientRect()
    var left = rect.left + (rect.width - menuRect.width) / 2
    var top = rect.bottom + this.edgeGap
    left = Math.max(this.edgeGap, Math.min(left,
      window.innerWidth - menuRect.width - this.edgeGap))
    if (top + menuRect.height + this.edgeGap > window.innerHeight) {
      top = rect.top - menuRect.height - this.edgeGap
    }
    this.menu.style.left = left + 'px'
    this.menu.style.top = Math.max(this.edgeGap, top) + 'px'
  },

  runAction: function (action) {
    var image = this.activeImage
    var paragraph = this.activeParagraph
    this.close()
    if (action === 'comment') {
      LongArticleParagraphComments.open(
        paragraph.getAttribute('data-long-paragraph-id'))
      return
    }
    if (action === 'preview') {
      LongArticleParagraphComments.close()
      this.preview(image)
    }
  },

  preview: function (image) {
    var rect = image.getBoundingClientRect()
    var previewSrc = (image.getAttribute('src') || '').split('?imageView2')[0]
    var $preview = $('<div class="img-preview"></div>').on('click', function () {
      $(this).remove()
    })
    var $previewImage = $('<img>').css('transform', 'translate3d(' +
      Math.max(0, rect.left) + 'px, ' +
      Math.max(0, rect.top) + 'px, 0)').
      attr('src', previewSrc).
      on('load', Article.previewImgAfterLoading)
    $preview.append($previewImage).css({
      'background-color': '#fff',
      'position': 'fixed',
    })
    $('body').append($preview)
  },

  close: function () {
    if (!this.menu) {
      return
    }
    this.menu.classList.remove('is-open')
    this.menu.setAttribute('aria-hidden', 'true')
    if (this.activeImage) {
      this.activeImage.setAttribute('aria-expanded', 'false')
    }
  },

  stopEvent: function (event) {
    event.preventDefault()
    event.stopPropagation()
    event.stopImmediatePropagation()
  },
}
