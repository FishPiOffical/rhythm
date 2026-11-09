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
import org.b3log.latke.Latkes;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.model.ApiV3Client;
import org.b3log.symphony.model.ApiV3ClientRedirect;
import org.b3log.symphony.repository.ApiV3ClientRepository;
import org.b3log.symphony.repository.ApiV3ClientRedirectRepository;
import org.b3log.symphony.util.ApiV3Json;
import org.b3log.symphony.util.ApiV3OAuthRedirects;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.json.JSONObject;

/** 登记已经获批的应用，供管理员注册流程调用。 */
@Service
public class ApiV3ClientRegistrationService {
    @Inject private ApiV3ClientRepository clients;
    @Inject private ApiV3ClientRedirectRepository redirects;
    protected boolean development(){return Latkes.RuntimeMode.DEVELOPMENT==Latkes.getRuntimeMode();}
    protected String rowId(){return Ids.genTimeMillisId();}
    protected String clientId(){return "app_"+ApiV3TokenUtil.generate();}

    /** 只接收公开注册字段，由服务端在此生成 ID。 */
    public record Registered(String clientId,String name,String scope,List<String> redirectUris){
        public Registered{redirectUris=List.copyOf(redirectUris);}
    }

    /** 已批准的创建与管理员完整替换共用此无副作用的规范化逻辑。 */
    public record Definition(String name,String scope,List<String> redirectUris){
        public Definition{redirectUris=List.copyOf(redirectUris);}
    }
    public Definition normalizeDefinition(String name,String scope,List<String> redirectUris){
        final String display=displayName(name),permissions=permissions(scope);
        if(redirectUris==null||redirectUris.isEmpty())throw new IllegalArgumentException("registered redirect required");
        final List<String> callbacks=List.copyOf(redirectUris);
        for(String uri:callbacks)if(!ApiV3OAuthRedirects.valid(uri,development()))throw new IllegalArgumentException("invalid registered redirect");
        if(Set.copyOf(callbacks).size()!=callbacks.size())throw new IllegalArgumentException("duplicate registered redirect");
        return new Definition(display,permissions,callbacks);
    }

    /** 调用方负责审批、认证、幂等与严格根事务提交。 */
    public Registered createApprovedInCurrentTransaction(String name,String scope,List<String> redirectUris)throws Exception {
        if(!clients.hasTransactionBegun()||!redirects.hasTransactionBegun())throw new IllegalStateException("registration owner transaction required");
        final Definition definition=normalizeDefinition(name,scope,redirectUris);
        final String display=definition.name(),permissions=definition.scope();
        final List<String> callbacks=definition.redirectUris();
        return ApiV3TransactionSavepoint.run(()->{
            final String id=clientId(),row=rowId();
            if(id==null||!id.matches("app_[A-Za-z0-9_-]{43}")||row==null||!row.matches("[1-9][0-9]{0,18}"))throw new IllegalStateException("invalid registration identity");
            final JSONObject value=new JSONObject().put(Keys.OBJECT_ID,row).put(ApiV3Client.CLIENT_ID,id)
                    .put(ApiV3Client.NAME,display).put(ApiV3Client.SCOPE,permissions).put(ApiV3Client.STATUS,ApiV3Client.STATUS_ACTIVE);
            if(!row.equals(clients.add(value)))throw new IllegalStateException("client identity changed");
            for(String uri:callbacks){
                final String key=ApiV3ClientRedirectRepository.identity(id,uri);
                final JSONObject redirect=new JSONObject().put(Keys.OBJECT_ID,key).put(ApiV3ClientRedirect.CLIENT_ID,id)
                        .put(ApiV3ClientRedirect.REDIRECT_URI,uri).put(ApiV3ClientRedirect.STATUS,ApiV3ClientRedirect.ACTIVE);
                if(!key.equals(redirects.add(redirect)))throw new IllegalStateException("redirect identity changed");
            }
            return new Registered(id,display,permissions,callbacks);
        });
    }
    private static String displayName(String value){
        if(value==null)throw new IllegalArgumentException("client name required");
        final String name=value.strip();
        if(name.isEmpty()||name.length()>128||name.codePoints().anyMatch(Character::isISOControl))throw new IllegalArgumentException("invalid client name");
        ApiV3Json.canonical(name);return name;
    }
    private static String permissions(String value){
        if(value==null||value.isEmpty()||value.length()>255)throw new IllegalArgumentException("client scope required");
        final Set<String> available=Set.copyOf(Arrays.asList(ApiV3TokenMgmtService.DEFAULT_SCOPES.split(" ")));
        final TreeSet<String> scopes=new TreeSet<>();
        for(String item:value.split(" ",-1)){
            if(!available.contains(item))throw new IllegalArgumentException("unknown client scope");scopes.add(item);
        }
        return String.join(" ",scopes);
    }
}
