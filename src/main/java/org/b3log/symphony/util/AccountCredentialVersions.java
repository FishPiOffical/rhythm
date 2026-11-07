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
package org.b3log.symphony.util;

import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.symphony.model.AccountCredentialState;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.AccountCredentialStateRepository;
import org.json.JSONObject;

/** Internal authentication snapshots, separate from outward user objects. Reads current SQL on every check. */
public final class AccountCredentialVersions {
    private AccountCredentialVersions() { }
    public record Proof(String actor,long epoch,String password) { }
    private static final ThreadLocal<Proof> CURRENT=new ThreadLocal<>();
    private static final ThreadLocal<Boolean> HTTP=new ThreadLocal<>();
    public static void beginHttp(){clear();HTTP.set(true);}
    public static void endHttp(){clear();HTTP.remove();}
    public static boolean isHttp(){return Boolean.TRUE.equals(HTTP.get());}
    public static Proof saved(){return CURRENT.get();}
    public static boolean hasActiveTransaction(){var tx=JdbcRepository.TX.get();return tx!=null&&tx.isActive();}
    public static void clear(){CURRENT.remove();}
    public static Proof snapshot(String actor){Proof p=CURRENT.get();return p!=null&&p.actor().equals(actor)?p:null;}
    public static long payloadEpoch(JSONObject value){return value.has(AccountCredentialState.EPOCH)?AccountCredentialStateRepository.epoch(value.opt(AccountCredentialState.EPOCH)):0;}
    public static Proof current(String actor) {
        try {
        JSONObject value=BeanManager.getInstance().getReference(AccountCredentialStateRepository.class).current(actor);
        if(value==null||!actor.equals(value.optString("oId"))||AccountCredentialStateRepository.epoch(value.opt("userStatus"))!=UserExt.USER_STATUS_C_VALID
                ||!(value.opt("userPassword") instanceof String password))return null;
        return new Proof(actor,AccountCredentialStateRepository.epoch(value.opt(AccountCredentialState.EPOCH)),password);
        }catch(Exception e){clear();throw new IllegalStateException("credential state unavailable");}
    }
    public static boolean matches(String actor,long expected) {Proof now=current(actor);return now!=null&&now.epoch()==expected;}
    public static boolean authenticate(String actor,JSONObject credential,String password) {
        clear();long expected=payloadEpoch(credential);Proof current=current(actor);
        if(current==null)return false;
        if(current.epoch()==0){
            // state snapshot 尚未写入（存量用户/首次使用），降级为纯密码比对
            // 任何后续凭据变更（改密码/注销）都会 increment epoch 从 0→1 从此进入强校验
            if(!current.password().equals(password))return false;
            CURRENT.set(new Proof(actor,0,password));return true;
        }
        if(current.epoch()!=expected||!current.password().equals(password))return false;
        CURRENT.set(current);return true;
    }
    /** Issuance uses a verified proof, and rechecks it rather than upgrading it to a newer SQL epoch. */
    public static Proof issuance(String actor,String explicitPassword) {
        Proof expected=snapshot(actor),now=current(actor);
        if(expected==null||now==null||expected.epoch()!=now.epoch()||!expected.password().equals(now.password())
                ||explicitPassword!=null&&!explicitPassword.equals(now.password())){clear();return null;}
        return expected;
    }
    public static void remember(Proof proof){clear();if(proof!=null)CURRENT.set(proof);}
}
