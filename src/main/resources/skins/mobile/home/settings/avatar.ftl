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
<#include "macro-settings.ftl">
<@home "avatar">
    <div class="module">
        <link rel="stylesheet" href="${staticServePath}/js/lib/cropper/index.css">
        <link rel="stylesheet" href="${staticServePath}/js/lib/cropper/cropper.min.css">
        <div class="module-header fn-clear">
            <h2>自由裁剪</h2>
        </div>
        <div class="userInfo_box_uploadImg_content_top">
            <input id="inputImage" type="file" name="photoFile">
            <label class="userInfo_box_uploadImg_content_top_input curp" for="inputImage">选择头像</label>
            <label class="userInfo_box_uploadImg_content_top_message">${updateAvatarTipLabel}</label>
        </div>
        <div class="userInfo_box_uploadImg_content_mian" style="width: 100%">
            <div class="userInfo_box_uploadImg_content_mian_left fl">
                <div class="userInfo_box_uploadImg_content_mian_left_imgBox">
                    <script>
                        var originalImageURL = '${currentUser.userAvatarURL}?${currentUser.userUpdateTime?c}';
                    </script>
                    <img src="" id="image">
                </div>
                <div class="userInfo_box_uploadImg_content_mian_left_btns_zooms">
                    <label id="userImg_zoomOut" class="fl curp">-</label>
                    <label id="userImg_zoomIn" class="fr curp">+</label>
                </div>
                <div class="userInfo_box_uploadImg_content_mian_left_btns_others">
                    <label id="userImg_save" class="fl curp">确&nbsp;&nbsp;定</label>
                </div>
            </div>
        </div>
        <div class="fn-clear ft-center"><br/>
            <div class="userInfo_box_uploadImg_content_mian_right_preview100">
                <div class="userImg_preview"></div>
            </div>
            <label>100*100px</label>
            <div class="userInfo_box_uploadImg_content_mian_right_preview50">
                <div class="userImg_preview"></div>
            </div>
            <label>50*50px</label>
            <div class="userInfo_box_uploadImg_content_mian_right_preview30">
                <div class="userImg_preview"></div>
            </div>
            <label>30*30px</label>
        </div>

        <div class="module-header fn-clear" style="margin-top: 50px">
            <h2>直接上传头像（支持 GIF 动图）</h2>
        </div>
        <div class="module-panel form">
            <div class="fn-clear ft-center"><br/>
                <div class="avatar-big" id="avatarURL" data-imageurl="${currentUser.userAvatarURL}"
                     onclick="$('#avatarUpload input').click()"
                     style="background-image:url('${currentUser.userAvatarURL}?${currentUser.userUpdateTime?c}')"></div>
                &nbsp; &nbsp;
                <br/> <br/>
                <div class="avatar" id="avatarURLMid" data-imageurl="${currentUser.userAvatarURL}"
                     onclick="$('#avatarUpload input').click()"
                     style="background-image:url('${currentUser.userAvatarURL}?${currentUser.userUpdateTime?c}')"></div>
                &nbsp; &nbsp;
                <div class="avatar-small" id="avatarURLNor" data-imageurl="${currentUser.userAvatarURL}"
                     onclick="$('#avatarUpload input').click()"
                     style="background-image:url('${currentUser.userAvatarURL}?${currentUser.userUpdateTime?c}')"></div>
            </div>
            <br/>
            <div class="fn-clear">
                <form class="fn-right" id="avatarUpload" method="POST" enctype="multipart/form-data">
                    <label class="btn green">
                        ${uploadLabel}<input type="file" name="file">
                    </label>
                </form>
            </div>
        </div>
    </div>
</@home>
<script src="${staticServePath}/js/lib/jquery/file-upload/jquery.fileupload.min.js"></script>
<script src="${staticServePath}/js/lib/cropper/cropper.min.js"></script>
<script>
    var URL = window.URL || window.webkitURL;
    var $image = $('#image');
    var $rotate = $('#userImg_rotate');
    var $reUpload = $('#userImg_reUpload');
    var $zoomOut = $('#userImg_zoomOut');
    var $zoomIn = $('#userImg_zoomIn');
    var $save = $('#userImg_save');
    var croppable = false;
    var $previews = $('.userImg_preview');
    var options = {
        aspectRatio: 1,
        viewMode: 1,
        built: function () {
            croppable = true;
        },
        build: function (e) {
            var $clone = $(this).clone();

            $clone.css({
                display: 'block',
                width: '100%',
                minWidth: 0,
                minHeight: 0,
                maxWidth: 'none',
                maxHeight: 'none'
            });

            $previews.css({
                width: '100%',
                overflow: 'hidden'
            }).html($clone);
        },
        crop: function (e) {
            var imageData = $(this).cropper('getImageData');
            var previewAspectRatio = e.width / e.height;

            $previews.each(function () {
                var $preview = $(this);
                var previewWidth = $preview.width();
                var previewHeight = previewWidth / previewAspectRatio;
                var imageScaledRatio = e.width / previewWidth;

                $preview.height(previewHeight).find('img').css({
                    width: imageData.naturalWidth / imageScaledRatio,
                    height: imageData.naturalHeight / imageScaledRatio,
                    marginLeft: -e.x / imageScaledRatio,
                    marginTop: -e.y / imageScaledRatio
                });
            });
        }
    };
</script>
<script src="${staticServePath}/js/lib/cropper/index.js"></script>
<script>
    Settings.initUploadAvatar({
        id: 'avatarUpload',
        userId: '${currentUser.oId}',
        maxSize: '${imgMaxSize?c}'
    }, function (data) {
        updateAvatarByData(data);
    });

    function updateAvatarByData(data) {
        var uploadKey = data.result.key;
        $('#avatarURL').css("background-image", 'url(' + uploadKey + ')').data('imageurl', uploadKey);
        $('#avatarURLMid').css("background-image", 'url(' + uploadKey + ')').data('imageurl', uploadKey);
        $('#avatarURLNor').css("background-image", 'url(' + uploadKey + ')').data('imageurl', uploadKey);
        originalImageURL = uploadKey;
        $('#image').cropper('destroy').attr('src', originalImageURL).cropper(options);

        Settings.updateAvatar('${csrfToken}');
    }
</script>
