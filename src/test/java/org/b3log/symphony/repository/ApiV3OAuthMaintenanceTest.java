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
package org.b3log.symphony.repository;

import org.b3log.latke.Latkes;
import org.b3log.symphony.service.ApiV3OAuthMaintenanceService;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;
import static org.b3log.symphony.repository.ApiV3AuthorizationCodeTest.*;

public class ApiV3OAuthMaintenanceTest {
    static class Maintained extends ApiV3AuthorizationRequestTest.Consent {
        final ApiV3OAuthMaintenanceService maintenance=new ApiV3OAuthMaintenanceService(){@Override protected long now(){return service.clock;}};
        Maintained()throws Exception{for(var e:Map.<String,Object>of("requests",requests,"codes",codes,"tokens",tokens).entrySet())inject(maintenance,ApiV3OAuthMaintenanceService.class,e.getKey(),e.getValue());}
    }
    public static void main(String[] args)throws Exception{
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Maintained()){
            var unused=f.issue("content.read");var used=f.issue("content.read");var pair=f.exchange(used.code());f.start("old pending");f.decide(f.start("old denied"),false);
            f.service.clock+=TimeUnit.DAYS.toMillis(2);Assert.assertEquals(f.maintenance.cleanup(),3);Assert.assertEquals(query(f.reader,"SELECT * FROM codes").size(),1);Assert.assertTrue(query(f.reader,"SELECT * FROM auth_requests").isEmpty());Assert.assertEquals(query(f.reader,"SELECT * FROM tokens").size(),2);
            f.service.revokeForClient(CLIENT,pair.getString("refreshToken"));var collision=new JSONObject(f.token(pair.getString("refreshToken")).toString()).put("oId","collision").put("clientId","other").put("userId","2").put("tokenHash","unique").put("state",0).put("expiresAt",f.service.clock+100000);f.insert("tokens",collision);
            Assert.assertEquals(f.maintenance.cleanup(),1);Assert.assertTrue(query(f.reader,"SELECT * FROM codes").isEmpty());Assert.assertEquals(query(f.reader,"SELECT state FROM tokens WHERE oId='collision'").getFirst().getInt("state"),0);
        }
        System.out.println("PASS bounded maintenance removes old pending/denied requests and unused codes, retains replay evidence while the exact token family can refresh, and ignores colliding families belonging to another client/user");
        for(String fault:List.of("request.remove.after","code.remove.after","commit"))try(var f=new Maintained()){
            f.start("old");f.issue("content.read");f.service.clock+=TimeUnit.DAYS.toMillis(2);String before=f.every();f.faults.add(fault);try{f.maintenance.cleanup();Assert.fail(fault);}catch(Exception expected){}Assert.assertEquals(f.every(),before);f.faults.clear();Assert.assertEquals(f.maintenance.cleanup(),2);
        }
        try(var f=new Maintained()){
            var prepared=f.start("fresh");f.issue("content.read");Assert.assertEquals(f.maintenance.cleanup(),0);f.begin();try{try{f.maintenance.cleanup();Assert.fail();}catch(IllegalStateException expected){}}finally{f.rollback();}
            f.service.clock=prepared.expiresAt()+TimeUnit.DAYS.toMillis(1)-1;Assert.assertEquals(f.maintenance.cleanup(),1);Assert.assertEquals(query(f.reader,"SELECT * FROM auth_requests").size(),1);f.service.clock++;Assert.assertEquals(f.maintenance.cleanup(),1);
        }
        System.out.println("PASS cleanup root/delete/commit failures roll back every removal, retention boundaries preserve recent requests, and nested root ownership is rejected");
    }
}
