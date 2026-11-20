/* 旧版聊天室在昵称旁按需显示游园状态，沿用旧站登录会话。 */
;(function () {
    const chats = document.querySelector('#chats[data-pet-home-url]')
    const idPattern = /^[1-9][0-9]{0,18}$/
    const viewer = String(window.Label?.currentUserId || '')
    if (!chats || !idPattern.test(viewer) || !chats.dataset.petHomeUrl) return
    const home = new URL(chats.dataset.petHomeUrl, location.href)
    if (!['http:', 'https:'].includes(home.protocol)) return
    home.pathname = home.pathname.replace(/\/$/, '')
    home.search = home.hash = ''

    const selector = '.chats__content > [id="userName"] > .chat-game-anchor'
    const anchors = new Map()
    const visible = new Set()
    const cache = new Map()
    let panel, opener, timer, reading = false, nextRun = Infinity, retryAfter = 0

    function element(tag, text, className) {
        const node = document.createElement(tag)
        if (text) node.textContent = text
        if (className) node.className = className
        return node
    }

    // 和新版使用同样的手柄、爱心与公文包轮廓。
    function icon(path) {
        const ns = 'http://www.w3.org/2000/svg'
        const svg = document.createElementNS(ns, 'svg')
        svg.setAttribute('viewBox', '0 0 24 24')
        svg.setAttribute('aria-hidden', 'true')
        const shape = document.createElementNS(ns, 'path')
        shape.setAttribute('d', path)
        svg.append(shape)
        return svg
    }

    function close(restoreFocus) {
        if (!panel) return
        panel.remove()
        panel = null
        opener.setAttribute('aria-expanded', 'false')
        opener.removeAttribute('aria-controls')
        if (restoreFocus && opener.isConnected) opener.focus({preventScroll: true})
        opener = null
    }

    function gameLink(page, key, author) {
        const url = new URL(page, home)
        if (author !== viewer) url.searchParams.set(key, author)
        return url.href
    }

    function formatValue(value) {
        const places = value.length > 8 ? 8 : 4
        if (value.length <= places) return value
        const fraction = value.slice(-places).replace(/0+$/, '')
        return value.slice(0, -places) + (fraction ? '.' + fraction : '') + (places === 8 ? ' 亿' : ' 万')
    }

    function addRow(label, tile, kind, value, href, extra) {
        const link = element('a')
        link.href = href
        const badge = element('span', '', 'chat-game-tile ' + kind)
        badge.setAttribute('aria-hidden', 'true')
        badge.append(tile)
        const detail = element('span', value, 'chat-game-value')
        if (extra) detail.append(element('small', extra))
        link.append(badge, element('span', label), detail)
        panel.append(link)
    }

    function toggle(anchor, button) {
        const same = opener === button
        close(false)
        const author = anchor.dataset.gameUser
        const entry = cache.get(author)
        if (same || !entry?.status || entry.until <= Date.now() || window.Label?.currentUserId !== viewer) {
            schedule()
            return
        }
        opener = button
        panel = element('section', '', 'chat-game-panel')
        panel.id = 'chat-game-status-panel'
        panel.setAttribute('role', 'dialog')
        panel.setAttribute('aria-label', button.getAttribute('aria-label'))
        const heading = element('div', '', 'chat-game-heading')
        const dismiss = element('button', '×')
        dismiss.type = 'button'
        dismiss.setAttribute('aria-label', '收起游园状态')
        dismiss.onclick = () => close(true)
        heading.append(element('strong', '游园状态'), dismiss)
        panel.append(heading)

        const pet = anchor.parentElement.querySelector('a.fp-chat-pet-status')
        if (pet) {
            const parts = pet.querySelectorAll('.fp-chat-pet-name,.fp-chat-pet-state,.fp-chat-pet-level')
            addRow('宠物家园', icon('M12 20s-9-5-9-11a5 5 0 0 1 9-3 5 5 0 0 1 9 3c0 6-9 11-9 11Z'), 'pet', Array.from(parts, part => part.textContent).join(' · '), pet.href)
        }
        const status = entry.status
        if (typeof status.parkingFree === 'boolean') {
            addRow('抢车位', 'P', 'parking', status.parkingFree ? '车位空闲' : '车位已占用', gameLink('parking', 'host', author))
        }
        if (status.partner) {
            const partner = status.partner
            addRow('抢搭子', icon('M8 7V4h8v3M3 7h18v13H3V7Zm0 5a22 22 0 0 0 18 0M10 12h4v3h-4v-3Z'), 'partner', partner.full ? '已满价' : '名片已开放', gameLink('partners', 'peer', author),
                partner.displayValue ? '身价 ' + formatValue(partner.displayValue) : '')
        }

        const rect = button.getBoundingClientRect()
        const gutter = 8
        const width = Math.min(276, innerWidth - gutter * 2)
        const below = innerHeight - rect.bottom
        const above = below < 210 && rect.top > below
        panel.style.left = Math.max(gutter, Math.min(rect.left, innerWidth - width - gutter)) + 'px'
        panel.style[above ? 'bottom' : 'top'] = (above ? innerHeight - rect.top : rect.bottom) + 4 + 'px'
        panel.style.maxHeight = Math.max(0, (above ? rect.top : below) - gutter - 4) + 'px'
        document.body.append(panel)
        button.setAttribute('aria-expanded', 'true')
        button.setAttribute('aria-controls', panel.id)
        dismiss.focus({preventScroll: true})
    }

    function render(anchor) {
        const status = cache.get(anchor.dataset.gameUser)?.status
        const button = anchor.firstElementChild
        if (!status) {
            if (button && button === opener) close(false)
            if (button) button.remove()
            return
        }
        if (button) return
        const trigger = element('button', '游园', 'chat-game-trigger')
        trigger.prepend(icon('M8 8h8c3 0 4 2 5 8 1 4-3 4-5 0H8c-2 4-6 4-5 0 1-6 2-8 5-8ZM7 11v4m-2-2h4m7-1h.01m2 2h.01'))
        trigger.type = 'button'
        const name = anchor.parentElement.querySelector('.ft-gray')?.textContent.trim() || ''
        trigger.setAttribute('aria-label', '查看' + name + '的游园状态')
        trigger.setAttribute('aria-haspopup', 'dialog')
        trigger.setAttribute('aria-expanded', 'false')
        trigger.onclick = event => {
            event.stopPropagation()
            toggle(anchor, trigger)
        }
        anchor.append(trigger)
    }

    function decode(result, ids) {
        const list = result?.data?.list
        if (result?.code !== 0 || !Array.isArray(list) || list.length > ids.size) throw Error()
        const statuses = new Map()
        for (const row of list) {
            if (!row || !ids.has(row.userId) || statuses.has(row.userId) ||
                (row.parkingFree !== undefined && typeof row.parkingFree !== 'boolean') ||
                (row.partner !== undefined && (typeof row.partner?.full !== 'boolean' ||
                    (row.partner.displayValue !== undefined && (typeof row.partner.displayValue !== 'string' ||
                        !/^[1-9][0-9]{0,41}$/.test(row.partner.displayValue)))))) throw Error()
            statuses.set(row.userId, row.parkingFree !== undefined || row.partner ? row : null)
        }
        return statuses
    }

    // 只查询视口附近的作者，同一作者的多条消息及未开通游戏的结果均复用一分钟。
    function schedule() {
        if (document.hidden || reading || !visible.size || String(window.Label?.currentUserId || '') !== viewer) return
        let due = Infinity
        for (const anchor of visible) due = Math.min(due, cache.get(anchor.dataset.gameUser)?.until ?? 0)
        due = Math.max(Date.now() + 40, due, retryAfter)
        if (nextRun <= due) return
        clearTimeout(timer)
        nextRun = due
        timer = setTimeout(refresh, due - Date.now())
    }

    async function refresh() {
        nextRun = Infinity
        if (document.hidden || String(window.Label?.currentUserId || '') !== viewer) return
        const now = Date.now()
        for (const [id, entry] of cache) if (entry.until <= now) cache.delete(id)
        const ids = new Set()
        for (const anchor of visible) {
            if (!cache.has(anchor.dataset.gameUser)) ids.add(anchor.dataset.gameUser)
            if (ids.size >= 50) break
        }
        if (!ids.size) return schedule()
        reading = true
        const controller = new AbortController()
        const timeout = setTimeout(() => controller.abort(), 15000)
        try {
            const response = await fetch('/chat-room/game-status?' + new URLSearchParams({userIds: [...ids].join(',')}), {
                credentials: 'same-origin', mode: 'same-origin', cache: 'no-store', redirect: 'error', signal: controller.signal,
            })
            if (!response.ok) {
                if ([401, 403, 404].includes(response.status)) cache.clear()
                if (response.status === 404) retryAfter = Date.now() + 300000
                throw Error()
            }
            const statuses = decode(await response.json(), ids)
            if (String(window.Label?.currentUserId || '') === viewer) {
                for (const id of ids) cache.set(id, {status: statuses.get(id), until: Date.now() + 60000})
            }
        } catch {
            retryAfter = Math.max(retryAfter, Date.now() + 30000)
        } finally {
            clearTimeout(timeout)
            reading = false
            // 浮层是打开时的快照；状态刷新时收起旧内容，避免展示过期链接。
            if (opener && (ids.has(opener.parentElement?.dataset.gameUser) || !cache.has(opener.parentElement?.dataset.gameUser))) close(false)
            for (const anchor of anchors.values()) render(anchor)
            schedule()
        }
    }

    const observer = window.IntersectionObserver && new IntersectionObserver(entries => {
        for (const entry of entries) {
            const anchor = anchors.get(entry.target)
            if (!anchor) continue
            if (entry.isIntersecting) visible.add(anchor)
            else visible.delete(anchor)
        }
        schedule()
    }, {rootMargin: '100px'})

    function register(anchor) {
        const line = anchor.parentElement
        if (!line || anchors.has(line) || !idPattern.test(anchor.dataset.gameUser)) return
        anchors.set(line, anchor)
        // 空锚点还没有尺寸，观察所在的昵称行才能正确判断是否可见。
        if (observer) observer.observe(line)
        else visible.add(anchor)
        render(anchor)
    }
    chats.querySelectorAll(selector).forEach(register)
    new MutationObserver(changes => {
        let removed = false
        for (const change of changes) {
            if (change.target.closest?.('.chat-game-anchor')) continue
            if (change.removedNodes.length) removed = true
            for (const node of change.addedNodes) {
                if (node.nodeType !== 1) continue
                if (node.matches(selector)) register(node)
                node.querySelectorAll(selector).forEach(register)
            }
        }
        if (removed) {
            for (const [line, anchor] of anchors) {
                if (chats.contains(anchor)) continue
                observer?.unobserve(line)
                anchors.delete(line)
                visible.delete(anchor)
            }
            if (opener && !opener.isConnected) close(false)
        }
        schedule()
    }).observe(chats, {childList: true, subtree: true})
    function dismissOutside(event) {
        if (panel && !panel.contains(event.target) && !opener.contains(event.target)) close(false)
    }
    document.addEventListener('pointerdown', dismissOutside, true)
    document.addEventListener('focusin', dismissOutside)
    document.addEventListener('keydown', event => {
        if (panel && event.key === 'Escape') {
            event.preventDefault()
            event.stopPropagation()
            close(true)
        }
    }, true)
    window.addEventListener('scroll', event => {
        if (panel && !(event.target instanceof Node && panel.contains(event.target))) close(false)
    }, true)
    window.addEventListener('resize', () => close(false))
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) close(false)
        else schedule()
    })
    schedule()
})()
