package org.b3log.symphony.model;

import java.util.Map;

public record UserIpLease(String userId, String ip, long lastSeenAt, long expiresAt) {
    public static final String REPOSITORY = "user_ip_lease";

    public record Change(String userId, Map<String, UserIpLease> current) {
    }
}
