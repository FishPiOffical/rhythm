package org.b3log.symphony.repository;

import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.annotation.Repository;

/** At most one pending MFA preparation per account. oId is the account ID. */
@Repository
public class ApiV3MfaPreparationRepository extends AbstractRepository {
    public ApiV3MfaPreparationRepository(){super("api_v3_mfa_preparation");}
}
