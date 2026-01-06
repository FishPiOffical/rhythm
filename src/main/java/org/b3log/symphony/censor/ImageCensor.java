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

/**
 * 图片内容审核接口
 */
public interface ImageCensor {

    /**
     * 审核图片内容
     *
     * @param imageUrl 图片 URL
     * @return 审核结果，如果是回调模式返回 null
     */
    CensorResult censor(String imageUrl);

    /**
     * 是否是回调模式
     * 七牛云图片审核采用回调模式，AI 审核采用主动模式
     *
     * @return true 表示回调模式，false 表示主动审核模式
     */
    boolean isCallbackMode();
}
