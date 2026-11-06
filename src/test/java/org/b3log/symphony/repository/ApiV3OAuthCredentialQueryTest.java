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
import org.b3log.latke.repository.RepositoryException;
import org.b3log.symphony.service.ApiV3OAuthCredentialQueryService;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.*;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;
import static org.b3log.symphony.repository.ApiV3AuthorizationCodeTest.*;

/** Read-only proof used by the endpoint-local native OAuth admission policy. */
public class ApiV3OAuthCredentialQueryTest {
    static class Proof extends Grants {
        final ApiV3OAuthCredentialQueryService proof=new ApiV3OAuthCredentialQueryService(){@Override protected long now(){return service.clock;}};
        Proof()throws Exception{
            for(var e:Map.<String,Object>of("clients",clients,"redirects",redirects,"codes",codes,"tokens",tokens,"users",new UserRepository(){@Override public String getName(){return "user";}@Override public List<JSONObject> select(String sql,Object...args)throws RepositoryException{return query(reader,sql,args);}}).entrySet())inject(proof,ApiV3OAuthCredentialQueryService.class,e.getKey(),e.getValue());
        }
    }
    public static void main(String[] args)throws Exception {
        var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);Latkes.setRuntimeMode(Latkes.RuntimeMode.PRODUCTION);
        try(var f=new Proof()){
            var issued=f.issue("content.read");String before=f.all();var identity=f.proof.code(CLIENT,URI,issued.code(),VERIFIER);Assert.assertEquals(identity.keySet(),Set.of("oId"));Assert.assertEquals(identity.getString("oId"),"1");
            Assert.assertNull(f.proof.code("other",URI,issued.code(),VERIFIER));Assert.assertNull(f.proof.code(CLIENT,URI+"&tamper=1",issued.code(),VERIFIER));Assert.assertNull(f.proof.code(CLIENT,URI,issued.code(),"b".repeat(43)));Assert.assertEquals(f.all(),before);
            var pair=f.exchange(issued.code());before=f.all();Assert.assertEquals(f.proof.code(CLIENT,URI,issued.code(),VERIFIER).getString("oId"),"1");Assert.assertEquals(f.all(),before);
            f.service.revokeForClient(CLIENT,pair.getString("refreshToken"));Assert.assertNull(f.proof.code(CLIENT,URI,issued.code(),VERIFIER));
        }
        System.out.println("PASS prepared read-only code proof validates exact client/registered URI/S256/current owner, returns only the owner ID and recognizes a live consumed family for replay revocation without issuing or consuming credentials");
        try(var f=new Proof()){
            var issued=f.issue("content.read");f.service.clock=issued.expiresAt();Assert.assertNull(f.proof.code(CLIENT,URI,issued.code(),VERIFIER));Assert.assertTrue(query(f.reader,"SELECT * FROM tokens").isEmpty());
        }
        try(var f=new Proof()){
            var pair=f.grant(CLIENT,"content.read");String before=f.all();Assert.assertEquals(f.proof.token(CLIENT,pair.getString("refreshToken"),false).getString("oId"),"1");Assert.assertNull(f.proof.token(CLIENT,pair.getString("accessToken"),false));Assert.assertEquals(f.proof.token(CLIENT,pair.getString("accessToken"),true).getString("oId"),"1");Assert.assertNull(f.proof.token("other",pair.getString("refreshToken"),false));Assert.assertNull(f.proof.token("website_v3",pair.getString("refreshToken"),false));Assert.assertNull(f.proof.token(CLIENT,ApiV3TokenUtil.generate(),false));Assert.assertEquals(f.all(),before);
            var rotated=f.service.refreshForClient(CLIENT,pair.getString("refreshToken"));Assert.assertNotNull(f.proof.token(CLIENT,pair.getString("refreshToken"),false));f.service.revokeForClient(CLIENT,rotated.getString("refreshToken"));Assert.assertNull(f.proof.token(CLIENT,pair.getString("refreshToken"),false));Assert.assertNull(f.proof.token(CLIENT,rotated.getString("accessToken"),true));
        }
        System.out.println("PASS prepared refresh/revocation proof requires the correct client and credential type/state, rejects first-party/guessed/revoked credentials and does not mutate family or activity state");
        try(var f=new Proof()){
            var pair=f.grant(CLIENT,"content.read");exec(f.writer,"UPDATE user SET userStatus=1 WHERE oId='1'");Assert.assertNull(f.proof.token(CLIENT,pair.getString("refreshToken"),false));exec(f.writer,"UPDATE user SET userStatus=0");exec(f.writer,"UPDATE clients SET status=1 WHERE clientId=?",CLIENT);Assert.assertNull(f.proof.token(CLIENT,pair.getString("refreshToken"),false));
        }
        System.out.println("PASS prepared proof queries fail closed on current disabled user/client and expired authorization code; final mutation authorization remains in the existing strict write core");
    }
}
