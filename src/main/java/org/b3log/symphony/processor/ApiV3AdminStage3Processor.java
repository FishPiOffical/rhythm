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
package org.b3log.symphony.processor;

import org.apache.commons.lang.StringUtils;
import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.model.Common;
import org.b3log.symphony.model.Domain;
import org.b3log.symphony.model.Invitecode;
import org.b3log.symphony.model.Milestone;
import org.b3log.symphony.model.Role;
import org.b3log.symphony.model.Tag;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.processor.middleware.ApiV3WriteMidware;
import org.b3log.symphony.service.ApiV3AdminAccessService;
import org.b3log.symphony.service.ApiV3AdminAssetService;
import org.b3log.symphony.service.ApiV3AdminOpsService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import org.json.JSONObject;

import java.util.Set;

/**
 * API v3 管理后台三期处理器（角色权限、邀请码、VIP/优惠券、勋章、积分/工资/背包、审核扩展、领域/标签/保留词/广告位）。
 */
@Singleton
public class ApiV3AdminStage3Processor {

    private static final int MAX_EMPTY_BODY_BYTES = 1024;
    private static final int MAX_WRITE_BODY_BYTES = 64 * 1024;
    private static final int MAX_BATCH_BODY_BYTES = 256 * 1024;

    private static final Set<String> ROLE_ADD_FIELDS = Set.of(Role.ROLE_NAME, Role.ROLE_DESCRIPTION);
    private static final Set<String> ROLE_PERMS_FIELDS = Set.of("permissionIds");
    private static final Set<String> INVITECODE_GEN_FIELDS = Set.of(Common.QUANTITY, Invitecode.MEMO);
    private static final Set<String> INVITECODE_UPDATE_FIELDS = Set.of(Invitecode.STATUS, Invitecode.MEMO);

    private static final Set<String> VIP_LEVEL_FIELDS = Set.of(
            "oId", "lvCode", "name", "description", "price", "durationDays", "sortOrder", "enabled",
            "features", "starterBag", "badge", "color", "icon", "priority");
    private static final Set<String> VIP_USER_ADD_FIELDS = Set.of("target", "levelOId", "configJson");
    private static final Set<String> VIP_USER_UPDATE_FIELDS = Set.of("lvCode", "state", "expiresAt", "configJson");
    private static final Set<String> VIP_USER_EXTEND_FIELDS = Set.of("days");
    private static final Set<String> COUPON_ADD_FIELDS = Set.of("couponCode", "couponType", "discount", "times");
    private static final Set<String> COUPON_UPDATE_FIELDS = Set.of("times");

    private static final Set<String> MEDAL_DEF_FIELDS = Set.of("name", "type", "description", "attr");
    private static final Set<String> MEDAL_HOLD_CHECK_FIELDS = Set.of("medalId", "targets");
    private static final Set<String> MEDAL_GRANT_FIELDS = Set.of("target", "medalId", "expireTime", "data");
    private static final Set<String> MEDAL_REVOKE_FIELDS = Set.of("target", "medalId");
    private static final Set<String> MEDAL_BATCH_GRANT_FIELDS = Set.of("medalId", "targets", "expireTime", "data");

    private static final Set<String> POINT_CHARGE_FIELDS = Set.of("target", "amount", "mode", "memo");
    private static final Set<String> POINT_OP_FIELDS = Set.of("target", Common.POINT, "memo");
    private static final Set<String> SALARY_PAY_FIELDS = Set.of("items");
    private static final Set<String> BAG_ADJUST_FIELDS = Set.of("target", "item", "number", "maxTake");

    private static final Set<String> FISH_GAME_REVIEW_FIELDS = Set.of(
            "status", "fishGameName", "fishGameUrl", "fishGameTag", "fishGameDescription",
            "fishGameIcon", "fishGameRejectReason");
    private static final Set<String> FISH_GAME_EDIT_FIELDS = Set.of(
            "fishGameName", "fishGameUrl", "fishGameTag", "fishGameDescription",
            "fishGameIcon", "fishGameStatus", "fishGameOrder");
    private static final Set<String> FISH_GAME_IMPORT_FIELDS = Set.of("items");

