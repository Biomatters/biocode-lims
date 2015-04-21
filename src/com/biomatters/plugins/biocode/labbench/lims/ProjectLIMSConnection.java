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
    public abstract Map<Integer, String> getIDAndNameOfWorkflowsNotAddedToAProject() throws DatabaseServiceException;
    public abstract Map<Project, Collection<Workflow>> getProjectToWorkflows() throws DatabaseServiceException;
    public abstract void addWorkflowToProject(int workflowID, int projectID) throws DatabaseServiceException;
    public abstract void removeWorkflowFromProject(int workflowID, int projectID) throws DatabaseServiceException;
}
