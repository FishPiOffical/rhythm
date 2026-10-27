package org.b3log.symphony.service;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.*;
import org.json.JSONObject;

/** Password mutation and complete credential revocation share the caller-owned strict root. */
@Service
public class ApiV3SecuritySettingsService {
    @Inject private ApiV3SecurityProofService proof;
    @Inject private AccountCredentialMgmtService credentials;
    @Inject private ApiV3TokenRepository tokens;
    @Inject private UserRepository users;
    public JSONObject mfa(final JSONObject identity){
        try{final JSONObject before=proof.read(identity);final JSONObject after=proof.read(identity);if(!ApiV3Json.canonical(before).equals(ApiV3Json.canonical(after)))throw ApiV3SecurityProofService.unavailable();return new JSONObject().put("enabled",!before.getString("secret2fa").isEmpty());}
        catch(ApiV3Exception e){throw e;}catch(Exception e){throw ApiV3SecurityProofService.unavailable();}
    }
    public JSONObject password(final JSONObject identity,final JSONObject body,final String ip)throws Exception {
        final var input=ApiV3SecurityInputs.password(body);proof.checkAttempts(identity,ip);
        if(tokens.hasTransactionBegun())throw new IllegalStateException("password root required");
        final Transaction tx;try{tx=ApiV3Transactions.strict(tokens.beginTransaction());}catch(Exception e){throw ApiV3SecurityProofService.unavailable();}String actor=null;
        try{
            final JSONObject current=proof.requireInCurrentTransaction(identity,input.currentPassword(),input.currentTotp());actor=current.getString("oId");
            if(!input.newPassword().equals(current.getString("userPassword"))&&ApiV3TransactionStatements.execute("UPDATE `"+users.getName()+"` SET userPassword=? WHERE oId=?",input.newPassword(),actor)!=1)throw ApiV3SecurityProofService.unavailable();
            credentials.revokeInCurrentTransaction(actor);tx.commit();return new JSONObject().put("changed",true).put("reauthenticationRequired",true);
        }catch(ApiV3Exception e){throw e;}catch(Exception e){throw ApiV3SecurityProofService.unavailable();}
        finally{try{if(tx.isActive())tx.rollback();}finally{if(actor!=null&&!tokens.hasTransactionBegun())try{users.evictSettingsCaches(actor);}catch(RuntimeException ignored){}}}
    }
}