    private static final Set<String> MILESTONE_UPDATE_FIELDS = Set.of(
            Milestone.MILESTONE_TITLE, Milestone.MILESTONE_DATE, Milestone.MILESTONE_END_DATE,
            Milestone.MILESTONE_CONTENT, Milestone.MILESTONE_LINK, Milestone.MILESTONE_STATUS);
    private static final Set<String> IMAGE_REVIEW_FIELDS = Set.of("decision");

    private static final Set<String> DOMAIN_FIELDS = Set.of(
            Domain.DOMAIN_TITLE, Domain.DOMAIN_URI, Domain.DOMAIN_DESCRIPTION, Domain.DOMAIN_ICON_PATH,
            Domain.DOMAIN_CSS, Domain.DOMAIN_SORT, Domain.DOMAIN_STATUS, Domain.DOMAIN_NAV, Domain.DOMAIN_TYPE);
    private static final Set<String> DOMAIN_TAG_BIND_FIELDS = Set.of(Tag.TAG_TITLE);
    private static final Set<String> TAG_ADD_FIELDS = Set.of(Tag.TAG_TITLE);
    private static final Set<String> TAG_UPDATE_FIELDS = Set.of(
            Tag.TAG_TITLE, Tag.TAG_URI, Tag.TAG_DESCRIPTION, Tag.TAG_ICON_PATH, Tag.TAG_STATUS);
    private static final Set<String> RESERVED_WORD_FIELDS = Set.of("word");
    private static final Set<String> AD_UPDATE_FIELDS = Set.of("sideFullAd", "headerBanner");

    @Inject
    private ApiV3AdminAccessService accessService;

    @Inject
    private ApiV3AdminAssetService assetService;

    @Inject
    private ApiV3AdminOpsService opsService;

