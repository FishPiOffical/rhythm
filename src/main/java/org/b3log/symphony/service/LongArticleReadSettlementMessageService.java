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
package org.b3log.symphony.service;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.Article;
import org.b3log.symphony.model.LongArticleRead;
import org.b3log.symphony.processor.channel.ChatChannel;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;

/** 发送单篇长篇阅读结算通知。 */
@Service
public class LongArticleReadSettlementMessageService {

    private static final Logger LOGGER = LogManager.getLogger(LongArticleReadSettlementMessageService.class);

    @Inject
    private UserQueryService userQueryService;

    public void send(final JSONObject settlement) {
        try {
            final JSONObject author = userQueryService.getUser(settlement.optString(Article.ARTICLE_AUTHOR_ID));
            if (null == author || settlement.optInt(LongArticleRead.REWARD_POINT) == 0) {
                return;
            }
            ChatChannel.sendAdminMsg(author.optString(User.USER_NAME), message(settlement));
        } catch (final Exception e) {
            LOGGER.error("发送长篇阅读结算消息失败", e);
        }
    }

    private String message(final JSONObject settlement) {
        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
        return "【长文阅读结算】《" + settlement.optString(Article.ARTICLE_TITLE) + "》\n"
                + "周期：" + formatter.format(new Date(settlement.optLong(LongArticleRead.WINDOW_START))) + " ~ "
                + formatter.format(new Date(settlement.optLong(LongArticleRead.WINDOW_END))) + "\n"
                + "奖励：" + settlement.optInt(LongArticleRead.REWARD_POINT) + " 积分，注册 "
                + settlement.optInt(LongArticleRead.REGISTERED_CNT) + "，未注册 "
                + settlement.optInt(LongArticleRead.ANON_CNT) + "，封顶计入 "
                + settlement.optInt(LongArticleRead.ANON_CAPPED_CNT) + "，文章链接：/article/"
                + settlement.optString(Article.ARTICLE_T_ID);
    }
}
