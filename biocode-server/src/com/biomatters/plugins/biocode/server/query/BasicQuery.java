package com.biomatters.plugins.biocode.server.query;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import jebl.util.ProgressListener;

import java.util.Map;
import java.util.Set;

/**
 * @author Gen Li
 *         Created on 5/06/14 12:01 PM
 */
public abstract class BasicQuery extends Query {
    @Override
    public LimsSearchResult execute(Map<String, Object> tissuesWorkflowsPlatesSequences, Set<String> tissuesToMatch) throws DatabaseServiceException {
        LIMSConnection limsConnection = LIMSInitializationListener.getLimsConnection();

        if (limsConnection == null) {
            throw new DatabaseServiceException("The lims connection is null.", false);
        }

        return LIMSInitializationListener.getLimsConnection().getMatchingDocumentsFromLims(createGeneiousQuery(tissuesWorkflowsPlatesSequences), tissuesToMatch, ProgressListener.EMPTY);
    }

    protected abstract com.biomatters.geneious.publicapi.databaseservice.Query createGeneiousQuery(Map<String, Object> tissuesWorkflowsPlatesSequences);
}