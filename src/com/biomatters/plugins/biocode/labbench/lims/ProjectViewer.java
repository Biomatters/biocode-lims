package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.GTextPane;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.ActionProvider;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.plugin.GeneiousAction;
import com.biomatters.geneious.publicapi.utilities.IconUtilities;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.Project;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;

/**
 * @author Gen Li
 *         Created on 24/04/15 1:53 PM
 */
public class ProjectViewer extends DocumentViewer {
    private GTextPane table;
    private ProjectLimsConnection projectLimsConnection;
    private Set<Workflow> workflows;
    private Map<Project, Collection<Workflow>> projectToWorkflows;

    public ProjectViewer(AnnotatedPluginDocument[] annotatedDocuments, ProjectLimsConnection projectLimsConnection) throws DocumentOperationException, DatabaseServiceException {
        this.projectLimsConnection = projectLimsConnection;
        projectToWorkflows = getProjectToWorkflows(projectLimsConnection);
        workflows = getWorkflows(annotatedDocuments);
        table = createTable(generateTableHtml(workflows, projectToWorkflows));
    }

    private static Map<Project, Collection<Workflow>> getProjectToWorkflows(ProjectLimsConnection projectLimsConnection) throws DocumentOperationException {
        try {
            return projectLimsConnection.getProjectToWorkflows();
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException(e);
        }
    }

    private Set<Workflow> getWorkflows(AnnotatedPluginDocument[] annotatedPluginDocuments) throws DocumentOperationException, DatabaseServiceException {
        Set<Workflow> workflows = new HashSet<Workflow>();

        for (AnnotatedPluginDocument annotatedPluginDocument : annotatedPluginDocuments) {
            PluginDocument pluginDocument = annotatedPluginDocument.getDocument();
            if (pluginDocument instanceof WorkflowDocument) {
                workflows.add(((WorkflowDocument)pluginDocument).getWorkflow());
            } else if (pluginDocument instanceof PlateDocument) {
                workflows.addAll(getWorkflows(((PlateDocument)pluginDocument).getPlate()));
            }
        }

        return workflows;
    }

    private Set<Workflow> getWorkflows(Plate plate) throws DatabaseServiceException {
        Set<Workflow> workflows = new HashSet<Workflow>();

        if (!plate.getReactionType().equals(Reaction.Type.Extraction)) {
            for (Reaction reaction : plate.getReactions()) {
                Workflow workflow = reaction.getWorkflow();
                if (workflow != null) {
                    workflows.add(workflow);
                }
            }
        }

        return workflows;
    }

    private static GTextPane createTable(String tableHtml) {
        GTextPane table = new GTextPane();

        table.setContentType("text/html");
        table.setText(tableHtml);
        table.setEditable(false);

        return table;
    }

    private static String generateTableHtml(Collection<Workflow> workflows, Map<Project, Collection<Workflow>> projectToWorkflows) {
        StringBuilder tableHtmlBuilder = new StringBuilder("<html>");

        Map<Project, Collection<Workflow>> filteredProjectToWorkflows = filterProjectToWorkflowsByWorkflows(projectToWorkflows, workflows);

        if (filteredProjectToWorkflows.size() == 1) {
            Map.Entry<Project, Collection<Workflow>> projectAndWorkflows = filteredProjectToWorkflows.entrySet().iterator().next();

            List<Workflow> workflowsUnderProject = new ArrayList<Workflow>(projectAndWorkflows.getValue());

            Collections.sort(workflowsUnderProject, new WorkflowIdComparator());

            int numberOfWorkflows = workflowsUnderProject.size();
            tableHtmlBuilder.append("<strong>").append(projectAndWorkflows.getKey().name).append(" (All ").append(numberOfWorkflows).append(" ").append(getPlural(numberOfWorkflows, "workflow", "s")).append(")</strong><br>");

            tableHtmlBuilder.append(generateHtmlNewLineSeparatedListOfWorkflowNames(workflowsUnderProject)).append("<br>");
        } else {
            for (Map.Entry<Project, Collection<Workflow>> projectAndWorkflows : filteredProjectToWorkflows.entrySet()) {
                List<Workflow> workflowsUnderProject = new ArrayList<Workflow>(projectAndWorkflows.getValue());

                Collections.sort(workflowsUnderProject, new WorkflowIdComparator());

                int numberOfWorkflows = workflowsUnderProject.size();
                tableHtmlBuilder.append("<strong>").append(projectAndWorkflows.getKey().name).append(" (").append(numberOfWorkflows).append(" ").append(getPlural(numberOfWorkflows, "workflow", "s")).append(")</strong><br>");

                tableHtmlBuilder.append(generateHtmlNewLineSeparatedListOfWorkflowNames(workflowsUnderProject)).append("<br>");
            }
        }

        tableHtmlBuilder.append("</html>");

        return tableHtmlBuilder.toString();
    }

