package org.b3log.symphony.util;

import org.b3log.latke.Latkes;
import org.b3log.latke.http.handler.RouteHandler;
import org.b3log.symphony.util.Symphonys;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only runtime checks for the security panel. Checks bindings, not business handlers. */
public final class SecuritySelfCheck {
    private SecuritySelfCheck() {
    }

    public static Map<String, Object> run() {
        final List<Map<String, Object>> checks = new ArrayList<>();
        check(checks, "验证码盾版本", AnonymousCaptchaShield.POLICY_VERSION > 0,
                "策略版本 " + AnonymousCaptchaShield.POLICY_VERSION);
        check(checks, "全局入口路由", routeExists("/test", "GET") && routeExists("/validateCaptcha", "POST"),
                "已登记 /test 与 /validateCaptcha");
        check(checks, "后台统一保存路由", routeExists("/admin/security/save", "POST"),
                "已登记统一保存入口");
        final String miscRule = "permission.rule.url./admin/misc.GET";
        check(checks, "后台页面权限规则", Symphonys.URL_PERMISSION_RULES.containsKey(miscRule),
                Symphonys.URL_PERMISSION_RULES.containsKey(miscRule) ? "已配置" : "缺失");
        check(checks, "运行模式", Latkes.getRuntimeMode() != null,
                Latkes.getRuntimeMode() == null ? "未知" : Latkes.getRuntimeMode().name());
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("checkedAt", java.time.Instant.now().toString());
        result.put("runtimeMode", Latkes.getRuntimeMode() == null ? "UNKNOWN" : Latkes.getRuntimeMode().name());
        result.put("status", checks.stream().allMatch(item -> Boolean.TRUE.equals(item.get("passed"))) ? "PASS" : "REVIEW");
        result.put("checks", checks);
        result.put("note", "结构检查不等于真实匿名请求；验证码供应商、ipset和生产流量仍需单独验收");
        return result;
    }

    private static boolean routeExists(final String path, final String method) {
        try {
            RouteHandler.doMatch(path, method);
            return true;
        } catch (final Exception ignored) {
            return false;
        }
    }

    private static void check(final List<Map<String, Object>> checks, final String name,
                              final boolean passed, final String detail) {
        final Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", name);
        item.put("passed", passed);
        item.put("detail", detail);
        checks.add(item);
    }
}
