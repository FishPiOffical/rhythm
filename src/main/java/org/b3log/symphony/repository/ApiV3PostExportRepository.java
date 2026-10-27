package org.b3log.symphony.repository;

import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.annotation.Repository;

/** Private export metadata; artifact bytes live in owner-bound database chunks. */
@Repository
public class ApiV3PostExportRepository extends AbstractRepository {
    public ApiV3PostExportRepository(){super("api_v3_post_export");}
}
