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
import org.b3log.symphony.censor.ImageCensor;

/**
 * 七牛云图片审核实现
 * 采用回调模式，上传后由七牛云异步审核并回调通知
 */
public class QiniuImageCensor implements ImageCensor {

    @Override
    public CensorResult censor(String imageUrl) {
        // 七牛云采用回调模式，不主动审核
        // 审核结果通过 ApiProcessor.callbackFromQiNiu() 处理
        return null;
    }

    @Override
    public boolean isCallbackMode() {
        return true; // 七牛云是回调模式
    }
}
