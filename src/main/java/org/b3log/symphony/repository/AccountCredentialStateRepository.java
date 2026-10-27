package org.b3log.symphony.repository;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.AccountCredentialState;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.json.JSONObject;
import java.math.BigDecimal;

@Repository
public class AccountCredentialStateRepository extends AbstractRepository {
    @Inject private UserRepository users;
    public AccountCredentialStateRepository(){super(AccountCredentialState.TABLE);}
    /** No cache, no default-row write. Missing state is the legacy version zero. */
    public JSONObject current(String actor)throws RepositoryException {
        identity(actor);
        var rows=select("SELECT u.oId,u.userStatus,u.userPassword,COALESCE(s.credentialEpoch,0) AS credentialEpoch FROM `"+users.getName()+"` u LEFT JOIN `"+getName()+"` s ON s.oId=u.oId WHERE u.oId=?"+(hasTransactionBegun()?" FOR UPDATE":""),actor);
        if(rows.size()!=1)return null;return rows.getFirst();
    }
    /** Caller first locks clients then the current user. That user lock guards first-row creation. */
    public long incrementInCurrentTransaction(String actor,long time)throws RepositoryException {
        identity(actor);if(!hasTransactionBegun())throw new IllegalStateException("credential root required");
        var rows=select("SELECT oId,credentialEpoch FROM `"+getName()+"` WHERE oId=? FOR UPDATE",actor);
        if(rows.size()>1)throw new RepositoryException("credential state invalid");
        long before=rows.isEmpty()?0:epoch(rows.getFirst().opt(AccountCredentialState.EPOCH));
        if(before==Long.MAX_VALUE)throw new RepositoryException("credential version exhausted");
        long after=before+1;
        int changed=rows.isEmpty()?ApiV3TransactionStatements.execute("INSERT INTO `"+getName()+"` (oId,credentialEpoch,updatedAt) VALUES (?,?,?)",actor,after,time)
                :ApiV3TransactionStatements.execute("UPDATE `"+getName()+"` SET credentialEpoch=?,updatedAt=? WHERE oId=? AND credentialEpoch=?",after,time,actor,before);
        if(changed!=1)throw new RepositoryException("credential state write failed");return after;
    }
    public static long epoch(Object value) {
        if(!(value instanceof Number n))throw new IllegalArgumentException("credential version invalid");
        long version=new BigDecimal(n.toString()).longValueExact();if(version<0)throw new IllegalArgumentException("credential version invalid");return version;
    }
    private static void identity(String actor){if(actor==null||!actor.matches("[1-9][0-9]{0,18}"))throw new IllegalArgumentException("account identity");}
}
