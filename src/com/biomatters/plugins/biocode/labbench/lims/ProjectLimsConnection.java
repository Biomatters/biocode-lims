package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.BadDataException;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.Project;
import com.biomatters.plugins.biocode.server.Role;
import jebl.util.Cancelable;
import jebl.util.ProgressListener;

import java.util.*;

/**
 * A lims connection that restricts access to data via the segregation of workflows into groups called projects. Access
 * to a datum is defined by the project to which the workflow associated with the datum is in. Users that have write
 * access to a project have write access to data associated with workflows in the project.
 *
 * @author Gen Li
 *         Created on 21/04/15 1:44 PM
 */
public abstract class ProjectLimsConnection extends LIMSConnection {
    /**
     * @param minimumRole The role that the logged in user must at least have for projects that are to be returned.
     * @return A list of projects for which the logged in user must have at least the supplied role.
     * @throws DatabaseServiceException
     */
    public abstract List<Project> getProjects(Role minimumRole) throws DatabaseServiceException;

    /**
     * @return A map between projects and workflows.
     * @throws DatabaseServiceException
     */
    public abstract Map<Project, Collection<Workflow>> getProjectToWorkflows() throws DatabaseServiceException;

    /**
     * Adds the workflows denoted by the supplied workflow ids to the project denoted by the supplied project id.
     *
     * @param projectId The id of the project to which the workflows denoted by the supplied workflow ids are to be
     *                  added.
     * @param workflowIds The ids of the workflows to be added to the project denoted by the supplied project id.
     * @throws DatabaseServiceException
     */
    public abstract void addWorkflowsToProject(int projectId, Collection<Integer> workflowIds) throws DatabaseServiceException;

    /**
     * Removes the workflows denoted by the supplied workflow ids from all projects.
     *
     * @param workflowIds The ids of the workflows to be removed from all projects.
     * @throws DatabaseServiceException
     */
    public abstract void removeWorkflowsFromProject(Collection<Integer> workflowIds) throws DatabaseServiceException;

    /**
     * Template method for LimsConnection.getPlates()_. Retrieves the plates that are denoted by the supplied plate ids.
     *
     * @param plateIds The ids of plates to be returned.
     * @param cancelable A cancelable for displaying the progress of the method and providing users with the ability to
     *                   cancel the method.
     * @return
     * @throws DatabaseServiceException
     */
    protected abstract List<Plate> getPlates__(Collection<Integer> plateIds, Cancelable cancelable) throws DatabaseServiceException;

    /**
     * Template method for LimsConnection.savePlates(). Saves the supplied plates.
     *
     * @param plates A list of plates to be saved.
     * @param progressListener A progress listener for displaying the progress of the method.
     * @throws BadDataException
     * @throws DatabaseServiceException
     */
    protected abstract void savePlates_(List<Plate> plates, ProgressListener progressListener) throws BadDataException, DatabaseServiceException;

    @Override
    protected final List<Plate> getPlates_(Collection<Integer> plateIds, Cancelable cancelable) throws DatabaseServiceException {
        List<Plate> plates = getPlates__(plateIds, cancelable);

        Map<Project, Collection<Workflow>> projectToWorkflows = getProjectToWorkflows();
        for (Plate plate : plates) {
            setProjectValue(plate, projectToWorkflows);
        }

        return plates;
    }

    @Override
    public void savePlates(List<Plate> plates, ProgressListener progress) throws BadDataException, DatabaseServiceException {
        savePlates_(plates, progress);

        plates = getPCRAndCycleSequencingPlatesThatOnlyConsistOfReactionsNotAssociatedWithAWorkflow(plates);

        try {
            addWorkflowsToReactionsOnPlates(plates);
            for (Map.Entry<Integer, Collection<Integer>> newProjectIdToWorkflowIdMappings : getNewProjectToWorkflowMappings(getProjectToWorkflows(), accumulateReactionsFromPlates(plates)).entrySet()) {
                int projectId = newProjectIdToWorkflowIdMappings.getKey();
                if (projectId == Project.NONE_PROJECT.id) {
                    removeWorkflowsFromProject(newProjectIdToWorkflowIdMappings.getValue());
                } else {
                    addWorkflowsToProject(projectId, newProjectIdToWorkflowIdMappings.getValue());
                }
            }
        } catch (DatabaseServiceException e) {
            throw new DatabaseServiceException(e, "An error occurred: " + e.getMessage() + ".\n\n" +
                    "Please re-assign the workflows associated with the saved reactions to projects manually.", e.isNetworkConnectionError());
        }
    }

    private void addWorkflowsToReactionsOnPlates(List<Plate> plates) throws DatabaseServiceException {
        for (Plate plate : plates) {
            addWorkflowsToReactionsOnPlate(plate);
        }
    }

    private void addWorkflowsToReactionsOnPlate(Plate plate) throws DatabaseServiceException {
        Map<String, String> extractionIdToWorkflowName = getWorkflowNames(getExtractionIds(plate), getLoci(plate), plate.getReactionType());
        List<Workflow> workflows = getWorkflowsByName(extractionIdToWorkflowName.values());
        for (Reaction reaction : plate.getReactions()) {
            reaction.setWorkflow(getWorkflow(workflows, extractionIdToWorkflowName.get(reaction.getExtractionId())));
        }
    }

    private static Workflow getWorkflow(Collection<Workflow> workflows, String workflowName) {
        Workflow workflowWithTheSuppliedName = null;

        for (Workflow workflow : workflows) {
            if (workflow.getName().equals(workflowName)) {
                workflowWithTheSuppliedName = workflow;
                break;
            }
        }

        return workflowWithTheSuppliedName;
    }

