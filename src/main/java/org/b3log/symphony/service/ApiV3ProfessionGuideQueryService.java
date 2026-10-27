package org.b3log.symphony.service;

import org.apache.logging.log4j.LogManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.service.annotation.Service;
import org.b3log.symphony.model.UserExt;
import org.b3log.symphony.repository.ProfessionRepository;
import org.b3log.symphony.repository.ProfessionRevisionRepository;
import org.b3log.symphony.repository.ProfessionLevelSchemeRepository;
import org.b3log.symphony.repository.ProfessionLevelRepository;
import org.b3log.symphony.repository.ProfessionLevelRewardRepository;
import org.b3log.symphony.repository.ProfessionAutomationRepository;
import org.b3log.symphony.repository.ProfessionAutomationRevisionRepository;
import org.b3log.symphony.repository.UserRepository;
import org.b3log.symphony.util.ApiV3ProfessionGuideDto;
import org.b3log.symphony.util.ApiV3Requests;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.HashSet;
import java.util.Set;

/** Bounded, read-only published growth metadata; no automation actions, conditions or progress mutation. */
@Service
public class ApiV3ProfessionGuideQueryService {
    private static final int MAX_ROWS = 512;
    @Inject private UserRepository users;
    @Inject private ProfessionRepository professions;
    @Inject private ProfessionRevisionRepository revisions;
    @Inject private ProfessionLevelSchemeRepository schemes;
    @Inject private ProfessionLevelRepository levels;
    @Inject private ProfessionLevelRewardRepository rewards;
    @Inject private ProfessionAutomationRepository automations;
    @Inject private ProfessionAutomationRevisionRepository automationRevisions;

