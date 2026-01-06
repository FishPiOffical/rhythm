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
package org.b3log.symphony.censor.impl;

import org.b3log.symphony.censor.CensorResult;
import org.b3log.symphony.censor.TextCensor;
import org.b3log.symphony.util.QiniuTextCensor;
import org.json.JSONObject;

/**
 * 七牛云文本审核适配器
 * 包装现有的 QiniuTextCensor，不修改原有代码
 */
public class QiniuTextCensorAdapter implements TextCensor {

    @Override
    public CensorResult censor(String text) {
        System.out.println("[七牛文本审核] 输入: " + (text.length() > 100 ? text.substring(0, 100) + "..." : text));

        JSONObject qiniuResult = QiniuTextCensor.censor(text);
        System.out.println("[七牛文本审核] 七牛原始响应: " + qiniuResult);

        CensorResult result = CensorResult.fromQiniuResult(qiniuResult);
        System.out.println("[七牛文本审核] 解析结果: " + result);

        return result;
    }
}
