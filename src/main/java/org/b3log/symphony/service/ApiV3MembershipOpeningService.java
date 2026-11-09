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

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.Transaction;
import org.b3log.latke.service.annotation.Service;
import org.b3log.latke.util.Ids;
import org.b3log.symphony.util.Escapes;
import org.b3log.symphony.cache.MembershipCache;
import org.b3log.symphony.model.Pointtransfer;
import org.b3log.symphony.model.PointtransferSource;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ApiV3RequestRepository;
import org.b3log.symphony.repository.CouponRepository;
import org.b3log.symphony.repository.MembershipActivationRepository;
import org.b3log.symphony.repository.MembershipLevelRepository;
import org.b3log.symphony.repository.MembershipRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.repository.PointtransferRepository;
import org.b3log.symphony.repository.PointtransferRedcRepository;
import org.b3log.symphony.repository.CloudRepository;
import org.b3log.symphony.util.ApiV3Exception;
import org.b3log.symphony.util.ApiV3MembershipConfiguration;
import org.b3log.symphony.util.ApiV3MembershipOpeningInputs;
import org.b3log.symphony.util.ApiV3MutationStore;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3Transactions;
import org.b3log.symphony.util.ApiV3TransactionSavepoint;
import org.b3log.symphony.util.ApiV3TransactionStatements;
import org.b3log.symphony.util.ArticlePublicationCaches;
import org.b3log.symphony.util.ApiV3Json;
import org.b3log.symphony.util.ApiV3TokenUtil;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

/** 购买、优惠券核销、会员开通及新手礼包与回执在同一个严格事务中提交。 */
@Service
public class ApiV3MembershipOpeningService {
    @Inject private UserRepository users;
    @Inject private MembershipLevelRepository levels;
    @Inject private MembershipRepository memberships;
    @Inject private MembershipActivationRepository activations;
    @Inject private CouponRepository coupons;
    @Inject private ApiV3RequestRepository requests;
    @Inject private PointtransferMgmtService points;
    @Inject private BagMgmtService bags;
    @Inject private MembershipCache cache;
    @Inject private PointtransferRepository transfers;
    @Inject private PointtransferRedcRepository transferIndex;
    @Inject private CloudRepository cloud;
    protected long now() {
        return System.currentTimeMillis();
    }

    protected ZoneId zone() {
        return ZoneId.systemDefault();
    }

