package pro.taskana.impl;

import org.apache.ibatis.session.SqlSession;

import pro.taskana.TaskanaEngine;
import pro.taskana.history.HistoryEventProducer;

/**
 * FOR INTERNAL USE ONLY.
 *
 * Contains all actions which are necessary within taskana.
 */
public interface InternalTaskanaEngine {

    /**
     * Opens the connection to the database. Has to be called at the begin of each Api call that accesses the database
     */
    void openConnection();

    /**
     * Returns the database connection into the pool. In the case of nested calls, simply pops the latest session from
     * the session stack. Closes the connection if the session stack is empty. In mode AUTOCOMMIT commits before the
     * connection is closed. To be called at the end of each Api call that accesses the database
     */
    void returnConnection();

    /**
     * Initializes the SqlSessionManager.
     */
    void initSqlSession();

    /**
     * Returns true if the given domain does exist in the configuration.
     *
     * @param domain
     *            the domain specified in the configuration
     * @return <code>true</code> if the domain exists
     */
    boolean domainExists(String domain);

    /**
     * retrieve the SqlSession used by taskana.
     *
     * @return the myBatis SqlSession object used by taskana
     */
    SqlSession getSqlSession();

    /**
     * Retrieve TaskanaEngine.
     * @return The nested TaskanaEngine.
     */
    TaskanaEngine getEngine();

    /**
     * Retrieve HistoryEventProducer.
     *
     * @return the HistoryEventProducer instance.
     */
    HistoryEventProducer getHistoryEventProducer();

}