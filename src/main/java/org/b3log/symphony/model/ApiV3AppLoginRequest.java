package org.b3log.symphony.model;

/** Short-lived website-to-native login request. Raw request handles, verifiers and tokens are never stored here. */
public final class ApiV3AppLoginRequest {
    private ApiV3AppLoginRequest() { }
    public static final String TABLE = "api_v3_app_login_request";
    public static final String APP_CLIENT = ApiV3Client.APP_CLIENT;
}
