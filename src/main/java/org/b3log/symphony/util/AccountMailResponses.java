package org.b3log.symphony.util;

import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Provider acknowledgement is not a promise of inbox delivery. */
public final class AccountMailResponses {
    private AccountMailResponses(){ }
    public static boolean accepted(String channel,int status,String body) {
        if(status!=200||body==null||body.length()>16384)return false;
        try {
            JSONObject value=ApiV3Json.parse(body.getBytes(StandardCharsets.UTF_8));
            return switch(channel) {
                case "sendcloud" -> Boolean.TRUE.equals(value.opt("result"))&&value.opt("statusCode") instanceof Number n&&"200".equals(n.toString());
                case "aliyun" -> !value.has("Code")&&value.opt("RequestId") instanceof String request&&!request.isBlank()
                        &&value.opt("EnvId") instanceof String event&&!event.isBlank();
                default -> false;
            };
        }catch(Exception ignored){return false;}
    }
}
