<#--

    Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
    Modified version from Symphony, Thanks Symphony :)
    Copyright (C) 2012-present, b3log.org

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.

-->
<#include "macro-admin.ftl">
<@admin "articles">
<div class="content">
    <div class="module">
        <div class="module-header">
            <h2>${unmodifiableLabel}</h2>
            <a class="fn__right" href="${servePath}${article.articlePermalink}">${permalinkLabel}</a>
        </div>
        <div class="module-panel form fn-clear form--admin">
            <div class="fn__flex">
                <label>
                    <div>Id</div>
                    <input onfocus="this.select()" type="text" id="oId" name="oId" value="${article.oId}"
                           readonly="readonly"/>
                </label>
                <label>
                    <div>${authorIdLabel}</div>
                    <input onfocus="this.select()" type="text" id="articleAuthorId" name="articleAuthorId"
                           value="${article.articleAuthorId}"
                           readonly="readonly"/>
                </label>
                <label>
                    <div>${commentCountLabel}</div>
                    <input onfocus="this.select()" type="text" id="articleCommentCount" name="articleCommentCount"
                           value="${article.articleCommentCount?c}" readonly="readonly"/>
                </label>
                <label>
                    <div>${viewCountLabel}</div>
                    <input onfocus="this.select()" type="text" id="articleViewCount" name="articleViewCount"
                           value="${article.articleViewCount?c}"
                           readonly="readonly"/>
                </label>
            </div>
            <div class="fn__flex">
                <label>
                    <div>${permalinkLabel}</div>
                    <input onfocus="this.select()" type="text" id="articlePermalink" readonly name="articlePermalink"
                           value="${servePath}${article.articlePermalink}"/>
                </label>
            </div>
            <div class="fn__flex">
                <label>
                    <div>${createTimeLabel}</div>
                    <input onfocus="this.select()" type="text" id="articleCreateTime" name="articleCreateTime"
                           readonly
                           value="${article.articleCreateTime?number_to_datetime}"/>
                </label>
                <label>
                    <div>${updateTimeLabel}</div>
                    <input onfocus="this.select()" type="text" id="articleUpdateTime" name="articleUpdateTime"
                           readonly
                           value="${article.articleUpdateTime?number_to_datetime}"/>
                </label>
            </div>
            <div class="fn__flex">
                <label>
                    <div>${eidotrTypeLabel}</div>
                    <input onfocus="this.select()" type="text" id="articleEditorType" name="articleEditorType"
                           value="<#if 0 == article.articleEditorType>Markdown<#else>${article.articleEditorType}</#if>"
                           readonly="readonly"/>
                </label>
                <label>
                    <div>IP</div>
                    <input onfocus="this.select()" type="text" id="articleIP" name="articleIP"
                           value="${article.articleIP}"
                           readonly="readonly"/>
                </label>
                <label>
                    <div>${stickLabel}</div>
                    <input onfocus="this.select()" type="text" id="articleStick" name="articleStick"
                           value="<#if 0 == article.articleStick>${noLabel}<#else>${yesLabel}</#if>"
                           readonly="readonly"/>
                </label>
                <label>
                    <div>${anonymousLabel}</div>
                    <input onfocus="this.select()" type="text" id="articleStick" name="articleStick"
                           value="<#if 0 == article.articleAnonymous>${noLabel}<#else>${yesLabel}</#if>"
                           readonly="readonly"/>
                </label>
            </div>
            <div class="fn__flex">
                <label>
                    <div>UA</div>
                    <input onfocus="this.select()" type="text" id="articleUA" name="articleUA"
                           value="${article.articleUA}"
                           readonly="readonly"/>
                </label>
            </div>
            <#if article.articleType?? && 6 == article.articleType>
            <div class="fn__flex">
                <label>
                    <div>专栏</div>
                    <input onfocus="this.select()" type="text" id="articleColumnTitle" name="articleColumnTitle"
                           value="${article.columnTitle!'-'}"
                           readonly="readonly"/>
                </label>
                <label>
                    <div>专栏ID</div>
                    <input onfocus="this.select()" type="text" id="articleColumnId" name="articleColumnId"
                           value="${article.columnId!'-'}"
                           readonly="readonly"/>
                </label>
                <label>
                    <div>章节号</div>
                    <input onfocus="this.select()" type="text" id="articleChapterNo" name="articleChapterNo"
                           value="${article.chapterNo!'-'}"
                           readonly="readonly"/>
                </label>
            </div>
            </#if>
        </div>
    </div>

    <#if permissions["articleUpdateArticleBasic"].permissionGrant>
    <div class="module">
        <div class="module-header">
            <h2>${modifiableLabel}</h2>
        </div>
        <div class="module-panel form fn-clear form--admin">
            <form action="${servePath}/admin/article/${article.oId}" method="POST">
                <div class="fn__flex">
                    <label>
                        <div>${titleLabel}</div>
                        <input type="text" id="articleTitle" name="articleTitle" value="${article.articleTitle}"/>
                    </label>
                </div>
                <div class="fn__flex">
                    <label>
                        <div>${tagLabel}</div>
                        <input type="text" id="articleTags" name="articleTags" value="${article.articleTags}"/>
                    </label>
                </div>
                <div class="fn__flex">
                    <label>
                        <div>${contentLabel}</div>
                        <textarea name="articleContent" rows="28">${article.articleContent}</textarea>
                    </label>
                </div>
                <div class="fn__flex">
                    <label>
                        <div>${rewardContentLabel}</div>
                        <textarea name="articleRewardContent">${article.articleRewardContent}</textarea>
                    </label>
                </div>
                <div class="fn__flex">
                    <label>
                        <div>${rewardPointLabel}</div>
                        <input type="text" id="articleRewardPoint" name="articleRewardPoint"
                               value="${article.articleRewardPoint?c}"/>
                    </label>
                    <label class="mid">
                        <div>${perfectLabel}</div>
                        <select id="articlePerfect" name="articlePerfect">
                            <option value="0"<#if 0 == article.articlePerfect> selected</#if>>${noLabel}</option>
                            <option value="1"<#if 1 == article.articlePerfect> selected</#if>>${yesLabel}</option>
                        </select>
                    </label>
                    <label>
                        <div>${commentableLabel}</div>
                        <select id="articleCommentable" name="articleCommentable">
                            <option value="true"<#if article.articleCommentable> selected</#if>>${yesLabel}</option>
                            <option value="false"<#if !article.articleCommentable> selected</#if>>${noLabel}</option>
                        </select>
                    </label>
                </div>
                <div class="fn__flex">
                    <label>
                        <div>${articleStatusLabel}</div>
                        <select id="articleStatus" name="articleStatus">
                            <option value="0"<#if 0 == article.articleStatus> selected</#if>>${validLabel}</option>
                            <option value="1"<#if 1 == article.articleStatus> selected</#if>>${banLabel}</option>
                        </select>
                    </label>
                    <label class="mid">
                        <div>${articleTypeLabel}</div>
                        <select id="articleType" name="articleType">
                            <option value="0"<#if 0 == article.articleType> selected</#if>>${articleLabel}</option>
                            <option value="1"<#if 1 == article.articleType> selected</#if>>${discussionLabel}</option>
                            <option value="2"<#if 2 == article.articleType> selected</#if>>${cityBroadcastLabel}</option>
                            <option value="3"<#if 3 == article.articleType> selected</#if>>${thoughtLabel}</option>
                            <option value="5"<#if 5 == article.articleType> selected</#if>>${qnaLabel}</option>
                            <option value="6"<#if 6 == article.articleType> selected</#if>>${longArticleTypeLabel}</option>
                        </select>
                    </label>
                    <label>
                        <div>${goodCntLabel}</div>
                        <input type="text" id="articleGoodCnt" name="articleGoodCnt" value="${article.articleGoodCnt}"/>
                    </label>
                </div>
                <div class="fn__flex" id="adminLongArticleColumnWrap"<#if 6 != article.articleType> style="display:none"</#if>>
                    <#assign adminLongColumns = longArticleColumns![]>
                    <#assign adminSelectedColumnId = article.columnId!"">
                    <#assign adminSelectedColumnTitle = article.columnTitle!"">
                    <#assign adminShowCreateColumnInput = !adminSelectedColumnId?has_content && adminSelectedColumnTitle?has_content>
                    <#assign adminHasSelectedColumn = false>
                    <#list adminLongColumns as longColumn>
                        <#if adminSelectedColumnId == longColumn.oId>
                            <#assign adminHasSelectedColumn = true>
                        </#if>
                    </#list>
                    <label>
                        <div>专栏归属</div>
                        <select id="adminLongArticleColumnId" name="columnId">
                            <option value="">不归属专栏（下架专栏）</option>
                            <#if adminSelectedColumnId?has_content && !adminHasSelectedColumn>
                                <option value="${adminSelectedColumnId}" selected>${article.columnTitle!adminSelectedColumnId}（当前专栏）</option>
                            </#if>
                            <#list adminLongColumns as longColumn>
                                <option value="${longColumn.oId}"<#if adminSelectedColumnId == longColumn.oId> selected</#if>>
                                    ${longColumn.columnTitle}<#if longColumn.columnArticleCount??>（${longColumn.columnArticleCount} 章）</#if>
                                </option>
                            </#list>
                            <option value="__NEW__"<#if adminShowCreateColumnInput> selected</#if>>+ 新建专栏</option>
                        </select>
                    </label>
                    <label class="mid" id="adminLongArticleColumnTitleWrap"<#if !adminShowCreateColumnInput> style="display:none"</#if>>
                        <div>新专栏名称</div>
                        <input type="text" id="adminLongArticleColumnTitle" name="columnTitle" maxlength="64"
                               value="${adminSelectedColumnTitle}" placeholder="请输入新专栏名称"/>
                    </label>
                    <label id="adminLongArticleChapterNoWrap"<#if !adminSelectedColumnId?has_content && !adminShowCreateColumnInput> style="display:none"</#if>>
                        <div>章节号</div>
                        <input type="number" min="1" id="adminLongArticleChapterNo" name="chapterNo"
                               <#if !adminSelectedColumnId?has_content && !adminShowCreateColumnInput>disabled</#if>
                               value="<#if article.chapterNo??>${article.chapterNo?c}</#if>" placeholder="留空自动排在专栏末尾"/>
                    </label>
                </div>
                <div class="fn__flex" id="adminLongArticleColumnTipWrap"<#if 6 != article.articleType> style="display:none"</#if>>
                    <div class="ft-smaller ft-gray" style="margin-top:4px;line-height:1.6;">
                        管理员可为该长文新建专栏、切换专栏，或选择“不归属专栏”进行下架。
                    </div>
                </div>

                <div class="fn__flex">
                    <label>
                        <div>${badCntLabel}</div>
                        <input type="text" id="articleBadCnt" name="articleBadCnt" value="${article.articleBadCnt}"/>
                    </label>
                    <label class="mid">
                        <div>${miscAllowAnonymousViewLabel}</div>
                        <select name="articleAnonymousView">
                            <option value="0"<#if 0 == article.articleAnonymousView>
                                    selected</#if>>${useGlobalLabel}</option>
                            <option value="1"<#if 1 == article.articleAnonymousView> selected</#if>>${noLabel}</option>
                            <option value="2"<#if 2 == article.articleAnonymousView> selected</#if>>${yesLabel}</option>
                        </select>
                    </label>
                    <label>
                        <div>${pushLabel} Email ${pushLabel}</div>
                        <input type="number" name="articlePushOrder" value="${article.articlePushOrder}" />
                    </label>
                </div>
                <div class="fn__flex">
                    <label>
                        <div>${qnaOfferPointLabel}</div>
                        <input type="text" name="articleQnAOfferPoint" value="${article.articleQnAOfferPoint?c}"/>
                    </label>
                    <label class="mid">
                        <div>${showInListLabel}</div>
                        <select id="articleShowInList" name="articleShowInList">
                            <option value="1"<#if 1==article.articleShowInList> selected</#if>>${yesLabel}</option>
                            <option value="0"<#if 0==article.articleShowInList> selected</#if>>${noLabel}</option>
                        </select>
                    </label>
                    <label class="mid">
                        <div>${statementLabel}</div>
                        <select id="articleStatement" name="articleStatement">
                            <option value="0" <#if 0 == article.articleStatement>selected</#if>>${statementNoneLabel}</option>
                            <option value="1" <#if 1 == article.articleStatement>selected</#if>>${statementAILabel}</option>
                            <option value="2" <#if 2 == article.articleStatement>selected</#if>>${statementSpoilersLabel}</option>
                            <option value="3" <#if 3 == article.articleStatement>selected</#if>>${statementImaginaryLabel}</option>
                        </select>
                    </label>
                </div>
                <br/>
                <button type="submit" class="green fn-right">${submitLabel}</button>
            </form>
        </div>
    </div>
    </#if>

    <#if permissions["articleStickArticle"].permissionGrant>
    <div class="module">
        <div class="module-header">
            <h2>${stickLabel}</h2>
        </div>
        <div class="module-panel form fn-clear form--admin">
            <form action="${servePath}/admin/stick-article" method="POST" class="fn__flex">
                <label>
                    <div>Id</div>
                    <input type="text" id="articleId" name="articleId" value="${article.oId}" readonly class="input--admin-readonly"/>
                </label>
                <div>
                    &nbsp; &nbsp;
                    <button type="submit" class="green fn-right btn--admin">${submitLabel}</button>
                </div>
            </form>
        </div>
    </div>
    </#if>

    <#if permissions["articleCancelStickArticle"].permissionGrant>
    <div class="module">
        <div class="module-header">
            <h2>${cancelStickLabel}</h2>
        </div>
        <div class="module-panel form fn-clear form--admin">
            <form action="${servePath}/admin/cancel-stick-article" method="POST" class="fn__flex">
                <label>
                    <div>Id</div>
                    <input type="text" id="articleId" name="articleId" value="${article.oId}" readonly class="input--admin-readonly"/>
                </label>
                <div>
                    &nbsp; &nbsp;
                    <button type="submit" class="green fn-right btn--admin">${submitLabel}</button>
                </div>
            </form>
        </div>
    </div>
    </#if>

    <#if (esEnabled || algoliaEnabled) && permissions["articleReindexArticle"].permissionGrant>
    <div class="module">
        <div class="module-header">
            <h2>${searchIndexLabel}</h2>
        </div>
        <div class="module-panel form fn-clear form--admin">
            <form action="${servePath}/admin/search-index-article" method="POST" class="fn__flex">
                <label>
                    <div>Id</div>
                    <input type="text" id="articleId" name="articleId" value="${article.oId}" readonly class="input--admin-readonly"/>
                </label>
                <div>
                    &nbsp; &nbsp;
                    <button type="submit" class="green fn-right btn--admin">${submitLabel}</button>
                </div>
            </form>
        </div>
    </div>
    </#if>

    <#if permissions["articleRemoveArticle"].permissionGrant>
    <div class="module">
        <div class="module-header">
            <h2 class="ft-red">${removeDataLabel}</h2>
        </div>
        <div class="module-panel form fn-clear form--admin">
            <form action="${servePath}/admin/remove-article" method="POST"
                  class="fn__flex"
                  onsubmit="return window.confirm('${confirmRemoveLabel}')">
                <label>
                    <div>Id</div>
                    <input type="text" id="articleId" name="articleId" value="${article.oId}" readonly class="input--admin-readonly"/>
                </label>
                <div>
                    &nbsp; &nbsp;
                    <button type="submit" class="red fn-right btn--admin">${submitLabel}</button>
                </div>
            </form>
        </div>
    </div>
    </#if>
