package org.b3log.symphony.processor;

import org.b3log.latke.http.Dispatcher;
import org.b3log.latke.http.RequestContext;
import org.b3log.latke.ioc.BeanManager;
import org.b3log.latke.ioc.Inject;
import org.b3log.latke.ioc.Singleton;
import org.b3log.symphony.processor.middleware.AnonymousViewCheckMidware;
import org.b3log.symphony.processor.middleware.ApiV3AuthMidware;
import org.b3log.symphony.service.ApiV3ParagraphQueryService;
import org.b3log.symphony.util.ApiV3Requests;
import org.b3log.symphony.util.ApiV3WriteInputs;
import java.util.Set;

@Singleton
public class ApiV3ParagraphProcessor {
    @Inject private ApiV3ParagraphQueryService queries;
    public static void register() {
        final var beans = BeanManager.getInstance();
        final var processor = beans.getReference(ApiV3ParagraphProcessor.class);
        final var auth = beans.getReference(ApiV3AuthMidware.class);
        final var anonymous = beans.getReference(AnonymousViewCheckMidware.class);
        Dispatcher.get("/api/v3/articles/{articleId}/paragraphs", processor::paragraphs, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/articles/{articleId}/paragraph-comments", processor::comments, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/comments/{commentId}/location", processor::location, auth::optionalContent, anonymous::handle);
        Dispatcher.get("/api/v3/comments/{commentId}", processor::comment, auth::optionalContent, anonymous::handle);
    }
    public void paragraphs(final RequestContext context) {
        ApiV3Requests.respond(context, () -> queries.paragraphs(ApiV3Requests.id(context, "articleId"), ApiV3Requests.viewer(context),
                ApiV3Requests.number(context, "p", 1, Integer.MAX_VALUE / 50), ApiV3Requests.number(context, "size", 20, 50),
                "true".equals(ApiV3Requests.choice(context, "includeContent", "false", Set.of("true", "false")))));
    }
    public void comments(final RequestContext context) {
        ApiV3Requests.respond(context, () -> {
            final String raw = context.param("paragraphId");
            final String paragraph = null == raw ? "" : raw.trim();
            if (null != raw && !paragraph.matches("[0-9a-f]{64}")) throw ApiV3WriteInputs.invalid();
            return queries.comments(ApiV3Requests.id(context, "articleId"), ApiV3Requests.viewer(context), paragraph,
                    ApiV3Requests.choice(context, "state", "active", Set.of("active", "orphaned", "all")),
                    ApiV3Requests.number(context, "p", 1, Integer.MAX_VALUE / 50), ApiV3Requests.number(context, "size", 20, 50),
                    ApiV3Requests.choice(context, "sort", "oldest", Set.of("oldest", "latest", "hot")));
        });
    }
    public void location(final RequestContext context) {
        ApiV3Requests.respond(context, () -> queries.location(ApiV3Requests.id(context, "commentId"), ApiV3Requests.viewer(context)));
    }
    public void comment(final RequestContext context) {
        ApiV3Requests.respond(context, () -> queries.comment(ApiV3Requests.id(context, "commentId"), ApiV3Requests.viewer(context)));
    }
}
