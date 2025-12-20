/*
 * Rhythm - A modern community (forum/BBS/SNS/blog) platform written in Java.
 * Modified version from Symphony, Thanks Symphony :)
 * Copyright (C) 2012-present, b3log.org
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.b3log.symphony.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.model.User;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.processor.bot.ChatRoomBot;
import org.b3log.symphony.processor.channel.ChatroomChannel;
import org.b3log.symphony.service.AvatarQueryService;
import org.json.JSONArray;
import org.json.JSONObject;

public class NodeUtil {

    private static final ExecutorService BROADCAST_POOL = Executors.newVirtualThreadPerTaskExecutor();

    public static List<String> uriNodes = new ArrayList<>();

    public static List<WebSocket> wsNodes = new ArrayList<>();

    public static Map<String, Integer> wsOnline = new HashMap<>();

    private static final Logger LOGGER = LogManager.getLogger(NodeUtil.class);

    public static JSONArray remoteUsers = new JSONArray();

    public static HashMap<String, HashMap<String, Integer>> remoteUserPerNode = new HashMap<>();

    public static HashMap<String, String> nodeNickNames = new HashMap<>();

    public static Map<String, Integer> nodeWeights = new HashMap<>();

    public static void init() {
        LOGGER.log(Level.INFO, "Loading nodes");
        List<WebSocket> tmp_wsNodes = new ArrayList<>();
        List<String> tmp_uriNodes = new ArrayList<>();
        HashMap<String, String> tmp_nodeNickNames = new HashMap<>();
        Map<String, Integer> tmp_nodeWeights = new HashMap<>();
        String[] nodes = Symphonys.get("chatroom.node.url").split(";");
        for (String i : nodes) {
            tmp_nodeNickNames.put(i.split(",")[0], i.split(",")[1]);
            tmp_nodeWeights.put(i.split(",")[0], Integer.parseInt(i.split(",")[2]));
            i = i.split(",")[0];
            String serverUri = i + "?apiKey=" + Symphonys.get("chatroom.node.adminKey");
            try {
                SSLContext sslContext = createInsecureSSLContext();
                HttpClient client = HttpClient.newBuilder()
                        .sslContext(sslContext)
                        .build();

                CompletableFuture<String> future = new CompletableFuture<>();
                WebSocket webSocket = client.newWebSocketBuilder()
                        .buildAsync(URI.create(serverUri), new WebSocketListener(future))
                        .join();

                webSocket.sendText(Symphonys.get("chatroom.node.adminKey") + ":::hello", true);
                future.get(10, TimeUnit.SECONDS);
                tmp_wsNodes.add(webSocket);
                tmp_uriNodes.add(i);
            } catch (Exception e) {
                LOGGER.log(Level.ERROR, "Failed to connect to node: " + serverUri, e);
            }
        }

        for (WebSocket i : wsNodes) {
            i.sendClose(1000, "Nornal Closure");
        }
        wsNodes.clear();
        uriNodes.clear();
        nodeNickNames.clear();
        nodeWeights.clear();

        wsNodes.addAll(tmp_wsNodes);
        uriNodes.addAll(tmp_uriNodes);
        nodeNickNames.putAll(tmp_nodeNickNames);
        nodeWeights.putAll(tmp_nodeWeights);
    }

    public static void notice(String text) {
        String msg = Symphonys.get("chatroom.node.adminKey") + ":::" + text;
        for (WebSocket ws : wsNodes) {
            BROADCAST_POOL.submit(() -> {
                try {
                    ws.sendText(msg, true);
                } catch (Exception ignore) {
                }
            });
        }
    }

    public static void sendTell(String who, String text) {
        notice("tell " + who + " " + text);
    }

    public static void sendMsg(String who, String text) {
        notice("msg " + who + " " + text);
    }

    public static void sendAll(String text) {
        notice("all " + text);
    }

    public static void sendSlow(String text) {
        notice("slow " + text);
    }

    public static void sendKick(String userName) {
        notice("kick " + userName);
    }

    public static void sendClear() {
        for (String i : uriNodes) {
            try {
                String serverUri = i + "?apiKey=" + Symphonys.get("chatroom.node.adminKey");
                SSLContext sslContext = createInsecureSSLContext();
                HttpClient client = HttpClient.newBuilder()
                        .sslContext(sslContext)
                        .build();
                CompletableFuture<String> responseFuture = new CompletableFuture<>();
                WebSocket webSocket = client.newWebSocketBuilder()
                        .buildAsync(URI.create(serverUri), new WebSocketListener(responseFuture))
                        .join();
                webSocket.sendText(Symphonys.get("chatroom.node.adminKey") + ":::clear", true);
                try {
                    String response = responseFuture.get(10, TimeUnit.SECONDS);
                    String nickName = NodeUtil.nodeNickNames.get(i);
                    if (response.equals("{}")) {
                        ChatRoomBot.sendBotMsg(nickName + "：报告！没有超过6小时未活跃的成员，一切都很和谐~");
                    } else {
                        Map<String, Object> result = new JSONObject(response).toMap();
                        StringBuilder stringBuilder = new StringBuilder();
                        stringBuilder.append(nickName + "：报告！成功扫描超过6小时未活跃的成员，并已将他们断开连接：<br>");
                        stringBuilder.append("<details><summary>不活跃用户列表</summary>");
                        for (String j : result.keySet()) {
                            long time = Long.parseLong(result.get(j).toString());
                            stringBuilder.append(j + " AFK " + time + "小时<br>");
                        }
                        stringBuilder.append("</details>");
                        ChatRoomBot.sendBotMsg(stringBuilder.toString());
                    }
                    webSocket.sendClose(1000, "Nornal Closure");
                } catch (Exception e) {
                    webSocket.sendClose(1000, "Nornal Closure");
                    LOGGER.log(Level.ERROR,
                            "Send clear command to " + serverUri + " has no response within 10 seconds. giveup.", e);
                }
            } catch (Exception ignored) {
            }
        }
    }

    public static void initOnline() {
        JSONArray onlineList = new JSONArray();
        Map<String, Integer> tmp_wsOnline = new HashMap<>();
        HashMap<String, HashMap<String, Integer>> tmp_remoteUserPerNode = new HashMap<>();
        for (String i : uriNodes) {
            tmp_remoteUserPerNode.put(i, new HashMap<>());
            try {
                String serverUri = i + "?apiKey=" + Symphonys.get("chatroom.node.adminKey");
                SSLContext sslContext = createInsecureSSLContext();
                HttpClient client = HttpClient.newBuilder()
                        .sslContext(sslContext)
                        .build();
                CompletableFuture<String> responseFuture = new CompletableFuture<>();
                WebSocket webSocket = client.newWebSocketBuilder()
                        .buildAsync(URI.create(serverUri), new WebSocketListener(responseFuture))
                        .join();
                webSocket.sendText(Symphonys.get("chatroom.node.adminKey") + ":::online", true);
                try {
                    String response = responseFuture.get(10, TimeUnit.SECONDS);
                    JSONArray jsonArray = new JSONArray(response);
                    for (int j = 0; j < jsonArray.length(); j++) {
                        onlineList.put(jsonArray.get(j));
                        JSONObject jsonObject = jsonArray.getJSONObject(j);
                        String userName = jsonObject.optString("userName");
                        if (tmp_remoteUserPerNode.get(i).containsKey(userName)) {
                            tmp_remoteUserPerNode.get(i).put(userName, tmp_remoteUserPerNode.get(i).get(userName) + 1);
                        } else {
                            tmp_remoteUserPerNode.get(i).put(userName, 1);
                        }
                    }
                    tmp_wsOnline.put(i, jsonArray.length());
                    webSocket.sendClose(1000, "Nornal Closure");
                } catch (Exception e) {
                    webSocket.sendClose(1000, "Nornal Closure");
                    LOGGER.log(Level.ERROR, serverUri + " No response within 10 seconds. giveup.", e);
                }
            } catch (Exception ignored) {
            }
        }
        remoteUsers = onlineList;

        wsOnline.clear();
        remoteUserPerNode.clear();

        wsOnline.putAll(tmp_wsOnline);
        remoteUserPerNode.putAll(tmp_remoteUserPerNode);

        // 推送在线信息给子节点
        final BeanManager beanManager = BeanManager.getInstance();
        final AvatarQueryService avatarQueryService = beanManager.getReference(AvatarQueryService.class);

        Map<String, JSONObject> filteredOnlineUsers = new HashMap<>();
        for (JSONObject object : ChatroomChannel.onlineUsers.values()) {
            String name = object.optString(User.USER_NAME);
            filteredOnlineUsers.put(name, object);
        }

        for (int i = 0; i < NodeUtil.remoteUsers.length(); i++) {
            try {
                JSONObject temp = NodeUtil.remoteUsers.getJSONObject(i);
                filteredOnlineUsers.put(temp.optString(User.USER_NAME), temp);
            } catch (Exception ignored) {
            }
        }

        JSONArray onlineArray = new JSONArray();
        for (String user : filteredOnlineUsers.keySet()) {
            JSONObject object = filteredOnlineUsers.get(user);

            String avatar = object.optString(UserExt.USER_AVATAR_URL);
            String homePage = Latkes.getStaticServePath() + "/member/" + user;

            JSONObject generated = new JSONObject();
            generated.put(User.USER_NAME, user);
            generated.put(UserExt.USER_AVATAR_URL, avatar);
            avatarQueryService.fillUserAvatarURL(generated);
            generated.put("homePage", homePage);
            onlineArray.put(generated);
        }

        JSONObject result = new JSONObject();
        result.put(Common.ONLINE_CHAT_CNT, filteredOnlineUsers.size());
        result.put(Common.TYPE, "online");
        result.put("users", onlineArray);
        result.put("discussing", ChatroomChannel.discussing);

        for (String i : uriNodes) {
            try {
                String serverUri = i + "?apiKey=" + Symphonys.get("chatroom.node.adminKey");
                SSLContext sslContext = createInsecureSSLContext();
                HttpClient client = HttpClient.newBuilder()
                        .sslContext(sslContext)
                        .build();
                CompletableFuture<String> responseFuture = new CompletableFuture<>();
                WebSocket webSocket = client.newWebSocketBuilder()
                        .buildAsync(URI.create(serverUri), new WebSocketListener(responseFuture))
                        .join();

                String message = Symphonys.get("chatroom.node.adminKey") + ":::push " + result;
                webSocket.sendText(message, true);

                try {
                    String response = responseFuture.get(10, TimeUnit.SECONDS);
                    webSocket.sendClose(1000, "Nornal Closure");
                } catch (Exception e) {
                    webSocket.sendClose(1000, "Nornal Closure");
                    LOGGER.log(Level.ERROR,
                            "Push online list to " + serverUri + " has no response within 10 seconds. giveup.", e);
                }
            } catch (Exception ignored) {
            }
        }
    }

    // 创建忽略 SSL 证书的 SSLContext
    private static SSLContext createInsecureSSLContext() throws Exception {
        TrustManager[] trustAllCerts = new TrustManager[] {
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }
        };

        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        return sslContext;
    }

    private static class WebSocketListener implements WebSocket.Listener {
        private final CompletableFuture<String> responseFuture;

        public WebSocketListener(CompletableFuture<String> responseFuture) {
            this.responseFuture = responseFuture;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1); // 请求接收一条消息
        }

        private final StringBuilder messageBuffer = new StringBuilder();

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            messageBuffer.append(data);

            if (last) {
                String completeMessage = messageBuffer.toString();
                messageBuffer.setLength(0);

                if (!responseFuture.isDone()) {
                    responseFuture.complete(completeMessage);
                }
            }

            webSocket.request(1);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            responseFuture.completeExceptionally(error); // 异常时完成 Future
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static class WebSocketListenerNoFuture implements WebSocket.Listener {
        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1); // 请求接收一条消息
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            webSocket.request(1); // 请求接收下一条消息
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            return CompletableFuture.completedFuture(null);
        }
    }
}
