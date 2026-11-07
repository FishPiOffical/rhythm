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
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.model.User;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.*;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.processor.channel.UserChannel;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.Symphonys;
import org.json.JSONObject;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/** Best-effort delivery of a committed activation; none of these effects own registration proof. */
@Service
public class AccountRegistrationEffectsService {
    @Inject private UserRepository users;
    @Inject private PointtransferMgmtService points;
    @Inject private NotificationMgmtService notifications;
    @Inject private AvatarQueryService avatars;
    @Inject private LivenessQueryService liveness;
    public void afterCommit(AccountRegistrationService.Result result){
        if(users.hasTransactionBegun())throw new IllegalStateException("registration effects before transaction end");
        for(var transfer:result.transfers())try{points.indexTransferAsync(Pointtransfer.ID_C_SYS,transfer.recipient(),transfer.id());}catch(RuntimeException e){warn();}
        for(String recipient:result.recipients())try{notifications.publishRefreshNotification(recipient);}catch(RuntimeException e){warn();}
        try{
            // Use a current projection instead of the activation-time account object.
            final var rows=users.select("SELECT userName,userAvatarURL,userStatus FROM `"+users.getName()+"` WHERE oId=?",result.userId());
            if(rows.size()==1&&rows.getFirst().optInt(UserExt.USER_STATUS,-1)==UserExt.USER_STATUS_C_VALID){
                final var user=rows.getFirst();final String name=user.getString(User.USER_NAME);
                final var entry=new JSONObject().put(User.USER_NAME,name).put(UserExt.USER_T_NAME_LOWER_CASE,name.toLowerCase(Locale.ROOT))
                        .put(UserExt.USER_AVATAR_URL,avatars.getAvatarURLByUser(user,"20"));
                synchronized(UserQueryService.USER_NAMES){
                    UserQueryService.USER_NAMES.removeIf(row->row.optString(User.USER_NAME).equalsIgnoreCase(result.name())||row.optString(User.USER_NAME).equalsIgnoreCase(name));
                    UserQueryService.USER_NAMES.add(entry);
                    UserQueryService.USER_NAMES.sort(java.util.Comparator.comparing(row->row.optString(UserExt.USER_T_NAME_LOWER_CASE)));
                }
            }
        }catch(Exception e){warn();}
        startOnlineRewards(result.name());
    }
    protected void startOnlineRewards(String joinedName){
        Thread.startVirtualThread(()->{
            try{rewardOnlineUsers(joinedName);}
            catch(Exception e){warn();}
            finally{try{ApiV3Transactions.releaseThreadResources();}catch(RuntimeException e){warn();}}
        });
    }
    /** Preserve the existing yesterday-liveness match and 1..24 reward, after activation only. */
    private void rewardOnlineUsers(String joinedName)throws Exception {
        for(String id:UserChannel.SESSIONS.keySet()){
            try{Thread.sleep(500);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}
            final var yesterday=liveness.getYesterdayLiveness(id);if(yesterday==null)continue;
            final int current=Liveness.calcPoint(yesterday),maximum=Symphonys.ACTIVITY_YESTERDAY_REWARD_MAX;
            final float percent=(float)Math.round((float)current/maximum*100*100)/100;
            if(percent!=100)continue;
            final int sum=ThreadLocalRandom.current().nextInt(1,25);
            final String transfer=points.transfer(Pointtransfer.ID_C_SYS,id,Pointtransfer.TRANSFER_TYPE_C_ACTIVITY_REDPACKET_FROM_SKY,sum,joinedName,System.currentTimeMillis(),"");
            if(transfer==null)continue;
            notifications.addRedPacketFromSkyNotification(new JSONObject().put(Notification.NOTIFICATION_USER_ID,id).put(Notification.NOTIFICATION_DATA_ID,joinedName+":"+sum));
        }
    }
    private static void warn(){LogManager.getLogger(AccountRegistrationEffectsService.class).warn("Committed registration delivery unavailable");}
}
