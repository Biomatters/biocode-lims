package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.server.Project;

import java.util.Collection;
import java.util.Map;

/**
 *
 * @author Gen Li
 *         Created on 21/04/15 1:44 PM
 */
public abstract class ProjectLimsConnection extends LIMSConnection {
    public abstract Map<Project, Collection<Workflow>> getProjectToWorkflows() throws DatabaseServiceException;
    public abstract void addWorkflowsToProject(Collection<Integer> workflowIds, int projectId) throws DatabaseServiceException;
    public abstract void removeWorkflowsFromProject(Collection<Integer> workflowIds, int projectId) throws DatabaseServiceException;
}
