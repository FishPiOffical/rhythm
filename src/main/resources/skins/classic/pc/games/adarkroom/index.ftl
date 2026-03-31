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
<html itemscope itemtype="https://schema.org/CreativeWork">
<head>
	<meta charset="UTF-8"/>
	<!--
		A Dark Room (v1.4)
		==================

		A minimalist text adventure by Michael Townsend and all his friends.
		Inspired by Candy Box (http://candies.aniwey.net/)
		Contribute on GitHub! (https://github.com/doublespeakgames/adarkroom/)
	-->
	<#if hasSystemTitle>
		<title>${systemTitle}</title>
	<#else>
		<title>A Dark Room</title>
	</#if>
	<meta itemprop="description" name="description" property="og:description" content="A minimalist text adventure">
	<meta itemprop="image" property="og:image" content="https://file.fishpi.cn/adarkroom/img/adr.png" />
	<meta itemprop="name" property="og:title" content="A Dark Room" />
	<link rel="shortcut icon" href="https://file.fishpi.cn/adarkroom/favicon.ico" />
	<link rel="image_src" href="https://file.fishpi.cn/adarkroom/img/adr.png" />
	<script>
		var servePath = "${servePath}";
	</script>
	<script src="https://file.fishpi.cn/adarkroom/lib/jquery.min.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/lib/jquery.color-2.1.2.min.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/lib/jquery.event.move.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/lib/jquery.event.swipe.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/lib/base64.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/lib/translate.js"></script>

	<script src="https://file.fishpi.cn/adarkroom/lang/langs.js"></script>

	<script>
		// try to read "lang" param's from url
		var lang = decodeURIComponent((new RegExp('[?|&]lang=' + '([^&;]+?)(&|#|;|$)').exec(location.search)||[,""])[1].replace(/\+/g, '%20'))||null;
		// if no language requested, try to read it from local storage
		if(!lang){
			try {
				lang = localStorage.lang;
			} catch(e) {}
		}
		// if a language different than english requested, load all translations
		if(lang && lang != 'en'){
			document.write('<script src="https://file.fishpi.cn/adarkroom/lang/'+lang+'/strings.js"><\/script>');
			document.write('<link rel="stylesheet" type="text/css" href="https://file.fishpi.cn/adarkroom/lang/'+lang+'/main.css" \/>');
		}
	</script>

	<script src="https://file.fishpi.cn/adarkroom/script/Button.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/audioLibrary.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/audio.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/engine.js?v=100"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/state_manager.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/header.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/notifications.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/events.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/room.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/outside.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/world.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/path.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/ship.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/space.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/prestige.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/scoring.js"></script>
	<!-- Event modules -->
	<script src="https://file.fishpi.cn/adarkroom/script/events/global.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/events/room.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/events/outside.js"></script>
	<script src="https://file.fishpi.cn/adarkroom/script/events/encounters.js"></script>
  <script src="https://file.fishpi.cn/adarkroom/script/events/setpieces.js"></script>
  <script src="https://file.fishpi.cn/adarkroom/script/events/marketing.js"></script>

	<script type='text/javascript'>
		var oldIE = false;
	</script>
	<!--[if lt IE 9]>
		<script type="text/javascript">oldIE = true;</script>
	<![endif]-->

	<link rel="stylesheet" type="text/css" href="https://file.fishpi.cn/adarkroom/css/main.css" />
	<link rel="stylesheet" type="text/css" href="https://file.fishpi.cn/adarkroom/css/room.css" />
	<link rel="stylesheet" type="text/css" href="https://file.fishpi.cn/adarkroom/css/outside.css" />
	<link rel="stylesheet" type="text/css" href="https://file.fishpi.cn/adarkroom/css/path.css" />
	<link rel="stylesheet" type="text/css" href="https://file.fishpi.cn/adarkroom/css/world.css" />
	<link rel="stylesheet" type="text/css" href="https://file.fishpi.cn/adarkroom/css/ship.css" />
	<link rel="stylesheet" type="text/css" href="https://file.fishpi.cn/adarkroom/css/space.css" />

	<script src="https://file.fishpi.cn/adarkroom/script/localization.js"></script>
</head>
<body>
	<div id="wrapper">
		<div id="saveNotify"><script>document.write(_("saved."));</script></div>
		<div id="content">
			<div id="outerSlider">
				<div id="main">
					<div id="header"></div>
				</div>
			</div>
		</div>
  </div>
  <a class="logo" href="https://fishpi.cn" alt="摸鱼派社区小游戏-作者DoubleSpeakGames.com" target="_blank" style="text-decoration: none; color: #3c4148;">
    <svg xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:cc="http://creativecommons.org/ns#" xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" xmlns:svg="http://www.w3.org/2000/svg" xmlns="http://www.w3.org/2000/svg" version="1.1" viewBox="0 0 32 32" class="logo-icon">
		<image id="image0" width="32" height="32" x="0" y="0" href="https://file.fishpi.cn/mplogo_128.png"/>
	</svg>
	  <p>反作弊已启动，将记录您的游戏时间<br>摸鱼派<span style="color: #3caf36; font-weight: bold;">实时在线</span>，用户名：${currentUser.userName}</p>
  </a>
	<script src="${staticServePath}/js/lib/reconnecting-websocket.min.js?${staticResourceVersion}"></script>
	<script src="${staticServePath}/js/symbol-defs${miniPostfix}.js?${staticResourceVersion}"></script>
	<script src="${staticServePath}/js/common${miniPostfix}.js?${staticResourceVersion}"></script>
	<script>
		var Label = {
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
