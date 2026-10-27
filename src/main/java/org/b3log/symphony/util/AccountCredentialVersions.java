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
        if(current==null||current.epoch()!=expected||!current.password().equals(password))return false;
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
