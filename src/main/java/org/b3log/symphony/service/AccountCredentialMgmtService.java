package org.b3log.symphony.service;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.repository.*;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.b3log.symphony.model.OpenIdRefreshToken;

/** Owner supplies strict root, client namespace and current user lock before calling. */
@Service
public class AccountCredentialMgmtService {
    @Inject private UserRepository users;
    @Inject private AccountCredentialStateRepository states;
    @Inject private OpenIdRefreshTokenRepository openid;
    @Inject private ApiV3TokenRepository tokens;
    @Inject private ApiV3AuthorizationRequestRepository requests;
    @Inject private ApiV3AuthorizationCodeRepository codes;
    protected long now(){return System.currentTimeMillis();}
    public void revokeInCurrentTransaction(String actor)throws RepositoryException {
        if(!users.hasTransactionBegun()||!states.hasTransactionBegun()||!tokens.hasTransactionBegun())throw new IllegalStateException("credential root required");
        var owner=users.select("SELECT oId FROM `"+users.getName()+"` WHERE oId=? FOR UPDATE",actor);
        if(owner.size()!=1)throw new RepositoryException("account unavailable");
        long time=now();if(time<=0)throw new IllegalStateException("credential clock invalid");
        states.incrementInCurrentTransaction(actor,time);
        ApiV3TransactionStatements.execute("UPDATE `"+openid.getName()+"` SET state=?,updatedAt=? WHERE userId=? AND state=?",OpenIdRefreshToken.STATE_REVOKED,time,actor,OpenIdRefreshToken.STATE_ACTIVE);
        requests.invalidatePendingForUserInCurrentTransaction(actor);
        codes.invalidatePendingForUserInCurrentTransaction(actor);
        tokens.revokeUserInCurrentTransaction(actor,time);
    }
}
