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
(function () {
    function element(tag, className, text) {
        const node = document.createElement(tag);
        if (className) node.className = className;
        if (text != null) node.textContent = text;
        return node;
    }

    function action(label, className, handler) {
        const button = element('button', className, label);
        button.type = 'button';
        button.addEventListener('click', handler);
        return button;
    }

    function setNotice(node, text) {
        node.textContent = text;
        node.hidden = !text;
    }

    async function requestJSON(url, options) {
        const response = await fetch(url, options);
        if (!response.ok) throw new Error('请求失败（' + response.status + '）');
        const result = await response.json();
        if (!result || result.code !== 0) throw new Error(result?.msg || '请求失败');
        return result;
    }

    function postMedal(path, fields) {
        return requestJSON('/api/medal/admin/' + path, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json;charset=UTF-8' },
            body: JSON.stringify({ apiKey: window.apiKey ?? null, ...fields })
        });
    }

    function formatExpiry(timestamp) {
        if (Number(timestamp) === 0) return '永久';
        const date = new Date(Number(timestamp));
        return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('zh-CN', { hour12: false });
    }

    function badgeImage(id) {
        const image = element('img', 'medal-badge');
        image.alt = '';
        image.addEventListener('error', () => { image.hidden = true; });
        image.src = (window.Label?.servePath || '') + '/gen?id=' + encodeURIComponent(id);
        return image;
    }

    class MedalCatalog {
        constructor(root) {
            this.page = 1;
            this.pageSize = 20;
            this.keyword = '';
            this.rows = [];
            this.matches = [];
            this.hasMore = false;
            this.loading = false;
            this.generation = 0;
            this.root = root;
            this.list = root.querySelector('#medalRows');
            this.status = root.querySelector('#medalListStatus');
            this.detail = root.querySelector('#medalDetail');
            this.detail.addEventListener('cancel', event => { if (this.deleting) event.preventDefault(); });
            this.catalog = root.querySelector('.medal-catalog');
            this.catalog.style.setProperty('--medal-page-size', this.pageSize);
            this.pagers = Array.from(root.querySelectorAll('[data-medal-pager]'), bar => this.bindPager(bar));
            const searchForm = root.querySelector('#medalSearchForm');
            searchForm.addEventListener('submit', event => {
                event.preventDefault();
                this.load(1, root.querySelector('#medalSearchInput').value.trim());
            });
            searchForm.addEventListener('reset', () => this.load(1, ''));
            this.drawers = new MedalDrawers(root, () => this.load());
            root.querySelector('#btnCreateMedal').addEventListener('click', () => this.drawers.edit());
            root.querySelector('#btnReorderAllMedals')?.addEventListener('click', event => this.reorder(event.currentTarget));
            this.load();
        }

        bindPager(bar) {
            const form = bar.querySelector('form');
            const page = form.querySelector('input');
            const previous = form.querySelector('[data-previous]');
            const next = form.querySelector('[data-next]');
            const jump = form.querySelector('[type=submit]');
            previous.addEventListener('click', () => this.changePage(this.page - 1));
            next.addEventListener('click', () => this.changePage(this.page + 1));
            page.addEventListener('focus', () => page.select());
            form.addEventListener('submit', event => {
                event.preventDefault();
                const target = page.value.trim();
                this.changePage(/^\d+$/.test(target) ? Number(target) : NaN);
            });
            return { page, previous, next, jump, range: bar.querySelector('.medal-catalog__range') };
        }

        changePage(page) {
            if (this.loading) return;
            if (!Number.isSafeInteger(page) || page < 1 || page * this.pageSize > 2147483647 || page === this.page) {
                this.syncPagers();
                return;
            }
            if (this.keyword) {
                if ((page - 1) * this.pageSize >= this.matches.length) this.syncPagers('该页无勋章');
                else this.setRows(page, this.keyword, this.matches);
                return;
            }
            this.load(page, '');
        }

        async load(target, keyword = this.keyword) {
            const refresh = target == null;
            let page = target ?? this.page;
            const generation = ++this.generation;
            const read = async requestedPage => {
                const { data } = keyword ? await postMedal('search', { keyword }) :
                    await postMedal('list', { page: requestedPage, pageSize: this.pageSize });
                if (!Array.isArray(data)) throw new Error('勋章列表无效');
                return data;
            };
            this.loading = true;
            this.list.setAttribute('aria-busy', 'true');
            this.syncPagers('加载中…');
            let notice = '';
            try {
                let rows = await read(page);
                if (generation !== this.generation) return;
                let reachedEnd = false;
                if (!keyword && page > 1 && !rows.length) {
                    if (!refresh) {
                        if (page === this.page + 1) this.hasMore = false;
                        notice = '该页无勋章';
                        return;
                    }
                    rows = await read(--page);
                    reachedEnd = true;
                    if (generation !== this.generation) return;
                    if (!rows.length && page > 1) {
                        page = 1;
                        rows = await read(page);
                        reachedEnd = false;
                    }
                }
                if (generation !== this.generation) return;
                this.setRows(page, keyword, rows);
                if (reachedEnd) this.hasMore = false;
            } catch (error) {
                if (generation !== this.generation) return;
                notice = '加载失败';
                if (!this.rows.length) setNotice(this.status, '加载失败：' + error.message);
                else window.alert('加载勋章失败：' + error.message);
            } finally {
                if (generation === this.generation) {
                    this.loading = false;
                    this.list.setAttribute('aria-busy', 'false');
                    this.syncPagers(notice);
                }
            }
        }

        setRows(page, keyword, data) {
            this.keyword = keyword;
            this.matches = keyword ? data : [];
            this.page = keyword ? Math.min(page, Math.max(1, Math.ceil(data.length / this.pageSize))) : page;
            const start = (this.page - 1) * this.pageSize;
            this.rows = keyword ? data.slice(start, start + this.pageSize) : data;
            this.hasMore = keyword ? start + this.pageSize < data.length : data.length === this.pageSize;
            this.list.replaceChildren(...this.rows.map(medal => this.makeRow(medal)));
            setNotice(this.status, this.rows.length ? '' : keyword ? '没有匹配的勋章' : '暂无勋章');
            this.syncPagers();
        }

        syncPagers(notice) {
            const first = (this.page - 1) * this.pageSize + 1;
            let range = this.rows.length ? first + '–' + (first + this.rows.length - 1) : '0 条';
            if (this.keyword) range += ' / ' + this.matches.length;
            this.pagers.forEach(pager => {
                pager.range.textContent = notice || range;
                pager.page.value = String(this.page);
                pager.page.disabled = this.page === 1 && !this.hasMore;
                pager.page.readOnly = this.loading;
                pager.jump.disabled = pager.page.disabled;
                pager.previous.disabled = this.page === 1;
                pager.next.disabled = !this.hasMore;
                [pager.previous, pager.next, pager.jump].forEach(button => {
                    button.setAttribute('aria-disabled', String(this.loading || button.disabled));
                });
            });
        }

        makeRow(medal) {
            const id = String(medal.medal_id);
            const row = element('li', 'medal-catalog__row');
            const identity = element('div', 'medal-catalog__identity');
            const labels = element('div', 'medal-catalog__labels');
            const name = action(medal.medal_name || id, 'medal-catalog__name', () => this.openDetail(medal));
            name.title = medal.medal_name || id;
            labels.append(name, element('span', 'medal-catalog__id', id));
            identity.append(badgeImage(id), labels);
            const type = element('span', 'medal-catalog__type', medal.medal_type || '—');
            type.dataset.type = medal.medal_type || '';
            const actions = element('div', 'medal-catalog__actions');
            actions.append(
                action('持有人', 'btn medal-catalog__quiet', () => this.drawers.owners(medal)),
                action('发放', 'btn btn-secondary', () => this.drawers.grant(medal))
            );
            row.append(identity, type, actions);
            return row;
        }

        openDetail(medal) {
            const id = String(medal.medal_id);
            const title = element('h2', '', medal.medal_name || id);
            title.id = 'medalDetailTitle';
            const header = element('header', 'medal-drawer__header');
            const close = action('×', 'medal-drawer__close', () => this.detail.close());
            close.setAttribute('aria-label', '关闭');
            header.append(title, close);
            const body = element('div', 'medal-drawer__body');
            body.append(element('p', 'medal-drawer__meta', [id, medal.medal_type].filter(Boolean).join(' · ')));
            if (medal.medal_description) body.append(element('p', 'medal-drawer__description', medal.medal_description));
            const attributes = element('details', 'medal-drawer__section');
            attributes.append(element('summary', '', '高级属性'), element('pre', '', medal.medal_attr || '—'));
            const more = element('details', 'medal-drawer__section');
            more.append(element('summary', '', '更多操作'), action('删除勋章', 'btn btn-danger', event => this.delete(id, event.currentTarget)));
            body.append(attributes, more);
            const footer = element('footer', 'medal-drawer__footer');
            footer.append(
                action('持有人', 'btn btn-secondary', () => { this.detail.close(); this.drawers.owners(medal); }),
                action('编辑', 'btn btn-secondary', () => { this.detail.close(); this.drawers.edit(medal); }),
                action('发放', 'btn btn-primary', () => { this.detail.close(); this.drawers.grant(medal); })
            );
            this.detail.replaceChildren(header, body, footer);
            this.detail.showModal();
        }

        async delete(medalId, button) {
            if (this.deleting || !window.confirm('确认删除勋章 ' + medalId + ' 吗？持有记录也会删除，此操作不可撤销。')) return;
            const actions = [...button.closest('dialog').querySelectorAll('button')];
            this.deleting = true;
            actions.forEach(actionButton => { actionButton.disabled = true; });
            try {
                await postMedal('delete', { medalId });
                this.detail.close();
                this.load();
            } catch (error) {
                window.alert('删除失败：' + error.message);
            } finally {
                this.deleting = false;
                actions.forEach(actionButton => { actionButton.disabled = false; });
            }
        }

        async reorder(button) {
            if (!window.confirm('确认重新排列所有捐赠勋章（一级到四级）的排名吗？此操作会更新所有用户的勋章排名信息。')) return;
            button.disabled = true;
            try {
                await postMedal('reorder', {});
                window.alert('重排成功');
            } catch (error) {
                window.alert('重排失败：' + error.message);
            } finally {
                button.disabled = false;
            }
        }
    }

    class MedalDrawers {
        constructor(root, refreshCatalog) {
            this.find = id => root.querySelector('#' + id);
            this.refreshCatalog = refreshCatalog;
            this.editDialog = this.find('medalEditModal');
            this.editForm = this.find('medalEditForm');
            this.ownersDialog = this.find('medalOwnersModal');
            this.grantDialog = this.find('medalGrantModal');
            this.grantForm = this.find('medalGrantForm');
            this.ownerPageSize = 20;
            this.ownerGeneration = 0;
            this.grantGeneration = 0;
            root.querySelectorAll('dialog [data-close]').forEach(button => {
                button.addEventListener('click', () => button.closest('dialog').close());
            });
            this.editDialog.addEventListener('cancel', event => { if (this.saving) event.preventDefault(); });
            this.grantDialog.addEventListener('cancel', event => { if (this.granting) event.preventDefault(); });
            this.editForm.addEventListener('submit', event => { event.preventDefault(); this.save(); });
            this.find('ownersPrevPage').addEventListener('click', () => this.loadOwners(this.ownerPage - 1));
            this.find('ownersNextPage').addEventListener('click', () => this.loadOwners(this.ownerPage + 1));
            this.find('grantExpiryMode').addEventListener('change', () => this.syncGrantDate());
            this.grantForm.addEventListener('submit', event => { event.preventDefault(); this.reviewGrant(); });
            this.find('medalGrantBack').addEventListener('click', () => this.backFromGrant());
            this.find('btnGrantMedalToUser').addEventListener('click', () => this.submitGrant());
        }

        edit(medal) {
            const fields = this.editForm.elements;
            this.find('medalEditModalTitle').textContent = medal ? '编辑勋章' : '新建勋章';
            fields.medalId.value = medal?.medal_id ?? '';
            fields.name.value = medal?.medal_name ?? '';
            const type = medal?.medal_type || '';
            const typePicker = fields.namedItem('type');
            typePicker.querySelector('[data-current-type]')?.remove();
            if (![...typePicker.options].some(option => option.value === type)) {
                const current = new Option(type, type);
                current.dataset.currentType = '';
                typePicker.add(current);
            }
            typePicker.value = type;
            fields.description.value = medal?.medal_description ?? '';
            fields.attr.value = medal?.medal_attr ?? '';
            this.editForm.querySelector('.medal-drawer__help').open = false;
            setNotice(this.find('medalEditError'), '');
            this.editDialog.showModal();
        }

        async save() {
            if (this.saving) return;
            const fields = this.editForm.elements;
            const medalId = fields.medalId.value.trim();
            const name = fields.name.value.trim();
            const errorNode = this.find('medalEditError');
            if (!name) {
                setNotice(errorNode, '请输入勋章名称');
                fields.name.focus();
                return;
            }
            const fieldsToSave = {
                name, type: fields.namedItem('type').value.trim(), description: fields.description.value.trim(), attr: fields.attr.value.trim()
            };
            if (medalId) fieldsToSave.medalId = medalId;
            this.saving = true;
            this.setBusy(this.editDialog, this.find('btnSaveMedal'), true);
            setNotice(errorNode, '');
            try {
                await postMedal(medalId ? 'edit' : 'create', fieldsToSave);
                this.editDialog.close();
                this.refreshCatalog();
            } catch (error) {
                setNotice(errorNode, '保存失败：' + error.message);
            } finally {
                this.saving = false;
                this.setBusy(this.editDialog, this.find('btnSaveMedal'), false);
            }
        }

        setBusy(dialog, primary, busy) {
            dialog.querySelectorAll('[data-close]').forEach(button => { button.disabled = busy; });
            primary.disabled = busy;
            dialog.setAttribute('aria-busy', String(busy));
        }

        owners(medal) {
            this.ownerMedalId = String(medal.medal_id);
            this.ownerPage = 1;
            this.ownerTotal = 0;
            this.find('ownersMedalLabel').textContent = medal.medal_name || this.ownerMedalId;
            this.ownersDialog.showModal();
            this.loadOwners(1);
        }

        async loadOwners(page) {
            const generation = ++this.ownerGeneration;
            const medalId = this.ownerMedalId;
            const list = this.find('medalOwnersList');
            const status = this.find('medalOwnersStatus');
            list.replaceChildren();
            setNotice(status, '加载中…');
            this.syncOwnersPager(true);
            try {
                const { data } = await postMedal('owners', { medalId, page, pageSize: this.ownerPageSize });
                if (generation !== this.ownerGeneration || !this.ownersDialog.open) return;
                if (!Array.isArray(data?.items) || !Number.isSafeInteger(data.total) || data.total < 0) throw new Error('持有人列表无效');
                this.ownerTotal = data.total;
                const lastPage = Math.max(1, Math.ceil(this.ownerTotal / this.ownerPageSize));
                if (page > lastPage) { this.loadOwners(lastPage); return; }
                this.ownerPage = page;
                list.replaceChildren(...data.items.map(owner => this.makeOwner(owner, medalId)));
                setNotice(status, data.items.length ? '' : '暂无持有人');
                this.syncOwnersPager(false);
            } catch (error) {
                if (generation !== this.ownerGeneration || !this.ownersDialog.open) return;
                setNotice(status, '加载持有人失败：' + error.message);
                status.append(' ', action('重试', 'btn btn-secondary', () => this.loadOwners(page)));
            }
        }

        syncOwnersPager(loading) {
            const pages = Math.max(1, Math.ceil(this.ownerTotal / this.ownerPageSize));
            this.find('ownersPageInfo').textContent = loading ? '' : '第 ' + this.ownerPage + ' / ' + pages + ' 页 · ' + this.ownerTotal + ' 人';
            this.find('ownersPrevPage').disabled = loading || this.ownerPage <= 1;
            this.find('ownersNextPage').disabled = loading || this.ownerPage >= pages;
        }

        makeOwner(owner, medalId) {
            const userId = String(owner.user_id || '');
            const label = owner.userName || userId || '—';
            const row = element('li', 'medal-owners__item');
            const copy = element('div', 'medal-owners__copy');
            copy.append(element('strong', '', label), element('small', '', formatExpiry(owner.expire_time) + ' · ' + (owner.display === false ? '不展示' : '展示')));
            if (owner.data || (userId && label !== userId)) {
                const more = element('details', 'medal-owners__details');
                more.append(element('summary', '', '持有详情'));
                if (userId && label !== userId) more.append(element('div', '', '用户 ID：' + userId));
                if (owner.data) more.append(element('pre', '', owner.data));
                copy.append(more);
            }
            const revoke = action('收回', 'btn btn-secondary', () => this.revoke(userId, medalId, label, revoke));
            revoke.disabled = !userId;
            row.append(copy, revoke);
            return row;
        }

        async revoke(userId, medalId, label, button) {
            if (!window.confirm('确认收回「' + label + '」的这枚勋章？')) return;
            button.disabled = true;
            try {
                await postMedal('revoke', { userId, medalId });
                if (this.ownersDialog.open && this.ownerMedalId === medalId) this.loadOwners(this.ownerPage);
            } catch (error) {
                window.alert('收回失败：' + error.message);
            } finally {
                button.disabled = false;
            }
        }

        grant(medal) {
            this.grantGeneration++;
            this.grantMedalId = String(medal.medal_id);
            this.find('grantMedalLabel').textContent = medal.medal_name || this.grantMedalId;
            this.grantForm.reset();
            this.grantForm.inert = false;
            this.find('medalGrantReview').disabled = false;
            this.syncGrantDate();
            this.setGrantReview(null);
            this.grantDialog.showModal();
        }

        syncGrantDate() {
            const dated = this.find('grantExpiryMode').value === 'date';
            this.find('grantDateField').hidden = !dated;
            this.find('grantDate').required = dated;
        }

        setGrantReview(pending) {
            this.pendingGrant = pending;
            this.find('medalGrantTitle').textContent = pending ? '确认发放' : '发放勋章';
            this.grantForm.hidden = !!pending;
            this.find('medalGrantReview').hidden = !!pending;
            this.find('btnGrantMedalToUser').hidden = !pending;
            this.find('medalGrantBack').textContent = pending ? '返回' : '取消';
            setNotice(this.find('medalGrantError'), '');
            setNotice(this.find('medalGrantSummary'), pending ? '发放给「' + pending.userName + '」 · ' +
                (pending.payload.expireTime === '0' ? '永久' : '到期：' + formatExpiry(pending.payload.expireTime)) : '');
        }

        backFromGrant() {
            if (!this.pendingGrant) { this.grantDialog.close(); return; }
            this.setGrantReview(null);
            this.find('grantUserId').focus();
        }

        async reviewGrant() {
            const review = this.find('medalGrantReview');
            if (review.disabled || this.pendingGrant) return;
            const userName = this.find('grantUserId').value.trim();
            const errorNode = this.find('medalGrantError');
            if (!userName) { setNotice(errorNode, '请输入用户名'); return; }
            if (!/^[A-Za-z0-9-]{1,64}$/.test(userName)) { setNotice(errorNode, '用户名格式不正确'); return; }
            const mode = this.find('grantExpiryMode').value;
            const data = this.find('grantData').value.trim();
            const medalId = this.grantMedalId;
            const generation = ++this.grantGeneration;
            const isCurrent = () => generation === this.grantGeneration && this.grantDialog.open;
            let expireTime = 0;
            if (mode === 'date') {
                const date = this.find('grantDate').value;
                expireTime = new Date(date + 'T23:59:59.999').getTime();
                if (!Number.isFinite(expireTime) || expireTime <= Date.now()) {
                    setNotice(errorNode, '请选择尚未到期的日期');
                    return;
                }
            }
            review.disabled = true;
            this.grantForm.inert = true;
            review.textContent = '查询中…';
            setNotice(errorNode, '');
            try {
                const query = new URLSearchParams({ apiKey: window.apiKey ?? '' });
                const user = await requestJSON('/user/' + encodeURIComponent(userName) + '?' + query);
                if (!isCurrent()) return;
                const userId = typeof user.oId === 'string' ? user.oId : '';
                if (!userId) throw new Error('未找到该用户');
                if (mode === 'membership') {
                    const { data: membership } = await requestJSON('/api/membership/' + encodeURIComponent(userId));
                    if (!isCurrent()) return;
                    if (membership?.state !== 1) throw new Error('该用户没有有效会员');
                    expireTime = Number(membership.expiresAt);
                    if (membership.expiresAt == null || !Number.isSafeInteger(expireTime) || expireTime < 0) throw new Error('会员到期时间无效');
                    if (expireTime !== 0 && expireTime <= Date.now()) throw new Error('该用户的会员已到期');
                }
                this.setGrantReview({ userName: user.userName || userName, payload: { medalId, userId, expireTime: String(expireTime), data } });
                this.find('btnGrantMedalToUser').focus();
            } catch (error) {
                if (isCurrent()) setNotice(errorNode, error.message);
            } finally {
                if (generation === this.grantGeneration) {
                    this.grantForm.inert = false;
                    review.disabled = false;
                    review.textContent = '确认发放';
                }
            }
        }

        async submitGrant() {
            if (this.granting || !this.pendingGrant) return;
            const payload = this.pendingGrant.payload;
            const errorNode = this.find('medalGrantError');
            if (payload.expireTime !== '0' && Number(payload.expireTime) <= Date.now()) {
                setNotice(errorNode, '到期时间已过，请返回重新选择');
                return;
            }
            const submit = this.find('btnGrantMedalToUser');
            const back = this.find('medalGrantBack');
            this.granting = true;
            back.disabled = true;
            this.setBusy(this.grantDialog, submit, true);
            setNotice(errorNode, '');
            try {
                await postMedal('grant', payload);
                this.grantDialog.close();
                window.alert('发放成功');
            } catch (error) {
                setNotice(errorNode, '发放失败：' + error.message);
            } finally {
                this.granting = false;
                back.disabled = false;
                this.setBusy(this.grantDialog, submit, false);
            }
        }
    }

    function init() {
        const root = document.getElementById('medalAdminRoot');
        if (root) new MedalCatalog(root);
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
})();
