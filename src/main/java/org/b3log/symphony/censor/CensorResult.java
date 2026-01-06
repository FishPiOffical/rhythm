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
package org.b3log.symphony.censor;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 统一内容审核结果类
 */
public class CensorResult {

    /**
     * 审核建议：pass, block, review
     */
    private String action;

    /**
     * 违规类型
     */
    private String type;

    /**
     * 处理动作：pass 或 block
     */
    private String doAction;

    /**
     * 违规词汇列表
     */
    private List<String> bannedWords;

    /**
     * 内容分析（AI 判断理由）
     */
    private String analysis;

    public CensorResult() {
        this.bannedWords = new ArrayList<>();
    }

    /**
     * 创建通过的审核结果
     */
    public static CensorResult pass() {
        CensorResult result = new CensorResult();
        result.setAction("pass");
        result.setType("正常");
        result.setDoAction("pass");
        return result;
    }

    /**
     * 创建阻止的审核结果
     */
    public static CensorResult block(String type, List<String> bannedWords) {
        CensorResult result = new CensorResult();
        result.setAction("block");
        result.setType(type);
        result.setDoAction("block");
        result.setBannedWords(bannedWords);
        return result;
    }

    /**
     * 从七牛云审核结果转换
     */
    public static CensorResult fromQiniuResult(JSONObject qiniuResult) {
        CensorResult result = new CensorResult();
        result.setAction(qiniuResult.optString("action", "pass"));
        result.setType(qiniuResult.optString("type", "未知"));
        result.setDoAction(qiniuResult.optString("do", "pass"));

        JSONArray bannedWordsArr = qiniuResult.optJSONArray("bannedWords");
        if (bannedWordsArr != null) {
            List<String> words = new ArrayList<>();
            for (int i = 0; i < bannedWordsArr.length(); i++) {
                words.add(bannedWordsArr.getString(i));
            }
            result.setBannedWords(words);
        }

        return result;
    }

    /**
     * 从 AI 响应解析审核结果
     * @return 解析成功返回结果，解析失败返回 null
     */
    public static CensorResult fromAIResponse(String aiResponse) {
        try {
            // 尝试从响应中提取 JSON
            String jsonStr = extractJson(aiResponse);
            if (jsonStr != null) {
                JSONObject json = new JSONObject(jsonStr);

                // 验证必须的 action 字段
                String action = json.optString("action", "");
                if (!action.equals("pass") && !action.equals("block") && !action.equals("review")) {
                    // action 字段无效，返回 null 触发 fallback
                    return null;
                }

                CensorResult result = new CensorResult();
                result.setAction(action);
                result.setType(json.optString("type", "未知"));

                // 根据 action 设置 doAction
                if ("block".equals(action)) {
                    result.setDoAction("block");
                } else {
                    result.setDoAction("pass");
                }

                // 解析违规词
                JSONArray bannedWordsArr = json.optJSONArray("bannedWords");
                if (bannedWordsArr != null) {
                    List<String> words = new ArrayList<>();
                    for (int i = 0; i < bannedWordsArr.length(); i++) {
                        words.add(bannedWordsArr.getString(i));
                    }
                    result.setBannedWords(words);
                }

                // 解析内容分析
                result.setAnalysis(json.optString("analysis", ""));

                return result;
            }
        } catch (Exception e) {
            // 解析失败
        }

        // 解析失败返回 null，由调用方决定 fallback 策略
        return null;
    }

    /**
     * 从字符串中提取 JSON
     */
    private static String extractJson(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }

        // 查找 JSON 对象
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }

        return null;
    }

    /**
     * 是否被阻止
     */
    public boolean isBlocked() {
        return "block".equals(doAction);
    }

    /**
     * 格式化显示违规词汇
     */
    public String showBannedWords() {
        if (bannedWords == null || bannedWords.isEmpty()) {
            return "无违规";
        }

        StringBuilder sb = new StringBuilder("违规：");
        for (int i = 0; i < bannedWords.size(); i++) {
            sb.append(bannedWords.get(i));
            if (i < bannedWords.size() - 1) {
                sb.append("、");
            }
        }
        return sb.toString();
    }

    // Getters and Setters

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDoAction() {
        return doAction;
    }

    public void setDoAction(String doAction) {
        this.doAction = doAction;
    }

    public List<String> getBannedWords() {
        return bannedWords;
    }

    public void setBannedWords(List<String> bannedWords) {
        this.bannedWords = bannedWords;
    }

    public String getAnalysis() {
        return analysis;
    }

    public void setAnalysis(String analysis) {
        this.analysis = analysis;
    }

    @Override
    public String toString() {
        return "CensorResult{" +
                "action='" + action + '\'' +
                ", type='" + type + '\'' +
                ", doAction='" + doAction + '\'' +
                ", bannedWords=" + bannedWords +
                ", analysis='" + analysis + '\'' +
                '}';
    }
}
