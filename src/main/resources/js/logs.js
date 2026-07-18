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
var Logs = {
    init: function () {
        //Logs.connectWS();
        $("#logCategorySelect").on("change", function () {
            Logs.filter($(this).val());
        });
        $("#clearLogFilter").on("click", function () {
            Logs.reset();
        });
        $("#loadMoreBtn").on("click", function () {
            Logs.more();
        });
        $("#logsContent").on("click", ".logs-category", function () {
            Logs.filter($(this).attr("data-category"));
        });
        Logs.more();
    },

    connectWS: function () {
        // 连接WS
        Logs.ws = new ReconnectingWebSocket(logsChannelURL);
        Logs.ws.onopen = function () {
            console.log("Connected to logs channel websocket.");
        };
        // 发心跳包
        setInterval(function () {
            //Logs.ws.send('p');
        }, 1000 * 30)
        Logs.ws.onmessage = function (evt) {
            var data = JSON.parse(evt.data);
            switch (data.type) {
                case "simple":
                    Logs.prependLog(data.key1, data.key2, data.key3, data.data);
                    break;
            }
        };
        Logs.ws.onclose = function () {
            console.log("Disconnected to logs channel websocket.");
        };
        Logs.ws.onerror = function (err) {
            console.log("ERROR", err);
        };
    },

    appendLog: function (key1, key2, key3, data) {
        let result = Logs.sumResult(key1, key2, key3, data);
        $("#logsContent").append(result);
    },

    prependLog: function (key1, key2, key3, data) {
        Logs.addCategory(key3);
        if (Logs.category && Logs.category !== key3) {
            return;
        }
        let result = Logs.sumResult(key1, key2, key3, data);
        $("#logsContent").prepend(result);
    },

    escapeHTML: function (text) {
        return String(text ?? "").replace(/&/g, "&amp;")
            .replace(/</g, "&lt;")
            .replace(/>/g, "&gt;")
            .replace(/"/g, "&quot;")
            .replace(/'/g, "&#39;");
    },

    sumResult: function (key1, key2, key3, data) {
        let categoryClass = "logs-category--default";
        if (key3 === "增加积分") {
            categoryClass = "logs-category--add";
        } else if (key3 === "扣除积分") {
            categoryClass = "logs-category--reduce";
        } else if (key3 === "发送弹幕") {
            categoryClass = "logs-category--post";
        }

        return '<article class="logs-item">' +
            '<div class="logs-item__meta">' +
            '<span>' + Logs.escapeHTML(key1) + '</span>' +
            '<span>' + Logs.escapeHTML(key2) + '</span>' +
            '</div>' +
            '<div class="logs-item__body">' +
            '<button type="button" class="logs-category ' + categoryClass + '" data-category="' +
            Logs.escapeHTML(key3) + '">' + Logs.escapeHTML(key3) + '</button>' +
            '<div class="logs-item__detail">' + (data ?? "") + '</div>' +
            '</div>' +
            '</article>';
    },

    page: 1,
    pageSize: 30,
    category: "",
    loading: false,
    hasMore: true,
    loadFailed: false,
    categories: {},

    addCategory: function (category) {
        if (!category || Logs.categories[category]) {
            return;
        }
        Logs.categories[category] = true;
        $("#logCategorySelect").append(new Option(category, category));
    },

    setControlsDisabled: function (disabled) {
        $("#logCategorySelect, #clearLogFilter").prop("disabled", disabled);
    },

    updateFilter: function () {
        Logs.addCategory(Logs.category);
        $("#logCategorySelect").val(Logs.category);
        $("#clearLogFilter").prop("hidden", !Logs.category);
    },

    updateLoadMore: function () {
        const $button = $("#loadMoreBtn");
        if (Logs.loading) {
            $button.show().prop("disabled", true).text("加载中");
        } else if (Logs.loadFailed) {
            $button.show().prop("disabled", false).text("重试");
        } else if (!Logs.hasMore) {
            $button.hide();
        } else {
            $button.show().prop("disabled", false).text("加载更多");
        }
    },

    more: function () {
        if (Logs.loading || !Logs.hasMore) {
            return;
        }
        const requestedPage = Logs.page;
        Logs.loading = true;
        Logs.loadFailed = false;
        Logs.setControlsDisabled(true);
        Logs.updateLoadMore();
        $.ajax({
            url: Label.servePath + "/logs/more",
            data: {
                page: requestedPage,
                pageSize: Logs.pageSize,
                key3: Logs.category
            },
            type: "GET",
            success: function (result) {
                if (0 !== result.code || !Array.isArray(result.data)) {
                    Logs.loadFailed = true;
                    return;
                }
                const data = result.data;
                if (requestedPage === 1) {
                    $("#logsContent").empty();
                }
                data.forEach(function (log) {
                    Logs.addCategory(log.key3);
                    Logs.appendLog(log.key1, log.key2, log.key3, log.data);
                });
                if (requestedPage === 1 && data.length === 0) {
                    $("#logsContent").html('<div class="logs-empty">暂无日志</div>');
                }
                Logs.page = requestedPage + 1;
                Logs.hasMore = data.length === Logs.pageSize;
            },
            error: function () {
                Logs.loadFailed = true;
            },
            complete: function () {
                Logs.loading = false;
                Logs.setControlsDisabled(false);
                Logs.updateFilter();
                Logs.updateLoadMore();
            },
        });
    },

    filter: function (category) {
        if (Logs.loading || category === Logs.category) {
            return;
        }
        Logs.category = category || "";
        Logs.page = 1;
        Logs.hasMore = true;
        Logs.loadFailed = false;
        Logs.updateFilter();
        Logs.more();
    },

    reset: function () {
        Logs.filter("");
    }
};

$(document).ready(function () {
    Logs.init();
});
