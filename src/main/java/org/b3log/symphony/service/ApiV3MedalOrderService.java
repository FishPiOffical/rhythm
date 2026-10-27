package org.b3log.symphony.service;

import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.MedalRepository;
import org.b3log.symphony.repository.UserMedalRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.util.*;
import org.json.JSONObject;
import java.util.*;

/** Adjacent move within all owner holdings, including hidden/expired, with complete order normalization. */
@Service
public class ApiV3MedalOrderService {
    private static final int MAX_HOLDINGS = 4096;
    @Inject private UserRepository users;
    @Inject private MedalRepository medals;
    @Inject private UserMedalRepository grants;
    @Inject private ApiV3RequestRepository requests;

    public JSONObject move(final String actor, final String medalId, final String direction, final String key) throws Exception {
        if (actor == null || !actor.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
        ApiV3MedalDisplay.medalId(medalId);
        final String normalized = ApiV3MedalOrderInputs.direction(direction);
        try {
            final ApiV3MutationStore guarded = new ApiV3MutationStore() {
                public Transaction beginTransaction() { return ApiV3Transactions.strict(requests.beginTransaction()); }
                public boolean hasTransactionBegun() { return requests.hasTransactionBegun(); }
                public JSONObject getForUpdate(final String id) throws RepositoryException { owner(actor); return requests.getForUpdate(id); }
                public String add(final JSONObject row) throws RepositoryException { return requests.add(row); }
                public void update(final String id, final JSONObject row, final String... names) throws RepositoryException { requests.update(id,row,names); }
            };
            return ApiV3IdempotencyMgmtService.runAtomic(guarded, actor, "medal.order", key,
                    new JSONObject().put("medalId", medalId).put("direction", normalized),
                    () -> moveInCurrentTransaction(actor, medalId, normalized));
        } catch (final ApiV3Exception e) { throw e; }
        catch (final Exception e) { throw new ApiV3Exception(503,"temporarily_unavailable","勋章排序暂不可用"); }
    }
    private void owner(final String actor) throws RepositoryException {
        final var rows = users.select("SELECT oId,userStatus FROM `" + users.getName() + "` WHERE oId=? FOR UPDATE", actor);
        if (rows.size()!=1 || !actor.equals(rows.getFirst().optString("oId"))
                || rows.getFirst().optInt("userStatus",-1)!=UserExt.USER_STATUS_C_VALID) throw ApiV3Requests.notFound();
    }
    public JSONObject moveInCurrentTransaction(final String actor, final String medalId, final String direction) throws Exception {
        if (!grants.hasTransactionBegun()) throw new IllegalStateException("medal order transaction required");
        if (actor==null || !actor.matches("[1-9][0-9]{0,18}")) throw ApiV3WriteInputs.invalid();
        ApiV3MedalDisplay.medalId(medalId);final String normalized=ApiV3MedalOrderInputs.direction(direction);
        return ApiV3TransactionSavepoint.run(() -> {
            owner(actor); // Compatible with display's user -> definition -> holding order.
            final var hints=grants.select("SELECT medal_id FROM `"+grants.getName()+"` WHERE user_id=? LIMIT " + (MAX_HOLDINGS + 1),actor);
            if (hints.size() > MAX_HOLDINGS) throw unavailable();
            final Set<String> identifiers=new TreeSet<>();
            for(final var hint:hints) identifiers.add(storedId(hint.opt("medal_id")));
            if (!identifiers.contains(medalId)) throw ApiV3Requests.notFound();
            // Lock definitions in a stable shared order, before any owned holding lock.
            for(final String id:identifiers) {
                final var definitions=medals.select("SELECT oId,medal_id FROM `"+medals.getName()+"` WHERE medal_id=? ORDER BY oId ASC LIMIT 2 FOR UPDATE",id);
                if(definitions.size()!=1 || !id.equals(definitions.getFirst().optString("medal_id"))) throw ApiV3MedalDisplay.inconsistent();
            }
            final var rows=grants.select("SELECT oId,user_id,medal_id,expire_time,display,display_order FROM `"+grants.getName()+"` WHERE user_id=? ORDER BY oId ASC LIMIT " + (MAX_HOLDINGS + 1) + " FOR UPDATE",actor);
            if (rows.size() > MAX_HOLDINGS) throw unavailable();
            final Set<String> actual=new TreeSet<>(), rowIds=new HashSet<>();
            for(final var row:rows) {
                final String id=storedId(row.opt("medal_id"));
                if(!actor.equals(row.optString("user_id")) || !actual.add(id) || !rowIds.add(storedId(row.opt("oId")))) throw ApiV3MedalDisplay.inconsistent();
                ApiV3MedalDisplay.expiry(row.opt("expire_time"));ApiV3MedalDisplay.stored(row.opt("display"));ApiV3MedalOrderInputs.order(row.opt("display_order"));
            }
            if(!identifiers.equals(actual)) throw ApiV3MedalDisplay.inconsistent();
            final List<JSONObject> ordered=new ArrayList<>(rows);
            ordered.sort(Comparator.<JSONObject>comparingInt(row->ApiV3MedalOrderInputs.order(row.opt("display_order"))).thenComparing(row->row.getString("oId")));
            int position=-1;
            for(int i=0;i<ordered.size();i++) if(medalId.equals(ordered.get(i).getString("medal_id"))) position=i;
            if(position<0) throw ApiV3Requests.notFound();
            final int next="up".equals(normalized)?Math.max(0,position-1):Math.min(ordered.size()-1,position+1);
            Collections.swap(ordered,position,next);
            for(int i=0;i<ordered.size();i++) {
                final JSONObject row=ordered.get(i);
                if(ApiV3MedalOrderInputs.order(row.opt("display_order"))==i) continue;
                final int changed=ApiV3TransactionStatements.execute("UPDATE `"+grants.getName()+"` SET display_order=? WHERE oId=? AND user_id=? AND medal_id=?",i,row.getString("oId"),actor,row.getString("medal_id"));
                if(changed!=1) throw ApiV3MedalDisplay.inconsistent();
            }
            return new JSONObject().put("id",medalId).put("direction",normalized).put("order",next);
        });
    }
    private String storedId(final Object value) {
        if(!(value instanceof String id)) throw ApiV3MedalDisplay.inconsistent();
        try { return ApiV3MedalDisplay.medalId(id); }
        catch(final ApiV3Exception e) { throw ApiV3MedalDisplay.inconsistent(); }
    }
    private static ApiV3Exception unavailable() {
        return new ApiV3Exception(503,"temporarily_unavailable","勋章排序暂不可用");
    }
}
