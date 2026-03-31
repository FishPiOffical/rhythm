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
  <meta name="viewport" content="width=device-width,initial-scale=1.0,maximum-scale=1.0">
  <link rel="icon" href="favicon.svg" type="image/svg+xml">
  <title>汉兜 - 汉字 Wordle</title>
  <meta name="description" content="汉兜 - 汉字 Wordle">
  <meta property="og:image" content="https://handle.antfu.me/og.png"/>
  <meta property="og:title" content="汉兜"/>
  <meta property="og:description" content="汉兜 - 汉字 Wordle"/>
  <meta name="twitter:image" content="https://handle.antfu.me/og.png"/>
  <meta name="twitter:card" content="summary_large_image"/>
  <meta name="twitter:creator" content="antfu7"/>
  <script type="module" crossorigin src="https://file.fishpi.cn/handle/assets/index.bf23f960.js"></script>
  <link rel="modulepreload" href="https://file.fishpi.cn/handle/assets/vendor.1d256264.js">
  <link rel="stylesheet" href="https://file.fishpi.cn/handle/assets/index.84457388.css">
</head>
<body>
<div id="app"></div>
<script>
  (function() {
    const prefersDark = window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches
    const setting = localStorage.getItem('color-schema') || 'auto'
    if (setting === 'dark' || (prefersDark && setting !== 'light'))
      document.documentElement.classList.toggle('dark', true)
  })()
</script>

</body>
</html>