    public JSONObject open(final String actor, final ApiV3MembershipOpeningInputs.Opening input, final String key) throws Exception {
        if (actor==null || !actor.matches("[1-9][0-9]{0,18}")) throw ApiV3Requests.invalid();
        final var normalized=ApiV3MembershipOpeningInputs.parse(input.normalized());
        final String billingId=ApiV3TokenUtil.hash("membership.open\0"+actor+"\0"+key);
        try {
            final JSONObject durable=points.executeWithTransferLocks(actor,List.of(Pointtransfer.ID_C_SYS),()-> {
                try (final var boundary=ArticlePublicationCaches.open(requests::hasTransactionBegun)) {
                    final ApiV3MutationStore store=new ApiV3MutationStore() {
                        public Transaction beginTransaction() {
                            return ApiV3Transactions.strict(requests.beginTransaction());
                        }

                        public boolean hasTransactionBegun() {
                            return requests.hasTransactionBegun();
                        }

                        public JSONObject getForUpdate(String id) throws RepositoryException {
                            requireActor(actor);
                            return requests.getForUpdate(id);
                        }

                        public String add(JSONObject value) throws RepositoryException {
                            return requests.add(value);
                        }

                        public void update(String id, JSONObject value, String... fields) throws RepositoryException {
                            requests.update(id, value, fields);
                        }
                    };
                    return ApiV3IdempotencyMgmtService.runAtomic(store,actor,"membership.open",key,normalized.normalized(),
                            ()->openInCurrentTransaction(actor,normalized,billingId));
                }
            });
            final JSONObject result=publicResult(durable);
            // 回放也会清除缓存，避免旧读取入口看到事务内的快照。
            invalidate(actor);
            index(actor,billingId,durable.getString("indexTransferId"));
            return result;
        } catch (ApiV3Exception e) {
            invalidate(actor);
            throw e;
        } catch (Exception e) {
            invalidate(actor);
            throw unavailable();
        }
    }
    private void invalidate(final String actor) {
        try {
            cache.remove(actor);
        } catch (RuntimeException e) {
            LogManager.getLogger(getClass()).warn("Membership cache invalidation delayed");
        }
    }
    private void index(final String actor, final String billingId, final String transferId) {
        Transaction transaction=null;
        try {
            if (transfers.hasTransactionBegun()) throw new IllegalStateException("committed transfer indexing required");
            transaction=ApiV3Transactions.strict(transfers.beginTransaction());
            final var rows=transfers.select("SELECT oId FROM `"+transfers.getName()+"` WHERE oId=? AND fromId=? AND toId=? AND type=? AND sourceScene=? AND sourceRequestId=? LIMIT 2 FOR UPDATE",
                    transferId,actor,Pointtransfer.ID_C_SYS,Pointtransfer.TRANSFER_TYPE_C_ABUSE_DEDUCT,"membership.open",billingId);
            if (rows.size()!=1 || !rows.getFirst().optString("oId").matches("[1-9][0-9]{0,18}")) throw unavailable();
            final String raw=rows.getFirst().getString("oId");
            // 以已提交的主账目行锁串行处理回放修复，旧异步写入按原方式运行。
            for (final String owner:List.of(actor,Pointtransfer.ID_C_SYS)) {
                final var existing=transferIndex.select("SELECT oId FROM `"+transferIndex.getName()+"` WHERE userId=? AND rawId=? LIMIT 2 FOR UPDATE",owner,raw);
                if (existing.isEmpty() && ApiV3TransactionStatements.execute("INSERT INTO `"+transferIndex.getName()+"` (oId,userId,rawId) VALUES (?,?,?)",Ids.genTimeMillisId(),owner,raw)!=1) throw unavailable();
            }
            transaction.commit();
        } catch (Exception e) {
            if (transaction != null && transaction.isActive())
                try {
                    transaction.rollback();
                } catch (RuntimeException ignored) { }
            LogManager.getLogger(getClass()).warn("Membership transfer indexing delayed");
        }
    }
    private JSONObject requireActor(final String actor)throws RepositoryException {
        final var rows=users.select("SELECT oId,userStatus,userPoint FROM `"+users.getName()+"` WHERE oId=? FOR UPDATE",actor);
        if (rows.size()!=1 || !actor.equals(rows.getFirst().optString("oId")) || number(rows.getFirst().opt("userStatus"))!=UserExt.USER_STATUS_C_VALID) throw ApiV3Requests.notFound();
        final long balance=number(rows.getFirst().opt("userPoint"));
        if (balance<Integer.MIN_VALUE || balance>Integer.MAX_VALUE) throw unavailable();
        return rows.getFirst();
    }
    private JSONObject openInCurrentTransaction(final String actor, final ApiV3MembershipOpeningInputs.Opening input, final String billingId)throws Exception {
        if (!requests.hasTransactionBegun()) throw new IllegalStateException("membership opening transaction required");
        return ApiV3TransactionSavepoint.run(()-> {
            final JSONObject owner=requireActor(actor);
            final var planRows=levels.select("SELECT oId,lvCode,lvName,price,durationType,durationValue,benefits FROM `"+levels.getName()+"` WHERE oId=? LIMIT 2 FOR UPDATE",input.levelId());
            if (planRows.isEmpty()) throw ApiV3Requests.notFound();
            if (planRows.size()!=1) throw unavailable();
            final JSONObject plan=planRows.getFirst();
            final String levelId=text(plan,"oId",19), code=text(plan,"lvCode",32), name=text(plan,"lvName",64), duration=text(plan,"durationType",16);
            if (!input.levelId().equals(levelId)) throw unavailable();
            final long price=number(plan.opt("price")), days=number(plan.opt("durationValue"));
            if (price<0 || price>Integer.MAX_VALUE || days<1 || days>Integer.MAX_VALUE) throw unavailable();
            final JSONObject config=ApiV3MembershipConfiguration.defaults(plan);
            final long now=now();
            final ZoneId zone=zone();
            final long expires;
            try { expires=Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1L+days)
                    .atStartOfDay(zone).toInstant().toEpochMilli(); }
            catch (RuntimeException e) { throw unavailable(); }
            final JSONObject coupon=coupon(input.couponCode());
            final int paid=coupon==null?(int)price:Math.toIntExact((price*number(coupon.opt("discount"))+50L)/100L);
            final var prior=memberships.select("SELECT oId,userId,lvCode,state,expiresAt FROM `"+memberships.getName()+"` WHERE userId=? ORDER BY oId ASC LIMIT 2 FOR UPDATE",actor);
            if (prior.size()>1) throw unavailable();
            final JSONObject previous=prior.isEmpty()?null:prior.getFirst();
            if (previous!=null) {
                final long state=number(previous.opt("state")), until=number(previous.opt("expiresAt"));
                if (state<0 || state>1 || until<0 || !actor.equals(previous.optString("userId"))) throw unavailable();
                if (until==0 || state==1 && until>now) throw new ApiV3Exception(409,"membership_active","当前会员不可重复开通");
            }
            if (number(owner.opt("userPoint"))<paid) throw new ApiV3Exception(409,"insufficient_points","积分不足");
            final String receiptId=Ids.genTimeMillisId();
            final String transferId=points.transferInCurrentTransaction(actor,Pointtransfer.ID_C_SYS,Pointtransfer.TRANSFER_TYPE_C_ABUSE_DEDUCT,
                    paid,"开通 "+label(name,48)+"("+label(duration,16)+") 会员\n原价："+price+"(优惠价："+paid+")",now,"",
                    new PointtransferSource("","","membership.open",billingId),false);
            if (transferId==null || !transferId.matches("[1-9][0-9]{0,18}")) throw unavailable();
            if (coupon!=null && number(coupon.opt("times"))>0) {
                final int changed=ApiV3TransactionStatements.execute("UPDATE `"+coupons.getName()+"` SET times=?,updatedAt=? WHERE oId=? AND coupon_code=?",
                        number(coupon.opt("times"))-1L,now,coupon.getString("oId"),input.couponCode());
                if (changed!=1) throw unavailable();
            }
            final String membershipId=previous==null?Ids.genTimeMillisId():storedId(previous,"oId");
            final int changed=previous==null?ApiV3TransactionStatements.execute("INSERT INTO `"+memberships.getName()+"` (oId,userId,lvCode,state,expiresAt,configJson,createdAt,updatedAt) VALUES (?,?,?,?,?,?,?,?)",
                    membershipId,actor,code,1,expires,config.toString(),now,now):ApiV3TransactionStatements.execute("UPDATE `"+memberships.getName()+"` SET lvCode=?,state=?,expiresAt=?,configJson=?,updatedAt=? WHERE oId=? AND userId=?",
                    code,1,expires,config.toString(),now,membershipId,actor);
            if (changed!=1) throw unavailable();
            if (ApiV3TransactionStatements.execute("INSERT INTO `"+activations.getName()+"` (oId,userId,lvCode,price,durationType,durationValue,couponCode,configJson,createdAt,updatedAt) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    receiptId,actor,code,paid,duration,days,input.couponCode(),config.toString(),now,now)!=1) throw unavailable();
            gift(actor,code);
            final JSONObject durable=new JSONObject().put("result",new JSONObject().put("id",membershipId).put("levelCode",code).put("paidPoints",paid).put("expiresAt",expires))
                    .put("indexTransferId",transferId);
            publicResult(durable);
            return durable;
        });
    }
    private JSONObject coupon(final String code)throws RepositoryException {
        if (code.isEmpty()) return null;
        final var rows=coupons.select("SELECT oId,coupon_code,coupon_type,discount,times FROM `"+coupons.getName()+"` WHERE coupon_code=? ORDER BY oId ASC LIMIT 2 FOR UPDATE",code);
        if (rows.isEmpty()) throw invalidCoupon();
        if (rows.size()!=1) throw unavailable();
        final var coupon=rows.getFirst();
        final long type=number(coupon.opt("coupon_type")), discount=number(coupon.opt("discount")), times=number(coupon.opt("times"));
        if (type!=0 || discount<0 || discount>100 || times==0 || times< -1 || times>Integer.MAX_VALUE) throw invalidCoupon();
        storedId(coupon, "oId");
        return coupon;
    }
    private void gift(final String actor, final String code)throws RepositoryException {
        final int quantity = switch (code) {
            case "VIP1_YEAR" -> 20;
            case "VIP2_YEAR" -> 50;
            case "VIP3_YEAR" -> 120;
            case "VIP4_YEAR" -> 366;
            default -> 0;
        };
        if (quantity==0) return;
        if (cloud.select("SELECT oId FROM `"+cloud.getName()+"` WHERE userId=? AND gameId=? ORDER BY oId ASC LIMIT 2 FOR UPDATE",actor,"sys-bag").size()>1) throw unavailable();
        final String item="VIP4_YEAR".equals(code)?"sysCheckinRemain":"checkin1day";
        bags.mutateInCurrentTransaction(actor,bag->{
            final long before=bag.has(item)?number(bag.opt(item)):0L;
            if (before<0 || before>Integer.MAX_VALUE) throw unavailable();
            bag.put(item, Math.min(Integer.MAX_VALUE, before + quantity));
            return bag;
        });
    }
    private static long number(final Object value) {
        if (!(value instanceof Number number)) throw unavailable();
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw unavailable();
        }
    }
    private static JSONObject publicResult(final JSONObject durable) {
        if (!durable.keySet().equals(Set.of("result","indexTransferId")) || !(durable.opt("result") instanceof JSONObject value)
                || !durable.optString("indexTransferId").matches("[1-9][0-9]{0,18}")
                || !value.keySet().equals(Set.of("id","levelCode","paidPoints","expiresAt"))
                || !value.optString("id").matches("[1-9][0-9]{0,18}")) throw unavailable();
        final String code = text(value, "levelCode", 32);
        final long paid = number(value.opt("paidPoints")), expires = number(value.opt("expiresAt"));
        if (paid<0 || paid>Integer.MAX_VALUE || expires<1) throw unavailable();
        return new JSONObject().put("id",value.getString("id")).put("levelCode",code).put("paidPoints",paid).put("expiresAt",expires);
    }
    private static String text(final JSONObject value, final String key, final int max) {
        if (!(value.opt(key) instanceof String text) || text.isBlank() || text.length()>max || text.codePoints().anyMatch(Character::isISOControl)) throw unavailable();
        try {
            ApiV3Json.canonical(text);
        } catch (IllegalArgumentException e) {
            throw unavailable();
        }
        return text;
    }
    private static String storedId(final JSONObject row, final String key) {
        final String value=text(row,key,19);
        if (!value.matches("[1-9][0-9]{0,18}")) throw unavailable();
        return value;
    }
    private static String label(final String value, final int max) {
        final StringBuilder result=new StringBuilder();
        final var chars=value.codePoints().iterator();
        while(chars.hasNext()) {
            final String encoded=Escapes.escapeHTML(new String(Character.toChars(chars.nextInt())));
            if (result.length()+encoded.length()>max) break;
            result.append(encoded);
        }
        return result.toString();
    }
    private static ApiV3Exception invalidCoupon() {
        return new ApiV3Exception(422, "invalid_coupon", "优惠券不可用");
    }

    public static ApiV3Exception unavailable() {
        return new ApiV3Exception(503, "temporarily_unavailable", "会员开通暂不可用");
    }
}
