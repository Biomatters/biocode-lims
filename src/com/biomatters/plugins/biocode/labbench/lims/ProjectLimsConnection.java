package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.Project;
import jebl.util.Cancelable;

import java.util.*;

/**
 *
 * @author Gen Li
 *         Created on 21/04/15 1:44 PM
 */
public abstract class ProjectLimsConnection extends LIMSConnection {
    public abstract Map<Project, Collection<Workflow>> getProjectToWorkflows() throws DatabaseServiceException;
    public abstract void addWorkflowsToProject(int projectId, Collection<Integer> workflowIds) throws DatabaseServiceException;
    public abstract void removeWorkflowsFromProject(Collection<Integer> workflowIds) throws DatabaseServiceException;
    protected abstract List<Plate> getPlates__(Collection<Integer> plateIds, Cancelable cancelable) throws DatabaseServiceException;

    @Override
    protected List<Plate> getPlates_(Collection<Integer> plateIds, Cancelable cancelable) throws DatabaseServiceException {
        return setProjectValue(getPlates__(plateIds, cancelable), getProjectToWorkflows());
    }

    private static List<Plate> setProjectValue(List<Plate> plates, Map<Project, Collection<Workflow>> projectToWorkflows) throws DatabaseServiceException {
        for (Plate plate : plates) {
            for (Reaction reaction : plate.getReactions()) {
                Workflow reactionWorkflow = reaction.getWorkflow();
                if (reactionWorkflow != null) {
                    reaction.getOptions().setPossibleProjects(Collections.singletonList(getProject(projectToWorkflows, reactionWorkflow)));
                }
            }
        }

        return plates;
    }

    private static Project getProject(Map<Project, Collection<Workflow>> projectToWorkflows, Workflow workflowInProject) {
        for (Map.Entry<Project, Collection<Workflow>> projectAndWorkflows : projectToWorkflows.entrySet()) {
            if (projectAndWorkflows.getValue().contains(workflowInProject)) {
                return projectAndWorkflows.getKey();
            }
        }
        return Project.NONE_PROJECT;
    }
}