    private static void id(final String id) {
        if (id == null || !id.matches("[0-9]{1,19}")) throw ApiV3Requests.invalid();
    }
    private void actor(final String actor) throws RepositoryException {
        if (users.select("SELECT oId FROM `"+users.getName()+"` WHERE oId=? AND userStatus=? LIMIT 2",actor,
                UserExt.USER_STATUS_C_VALID).size()!=1) throw ApiV3Requests.notFound();
    }
    private JSONObject current(final String professionId) throws RepositoryException {
        final var rows = professions.select("SELECT p.oId AS professionId,p.currentRevisionId,p.currentLevelSchemeId,"
                +"r.oId AS revisionId,r.professionId AS revisionProfessionId,r.status AS revisionStatus,"
                +"s.oId AS schemeId,s.professionId AS schemeProfessionId,s.professionRevisionId AS schemeRevisionId,s.status AS schemeStatus,"
                +"sr.oId AS schemeRevisionFoundId,sr.professionId AS schemeRevisionProfessionId,sr.status AS schemeRevisionStatus"
                +" FROM `"+professions.getName()+"` p LEFT JOIN `"+revisions.getName()+"` r ON r.oId=p.currentRevisionId"
                +" LEFT JOIN `"+schemes.getName()+"` s ON s.oId=p.currentLevelSchemeId"
                +" LEFT JOIN `"+revisions.getName()+"` sr ON sr.oId=s.professionRevisionId WHERE p.oId=? AND p.status='PUBLISHED' LIMIT 2",professionId);
        if (rows.isEmpty()) throw ApiV3Requests.notFound();
        if (rows.size()!=1) throw ApiV3ProfessionGuideDto.unavailable();
        final JSONObject row=rows.getFirst();
        final String revision=ApiV3ProfessionGuideDto.text(row,"currentRevisionId",19,false);
        final String scheme=ApiV3ProfessionGuideDto.text(row,"currentLevelSchemeId",19,false);
        // Definition publication changes the current revision independently from the level scheme.
        // A still-current scheme may therefore legitimately refer to a retired definition revision.
        final String schemeRevision=ApiV3ProfessionGuideDto.text(row,"schemeRevisionId",19,false);
        if (!revision.matches("[0-9]{1,19}") || !scheme.matches("[0-9]{1,19}")
                || !revision.equals(row.opt("revisionId")) || !professionId.equals(row.opt("revisionProfessionId"))
                || !"PUBLISHED".equals(row.opt("revisionStatus")) || !scheme.equals(row.opt("schemeId"))
                || !professionId.equals(row.opt("schemeProfessionId")) || !schemeRevision.matches("[0-9]{1,19}")
                || !schemeRevision.equals(row.opt("schemeRevisionFoundId")) || !professionId.equals(row.opt("schemeRevisionProfessionId"))
                || !Set.of("PUBLISHED","RETIRED").contains(row.opt("schemeRevisionStatus"))
                || !"PUBLISHED".equals(row.opt("schemeStatus"))) throw ApiV3ProfessionGuideDto.unavailable();
        return row;
    }
    public JSONObject guides(final String actor, final String professionId) {
        id(actor); id(professionId);
        try {
            actor(actor);
            final JSONObject state=current(professionId);
            final var sources=automations.select("SELECT a.oId AS automationId,a.currentRevisionId,r.oId AS revisionId,"
                    +"r.automationId AS revisionAutomationId,r.professionId AS revisionProfessionId,r.status AS revisionStatus,r.triggerType"
                    +" FROM `"+automations.getName()+"` a LEFT JOIN `"+automationRevisions.getName()+"` r ON r.oId=a.currentRevisionId"
                    +" WHERE a.professionId=? AND a.status='PUBLISHED' ORDER BY a.sortOrder ASC,a.oId ASC LIMIT ?",professionId,MAX_ROWS+1);
            if (sources.size()>MAX_ROWS) throw ApiV3ProfessionGuideDto.unavailable();
            final JSONArray triggers=new JSONArray(); final Set<String> seen=new HashSet<>();
            for (final JSONObject row:sources) {
                final String automation=ApiV3ProfessionGuideDto.text(row,"automationId",19,false);
                final String revision=ApiV3ProfessionGuideDto.text(row,"currentRevisionId",19,false);
                if (!seen.add(automation) || !automation.matches("[0-9]{1,19}")
                        || !automation.equals(row.opt("revisionAutomationId")) || !professionId.equals(row.opt("revisionProfessionId"))
                        || !revision.matches("[0-9]{1,19}") || !revision.equals(row.opt("revisionId")) || !"PUBLISHED".equals(row.opt("revisionStatus")))
                    throw ApiV3ProfessionGuideDto.unavailable();
                triggers.put(ApiV3ProfessionGuideDto.trigger(row));
            }
            final var benefits=rewards.select("SELECT rw.oId AS rewardId,rw.levelId,rw.levelCode,rw.rewardType,"
                    +"SUBSTR(rw.rewardConfigJson,1,4097) AS rewardConfigJson,l.oId AS matchedLevelId,l.levelCode AS matchedLevelCode"
                    +" FROM `"+rewards.getName()+"` rw LEFT JOIN `"+levels.getName()+"` l ON l.oId=rw.levelId AND l.schemeId=rw.schemeId"
                    +" WHERE rw.schemeId=? ORDER BY rw.sortOrder ASC,rw.oId ASC LIMIT ?",state.getString("schemeId"),MAX_ROWS+1);
            if (benefits.size()>MAX_ROWS) throw ApiV3ProfessionGuideDto.unavailable();
            final JSONArray rewardDto=new JSONArray(); seen.clear();
            for (final JSONObject row:benefits) {
                final String reward=ApiV3ProfessionGuideDto.text(row,"rewardId",19,false);
                final String level=ApiV3ProfessionGuideDto.text(row,"levelId",19,false);
                final String code=ApiV3ProfessionGuideDto.text(row,"levelCode",64,false);
                if (!seen.add(reward) || !reward.matches("[0-9]{1,19}") || !level.matches("[0-9]{1,19}")
                        || !level.equals(row.opt("matchedLevelId")) || !code.equals(row.opt("matchedLevelCode")))
                    throw ApiV3ProfessionGuideDto.unavailable();
                rewardDto.put(ApiV3ProfessionGuideDto.reward(row));
            }
            if (!state.similar(current(professionId))) throw ApiV3ProfessionGuideDto.unavailable();
            actor(actor);
            return new JSONObject().put("professionId",professionId).put("triggers",triggers).put("rewards",rewardDto);
        } catch (final RepositoryException | org.json.JSONException e) {
            LogManager.getLogger(getClass()).warn("Profession guide read unavailable");
            throw ApiV3ProfessionGuideDto.unavailable();
        }
    }
}
