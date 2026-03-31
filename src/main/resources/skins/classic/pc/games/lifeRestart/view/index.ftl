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
<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta http-equiv="X-UA-Compatible" content="IE=edge">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <meta name="description" content="やり直すんだ。そして、次はうまくやる。"/>
    <meta name="keywords" content="人生重开模拟器 liferestart life restart remake 人生重来"/>
    <link id="themeLink" rel="stylesheet" href="https://file.fishpi.cn/lifeRestart/view/light.css">
    <link rel="manifest" href="https://file.fishpi.cn/lifeRestart/view/manifest.json">
    <title>Life Restart</title>
</head>
<body>
  <div class="banners-container">
    <div class="banners">
      <div class="banner error">
        <div class="banner-icon"><span class="iconfont">&#xe6a1;</span></div>
        <pre class="banner-message">Oops! Something went wrong!</pre>
        <div class="banner-close" onclick="hideBanners()"><span class="iconfont">&#xe6a8;</span></div>
      </div>
      <div class="banner success">
        <div class="banner-icon"><span class="iconfont">&#xe6a2;</span></div>
        <pre class="banner-message">Everything was fine!</pre>
        <div class="banner-close" onclick="hideBanners()"><span class="iconfont">&#xe6a8;</span></div>
      </div>
      <div class="banner info">
        <div class="banner-icon"><span class="iconfont">&#xe6a3;</span></div>
        <pre class="banner-message">Here is some useful information</pre>
        <div class="banner-close" onclick="hideBanners()"><span class="iconfont">&#xe6a8;</span></div>
      </div>
    </div>
  </div>
  <script src="https://file.fishpi.cn/lifeRestart/lib/jquery-3.6.0.min.js"></script>
  <script src="https://file.fishpi.cn/lifeRestart/lib/dom-to-image.min.js"></script>
  <script src="https://file.fishpi.cn/bundle.js?v=100"></script>
  <script>
    if ('serviceWorker' in navigator) {
      window.addEventListener('load', function () {
        navigator.serviceWorker.register('./sw.js', {scope: '.'})
          .then(function (registration) {
            console.log('ServiceWorker registration successful');
          })
          .catch(function (err) {
            console.log('ServiceWorker registration failed');
          });
      });
    }
  </script>
  <script src="${staticServePath}/js/lib/reconnecting-websocket.min.js?${staticResourceVersion}"></script>
  <script src="${staticServePath}/js/symbol-defs${miniPostfix}.js?${staticResourceVersion}"></script>
  <script src="${staticServePath}/js/common${miniPostfix}.js?${staticResourceVersion}"></script>
  <script>
      var Label = {
          saveData: '',
          commentEditorPlaceholderLabel: '${commentEditorPlaceholderLabel}',
          langLabel: '${langLabel}',
          luteAvailable: ${luteAvailable?c},
          reportSuccLabel: '${reportSuccLabel}',
          breezemoonLabel: '${breezemoonLabel}',
          confirmRemoveLabel: "${confirmRemoveLabel}",
          reloginLabel: "${reloginLabel}",
          invalidPasswordLabel: "${invalidPasswordLabel}",
          loginNameErrorLabel: "${loginNameErrorLabel}",
          followLabel: "${followLabel}",
          unfollowLabel: "${unfollowLabel}",
          symphonyLabel: "${symphonyLabel}",
          visionLabel: "${visionLabel}",
          cmtLabel: "${cmtLabel}",
          collectLabel: "${collectLabel}",
          uncollectLabel: "${uncollectLabel}",
          desktopNotificationTemplateLabel: "${desktopNotificationTemplateLabel}",
          servePath: "${servePath}",
          staticServePath: "${staticServePath}",
          isLoggedIn: ${isLoggedIn?c},
          funNeedLoginLabel: '${funNeedLoginLabel}',
          notificationCommentedLabel: '${notificationCommentedLabel}',
          notificationReplyLabel: '${notificationReplyLabel}',
          notificationAtLabel: '${notificationAtLabel}',
          notificationFollowingLabel: '${notificationFollowingLabel}',
          pointLabel: '${pointLabel}',
          sameCityLabel: '${sameCityLabel}',
          systemLabel: '${systemLabel}',
          newFollowerLabel: '${newFollowerLabel}',
          makeAsReadLabel: '${makeAsReadLabel}',
          imgMaxSize: ${imgMaxSize?c},
          fileMaxSize: ${fileMaxSize?c},
          <#if isLoggedIn>
          currentUserName: '${currentUser.userName}',
          </#if>
          <#if csrfToken??>
          csrfToken: '${csrfToken}'
          </#if>
      }

      <#if isLoggedIn>
      Label.userKeyboardShortcutsStatus = '${currentUser.userKeyboardShortcutsStatus}'
      </#if>

      <#if isLoggedIn>
      // Init [User] channel
      Util.initUserChannel("${wsScheme}://${serverHost}:${serverPort}${contextPath}/user-channel")
      </#if>
  </script>
  <script>
      var _hmt = _hmt || [];
      (function() {
          var hm = document.createElement("script");
          hm.src = "https://hm.baidu.com/hm.js?bab35868f6940b3c4bfc020eac6fe61f";
          var s = document.getElementsByTagName("script")[0];
          s.parentNode.insertBefore(hm, s);
      })();
  </script>
</body>
</html>