</div>
<script>
(function () {
    var articleTypeEl = document.getElementById('articleType');
    var columnWrap = document.getElementById('adminLongArticleColumnWrap');
    var columnTipWrap = document.getElementById('adminLongArticleColumnTipWrap');
    var columnSelect = document.getElementById('adminLongArticleColumnId');
    var columnTitleWrap = document.getElementById('adminLongArticleColumnTitleWrap');
    var columnTitleInput = document.getElementById('adminLongArticleColumnTitle');
    var chapterWrap = document.getElementById('adminLongArticleChapterNoWrap');
    var chapterInput = document.getElementById('adminLongArticleChapterNo');

    if (!articleTypeEl || !columnSelect || !columnWrap || !columnTitleWrap || !chapterWrap) {
        return;
    }

    var toggleColumnForm = function () {
        var selectedColumnId = columnSelect.value || '';
        var isCreate = selectedColumnId === '__NEW__';
        var isBound = selectedColumnId !== '';

        if (isCreate) {
            columnTitleWrap.style.display = '';
            if (columnTitleInput) {
                columnTitleInput.disabled = false;
            }
        } else {
            columnTitleWrap.style.display = 'none';
            if (columnTitleInput) {
                columnTitleInput.disabled = true;
            }
        }

        if (isBound) {
            chapterWrap.style.display = '';
            if (chapterInput) {
                chapterInput.disabled = false;
            }
        } else {
            chapterWrap.style.display = 'none';
            if (chapterInput) {
                chapterInput.disabled = true;
                chapterInput.value = '';
            }
            if (columnTitleInput) {
                columnTitleInput.value = '';
            }
        }
    };

    var toggleByType = function () {
        var isLongArticle = articleTypeEl.value === '6';
        columnWrap.style.display = isLongArticle ? '' : 'none';
        if (columnTipWrap) {
            columnTipWrap.style.display = isLongArticle ? '' : 'none';
        }

        if (!isLongArticle) {
            columnSelect.value = '';
            if (columnTitleInput) {
                columnTitleInput.value = '';
            }
            if (chapterInput) {
                chapterInput.value = '';
            }
        }

        toggleColumnForm();
    };

    columnSelect.addEventListener('change', toggleColumnForm);
    articleTypeEl.addEventListener('change', toggleByType);
    toggleByType();
})();
</script>
</@admin>
