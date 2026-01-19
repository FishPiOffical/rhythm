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
<#include "macro-head.ftl">
<!DOCTYPE html>
<html>
<head>
    <@head title="${submitMilestoneLabel} - ${symphonyLabel}"></@head>
    <style>
        .form-group {
            margin-bottom: 20px;
        }
        .form-label {
            display: block;
            margin-bottom: 8px;
            font-weight: 600;
            color: #333;
            font-size: 14px;
        }
        .form-control {
            width: 100%;
            padding: 10px 12px;
            border: 1px solid #ddd;
            border-radius: 4px;
            font-size: 14px;
            box-sizing: border-box;
        }
        .form-control:focus {
            outline: none;
            border-color: #667eea;
            box-shadow: 0 0 0 3px rgba(102, 126, 234, 0.1);
        }
        textarea.form-control {
            min-height: 120px;
            resize: vertical;
        }

        .help-text {
            font-size: 12px;
            color: #999;
            margin-top: 4px;
        }
    </style>
</head>
<body>
    <#include "header.ftl">
    <div class="main">
        <div class="wrapper">
            <div class="content">
                <div class="module">
                    <h2 style="margin: 10px 0px">提交大事记</h2>
                    <div class="module-panel">
                        <form id="milestoneForm" onsubmit="return false;">
                            <div class="form-group">
                                <label class="form-label">标题 *</label>
                                <input type="text" class="form-control" id="title" name="title" required placeholder="请输入大事记标题">
                            </div>

                            <div class="form-group">
                                <label class="form-label">日期 *</label>
                                <div style="display: flex; gap: 10px;">
                                    <div style="flex: 1;">
                                        <input type="date" class="form-control" id="milestoneDate" name="milestoneDate" required>
                                        <div class="help-text" style="font-size: 12px; color: #999; margin-top: 4px;">开始日期</div>
                                    </div>
                                    <div style="flex: 1;">
                                        <input type="date" class="form-control" id="milestoneEndDate" name="milestoneEndDate">
                                        <div class="help-text" style="font-size: 12px; color: #999; margin-top: 4px;">结束日期（可选，用于时间段）</div>
                                    </div>
                                </div>
                            </div>

                            <div class="form-group">
                                <label class="form-label">内容 *</label>
                                <textarea class="form-control" id="content" name="content" required placeholder="请输入大事记详细内容"></textarea>
                            </div>

                            <div class="form-group">
                                <label class="form-label">媒体标题（可选）</label>
                                <input type="text" class="form-control" id="mediaCaption" name="mediaCaption" placeholder="请输入媒体标题">
                            </div>

                            <div class="form-group">
                                <label class="form-label">媒体链接（可选）</label>
                                <input type="text" class="form-control" id="mediaUrl" name="mediaUrl" placeholder="图片或视频URL">
                                <div class="help-text" style="font-size: 12px; color: #999; margin-top: 4px;">支持图片和视频链接</div>
                            </div>

                            <div class="form-group">
                                <label class="form-label">媒体类型</label>
                                <select class="form-control" id="mediaType" name="mediaType">
                                    <option value="image">图片</option>
                                    <option value="video">视频</option>
                                </select>
                            </div>

                            <div class="form-group">
                                <label class="form-label">相关链接（可选）</label>
                                <input type="text" class="form-control" id="milestoneLink" name="milestoneLink" placeholder="相关文章或页面URL">
                            </div>

                            <button type="submit" class="green" onclick="submitMilestone()">提交审核</button>
                        </form>
                    </div>
                </div>
            </div>
        </div>
    </div>
    <#include "footer.ftl">
    <script src="${staticServePath}/js/lib/jquery/jquery.min.js"></script>
    <script>
        function submitMilestone() {

            const title = document.getElementById('title').value.trim();
            const date = document.getElementById('milestoneDate').value;
            const endDate = document.getElementById('milestoneEndDate').value;
            const content = document.getElementById('content').value.trim();
            const mediaUrl = document.getElementById('mediaUrl').value.trim();
            const mediaCaption = document.getElementById('mediaCaption').value.trim();
            const mediaType = document.getElementById('mediaType').value;
            const link = document.getElementById('milestoneLink').value.trim();

            console.log(title,date,content)
            if (!title || !date || !content) {
                alert('请填写必填项');
                return;
            }

            const params = new URLSearchParams();
            params.append('title', title);
            params.append('date', date);
            params.append('endDate', endDate || '');
            params.append('content', content);
            params.append('mediaUrl', mediaUrl);
            params.append('mediaType', mediaType);
            params.append('mediaCaption', mediaCaption);
            params.append('link', link);

            fetch('${servePath}/milestones/submit', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded',
                },
                body: params.toString()
            })
            .then(response => response.json())
            .then(data => {
                if (data.code === 0) {
                    alert(data.msg);
                    window.location.href = '${servePath}/milestones';
                } else {
                    alert(data.msg || '提交失败');
                }
            })
            .catch(error => {
                alert('提交失败，请稍后重试');
                console.error('Error:', error);
            });
        }
    </script>
</body>
</html>