    /**
     * 声明管理后台三期全部 V3 路由映射；保持方法体内无条件或循环语句以符合契约审计规范。
     */
    public static void register() {
        final BeanManager beans = BeanManager.getInstance();
        final ApiV3AdminStage3Processor p = beans.getReference(ApiV3AdminStage3Processor.class);
        final ApiV3AuthMidware auth = beans.getReference(ApiV3AuthMidware.class);
        final ApiV3WriteMidware writes = beans.getReference(ApiV3WriteMidware.class);

        // 角色与权限
        Dispatcher.get("/api/v3/admin/roles", p::listRoles, auth::profileRead);
        Dispatcher.post("/api/v3/admin/roles", p::addRole, auth::settingsWrite, writes::handle);
        Dispatcher.delete("/api/v3/admin/roles/{roleId}", p::removeRole, auth::settingsWrite, writes::handle);
        Dispatcher.get("/api/v3/admin/roles/{roleId}/permissions", p::getRolePermissions, auth::profileRead);
        Dispatcher.put("/api/v3/admin/roles/{roleId}/permissions", p::updateRolePermissions, auth::settingsWrite, writes::handle);

        // 邀请码
        Dispatcher.get("/api/v3/admin/invitecodes", p::listInvitecodes, auth::profileRead);
        Dispatcher.post("/api/v3/admin/invitecodes/generate", p::generateInvitecodes, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/admin/invitecodes/{invitecodeId}", p::updateInvitecode, auth::settingsWrite, writes::handle);

        // VIP 与优惠券
        Dispatcher.get("/api/v3/admin/vip/levels", p::listVipLevels, auth::profileRead);
        Dispatcher.post("/api/v3/admin/vip/levels", p::addVipLevel, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/admin/vip/levels/{levelId}", p::updateVipLevel, auth::settingsWrite, writes::handle);
        Dispatcher.delete("/api/v3/admin/vip/levels/{levelId}", p::removeVipLevel, auth::settingsWrite, writes::handle);
        Dispatcher.get("/api/v3/admin/vip/users", p::listUserVips, auth::profileRead);
        Dispatcher.post("/api/v3/admin/vip/users", p::addUserVip, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/admin/vip/users/{userId}", p::updateUserVip, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/vip/users/{userId}/extend", p::extendUserVip, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/vip/users/{userId}/refund", p::refundUserVip, auth::settingsWrite, writes::handle);
        Dispatcher.get("/api/v3/admin/coupons", p::listCoupons, auth::profileRead);
        Dispatcher.post("/api/v3/admin/coupons", p::addCoupon, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/admin/coupons/{couponId}", p::updateCouponTimes, auth::settingsWrite, writes::handle);
        Dispatcher.delete("/api/v3/admin/coupons/{couponId}", p::removeCoupon, auth::settingsWrite, writes::handle);

        // 勋章定义、查询与批量授予
        Dispatcher.get("/api/v3/admin/medals", p::listMedals, auth::profileRead);
        Dispatcher.post("/api/v3/admin/medals", p::addMedal, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/admin/medals/{medalId}", p::updateMedal, auth::settingsWrite, writes::handle);
        Dispatcher.delete("/api/v3/admin/medals/{medalId}", p::deleteMedal, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/medals/sponsor/reorder", p::reorderSponsorMedals, auth::settingsWrite, writes::handle);
        Dispatcher.get("/api/v3/admin/medals/{medalId}/owners", p::listMedalOwners, auth::profileRead);
        Dispatcher.get("/api/v3/admin/medals/users/{target}", p::listUserMedals, auth::profileRead);
        Dispatcher.post("/api/v3/admin/medals/hold-check", p::checkMedalHoldStatuses, auth::profileRead);
        Dispatcher.post("/api/v3/admin/medals/grant", p::grantMedal, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/medals/revoke", p::revokeMedal, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/medals/batch-grant", p::batchGrantMedals, auth::settingsWrite, writes::handle);

        // 积分、工资与背包
        Dispatcher.post("/api/v3/admin/points/charge", p::chargePoint, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/points/deduct", p::deductPoint, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/points/init", p::initPoint, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/points/exchange", p::exchangePoint, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/salary/pay", p::paySalary, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/bags/adjust", p::adjustBag, auth::settingsWrite, writes::handle);

        // 鱼游、大事记与图片审核
        Dispatcher.get("/api/v3/admin/fish-games", p::listFishGames, auth::profileRead);
        Dispatcher.post("/api/v3/admin/fish-games", p::addFishGame, auth::settingsWrite, writes::handle);
        Dispatcher.get("/api/v3/admin/fish-games/export", p::exportFishGames, auth::profileRead);
        Dispatcher.post("/api/v3/admin/fish-games/import", p::importFishGames, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/admin/fish-games/{gameId}", p::updateFishGame, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/fish-games/{gameId}/review", p::reviewFishGame, auth::settingsWrite, writes::handle);

        Dispatcher.get("/api/v3/admin/milestones", p::listMilestones, auth::profileRead);
        Dispatcher.put("/api/v3/admin/milestones/{milestoneId}", p::updateMilestone, auth::settingsWrite, writes::handle);
        Dispatcher.delete("/api/v3/admin/milestones/{milestoneId}", p::removeMilestone, auth::settingsWrite, writes::handle);

        Dispatcher.get("/api/v3/admin/images", p::listImages, auth::profileRead);
        Dispatcher.post("/api/v3/admin/images/{imageId}/review", p::reviewImage, auth::settingsWrite, writes::handle);

        // 领域、标签、保留词与广告位
        Dispatcher.get("/api/v3/admin/domains", p::listDomains, auth::profileRead);
        Dispatcher.post("/api/v3/admin/domains", p::addDomain, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/admin/domains/{domainId}", p::updateDomain, auth::settingsWrite, writes::handle);
        Dispatcher.delete("/api/v3/admin/domains/{domainId}", p::removeDomain, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/domains/{domainId}/tags", p::bindDomainTag, auth::settingsWrite, writes::handle);
        Dispatcher.delete("/api/v3/admin/domains/{domainId}/tags/{tagId}", p::unbindDomainTag, auth::settingsWrite, writes::handle);

        Dispatcher.get("/api/v3/admin/tags", p::listTags, auth::profileRead);
        Dispatcher.post("/api/v3/admin/tags", p::addTag, auth::settingsWrite, writes::handle);
        Dispatcher.put("/api/v3/admin/tags/{tagId}", p::updateTag, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/tags/remove-unused", p::removeUnusedTags, auth::settingsWrite, writes::handle);

        Dispatcher.get("/api/v3/admin/reserved-words", p::listReservedWords, auth::profileRead);
        Dispatcher.post("/api/v3/admin/reserved-words", p::addReservedWord, auth::settingsWrite, writes::handle);
        Dispatcher.post("/api/v3/admin/reserved-words/remove", p::removeReservedWord, auth::settingsWrite, writes::handle);

        Dispatcher.get("/api/v3/admin/ads", p::getAds, auth::profileRead);
        Dispatcher.put("/api/v3/admin/ads", p::updateAds, auth::settingsWrite, writes::handle);
    }

    // ==================== 角色与邀请码 ====================

    public void listRoles(final RequestContext context) {
        ApiV3Requests.read(context, () -> accessService.listRoles(
                identity(context), ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    public void addRole(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, ROLE_ADD_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return accessService.addRole(
                    identity(context), ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void removeRole(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true, MAX_EMPTY_BODY_BYTES);
            return accessService.removeRole(
                    identity(context), ApiV3Requests.id(context, "roleId"),
                    ApiV3WriteInputs.idempotencyKey(context), context.getRequest());
        });
    }

    public void getRolePermissions(final RequestContext context) {
        ApiV3Requests.read(context, () -> accessService.getRolePermissions(
                identity(context), ApiV3Requests.id(context, "roleId")));
    }

    public void updateRolePermissions(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, ROLE_PERMS_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return accessService.updateRolePermissions(
                    identity(context), ApiV3Requests.id(context, "roleId"),
                    ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void listInvitecodes(final RequestContext context) {
        ApiV3Requests.read(context, () -> accessService.listInvitecodes(
                identity(context),
                ApiV3Requests.page(context),
                ApiV3Requests.size(context),
                context.param("keyword"),
                optionalInt(context.param("status"))));
    }

    public void generateInvitecodes(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, INVITECODE_GEN_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return accessService.generateInvitecodes(
                    identity(context), ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void updateInvitecode(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, INVITECODE_UPDATE_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return accessService.updateInvitecode(
                    identity(context), ApiV3Requests.id(context, "invitecodeId"),
                    ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    // ==================== VIP 与优惠券 ====================

    public void listVipLevels(final RequestContext context) {
        ApiV3Requests.read(context, () -> assetService.listVipLevels(identity(context)));
    }

    public void addVipLevel(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, VIP_LEVEL_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.addVipLevel(identity(context), ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void updateVipLevel(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, VIP_LEVEL_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.updateVipLevel(
                    identity(context), ApiV3Requests.id(context, "levelId"),
                    ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void removeVipLevel(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true, MAX_EMPTY_BODY_BYTES);
            return assetService.removeVipLevel(
                    identity(context), ApiV3Requests.id(context, "levelId"), ApiV3WriteInputs.idempotencyKey(context));
        });
    }

    public void listUserVips(final RequestContext context) {
        ApiV3Requests.read(context, () -> assetService.listUserVips(
                identity(context),
                ApiV3Requests.page(context),
                ApiV3Requests.size(context),
                context.param("userName"),
                context.param("lvCode"),
                optionalInt(context.param("state"))));
    }

    public void addUserVip(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, VIP_USER_ADD_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.addUserVip(identity(context), ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void updateUserVip(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, VIP_USER_UPDATE_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.updateUserVip(
                    identity(context), ApiV3Requests.id(context, "userId"),
                    ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void extendUserVip(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, VIP_USER_EXTEND_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.extendUserVip(
                    identity(context), ApiV3Requests.id(context, "userId"),
                    ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void refundUserVip(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true, MAX_EMPTY_BODY_BYTES);
            return assetService.refundUserVip(
                    identity(context), ApiV3Requests.id(context, "userId"), ApiV3WriteInputs.idempotencyKey(context));
        });
    }

    public void listCoupons(final RequestContext context) {
        ApiV3Requests.read(context, () -> assetService.listCoupons(identity(context)));
    }

    public void addCoupon(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, COUPON_ADD_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.addCoupon(identity(context), ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void updateCouponTimes(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, COUPON_UPDATE_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.updateCouponTimes(
                    identity(context), ApiV3Requests.id(context, "couponId"),
                    ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void removeCoupon(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true, MAX_EMPTY_BODY_BYTES);
            return assetService.removeCoupon(
                    identity(context), ApiV3Requests.id(context, "couponId"), ApiV3WriteInputs.idempotencyKey(context));
        });
    }

    // ==================== 勋章 ====================

    public void listMedals(final RequestContext context) {
        ApiV3Requests.read(context, () -> assetService.listMedals(
                identity(context),
                ApiV3Requests.page(context),
                ApiV3Requests.size(context),
                context.param("keyword")));
    }

    public void addMedal(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, MEDAL_DEF_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.addMedal(identity(context), ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void updateMedal(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, MEDAL_DEF_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.updateMedal(
                    identity(context), ApiV3Requests.id(context, "medalId"),
                    ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void deleteMedal(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true, MAX_EMPTY_BODY_BYTES);
            return assetService.deleteMedal(
                    identity(context), ApiV3Requests.id(context, "medalId"), ApiV3WriteInputs.idempotencyKey(context));
        });
    }

    public void reorderSponsorMedals(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true, MAX_EMPTY_BODY_BYTES);
            return assetService.reorderSponsorMedals(identity(context), ApiV3WriteInputs.idempotencyKey(context));
        });
    }

    public void listMedalOwners(final RequestContext context) {
        ApiV3Requests.read(context, () -> assetService.listMedalOwners(
                identity(context), ApiV3Requests.id(context, "medalId")));
    }

    public void listUserMedals(final RequestContext context) {
        ApiV3Requests.read(context, () -> assetService.listUserMedals(
                identity(context), context.pathVar("target")));
    }

    public void checkMedalHoldStatuses(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, MEDAL_HOLD_CHECK_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.checkMedalHoldStatuses(identity(context), body);
        });
    }

    public void grantMedal(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, MEDAL_GRANT_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.grantMedal(identity(context), ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void revokeMedal(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, MEDAL_REVOKE_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.revokeMedal(identity(context), ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void batchGrantMedals(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, MEDAL_BATCH_GRANT_FIELDS, false, MAX_BATCH_BODY_BYTES);
            return assetService.batchGrantMedals(identity(context), ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    // ==================== 积分、工资与背包 ====================

    public void chargePoint(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, POINT_CHARGE_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.chargePoint(
                    identity(context), ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void deductPoint(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, POINT_OP_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.deductPoint(
                    identity(context), ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void initPoint(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, POINT_OP_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.initPoint(
                    identity(context), ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void exchangePoint(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, POINT_OP_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.exchangePoint(
                    identity(context), ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void paySalary(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, SALARY_PAY_FIELDS, false, MAX_BATCH_BODY_BYTES);
            return assetService.paySalary(identity(context), ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void adjustBag(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, BAG_ADJUST_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return assetService.adjustBag(identity(context), ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    // ==================== 鱼游、大事记与图片审核 ====================

    public void listFishGames(final RequestContext context) {
        ApiV3Requests.read(context, () -> opsService.listFishGames(
                identity(context),
                ApiV3Requests.page(context),
                ApiV3Requests.size(context),
                optionalInt(context.param("status")),
                context.param("keyword"),
                "true".equalsIgnoreCase(context.param("editPendingOnly"))));
    }

    public void addFishGame(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, FISH_GAME_EDIT_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return opsService.addFishGame(identity(context), ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void exportFishGames(final RequestContext context) {
        ApiV3Requests.read(context, () -> opsService.exportFishGames(identity(context)));
    }

    public void importFishGames(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, FISH_GAME_IMPORT_FIELDS, false, MAX_BATCH_BODY_BYTES);
            return opsService.importFishGames(identity(context), ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void updateFishGame(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, FISH_GAME_EDIT_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return opsService.updateFishGame(
                    identity(context), ApiV3Requests.id(context, "gameId"),
                    ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void reviewFishGame(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, FISH_GAME_REVIEW_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return opsService.reviewFishGame(
                    identity(context), ApiV3Requests.id(context, "gameId"),
                    ApiV3WriteInputs.idempotencyKey(context), body);
        });
    }

    public void listMilestones(final RequestContext context) {
        ApiV3Requests.read(context, () -> opsService.listMilestones(
                identity(context),
                ApiV3Requests.page(context),
                ApiV3Requests.size(context),
                optionalInt(context.param("status")),
                context.param("keyword")));
    }

    public void updateMilestone(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, MILESTONE_UPDATE_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return opsService.updateMilestone(
                    identity(context), ApiV3Requests.id(context, "milestoneId"),
                    ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void removeMilestone(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true, MAX_EMPTY_BODY_BYTES);
            return opsService.removeMilestone(
                    identity(context), ApiV3Requests.id(context, "milestoneId"),
                    ApiV3WriteInputs.idempotencyKey(context), context.getRequest());
        });
    }

    public void listImages(final RequestContext context) {
        ApiV3Requests.read(context, () -> opsService.listImages(
                identity(context), ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    public void reviewImage(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, IMAGE_REVIEW_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return opsService.reviewImage(
                    identity(context), ApiV3Requests.id(context, "imageId"),
                    ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    // ==================== 领域、标签、保留词与广告位 ====================

    public void listDomains(final RequestContext context) {
        ApiV3Requests.read(context, () -> opsService.listDomains(
                identity(context), ApiV3Requests.page(context), ApiV3Requests.size(context)));
    }

    public void addDomain(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, DOMAIN_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return opsService.addDomain(
                    identity(context), ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void updateDomain(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, DOMAIN_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return opsService.updateDomain(
                    identity(context), ApiV3Requests.id(context, "domainId"),
                    ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void removeDomain(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true, MAX_EMPTY_BODY_BYTES);
            return opsService.removeDomain(
                    identity(context), ApiV3Requests.id(context, "domainId"),
                    ApiV3WriteInputs.idempotencyKey(context), context.getRequest());
        });
    }

    public void bindDomainTag(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, DOMAIN_TAG_BIND_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return opsService.bindDomainTag(
                    identity(context), ApiV3Requests.id(context, "domainId"),
                    ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void unbindDomainTag(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true, MAX_EMPTY_BODY_BYTES);
            return opsService.unbindDomainTag(
                    identity(context), ApiV3Requests.id(context, "domainId"),
                    ApiV3Requests.id(context, "tagId"),
                    ApiV3WriteInputs.idempotencyKey(context), context.getRequest());
        });
    }

    public void listTags(final RequestContext context) {
        ApiV3Requests.read(context, () -> opsService.listTags(
                identity(context), ApiV3Requests.page(context), ApiV3Requests.size(context),
                context.param("keyword")));
    }

    public void addTag(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, TAG_ADD_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return opsService.addTag(
                    identity(context), ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void updateTag(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, TAG_UPDATE_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return opsService.updateTag(
                    identity(context), ApiV3Requests.id(context, "tagId"),
                    ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void removeUnusedTags(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            ApiV3WriteInputs.body(context, Set.of(), true, MAX_EMPTY_BODY_BYTES);
            return opsService.removeUnusedTags(
                    identity(context), ApiV3WriteInputs.idempotencyKey(context), context.getRequest());
        });
    }

    public void listReservedWords(final RequestContext context) {
        ApiV3Requests.read(context, () -> opsService.listReservedWords(
                identity(context), context.param("keyword")));
    }

    public void addReservedWord(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, RESERVED_WORD_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return opsService.addReservedWord(
                    identity(context), ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void removeReservedWord(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, RESERVED_WORD_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return opsService.removeReservedWord(
                    identity(context), ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    public void getAds(final RequestContext context) {
        ApiV3Requests.read(context, () -> opsService.getAds(identity(context)));
    }

    public void updateAds(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final JSONObject body = ApiV3WriteInputs.body(context, AD_UPDATE_FIELDS, false, MAX_WRITE_BODY_BYTES);
            return opsService.updateAds(
                    identity(context), ApiV3WriteInputs.idempotencyKey(context), context.getRequest(), body);
        });
    }

    private static Integer optionalInt(final String raw) {
        if (StringUtils.isBlank(raw) || "all".equalsIgnoreCase(raw.trim())) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (final NumberFormatException e) {
            throw ApiV3Requests.invalid();
        }
    }

    private static JSONObject identity(final RequestContext context) {
        return (JSONObject) context.attr(ApiV3Processor.IDENTITY);
    }
}