    private static class WorkflowIdComparator implements Comparator<Workflow> {
        @Override
        public int compare(Workflow lhs, Workflow rhs) {
            int result = 1, lhsId = lhs.getId(), rhsId = rhs.getId();

            if (lhsId == rhsId) {
                result = 0;
            } else if (lhsId < rhsId) {
                result = -1;
            }

            return result;
        }
    }

    private static String generateHtmlNewLineSeparatedListOfWorkflowNames(Collection<Workflow> workflows) {
        StringBuilder listBuilder = new StringBuilder();

        for (Workflow workflow : workflows) {
            listBuilder.append(workflow.getName()).append("<br>");
        }

        return listBuilder.toString();
    }

    private static String getPlural(int n, String noun, String suffix) {
        return n == 1 ? noun : noun + suffix;
    }

    private static Map<Project, Collection<Workflow>> filterProjectToWorkflowsByWorkflows(Map<Project, Collection<Workflow>> projectToWorkflows, Collection<Workflow> workflowsToKeep) {
        Map<Project, Collection<Workflow>> filteredProjectToWorkflows = new HashMap<Project, Collection<Workflow>>();

        for (Workflow workflowToKeep : workflowsToKeep) {
            Project projectContainingWorkflow = getProjectContainingWorkflow(projectToWorkflows, workflowToKeep);

            Collection<Workflow> workflowsUnderProject = filteredProjectToWorkflows.get(projectContainingWorkflow);

            if (workflowsUnderProject == null) {
                workflowsUnderProject = new ArrayList<Workflow>();
                filteredProjectToWorkflows.put(projectContainingWorkflow, workflowsUnderProject);
            }

            workflowsUnderProject.add(workflowToKeep);
        }

        return filteredProjectToWorkflows;
    }

    private static Project getProjectContainingWorkflow(Map<Project, Collection<Workflow>> projectToWorkflows, Workflow workflow) {
        Project projectContainingWorkflow = Project.NONE_PROJECT;

        for (Map.Entry<Project, Collection<Workflow>> projectAndWorkflows : projectToWorkflows.entrySet()) {
            for (Workflow workflowUnderProject : projectAndWorkflows.getValue()) {
                if (workflowUnderProject.equals(workflow)) {
                    projectContainingWorkflow = projectAndWorkflows.getKey();
                    break;
                }
            }
        }

        return projectContainingWorkflow;
    }

    @Override
    public JComponent getComponent() {
        return new JScrollPane(table);
    }

    @Override
    public ActionProvider getActionProvider() {
        ActionProvider actionProvider = new ActionProvider() {
            @Override
            public List<GeneiousAction> getOtherActions() {
                GeneiousAction action = new GeneiousAction("Assign all workflows to a project", "", BiocodePlugin.getIcons("bulkEdit_16.png")) {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        ProjectSelectionOption projectSelectionOption = new ProjectSelectionOption(getProjects(projectToWorkflows));
                        if (Dialogs.showOptionsDialog(projectSelectionOption, "Project Selection", false)) {
                            try {
                                int idOfSelectedProject = projectSelectionOption.getIdOfSelectedProject();
                                if (idOfSelectedProject == Project.NONE_PROJECT.id) {
                                    projectLimsConnection.removeWorkflowsFromProject(getWorkflowIds(workflows));
                                } else {
                                    projectLimsConnection.addWorkflowsToProject(idOfSelectedProject, getWorkflowIds(workflows));
                                }
                            } catch (DatabaseServiceException e) {
                                Dialogs.showMessageDialog(e.getMessage(), "An error occurred", null, Dialogs.DialogIcon.ERROR);
                            }

                            try {
                                projectToWorkflows = getProjectToWorkflows(projectLimsConnection);
                                table.setText(generateTableHtml(workflows, projectToWorkflows));
                            } catch (DocumentOperationException e) {
                                Dialogs.showMessageDialog(e.getMessage(), "An error occurred", null, Dialogs.DialogIcon.ERROR);
                            }
                        }
                    }
                };

                return Collections.singletonList(action);
            }
        };

        return actionProvider;
    }

    private static Set<Project> getProjects(Map<Project, Collection<Workflow>> projectToWorkflows) {
        Set<Project> projects = new HashSet<Project>();

        projects.add(Project.NONE_PROJECT);
        projects.addAll(projectToWorkflows.keySet());

        return projects;
    }

    private static Set<Integer> getWorkflowIds(Collection<Workflow> workflows) {
        Set<Integer> workflowIds = new HashSet<Integer>();

        for (Workflow workflow : workflows) {
            workflowIds.add(workflow.getId());
        }

        return workflowIds;
    }
}