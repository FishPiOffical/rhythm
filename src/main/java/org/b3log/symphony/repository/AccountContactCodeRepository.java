package org.b3log.symphony.repository;

import org.b3log.latke.repository.AbstractRepository;
import org.b3log.latke.repository.annotation.Repository;
import org.b3log.symphony.model.AccountContactCode;

@Repository
public class AccountContactCodeRepository extends AbstractRepository {
    public AccountContactCodeRepository(){super(AccountContactCode.TABLE);}
}
