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
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.concurrent.atomic.AtomicReference;

/** Bearer topic changes share admission state with ordinary/legacy chat and one idempotency transaction. */
@Service
public class ApiV3ChatTopicService {
    @Inject private UserRepository users;
    @Inject private ChatTopicWriteService writes;
    @Inject private ChatTopicMgmtService topics;
    @Inject private ApiV3IdempotencyMgmtService idempotency;
    @Inject private ChatMessageValidationService validation;
    @Inject private ChatMessageRestrictionQueryService restrictions;
    @Inject private ChatMessagePenaltyMgmtService penalties;
    @Inject private ApiV3ChatPublicationService publication;
    private record Decision(String name,String content,ChatMessageAbuseState.Repeat repeat){ }
    protected long now(){return System.currentTimeMillis();}
    public JSONObject set(final JSONObject viewer,final String raw,final String key)throws Exception {
        final String actor=viewer==null?"":viewer.optString(Keys.OBJECT_ID);
        if(!actor.matches("[1-9][0-9]{0,18}"))throw new ApiV3Exception(401,"unauthorized","登录凭据无效");
        ChatTopicInputs.v3(raw);
        final var changed=new AtomicReference<ChatTopicMgmtService.Result>();
        final var penalty=new AtomicReference<ChatMessagePenaltyMgmtService.Result>();
        final var decision=new AtomicReference<Decision>();final var confirmed=new AtomicReference<String>();
        final var outcome=writes.boundary(actor,()->idempotency.executeWithOutcome(actor,"chat.topic.set",key,new JSONObject().put("text",raw),()->{
            final JSONObject current=users.getForUpdate(actor);
            if(current==null||current.optInt(UserExt.USER_STATUS,-1)!=UserExt.USER_STATUS_C_VALID)throw new ApiV3Exception(404,"not_found","用户不存在或不可用");
            if(current.optString("userPhone").isBlank())throw new ApiV3Exception(403,"phone_required","请先绑定手机号码");
            final String prepared;
            try{prepared=validation.prepare(current,ChatTopicInputs.OPEN+raw+ChatTopicInputs.CLOSE);}
            catch(ChatMessageValidation.Rejected e){throw new ApiV3Exception(e.status,e.code,e.getMessage());}
            final String text=ChatTopicInputs.v3(ChatTopicInputs.unwrap(prepared));final long time=now();
            ChatMessageRestrictions.check(restrictions.readInCurrentTransaction(actor),DataModelService.hasPermission(current.optString(User.USER_ROLE),3),prepared,time,()->{
                if(prepared.equals(confirmed.get()))return true;
                final boolean yes=ChatMessageAbuseState.confirmRisk(actor,prepared);if(yes)confirmed.set(prepared);return yes;
            });
            final String name=current.getString(User.USER_NAME);
            if(decision.get()==null||!name.equals(decision.get().name())||!prepared.equals(decision.get().content()))decision.set(new Decision(name,prepared,ChatMessageAbuseState.repeated(name,prepared)));
            penalty.set(decision.get().repeat()==ChatMessageAbuseState.Repeat.PENALIZE?penalties.applyInCurrentTransaction(actor,time):null);
            // User/restriction locks are still held; the storage core re-reads that same current user.
            final var result=topics.changeInCurrentTransaction(actor,text,verified->{});changed.set(result);
            return new JSONObject().put("text",result.topic().text()).put("cost",ChatTopicValue.COST).put("revision",Long.toString(result.topic().revision()));
        }));
        if(outcome.applied()){
            writes.afterCommitted(changed.get());ChatMessageAbuseState.accepted(decision.get().content());
            publication.afterSharedAdmission(actor,decision.get().name(),decision.get().repeat(),penalty.get());
        }
        return outcome.result();
    }
}
