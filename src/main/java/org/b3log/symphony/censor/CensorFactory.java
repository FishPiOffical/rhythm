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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.symphony.censor.impl.AIImageCensor;
import org.b3log.symphony.censor.impl.AITextCensor;
import org.b3log.symphony.censor.impl.QiniuImageCensor;
import org.b3log.symphony.censor.impl.QiniuTextCensorAdapter;
import org.b3log.symphony.util.Symphonys;

/**
 * 内容审核工厂类
 * 根据配置创建对应的审核实现
 */
public final class CensorFactory {

    private static final Logger LOGGER = LogManager.getLogger(CensorFactory.class);

    /**
     * 文本审核提供商配置
     * 可选值：qiniu, ai
     */
    public static final String TEXT_PROVIDER;

    /**
     * 图片审核提供商配置
     * 可选值：qiniu, ai
     */
    public static final String IMAGE_PROVIDER;

    private static volatile TextCensor textCensor;
    private static volatile ImageCensor imageCensor;

    static {
        String textProvider = Symphonys.get("censor.text.provider");
        String imageProvider = Symphonys.get("censor.image.provider");

        // 默认使用七牛云
        TEXT_PROVIDER = (textProvider != null && !textProvider.isBlank()) ? textProvider.toLowerCase() : "qiniu";
        IMAGE_PROVIDER = (imageProvider != null && !imageProvider.isBlank()) ? imageProvider.toLowerCase() : "qiniu";

        LOGGER.info("Content censor initialized - Text provider: {}, Image provider: {}", TEXT_PROVIDER, IMAGE_PROVIDER);
    }

    /**
     * 获取文本审核器
     */
    public static TextCensor getTextCensor() {
        if (textCensor == null) {
            synchronized (CensorFactory.class) {
                if (textCensor == null) {
                    textCensor = createTextCensor();
                }
            }
        }
        return textCensor;
    }

    /**
     * 获取图片审核器
     */
    public static ImageCensor getImageCensor() {
        if (imageCensor == null) {
            synchronized (CensorFactory.class) {
                if (imageCensor == null) {
                    imageCensor = createImageCensor();
                }
            }
        }
        return imageCensor;
    }

    /**
     * 检查图片审核是否为回调模式
     */
    public static boolean isImageCallbackMode() {
        return getImageCensor().isCallbackMode();
    }

    /**
     * 创建文本审核器实例
     */
    private static TextCensor createTextCensor() {
        if ("ai".equals(TEXT_PROVIDER)) {
            LOGGER.info("Using AI text censor");
            return new AITextCensor();
        } else {
            LOGGER.info("Using Qiniu text censor");
            return new QiniuTextCensorAdapter();
        }
    }

    /**
     * 创建图片审核器实例
     */
    private static ImageCensor createImageCensor() {
        if ("ai".equals(IMAGE_PROVIDER)) {
            LOGGER.info("Using AI image censor (active mode)");
            return new AIImageCensor();
        } else {
            LOGGER.info("Using Qiniu image censor (callback mode)");
            return new QiniuImageCensor();
        }
    }

    /**
     * 重置审核器（用于配置热更新）
     */
    public static synchronized void reset() {
        textCensor = null;
        imageCensor = null;
        LOGGER.info("Censor instances reset");
    }

    private CensorFactory() {
    }
}
