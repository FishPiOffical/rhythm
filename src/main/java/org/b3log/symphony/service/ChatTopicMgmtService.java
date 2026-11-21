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

import org.b3log.latke.Keys;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.ApiV3ChatEvent;
import org.b3log.symphony.model.Option;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ApiV3ChatEventRepository;
import org.b3log.symphony.repository.ApiV3ChatStateRepository;
import org.b3log.symphony.repository.OptionRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.Objects;

/** Durable topic core. No independent commit, admission shortcuts or in-memory state mutation. */
@Service
public class ChatTopicMgmtService {
    @Inject private UserRepository users;
    @Inject private OptionRepository options;
    @Inject private ApiV3ChatStateRepository head;
    @Inject private ApiV3ChatEventRepository events;
    @Inject private PointtransferMgmtService points;
    @FunctionalInterface public interface Admission { void check(JSONObject current) throws RepositoryException; }
    public record Result(String actor, ChatTopicValue topic, String transferId) { }
    protected long now(){return System.currentTimeMillis();}
    public ChatTopicValue readCommitted()throws RepositoryException {
        if(options.hasTransactionBegun())throw new IllegalStateException("committed topic read inside writer transaction");
        final var rows=options.select("SELECT optionValue FROM `"+options.getName()+"` WHERE oId=?",ChatTopicValue.OPTION_ID);
        if(rows.size()>1)throw new IllegalStateException("duplicate chat topic");
        return rows.isEmpty()?null:ChatTopicValue.parse(rows.getFirst().getString(Option.OPTION_VALUE));
    }
    /** Caller prepares text using its approved input policy, and supplies explicit current-user admission. */
    public Result changeInCurrentTransaction(final String actor,final String text,final Admission admission)throws RepositoryException {
        Objects.requireNonNull(admission,"explicit topic admission required");
        if(actor==null||!actor.matches("[1-9][0-9]{0,31}")||text==null||text.isBlank()||text.length()>64)throw new IllegalArgumentException("topic input");
        if(!users.hasTransactionBegun()||!ArticlePublicationCaches.active()
                ||!AccountPairLocks.lock(actor,Pointtransfer.ID_C_SYS).isHeldByCurrentThread())throw new IllegalStateException("topic transaction, cache boundary and prior account-pair lock required");
        return ApiV3ChatTransaction.write(()->{
            final JSONObject current=users.getForUpdate(actor);
            if(current==null||current.optInt(UserExt.USER_STATUS,-1)!=UserExt.USER_STATUS_C_VALID)throw new ApiV3Exception(404,"not_found","用户不存在或不可用");
            if(current.optString("userPhone").isBlank())throw new ApiV3Exception(403,"phone_required","请先绑定手机号码");
            admission.check(current);
            // Same lock as every persisted chat change, including the first creation of the option row.
            head.lockHeadInCurrentTransaction();
            final var previous=options.getForUpdate(ChatTopicValue.OPTION_ID);
            if(previous!=null)ChatTopicValue.parse(previous.getString(Option.OPTION_VALUE));
            if(current.optInt(UserExt.USER_POINT)<ChatTopicValue.COST)throw insufficient();
            final String transfer=points.transferInCurrentTransaction(actor,Pointtransfer.ID_C_SYS,
                    Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_SET_DISCUSS,ChatTopicValue.COST,"",now(),"",PointtransferSource.EMPTY,false);
            if(transfer==null)throw insufficient();
            final long revision=events.appendInCurrentTransaction(ApiV3ChatEvent.TOPIC_TARGET,ApiV3ChatEvent.TOPIC);
            final var topic=new ChatTopicValue(text,current.getString(User.USER_NAME),revision);
            final var row=new JSONObject().put(Keys.OBJECT_ID,ChatTopicValue.OPTION_ID).put(Option.OPTION_VALUE,topic.stored()).put(Option.OPTION_CATEGORY,"chat");
            if(previous==null){
                if(!ChatTopicValue.OPTION_ID.equals(options.add(row)))throw new RepositoryException("topic option identity changed");
            }else options.update(ChatTopicValue.OPTION_ID,row,Option.OPTION_VALUE);
            return new Result(actor,topic,transfer);
        });
    }
    private static ApiV3Exception insufficient(){return new ApiV3Exception(409,"insufficient_points","积分不足，无法修改话题");}
}
