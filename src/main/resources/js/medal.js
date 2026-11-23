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
    if (typeof window === 'undefined') {
        return;
    }

    function postJSON(url, body) {
        return fetch(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json;charset=UTF-8'
            },
            body: JSON.stringify(body || {})
        }).then(function (resp) {
            return resp.json();
        });
    }

    function formatTimestamp(ts) {
        if (!ts || ts <= 0) {
            return '永久';
        }
        try {
            var d = new Date(ts);
            if (isNaN(d.getTime())) {
                return String(ts);
            }
            var y = d.getFullYear();
            var m = ('0' + (d.getMonth() + 1)).slice(-2);
            var day = ('0' + d.getDate()).slice(-2);
            var hh = ('0' + d.getHours()).slice(-2);
            var mm = ('0' + d.getMinutes()).slice(-2);
            return y + '-' + m + '-' + day + ' ' + hh + ':' + mm;
        } catch (e) {
            return String(ts);
        }
    }

    function formatDateTime(ts) {
        if (!ts || ts <= 0) {
            return '永久';
        }
        var d = new Date(ts);
        if (isNaN(d.getTime())) {
            return String(ts);
        }
        var y = d.getFullYear();
        var m = ('0' + (d.getMonth() + 1)).slice(-2);
        var day = ('0' + d.getDate()).slice(-2);
        var hh = ('0' + d.getHours()).slice(-2);
        var mm = ('0' + d.getMinutes()).slice(-2);
        var ss = ('0' + d.getSeconds()).slice(-2);
        return y + '-' + m + '-' + day + ' ' + hh + ':' + mm + ':' + ss;
    }

    function element(tag, className, text) {
        var node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text != null) {
            node.textContent = text;
        }
        return node;
    }

    function action(label, className, handler) {
        var button = element('button', className, label);
        button.type = 'button';
        button.addEventListener('click', handler);
        return button;
    }

    function appendCell(row, content) {
        var cell = element('td');
        cell.append(content);
        row.append(cell);
    }

    var AdminMedalPage = {
        page: 1,
        pageSize: 20,
        medals: [],
        searchResults: [],
        listRequest: 0,
        listLoading: false,
        ownersPage: 1,
        ownersPageSize: 20,
        ownersTotal: 0,
        currentOwnersMedalId: null,
        keyword: '',
        hasMore: false,

        init: function () {
            var root = document.getElementById('medalAdminRoot');
            if (!root) {
                return;
            }

            this.cacheDom();
            this.mountCatalog(root);
            this.bindEvents();
            this.loadMedals();
        },

        cacheDom: function () {
            this.dom = {};
            this.dom.btnCreate = document.getElementById('btnCreateMedal');
            this.dom.btnReorderAll = document.getElementById('btnReorderAllMedals');

            this.dom.searchInput = document.getElementById('medalSearchInput');
            this.dom.btnSearch = document.getElementById('btnSearchMedal');
            this.dom.btnResetSearch = document.getElementById('btnResetSearchMedal');

            this.dom.modalEdit = document.getElementById('medalEditModal');
            this.dom.modalEditTitle = document.getElementById('medalEditModalTitle');
            this.dom.formEdit = document.getElementById('medalEditForm');
            this.dom.inputMedalId = document.getElementById('medalId');
            this.dom.inputMedalName = document.getElementById('medalName');
            this.dom.inputMedalType = document.getElementById('medalType');
            this.dom.inputMedalDesc = document.getElementById('medalDescription');
            this.dom.inputMedalAttr = document.getElementById('medalAttr');
            this.dom.btnSaveMedal = document.getElementById('btnSaveMedal');

            this.dom.modalOwners = document.getElementById('medalOwnersModal');
            this.dom.ownersTitle = this.dom.modalOwners.querySelector('h2');
            this.dom.ownersTitleText = this.dom.ownersTitle.textContent;
            this.dom.ownersMedalIdLabel = document.getElementById('ownersMedalIdLabel');
            this.dom.ownersTableBody = document.getElementById('medalOwnersTableBody');
            this.dom.ownersPageLabel = document.getElementById('ownersPage');
            this.dom.ownersTotalLabel = document.getElementById('ownersTotal');
            this.dom.ownersPrev = document.getElementById('ownersPrevPage');
            this.dom.ownersNext = document.getElementById('ownersNextPage');

            this.dom.grantUserId = document.getElementById('grantUserId');
            this.dom.grantExpireTime = document.getElementById('grantExpireTime');
            this.dom.grantData = document.getElementById('grantData');
            this.dom.btnGrant = document.getElementById('btnGrantMedalToUser');
            this.dom.btnFillVipExpireTime = document.getElementById('btnFillVipExpireTime');
        },

        mountCatalog: function (root) {
            var oldRows = root.querySelector('#medalTableBody, #medalMobileList');
            var oldCatalog = oldRows.closest('.medal-admin__table-wrapper, .medal-admin__list');
            var oldPager = document.getElementById('medalPage').closest('.medal-admin__pagination');
            var catalog = element('section', 'medal-catalog');
            catalog.setAttribute('aria-label', '勋章列表');
            catalog.style.setProperty('--medal-page-size', this.pageSize);
            var search = element('div', 'medal-catalog__search');
            this.dom.searchInput.setAttribute('aria-label', '按 ID、名称或描述搜索勋章');
            search.append(this.dom.searchInput, this.dom.btnSearch, this.dom.btnResetSearch);

            this.dom.pagers = [this.makePager(true), this.makePager(false)];
            var headings = element('div', 'medal-catalog__columns');
            headings.setAttribute('aria-hidden', 'true');
            headings.append(element('span', '', '勋章'), element('span', '', '类型'), element('span', '', '操作'));
            this.dom.rows = element('ul', 'medal-catalog__rows');
            this.dom.status = element('div', 'medal-catalog__status', '加载中…');
            this.dom.status.setAttribute('role', 'status');
            var listing = element('div', 'medal-catalog__listing');
            listing.append(this.dom.rows, this.dom.status);
            catalog.append(search, this.dom.pagers[0].bar, headings, listing, this.dom.pagers[1].bar);
            oldCatalog.replaceWith(catalog);
            oldPager.remove();

            var hint = root.querySelector('.medal-admin__hint');
            if (hint) {
                var help = element('details', 'medal-catalog__help');
                var summary = element('summary', '', '?');
                summary.setAttribute('aria-label', '勋章管理说明');
                help.append(summary, hint.querySelector('ul'));
                root.querySelector('.medal-admin__actions').prepend(help);
                hint.remove();
            }

            this.dom.detail = element('dialog', 'medal-detail');
            this.dom.detail.setAttribute('aria-labelledby', 'medalDetailTitle');
            root.append(this.dom.detail);
            root.classList.add('medal-admin--catalog');
        },

        makePager: function (sticky) {
            var self = this;
            var bar = element('nav', 'medal-catalog__pager' + (sticky ? ' medal-catalog__pager--top' : ''));
            bar.setAttribute('aria-label', sticky ? '顶部翻页' : '底部翻页');
            var range = element('span', 'medal-catalog__range');
            if (sticky) {
                range.setAttribute('aria-live', 'polite');
            }
            var form = element('form', 'medal-catalog__paging');
            var page = element('input', 'form-control medal-catalog__page');
            page.type = 'text';
            page.inputMode = 'numeric';
            page.enterKeyHint = 'go';
            page.setAttribute('aria-label', '跳到第几页');
            var buttonClass = 'btn btn-secondary';
            var previous = action('上一页', buttonClass, function () { self.changePage(self.page - 1); });
            var next = action('下一页', buttonClass, function () { self.changePage(self.page + 1); });
            var jump = element('button', buttonClass, '跳转');
            jump.type = 'submit';
            form.addEventListener('submit', function (event) {
                event.preventDefault();
                var target = page.value.trim();
                if (!/^\d+$/.test(target)) {
                    self.syncPagers();
                    return;
                }
                self.changePage(Number(target));
            });
            page.addEventListener('focus', function () { page.select(); });
            form.append(previous, page, jump, next);
            bar.append(range, form);
            return { bar: bar, range: range, page: page, jump: jump, previous: previous, next: next };
        },

        bindEvents: function () {
            var self = this;

            if (this.dom.btnCreate) {
                this.dom.btnCreate.addEventListener('click', function () {
                    self.openCreateModal();
                });
            }

            if (this.dom.btnReorderAll) {
                this.dom.btnReorderAll.addEventListener('click', function () {
                    self.reorderAllMedals();
                });
            }

            if (this.dom.btnSearch) {
                this.dom.btnSearch.addEventListener('click', function () {
                    var kw = (self.dom.searchInput && self.dom.searchInput.value || '').trim();
                    self.loadMedals(1, kw);
                });
            }

            // 允许在搜索框中按回车直接触发搜索
            if (this.dom.searchInput && this.dom.btnSearch) {
                this.dom.searchInput.addEventListener('keydown', function (e) {
                    var evt = e || window.event;
                    if (evt.key === 'Enter' || evt.keyCode === 13) {
                        evt.preventDefault();
                        self.dom.btnSearch.click();
                    }
                });
            }

            if (this.dom.btnResetSearch) {
                this.dom.btnResetSearch.addEventListener('click', function () {
                    if (self.dom.searchInput) {
                        self.dom.searchInput.value = '';
                    }
                    self.loadMedals(1, '');
                });
            }

            if (this.dom.btnSaveMedal) {
                this.dom.btnSaveMedal.addEventListener('click', function () {
                    self.saveMedal();
                });
            }

            if (this.dom.modalEdit) {
                this.dom.modalEdit.addEventListener('click', function (e) {
                    if (e.target.getAttribute('data-medal-modal-close') != null || e.target === self.dom.modalEdit.querySelector('.medal-modal__backdrop')) {
                        self.closeModal(self.dom.modalEdit);
                    }
                });
            }
            if (this.dom.modalOwners) {
                this.dom.modalOwners.addEventListener('click', function (e) {
                    if (e.target.getAttribute('data-medal-modal-close') != null || e.target === self.dom.modalOwners.querySelector('.medal-modal__backdrop')) {
                        self.closeModal(self.dom.modalOwners);
                    }
                });
            }

            if (this.dom.ownersPrev) {
                this.dom.ownersPrev.addEventListener('click', function () {
                    if (self.ownersPage > 1) {
                        self.ownersPage -= 1;
                        self.loadMedalOwners(self.currentOwnersMedalId);
                    }
                });
            }
            if (this.dom.ownersNext) {
                this.dom.ownersNext.addEventListener('click', function () {
                    var maxPage = Math.ceil(self.ownersTotal / self.ownersPageSize);
                    if (self.ownersPage < maxPage) {
                        self.ownersPage += 1;
                        self.loadMedalOwners(self.currentOwnersMedalId);
                    }
                });
            }

            if (this.dom.btnGrant) {
                this.dom.btnGrant.addEventListener('click', function () {
                    self.grantMedalToUser();
                });
            }

            if (this.dom.btnFillVipExpireTime) {
                this.dom.btnFillVipExpireTime.addEventListener('click', function () {
                    self.fillVipExpireTime();
                });
            }
        },

        apiPayloadBase: function () {
            return {
                apiKey: (typeof window.apiKey !== 'undefined' ? window.apiKey : null)
            };
        },

        changePage: function (page) {
            if (this.listLoading) {
                return;
            }
            if (!Number.isSafeInteger(page) || page < 1 || page * this.pageSize > 2147483647 || page === this.page) {
                this.syncPagers();
                return;
            }
            if (this.keyword) {
                var start = (page - 1) * this.pageSize;
                if (start >= this.searchResults.length) {
                    this.syncPagers('该页无勋章');
                    return;
                }
                this.setMedals(page, this.keyword, this.searchResults);
                return;
            }
            this.loadMedals(page, '');
        },

        loadMedals: function (page, keyword) {
            var self = this;
            var refresh = page == null;
            page = refresh ? this.page : page;
            keyword = refresh ? this.keyword : keyword;
            var request = ++this.listRequest;
            this.listLoading = true;
            this.dom.rows.setAttribute('aria-busy', 'true');
            this.syncPagers('加载中…');

            function read(targetPage) {
                var body = self.apiPayloadBase();
                var url = keyword ? '/api/medal/admin/search' : '/api/medal/admin/list';
                if (keyword) {
                    body.keyword = keyword;
                } else {
                    body.page = targetPage;
                    body.pageSize = self.pageSize;
                }
                return postJSON(url, body).then(function (ret) {
                    if (!ret || ret.code !== 0 || !Array.isArray(ret.data)) {
                        throw new Error(ret && ret.msg || '返回数据无效');
                    }
                    return ret.data;
                });
            }

            var notice = '';
            read(page).then(function (list) {
                if (request !== self.listRequest) {
                    return;
                }
                if (!keyword && page > 1 && list.length === 0) {
                    if (refresh) {
                        return read(page - 1).then(function (previous) {
                            if (request !== self.listRequest) {
                                return;
                            }
                            if (previous.length || page === 2) {
                                self.setMedals(page - 1, '', previous);
                                self.hasMore = false;
                                return;
                            }
                            return read(1).then(function (first) {
                                if (request === self.listRequest) {
                                    self.setMedals(1, '', first);
                                }
                            });
                        });
                    }
                    if (page === self.page + 1) {
                        self.hasMore = false;
                    }
                    notice = '该页无勋章';
                    return;
                }
                self.setMedals(page, keyword, list);
            }).catch(function (error) {
                if (request !== self.listRequest) {
                    return;
                }
                notice = '加载失败';
                if (!self.medals.length) {
                    self.dom.status.textContent = '加载失败';
                }
                alert('加载勋章失败：' + error.message);
            }).finally(function () {
                if (request === self.listRequest) {
                    self.listLoading = false;
                    self.dom.rows.setAttribute('aria-busy', 'false');
                    self.syncPagers(notice);
                }
            });
        },

        setMedals: function (page, keyword, list) {
            this.keyword = keyword;
            this.searchResults = keyword ? list : [];
            this.page = keyword ? Math.min(page, Math.max(1, Math.ceil(list.length / this.pageSize))) : page;
            var start = (this.page - 1) * this.pageSize;
            this.medals = keyword ? list.slice(start, start + this.pageSize) : list;
            this.hasMore = keyword ? start + this.pageSize < list.length : list.length === this.pageSize;
            this.renderMedals();
        },

        syncPagers: function (notice) {
            var self = this;
            var first = (this.page - 1) * this.pageSize + 1;
            var range = this.medals.length ? first + '–' + (first + this.medals.length - 1) : '0 条';
            if (this.keyword) {
                range += ' / ' + this.searchResults.length;
            }
            this.dom.pagers.forEach(function (pager) {
                pager.range.textContent = notice || range;
                pager.page.value = String(self.page);
                pager.page.disabled = self.page === 1 && !self.hasMore;
                pager.jump.disabled = pager.page.disabled;
                pager.previous.disabled = self.page === 1;
                pager.next.disabled = !self.hasMore;
                pager.page.readOnly = self.listLoading;
                pager.previous.setAttribute('aria-disabled', String(self.listLoading || pager.previous.disabled));
                pager.next.setAttribute('aria-disabled', String(self.listLoading || pager.next.disabled));
                pager.jump.setAttribute('aria-disabled', String(self.listLoading || pager.jump.disabled));
            });
        },

        renderMedals: function () {
            var self = this;
            var rows = this.medals.map(function (medal) {
                var id = String(medal.medal_id);
                var row = element('li', 'medal-catalog__row');
                var identity = element('div', 'medal-catalog__identity');
                var preview = element('img', 'medal-catalog__preview');
                preview.src = (window.Label && Label.servePath ? Label.servePath : '') + '/gen?id=' + encodeURIComponent(id);
                preview.alt = '';
                var labels = element('div', 'medal-catalog__labels');
                var name = action(medal.medal_name || id, 'medal-catalog__name', function () { self.openDetail(medal); });
                name.title = medal.medal_name || id;
                labels.append(name, element('span', 'medal-catalog__id', id));
                identity.append(preview, labels);
                var type = element('span', 'medal-catalog__type', medal.medal_type || '—');
                type.dataset.type = medal.medal_type || '';
                var actions = element('div', 'medal-catalog__actions');
                actions.append(
                    action('持有人', 'btn medal-catalog__quiet', function () { self.openOwnersModal(id); }),
                    action('发放', 'btn btn-secondary', function () { self.openGrant(id); })
                );
                row.append(identity, type, actions);
                return row;
            });
            this.dom.rows.replaceChildren.apply(this.dom.rows, rows);
            this.dom.status.hidden = rows.length > 0;
            this.dom.status.textContent = this.keyword ? '没有匹配的勋章' : '暂无勋章';
            this.syncPagers();
        },

        openDetail: function (medal) {
            var self = this;
            var dialog = this.dom.detail;
            var id = String(medal.medal_id);
            var title = element('h2', '', medal.medal_name || id);
            title.id = 'medalDetailTitle';
            var header = element('header', 'medal-detail__header');
            var close = action('×', 'medal-modal__close', function () { dialog.close(); });
            close.setAttribute('aria-label', '关闭');
            header.append(title, close);
            var body = element('div', 'medal-detail__body');
            body.append(element('p', 'medal-detail__meta', [id, medal.medal_type].filter(Boolean).join(' · ')));
            if (medal.medal_description) {
                body.append(element('p', 'medal-detail__description', medal.medal_description));
            }
            var attributes = element('details', 'medal-detail__section');
            attributes.append(element('summary', '', '高级属性'), element('pre', '', medal.medal_attr || '—'));
            var more = element('details', 'medal-detail__section');
            more.append(element('summary', '', '更多操作'), action('删除勋章', 'btn btn-danger', function () {
                self.confirmDeleteMedal(id);
            }));
            body.append(attributes, more);
            var footer = element('footer', 'medal-detail__footer');
            footer.append(
                action('持有人', 'btn btn-secondary', function () { dialog.close(); self.openOwnersModal(id); }),
                action('编辑', 'btn btn-secondary', function () { dialog.close(); self.openEditModal(id); }),
                action('发放', 'btn btn-primary', function () { dialog.close(); self.openGrant(id); })
            );
            dialog.replaceChildren(header, body, footer);
            dialog.showModal();
        },

        openGrant: function (medalId) {
            this.prepareOwnersModal(medalId, true);
            this.openModal(this.dom.modalOwners);
            if (this.dom.grantUserId) {
                this.dom.grantUserId.focus();
            }
        },

        openCreateModal: function () {
            if (!this.dom.modalEdit) {
                return;
            }
            this.dom.modalEditTitle.textContent = '新建勋章';
            this.dom.inputMedalId.value = '';
            this.dom.inputMedalName.value = '';
            this.dom.inputMedalType.value = '';
            this.dom.inputMedalDesc.value = '';
            this.dom.inputMedalAttr.value = '';
            this.openModal(this.dom.modalEdit);
        },

        openEditModal: function (medalId) {
            var medal = null;
            for (var i = 0; i < this.medals.length; i++) {
                if (String(this.medals[i].medal_id) === String(medalId)) {
                    medal = this.medals[i];
                    break;
                }
            }
            if (!medal || !this.dom.modalEdit) {
                return;
            }
            this.dom.modalEditTitle.textContent = '编辑勋章 ' + medal.medal_id;
            this.dom.inputMedalId.value = medal.medal_id || '';
            this.dom.inputMedalName.value = medal.medal_name || '';
            this.dom.inputMedalType.value = medal.medal_type || '';
            this.dom.inputMedalDesc.value = medal.medal_description || '';
            this.dom.inputMedalAttr.value = medal.medal_attr || '';
            this.openModal(this.dom.modalEdit);
        },

        openModal: function (el) {
            if (!el) {
                return;
            }
            el.classList.add('is-open');
        },

        closeModal: function (el) {
            if (!el) {
                return;
            }
            el.classList.remove('is-open');
        },

        saveMedal: function () {
            var self = this;
            if (!this.dom.formEdit) {
                return;
            }

            var medalId = (this.dom.inputMedalId.value || '').trim();
            var name = (this.dom.inputMedalName.value || '').trim();
            var type = (this.dom.inputMedalType.value || '').trim();
            var desc = (this.dom.inputMedalDesc.value || '').trim();
            var attr = (this.dom.inputMedalAttr.value || '').trim();

            if (!name) {
                alert('勋章名称不能为空');
                return;
            }

            var body = this.apiPayloadBase();
            // MedalProcessor#adminCreateMedal / adminEditMedal 预期字段
            if (medalId) {
                body.medalId = medalId;
            }
            body.name = name;
            body.type = type;
            body.description = desc;
            body.attr = attr;

            var url = medalId ? '/api/medal/admin/edit' : '/api/medal/admin/create';

            postJSON(url, body).then(function (ret) {
                if (!ret || ret.code !== 0) {
                    alert('保存失败：' + (ret ? ret.msg : '未知错误'));
                    return;
                }
                alert('保存成功');
                self.closeModal(self.dom.modalEdit);
                self.loadMedals();
            }).catch(function (e) {
                alert('保存失败：' + e);
            });
        },

        confirmDeleteMedal: function (medalId) {
            var self = this;
            if (!window.confirm('确认删除勋章 ' + medalId + ' 吗？持有记录也会删除，此操作不可撤销。')) {
                return;
            }
            var body = this.apiPayloadBase();
            body.medalId = medalId;

            postJSON('/api/medal/admin/delete', body).then(function (ret) {
                if (!ret || ret.code !== 0) {
                    alert('删除失败：' + (ret ? ret.msg : '未知错误'));
                    return;
                }
                alert('删除成功');
                self.dom.detail.close();
                self.loadMedals();
            }).catch(function (e) {
                alert('删除失败：' + e);
            });
        },

        openOwnersModal: function (medalId) {
            this.prepareOwnersModal(medalId, false);
            this.ownersPage = 1;
            this.ownersTotal = 0;
            this.renderMedalOwners([], '加载中…');
            this.loadMedalOwners(medalId);
            this.openModal(this.dom.modalOwners);
        },

        prepareOwnersModal: function (medalId, grantOnly) {
            if (this.currentOwnersMedalId !== medalId) {
                this.dom.grantUserId.value = '';
                this.dom.grantExpireTime.value = '';
                this.dom.grantData.value = '';
            }
            this.currentOwnersMedalId = medalId;
            this.dom.ownersMedalIdLabel.textContent = medalId;
            this.dom.modalOwners.classList.toggle('medal-modal--grant', grantOnly);
            this.dom.ownersTitle.textContent = grantOnly ? '发放勋章' : this.dom.ownersTitleText;
        },

        loadMedalOwners: function (medalId) {
            var self = this;
            var request = this.ownersRequest = (this.ownersRequest || 0) + 1;
            var body = this.apiPayloadBase();
            body.medalId = medalId;
            body.page = this.ownersPage;
            body.pageSize = this.ownersPageSize;

            postJSON('/api/medal/admin/owners', body).then(function (ret) {
                if (request !== self.ownersRequest) {
                    return;
                }
                if (!ret || ret.code !== 0) {
                    self.renderMedalOwners([], '加载失败');
                    alert('加载拥有者失败：' + (ret ? ret.msg : '未知错误'));
                    return;
                }
                var data = ret.data || {};
                var list = data.items || []; // MedalProcessor.adminGetMedalOwners 返回 items
                self.ownersTotal = data.total || 0;
                self.renderMedalOwners(list);
            }).catch(function (e) {
                if (request === self.ownersRequest) {
                    self.renderMedalOwners([], '加载失败');
                    alert('加载拥有者失败：' + e);
                }
            });
        },

        renderMedalOwners: function (owners, message) {
            var self = this;
            if (!this.dom.ownersTableBody) {
                return;
            }
            var headings = this.dom.ownersTableBody.closest('table').tHead.rows[0];
            if (headings.cells.length === 4) {
                headings.replaceChildren.apply(headings, ['用户 ID', '用户名', '过期时间', '展示', '数据', '操作'].map(function (title) {
                    return element('th', '', title);
                }));
            }
            var rows = (owners || []).map(function (owner) {
                var row = element('tr');
                var userId = String(owner.user_id || '');
                var medalId = String(owner.medal_id || '');
                appendCell(row, userId);
                appendCell(row, String(owner.userName || ''));
                appendCell(row, formatTimestamp(owner.expire_time));
                appendCell(row, owner.display === false ? '否' : '是');
                appendCell(row, element('pre', '', owner.data || ''));
                var revoke = action('收回', 'btn btn-secondary', function () { self.revokeMedalFromUser(userId, medalId); });
                revoke.disabled = !userId || !medalId;
                appendCell(row, revoke);
                return row;
            });
            if (!rows.length) {
                var emptyRow = element('tr');
                var emptyCell = element('td', 'medal-table__empty', message || '暂无拥有者');
                emptyCell.colSpan = headings.cells.length;
                emptyRow.append(emptyCell);
                rows.push(emptyRow);
            }
            this.dom.ownersTableBody.replaceChildren.apply(this.dom.ownersTableBody, rows);

            if (this.dom.ownersPageLabel) {
                this.dom.ownersPageLabel.textContent = String(this.ownersPage);
            }
            if (this.dom.ownersTotalLabel) {
                this.dom.ownersTotalLabel.textContent = String(this.ownersTotal);
            }
        },

        grantMedalToUser: function () {
            var self = this;
            if (!this.currentOwnersMedalId) {
                alert('请先选择要发放的勋章');
                return;
            }
            var userId = (this.dom.grantUserId.value || '').trim();
            var expireTime = (this.dom.grantExpireTime.value || '').trim();
            var data = (this.dom.grantData.value || '').trim();

            if (!userId) {
                alert('用户名不能为空');
                return;
            }

            // userName转userId
            $.ajax({
                url: '/user/' + encodeURIComponent(userId) + '?apiKey=' + encodeURIComponent(apiKey),
                type: 'GET',
                dataType: 'json',
                async: false,
                success: function (result) {
                    userId = result.oId;
                }
            });

            var body = this.apiPayloadBase();
            body.medalId = this.currentOwnersMedalId;
            body.userId = userId;
            body.expireTime = expireTime;
            body.data = data;

            postJSON('/api/medal/admin/grant', body).then(function (ret) {
                if (!ret || ret.code !== 0) {
                    alert('发放失败：' + (ret ? ret.msg : '未知错误'));
                    return;
                }
                alert('发放成功');
                if (self.currentOwnersMedalId === body.medalId && self.dom.modalOwners.classList.contains('is-open')) {
                    if (self.dom.modalOwners.classList.contains('medal-modal--grant')) {
                        self.closeModal(self.dom.modalOwners);
                    } else {
                        self.loadMedalOwners(body.medalId);
                    }
                }
            }).catch(function (e) {
                alert('发放失败：' + e);
            });
        },

        fillVipExpireTime: function () {
            var self = this;
            var userName = (this.dom.grantUserId && this.dom.grantUserId.value || '').trim();
            var medalId = this.currentOwnersMedalId;

            function isCurrentGrant() {
                return self.currentOwnersMedalId === medalId && self.dom.grantUserId.value.trim() === userName;
            }

            function resetSyncButton() {
                if (self.dom.btnFillVipExpireTime) {
                    self.dom.btnFillVipExpireTime.disabled = false;
                    self.dom.btnFillVipExpireTime.textContent = '同步VIP到期';
                }
            }

            if (!userName) {
                alert('请先填写用户名');
                return;
            }

            if (this.dom.btnFillVipExpireTime) {
                this.dom.btnFillVipExpireTime.disabled = true;
                this.dom.btnFillVipExpireTime.textContent = '同步中...';
            }

            $.ajax({
                url: '/user/' + encodeURIComponent(userName) + '?apiKey=' + encodeURIComponent(apiKey),
                type: 'GET',
                dataType: 'json',
                success: function (userResult) {
                    if (!isCurrentGrant()) {
                        resetSyncButton();
                        return;
                    }
                    var userId = userResult && userResult.oId;
                    if (!userId) {
                        alert('未找到该用户：' + userName);
                        resetSyncButton();
                        return;
                    }

                    $.ajax({
                        url: '/api/membership/' + encodeURIComponent(userId),
                        type: 'GET',
                        dataType: 'json',
                        success: function (vipResult) {
                            if (!isCurrentGrant()) {
                                return;
                            }
                            if (!vipResult || vipResult.code !== 0) {
                                alert('获取VIP状态失败：' + (vipResult ? vipResult.msg : '未知错误'));
                                return;
                            }

                            var vipData = vipResult.data || {};
                            var state = vipData.state;
                            var expiresAt = vipData.expiresAt;

                            if (state !== 1) {
                                alert('该用户当前不是有效VIP，无法自动填充。');
                                return;
                            }

                            if (!expiresAt || expiresAt <= 0) {
                                self.dom.grantExpireTime.value = '0';
                                alert('该用户VIP为永久有效，已填充勋章过期时间为 0（永久）。');
                                return;
                            }

                            self.dom.grantExpireTime.value = String(expiresAt);
                            alert('已填充勋章过期时间：' + formatDateTime(expiresAt));
                        },
                        error: function (xhr) {
                            alert('获取VIP状态失败：' + (xhr && xhr.statusText ? xhr.statusText : '网络错误'));
                        },
                        complete: resetSyncButton
                    });
                },
                error: function (xhr) {
                    alert('查询用户失败：' + (xhr && xhr.statusText ? xhr.statusText : '网络错误'));
                    resetSyncButton();
                }
            });
        },

        revokeMedalFromUser: function (userId, medalId) {
            var self = this;
            if (!window.confirm('确认收回该用户的此勋章吗？')) {
                return;
            }

            var body = this.apiPayloadBase();
            body.userId = userId;
            body.medalId = medalId;

            postJSON('/api/medal/admin/revoke', body).then(function (ret) {
                if (!ret || ret.code !== 0) {
                    alert('收回失败：' + (ret ? ret.msg : '未知错误'));
                    return;
                }
                alert('收回成功');
                if (self.currentOwnersMedalId === medalId && !self.dom.modalOwners.classList.contains('medal-modal--grant')) {
                    self.loadMedalOwners(medalId);
                }
            }).catch(function (e) {
                alert('收回失败：' + e);
            });
        },

        reorderAllMedals: function () {
            var self = this;
            if (!window.confirm('确认重新排列所有捐赠勋章（一级到四级）的排名吗？\n此操作会更新所有用户的勋章排名信息。')) {
                return;
            }

            var body = this.apiPayloadBase();

            // 禁用按钮防止重复点击
            if (this.dom.btnReorderAll) {
                this.dom.btnReorderAll.disabled = true;
                this.dom.btnReorderAll.textContent = '重排中...';
            }

            postJSON('/api/medal/admin/reorder', body).then(function (ret) {
                if (self.dom.btnReorderAll) {
                    self.dom.btnReorderAll.disabled = false;
                    self.dom.btnReorderAll.textContent = '重排捐赠勋章';
                }
                if (!ret || ret.code !== 0) {
                    alert('重排失败：' + (ret ? ret.msg : '未知错误'));
                    return;
                }
                alert('重排成功！所有捐赠勋章的排名已更新。');
            }).catch(function (e) {
                if (self.dom.btnReorderAll) {
                    self.dom.btnReorderAll.disabled = false;
                    self.dom.btnReorderAll.textContent = '重排捐赠勋章';
                }
                alert('重排失败：' + e);
            });
        }
    };

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', function () {
            AdminMedalPage.init();
        });
    } else {
        AdminMedalPage.init();
    }
})();
