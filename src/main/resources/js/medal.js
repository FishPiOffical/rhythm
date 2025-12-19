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

    var AdminMedalPage = {
        page: 1,
        pageSize: 20,
        medals: [],
        ownersPage: 1,
        ownersPageSize: 20,
        ownersTotal: 0,
        currentOwnersMedalId: null,

        init: function () {
            var root = document.getElementById('medalAdminRoot');
            if (!root) {
                return;
            }

            this.cacheDom();
            this.bindEvents();
            this.loadMedals();
        },

        cacheDom: function () {
            this.dom = {};
            this.dom.tableBody = document.getElementById('medalTableBody');
            this.dom.mobileList = document.getElementById('medalMobileList');
            this.dom.pageLabel = document.getElementById('medalPage');

            this.dom.btnPrev = document.getElementById('medalPrevPage');
            this.dom.btnNext = document.getElementById('medalNextPage');
            this.dom.btnCreate = document.getElementById('btnCreateMedal');

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
        },

        bindEvents: function () {
            var self = this;

            if (this.dom.btnCreate) {
                this.dom.btnCreate.addEventListener('click', function () {
                    self.openCreateModal();
                });
            }

            if (this.dom.btnPrev) {
                this.dom.btnPrev.addEventListener('click', function () {
                    if (self.page > 1) {
                        self.page -= 1;
                        self.loadMedals();
                    }
                });
            }
            if (this.dom.btnNext) {
                this.dom.btnNext.addEventListener('click', function () {
                    self.page += 1;
                    self.loadMedals();
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
        },

        apiPayloadBase: function () {
            return {
                apiKey: (typeof window.apiKey !== 'undefined' ? window.apiKey : null)
            };
        },

        loadMedals: function () {
            var self = this;
            var body = this.apiPayloadBase();
            body.page = this.page;
            body.pageSize = this.pageSize;

            postJSON('/api/medal/admin/list', body).then(function (ret) {
                if (!ret || ret.code !== 0) {
                    alert('加载勋章失败：' + (ret ? ret.msg : '未知错误'));
                    return;
                }
                self.medals = ret.data || [];
                self.renderMedals();
            }).catch(function (e) {
                alert('加载勋章失败：' + e);
            });
        },

        renderMedals: function () {
            var self = this;

            // desktop table
            if (this.dom.tableBody) {
                if (!this.medals || this.medals.length === 0) {
                    this.dom.tableBody.innerHTML =
                        '<tr><td colspan="6" class="medal-table__empty">暂无勋章</td></tr>';
                } else {
                    var rows = this.medals.map(function (m) {
                        var desc = (m.medal_description || '').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                        var attr = (m.medal_attr || '').replace(/</g, '&lt;').replace(/>/g, '&gt;');
                        var id = m.medal_id || '';
                        var previewSrc = (window.Label && Label.servePath ? Label.servePath : '') + '/gen?id=' + encodeURIComponent(id);
                        return '' +
                            '<tr data-medal-id="' + id + '">' +
                            '<td>' + id + '</td>' +
                            '<td>' + (m.medal_name || '') + '</td>' +
                            '<td>' + (m.medal_type || '') + '</td>' +
                            '<td>' + desc + '</td>' +
                            '<td><pre style="white-space:pre-wrap;max-width:200px;">' + attr + '</pre></td>' +
                            '<td><img src="' + previewSrc + '" alt="预览" style="max-width:60px;max-height:60px;border-radius:4px;"/></td>' +
                            '<td class="medal-table__actions">' +
                            '<button type="button" class="btn btn-primary" data-action="edit">编辑</button>' +
                            '<button type="button" class="btn btn-secondary" data-action="owners">拥有者</button>' +
                            '<button type="button" class="btn btn-secondary" data-action="delete">删除</button>' +
                            '</td>' +
                            '</tr>';
                    }).join('');
                    this.dom.tableBody.innerHTML = rows;

                    Array.prototype.forEach.call(this.dom.tableBody.querySelectorAll('button[data-action]'), function (btn) {
                        btn.addEventListener('click', function () {
                            var tr = btn.closest('tr');
                            var medalId = tr && tr.getAttribute('data-medal-id');
                            var action = btn.getAttribute('data-action');
                            if (!medalId) {
                                return;
                            }
                            if (action === 'edit') {
                                self.openEditModal(medalId);
                            } else if (action === 'delete') {
                                self.confirmDeleteMedal(medalId);
                            } else if (action === 'owners') {
                                self.openOwnersModal(medalId);
                            }
                        });
                    });
                }
            }

            // mobile list
            if (this.dom.mobileList) {
                if (!this.medals || this.medals.length === 0) {
                    this.dom.mobileList.innerHTML =
                        '<div class="medal-admin__list-empty">暂无勋章</div>';
                } else {
                    var items = this.medals.map(function (m) {
                        var desc = (m.medal_description || '');
                        var id = m.medal_id || '';
                        var previewSrc = (window.Label && Label.servePath ? Label.servePath : '') + '/gen?id=' + encodeURIComponent(id);
                        return '' +
                            '<div class="medal-admin__list-item" data-medal-id="' + id + '">' +
                            '<div class="medal-admin__list-header">' +
                                '<img class="medal-admin__thumb" src="' + previewSrc + '" alt="预览"/>' +
                                '<div class="medal-admin__list-title">' + (m.medal_name || '') + '（ID: ' + id + '）</div>' +
                            '</div>' +
                            '<div class="medal-admin__list-meta">类型：' + (m.medal_type || '') + '</div>' +
                            '<div class="medal-admin__list-desc">' + desc + '</div>' +
                            '<div class="medal-admin__list-actions">' +
                            '<button type="button" class="btn btn-primary" data-action="edit">编辑</button>' +
                            '<button type="button" class="btn btn-secondary" data-action="owners">拥有者</button>' +
                            '<button type="button" class="btn btn-secondary" data-action="delete">删除</button>' +
                            '</div>' +
                            '</div>';
                    }).join('');
                    this.dom.mobileList.innerHTML = items;

                    Array.prototype.forEach.call(this.dom.mobileList.querySelectorAll('button[data-action]'), function (btn) {
                        btn.addEventListener('click', function () {
                            var item = btn.closest('.medal-admin__list-item');
                            var medalId = item && item.getAttribute('data-medal-id');
                            var action = btn.getAttribute('data-action');
                            if (!medalId) {
                                return;
                            }
                            if (action === 'edit') {
                                self.openEditModal(medalId);
                            } else if (action === 'delete') {
                                self.confirmDeleteMedal(medalId);
                            } else if (action === 'owners') {
                                self.openOwnersModal(medalId);
                            }
                        });
                    });
                }
            }

            if (this.dom.pageLabel) {
                this.dom.pageLabel.textContent = String(this.page);
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
            if (!window.confirm('确认删除勋章 ' + medalId + ' 吗？此操作不可撤销。')) {
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
                self.loadMedals();
            }).catch(function (e) {
                alert('删除失败：' + e);
            });
        },

        openOwnersModal: function (medalId) {
            if (!this.dom.modalOwners) {
                return;
            }
            this.currentOwnersMedalId = medalId;
            this.ownersPage = 1;
            this.dom.ownersMedalIdLabel.textContent = String(medalId);
            this.loadMedalOwners(medalId);
            this.openModal(this.dom.modalOwners);
        },

        loadMedalOwners: function (medalId) {
            var self = this;
            var body = this.apiPayloadBase();
            body.medalId = medalId;
            body.page = this.ownersPage;
            body.pageSize = this.ownersPageSize;

            postJSON('/api/medal/admin/owners', body).then(function (ret) {
                if (!ret || ret.code !== 0) {
                    alert('加载拥有者失败：' + (ret ? ret.msg : '未知错误'));
                    return;
                }
                var data = ret.data || {};
                var list = data.items || []; // MedalProcessor.adminGetMedalOwners 返回 items
                self.ownersTotal = data.total || 0;
                self.renderMedalOwners(list);
            }).catch(function (e) {
                alert('加载拥有者失败：' + e);
            });
        },

        renderMedalOwners: function (owners) {
            var self = this;
            if (!this.dom.ownersTableBody) {
                return;
            }
            if (!owners || owners.length === 0) {
                this.dom.ownersTableBody.innerHTML =
                    '<tr><td colspan="6" class="medal-table__empty">暂无拥有者</td></tr>';
            } else {
                var rows = owners.map(function (o) {
                    var userId = o.user_id || '';
                    var userName = o.userName || '';
                    var medalId = o.medal_id || '';
                    var expireTime = o.expire_time;
                    var data = o.data || '';
                    return '' +
                        '<tr data-user-id="' + userId + '" data-medal-id="' + medalId + '">' +
                        '<td>' + userId + '</td>' +
                        '<td>' + userName + '</td>' +
                        '<td>' + formatTimestamp(expireTime) + '</td>' +
                        '<td>' + (o.display === false ? '否' : '是') + '</td>' +
                        '<td><pre style="white-space:pre-wrap;max-width:200px;">' + data + '</pre></td>' +
                        '<td>' +
                        '<button type="button" class="btn btn-secondary" data-action="revoke">收回</button>' +
                        '</td>' +
                        '</tr>';
                }).join('');
                this.dom.ownersTableBody.innerHTML = rows;

                Array.prototype.forEach.call(
                    this.dom.ownersTableBody.querySelectorAll('button[data-action="revoke"]'),
                    function (btn) {
                        btn.addEventListener('click', function () {
                            var tr = btn.closest('tr');
                            if (!tr) {
                                return;
                            }
                            var userId = tr.getAttribute('data-user-id');
                            var medalId = tr.getAttribute('data-medal-id');
                            if (!userId || !medalId) {
                                return;
                            }
                            self.revokeMedalFromUser(userId, medalId);
                        });
                    }
                );
            }

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
                alert('用户ID不能为空');
                return;
            }

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
                self.loadMedalOwners(self.currentOwnersMedalId);
            }).catch(function (e) {
                alert('发放失败：' + e);
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
                self.loadMedalOwners(self.currentOwnersMedalId);
            }).catch(function (e) {
                alert('收回失败：' + e);
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