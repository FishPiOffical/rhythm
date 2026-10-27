package org.b3log.symphony.util;

import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import java.sql.Savepoint;
import java.util.concurrent.Callable;

/** Local all-or-nothing boundary inside an already owned JDBC transaction. Never commits its owner. */
public final class ApiV3TransactionSavepoint {
    private ApiV3TransactionSavepoint(){ }
    public static <T>T run(final Callable<T> work)throws Exception {
        final JdbcTransaction transaction=JdbcRepository.TX.get();
        if(transaction==null||!transaction.isActive())throw new IllegalStateException("owner transaction required");
        final var connection=transaction.getConnection();final Savepoint point=connection.setSavepoint();
        try{
            final T result=work.call();
            if(!transaction.isActive()||JdbcRepository.TX.get()!=transaction)throw new IllegalStateException("owner transaction ended inside savepoint");
            connection.releaseSavepoint(point);return result;
        }catch(Exception|Error error){
            // An inner primitive may already have aborted and disposed this root.
            // Preserve its failure rather than touching the closed connection again.
            if(!transaction.isActive()||JdbcRepository.TX.get()!=transaction)throw error;
            try{connection.rollback(point);}
            catch(Exception|Error failure){
                error.addSuppressed(failure);
                try{transaction.rollback();}catch(Exception|Error rollbackFailure){error.addSuppressed(rollbackFailure);}
                throw error;
            }
            try{connection.releaseSavepoint(point);}catch(Exception|Error failure){error.addSuppressed(failure);}
            throw error;
        }
    }
}
