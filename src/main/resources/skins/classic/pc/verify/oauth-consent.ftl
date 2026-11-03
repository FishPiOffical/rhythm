<!doctype html>
<html lang="zh-CN">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta name="referrer" content="same-origin">
    <title>应用授权 · 摸鱼派</title>
    <link rel="stylesheet" href="${oauthStyle?html}">
</head>
<body>
<main class="oauth-consent">
    <p class="brand">摸鱼派 · 应用授权</p>
    <#if oauthLoggedIn>
        <h1>允许 <strong>${oauthClient?html}</strong> 访问你的账号？</h1>
        <p class="account">当前账号：<strong>${oauthUser?html}</strong></p>
        <p>此应用申请以下权限：</p>
        <ul class="permissions">
            <#list oauthPermissions as permission>
                <li>${permission.label?html}</li>
            </#list>
        </ul>
        <details>
            <summary>查看回调地址与权限标识</summary>
            <p class="redirect">${oauthRedirect?html}</p>
            <ul><#list oauthPermissions as permission><li>${permission.key?html}</li></#list></ul>
        </details>
        <p class="note">请仅授权你信任的应用。部分操作会消耗积分；你可以选择拒绝。</p>
        <form action="${oauthAction?html}" method="post" autocomplete="off">
            <input type="hidden" name="request" value="${oauthRequest?html}">
            <input type="hidden" name="proof" value="${oauthProof?html}">
            <input type="hidden" name="csrfToken" value="${oauthCsrf?html}">
            <div class="actions">
                <button type="submit" name="decision" value="deny">拒绝</button>
                <button type="submit" name="decision" value="approve" class="primary">允许以上权限</button>
            </div>
        </form>
        <footer><a href="${oauthHome?html}">返回摸鱼派</a> · 如果账号不正确，请返回站点切换账号后重新发起授权。</footer>
    <#else>
        <h1>登录后继续授权</h1>
        <p>请登录摸鱼派，查看应用及其申请的权限，再选择是否允许访问。</p>
        <p><a class="login primary" href="${oauthLogin?html}">登录并继续</a></p>
        <footer><a href="${oauthHome?html}">返回摸鱼派</a></footer>
    </#if>
</main>
</body>
</html>
