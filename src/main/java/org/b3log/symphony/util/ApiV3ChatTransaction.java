package org.b3log.symphony.util;

import org.b3log.latke.repository.RepositoryException;
import org.b3log.latke.repository.jdbc.JdbcRepository;
import org.b3log.latke.repository.jdbc.JdbcTransaction;
import java.sql.SQLException;
import java.sql.Savepoint;

/** A savepoint inside the existing owner transaction, never a nested or independently committed transaction. */
public final class ApiV3ChatTransaction {
    private ApiV3ChatTransaction() { }
    @FunctionalInterface public interface Write<T>{T run()throws RepositoryException;}
    public static <T>T write(Write<T> work)throws RepositoryException {
        final var transaction=JdbcRepository.TX.get();
        if(transaction==null||!transaction.isActive())throw new IllegalStateException("chat transaction required");
        final var connection=transaction.getConnection();final Savepoint point;
        try{point=connection.setSavepoint();}catch(SQLException e){throw new RepositoryException(e);}
        try{
            final T value=work.run();
            if(!transaction.isActive()||JdbcRepository.TX.get()!=transaction)throw new IllegalStateException("chat owner transaction ended inside savepoint");
            connection.releaseSavepoint(point);return value;
        }catch(SQLException failure){
            final var error=new RepositoryException(failure);rollback(transaction,point,error);throw error;
        }catch(RepositoryException|RuntimeException|Error error){rollback(transaction,point,error);throw error;}
    }
    private static void rollback(JdbcTransaction transaction,Savepoint point,Throwable error){
        // Latke's service interceptor or an inner primitive may already have disposed this owner.
        // Keep the original policy/storage error; never touch a closed or replacement connection.
        if(!transaction.isActive()||JdbcRepository.TX.get()!=transaction)return;
        final var connection=transaction.getConnection();
        try{connection.rollback(point);}
        catch(Exception|Error failure){
            error.addSuppressed(failure);
            try{transaction.rollback();}catch(Exception|Error rollbackFailure){error.addSuppressed(rollbackFailure);}
            return;
        }
        try{connection.releaseSavepoint(point);}catch(Exception|Error failure){error.addSuppressed(failure);}
    }
}
