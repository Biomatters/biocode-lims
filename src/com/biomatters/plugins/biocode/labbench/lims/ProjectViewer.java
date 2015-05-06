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

    public ProjectViewer(AnnotatedPluginDocument[] annotatedDocuments, ProjectLimsConnection projectLimsConnection) throws DocumentOperationException {
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

    private static Set<Workflow> getWorkflows(AnnotatedPluginDocument[] annotatedPluginDocuments) throws DocumentOperationException {
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

    private static Set<Workflow> getWorkflows(Plate plate) {
        Set<Workflow> workflows = new HashSet<Workflow>();

        for (Reaction reaction : plate.getReactions()) {
            Workflow workflow = reaction.getWorkflow();
            if (workflow != null) {
                workflows.add(workflow);
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
        StringBuilder tableHtmlBuilder = new StringBuilder().append("<html><table><tr><td><strong>Workflow</strong></td><td><strong>Project</strong></td></tr>");

        for (Workflow workflow : workflows) {
            Project projectContainingWorkflow = getProjectContainingWorkflow(projectToWorkflows, workflow);
            tableHtmlBuilder.append("<tr><td>").append(workflow.getName()).append("</td>").append("<td>").append(projectContainingWorkflow.name).append("</td></tr>");
        }

        tableHtmlBuilder.append("</table>").append("</html>");

        return tableHtmlBuilder.toString();
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
                GeneiousAction action = new GeneiousAction("Add the workflows to a project", "") {
                    @Override
                    public void actionPerformed(ActionEvent actionEvent) {
                        ProjectSelectionOption projectSelectionOption = new ProjectSelectionOption(getProjects(projectToWorkflows));
                        if (Dialogs.showOptionsDialog(projectSelectionOption, "Project selection", false)) {
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