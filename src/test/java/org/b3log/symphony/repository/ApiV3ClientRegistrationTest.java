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
import org.b3log.symphony.service.ApiV3ClientRegistrationService;
import org.b3log.symphony.util.ApiV3Exception;
import org.json.JSONObject;
import org.testng.Assert;
import java.util.List;
import java.util.Properties;
import static org.b3log.symphony.repository.ApiV3ChatEventRepositoryTest.*;

/** Real registration primitive and code/token integration with explicit SQLite base persistence. */
public class ApiV3ClientRegistrationTest {
    static class Registry extends ApiV3AuthorizationCodeTest.Grants {
        final ApiV3ClientRegistrationService registry=new ApiV3ClientRegistrationService(){
            @Override protected boolean development(){return Registry.this.development;}
        };
        Registry()throws Exception{
            inject(registry,ApiV3ClientRegistrationService.class,"clients",new ApiV3ClientRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public String add(JSONObject row)throws RepositoryException{
                    insert("clients",row);if(faults.contains("client.add.after"))throw new RepositoryException("client add failure");return row.getString("oId");
                }
            });
            inject(registry,ApiV3ClientRegistrationService.class,"redirects",new ApiV3ClientRedirectRepository(){
                @Override public boolean hasTransactionBegun(){return active();}
                @Override public String add(JSONObject row)throws RepositoryException{
                    insert("redirects",row);if(faults.contains("redirect.add.after"))throw new RepositoryException("redirect add failure");return row.getString("oId");
                }
            });
        }
        ApiV3ClientRegistrationService.Registered create(String name,String scope,List<String> uris)throws Exception{
            begin();try{var result=registry.createApprovedInCurrentTransaction(name,scope,uris);commit();return result;}finally{if(active())rollback();}
        }
    }
    public static void main(String[] args)throws Exception{
        final var props=new Properties();props.setProperty("runtimeDatabase","NONE");Latkes.setLatkeProps(props);Latkes.setLocalProps(props);
        try(var f=new Registry()){
            final String nativeUri="com.example.client:/oauth2redirect";
            var result=f.create(" 应用 <name> ","profile.read content.read profile.read",List.of(ApiV3AuthorizationCodeTest.URI,nativeUri));
            Assert.assertTrue(result.clientId().matches("app_[A-Za-z0-9_-]{43}"));Assert.assertEquals(result.name(),"应用 <name>");Assert.assertEquals(result.scope(),"content.read profile.read");
            Assert.assertEquals(query(f.reader,"SELECT * FROM redirects WHERE clientId=?",result.clientId()).size(),2);
            f.begin();var code=f.grants.issueInCurrentTransaction("1",result.clientId(),nativeUri,"content.read",ApiV3AuthorizationCodeTest.CHALLENGE,"S256");f.commit();
            var pair=f.grants.exchange(result.clientId(),nativeUri,code.code(),ApiV3AuthorizationCodeTest.VERIFIER);Assert.assertNotNull(f.service.authenticate(pair.getString("accessToken")));
            try{result.redirectUris().add("https://evil.example/");Assert.fail();}catch(UnsupportedOperationException expected){}
            Assert.assertFalse(result.toString().contains("accessToken"));
        }
        System.out.println("PASS approved registration creates a generated public client and exact HTTPS/native callback rows atomically; actual S256 exchange accepts the new registration and DTO callbacks are immutable");
        try(var f=new Registry()){
            String before=f.all();
            for(String name:new String[]{"", " ","n".repeat(129),"line\nname","bad\u0000name","\uD800"})reject(()->f.create(name,"content.read",List.of(ApiV3AuthorizationCodeTest.URI)));
            for(String scope:new String[]{"", "*", "openid", "content.read  profile.read", "content.read\nprofile.read", "content.read ","unknown.write"})reject(()->f.create("App",scope,List.of(ApiV3AuthorizationCodeTest.URI)));
            for(String uri:List.of("http://localhost:8080/cb","https://app.example/#fragment","https://user:pwd@app.example/","https://app.example/?%63ode=x","javascript:alert(1)","com.example.client://callback"," https://app.example/"))reject(()->f.create("App","content.read",List.of(uri)));
            reject(()->f.create("App","content.read",List.of()));reject(()->f.create("App","content.read",List.of(ApiV3AuthorizationCodeTest.URI,ApiV3AuthorizationCodeTest.URI)));
            Assert.assertEquals(f.all(),before);
            f.development=true;Assert.assertNotNull(f.create("Dev","content.read",List.of("http://127.0.0.1:8080/cb","http://[::1]:8080/cb")).clientId());
        }
        System.out.println("PASS registration validates current scope vocabulary, display-name bounds and Unicode, duplicate/invalid callbacks and production loopback policy without partial state; development loopback is accepted");
        for(String fault:List.of("client.add.after","redirect.add.after","release.after","commit"))try(var f=new Registry()){
            String before=f.all();f.faults.add(fault);try{f.create("App","content.read",List.of(ApiV3AuthorizationCodeTest.URI,"com.example.client:/cb"));Assert.fail(fault);}catch(Exception expected){}
            Assert.assertEquals(f.all(),before,fault);
        }
        try(var f=new Registry()){
            String before=f.all();f.begin();f.faults.add("redirect.add.after");try{f.registry.createApprovedInCurrentTransaction("App","content.read",List.of(ApiV3AuthorizationCodeTest.URI));Assert.fail();}catch(RepositoryException expected){}f.faults.clear();f.commit();Assert.assertEquals(f.all(),before);
            try{f.registry.createApprovedInCurrentTransaction("App","content.read",List.of(ApiV3AuthorizationCodeTest.URI));Assert.fail();}catch(IllegalStateException expected){}
        }
        System.out.println("PASS client/callback writes, savepoint-release and strict commit failures leave no partial registration; a caught local failure may not leak writes into an owner commit and absence of an owner transaction is rejected");
    }
    interface Work{void run()throws Exception;}
    static void reject(Work work)throws Exception{try{work.run();Assert.fail();}catch(IllegalArgumentException|ApiV3Exception expected){}}
}