    private static List<String> getExtractionIds(Plate plate) {
        List<String> extractionIds = new ArrayList<String>();

        for (Reaction reaction : plate.getReactions()) {
            String extractionId = reaction.getExtractionId();
            if (extractionId != null && !extractionId.isEmpty()) {
                extractionIds.add(extractionId);
            }
        }

        return extractionIds;
    }

    private static List<String> getLoci(Plate plate) {
        List<String> loci = new ArrayList<String>();

        for (Reaction reaction : plate.getReactions()) {
            String locus = reaction.getLocus();
            if (locus != null && !locus.isEmpty()) {
                loci.add(locus);
            }
        }

        return loci;
    }

    private static List<Plate> getPCRAndCycleSequencingPlatesThatOnlyConsistOfReactionsNotAssociatedWithAWorkflow(Collection<Plate> plates) {
        List<Plate> pcrAndCycleSequencingPlatesThatOnlyConsistOfReactionsNotAssociatedWithAWorkflow = new ArrayList<Plate>();

        for (Plate plate : plates) {
            if (!plate.getReactionType().equals(Reaction.Type.Extraction) && areNoReactionsAssociatedWithAWorkflow(Arrays.asList(plate.getReactions()))) {
                pcrAndCycleSequencingPlatesThatOnlyConsistOfReactionsNotAssociatedWithAWorkflow.add(plate);
            }
        }

        return pcrAndCycleSequencingPlatesThatOnlyConsistOfReactionsNotAssociatedWithAWorkflow;
    }

    private static boolean areNoReactionsAssociatedWithAWorkflow(Collection<Reaction> reactions) {
        boolean areNoReactionsAssociatedWithAWorkflow = true;

        for (Reaction reaction : reactions) {
            if (reaction.getWorkflow() != null) {
                areNoReactionsAssociatedWithAWorkflow = false;
                break;
            }
        }

        return areNoReactionsAssociatedWithAWorkflow;
    }

    private static Map<Integer, Collection<Integer>> getNewProjectToWorkflowMappings(Map<Project, Collection<Workflow>> projectToWorkflows, Collection<Reaction> reactions) throws DatabaseServiceException {
        Map<Integer, Collection<Integer>> newProjectToWorkflowMappings = new HashMap<Integer, Collection<Integer>>();

        Set<Project> projects = new HashSet<Project>(projectToWorkflows.keySet());
        projects.add(Project.NONE_PROJECT);
        for (Reaction reaction : reactions) {
            Workflow reactionWorkflow = reaction.getWorkflow();
            if (reactionWorkflow != null) {
                int reactionProjectId = reaction.getOptions().getDefaultProjectId();
                if (reactionProjectId == Project.NONE_PROJECT.id) {
                    if (getUnionOfCollections(projectToWorkflows.values()).contains(reactionWorkflow)) {
                        Collection<Integer> workflowIds = newProjectToWorkflowMappings.get(reactionProjectId);

                        if (workflowIds == null) {
                            workflowIds = new HashSet<Integer>();
                            newProjectToWorkflowMappings.put(reactionProjectId, workflowIds);
                        }

                        workflowIds.add(reactionWorkflow.getId());
                    }
                } else {
                    Project reactionProject = getProject(projects, reactionProjectId);

                    if (reactionProject == null) {
                        throw new DatabaseServiceException("Reaction with id " + reaction.getId() + " associated with invalid project (ID=" + reactionProjectId + ").", false);
                    }

                    if (!projectToWorkflows.get(reactionProject).contains(reactionWorkflow)) {
                        Collection<Integer> workflowIds = newProjectToWorkflowMappings.get(reactionProject.id);

                        if (workflowIds == null) {
                            workflowIds = new HashSet<Integer>();
                            newProjectToWorkflowMappings.put(reactionProjectId, workflowIds);
                        }

                        workflowIds.add(reactionWorkflow.getId());
                    }
                }
            }
        }

        return newProjectToWorkflowMappings;
    }

    private static <T> Collection<T> getUnionOfCollections(Collection<Collection<T>> collections) {
        Collection<T> unionOfCollections = new ArrayList<T>();

        for (Collection<T> collection : collections) {
            unionOfCollections.addAll(collection);
        }

        return unionOfCollections;
    }

    private static Project getProject(Collection<Project> projects, int id) {
        Project project = null;

        for (Project potentialProject : projects) {
            if (potentialProject.id == id) {
                project = potentialProject;
                break;
            }
        }

        return project;
    }

    private static List<Reaction> accumulateReactionsFromPlates(Collection<Plate> plates) {
        List<Reaction> reactions = new ArrayList<Reaction>();

        for (Plate plate : plates) {
            for (Reaction reaction : plate.getReactions()) {
                reactions.add(reaction);
            }
        }

        return reactions;
    }

    private static void setProjectValue(Plate plate, Map<Project, Collection<Workflow>> projectToWorkflows) throws DatabaseServiceException {
        for (Reaction reaction : plate.getReactions()) {
            Workflow reactionWorkflow = reaction.getWorkflow();
            if (reactionWorkflow != null) {
                reaction.getOptions().setPossibleProjects(Collections.singletonList(getProjectContainingWorkflow(projectToWorkflows, reactionWorkflow)), 0);
            }
        }
    }

    private static Project getProjectContainingWorkflow(Map<Project, Collection<Workflow>> projectToWorkflows, Workflow workflowInProject) {
        for (Map.Entry<Project, Collection<Workflow>> projectAndWorkflows : projectToWorkflows.entrySet()) {
            if (projectAndWorkflows.getValue().contains(workflowInProject)) {
                return projectAndWorkflows.getKey();
            }
        }
        return Project.NONE_PROJECT;
    }
}