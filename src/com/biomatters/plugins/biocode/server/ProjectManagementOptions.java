package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.lims.ProjectLimsConnection;
import org.virion.jam.util.SimpleListener;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.*;

/**
 * @author Gen Li
 *         Created on 17/04/15 1:25 PM
 */
public class ProjectManagementOptions extends Options {
    private ComboBoxOption<OptionValue> projectList;
    private MultipleLineStringOption workflowList;
    private StringOption workflowInput;
    private ButtonOption addWorkflowButton;
    private ButtonOption removeWorkflowButton;
    private Option<String, ? extends javax.swing.JComponent> message;

    private static final OptionValue NONE_PROJECT_OPTION_VALUE = new OptionValue("-1", "None");

    private ProjectLimsConnection projectLimsConnection;
    private Map<OptionValue, Collection<OptionValue>> projectToWorkflows = new HashMap<OptionValue, Collection<OptionValue>>();

    public ProjectManagementOptions(ProjectLimsConnection projectLimsConnection) {
        this.projectLimsConnection = projectLimsConnection;
        refreshLocalMappingOfProjectToWorkflows();
        initOptions();
        initListeners();
    }

    private void refreshLocalMappingOfProjectToWorkflows() {
        try {
            projectToWorkflows.clear();
            projectToWorkflows.put(NONE_PROJECT_OPTION_VALUE, getWorkflowsNotAddedToAProjectOptionValues());
            projectToWorkflows.putAll(getProjectToWorkflowsOptionValues());
        } catch (DatabaseServiceException e) {
            setMessage("An error occurred while retrieving project data: " + e.getMessage());
        }
    }

    private void initOptions() {
        beginAlignHorizontally("", false);
        List<OptionValue> projectOptionValues = new ArrayList<OptionValue>(projectToWorkflows.keySet());
        projectList = addComboBoxOption("projectList", "Projects: ", projectOptionValues, projectOptionValues.get(0));
        workflowList = addMultipleLineStringOption("workflowList", "Workflows: ", "", 10, false);
        refreshWorkflowList();
        workflowList.setEnabled(false);
        endAlignHorizontally();

        beginAlignHorizontally("", false);
        workflowInput = addStringOption("workflowInput", "Workflow ID", "");
        addWorkflowButton = addButtonOption("addWorkflowButton", "", "Add");
        removeWorkflowButton = addButtonOption("removeWorkflowButton", "", "Remove");
        endAlignHorizontally();

        message = addLabel("", true, false);
    }

    private void initListeners() {
        projectList.addChangeListener(new SimpleListener() {
            @Override
            public void objectChanged() {
                refreshWorkflowList();

                if (projectList.getValue().getName().equals("-1")) {
                    workflowInput.setEnabled(false);
                    addWorkflowButton.setEnabled(false);
                    removeWorkflowButton.setEnabled(false);
                } else {
                    workflowInput.setEnabled(true);
                    addWorkflowButton.setEnabled(true);
                    removeWorkflowButton.setEnabled(true);
                }
            }
        });

        addWorkflowButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                int workflowID = Integer.valueOf(workflowInput.getValue());
                int projectID = Integer.valueOf(projectList.getValue().getName());
                try {
                    projectLimsConnection.addWorkflowToProject(workflowID, projectID);
                    setMessage("The removal of workflow with id " + workflowID + " to project with id " + projectID + " was successful.");
                } catch (NumberFormatException e) {
                    setMessage("Invalid workflow id.");
                } catch (DatabaseServiceException e) {
                    setMessage("An error occurred while adding workflow with id " + workflowID + " to project with id " + projectID + ": " + e.getMessage());
                }

                refreshLocalMappingOfProjectToWorkflows();
                refreshProjectList();
                refreshWorkflowList();
            }
        });

        removeWorkflowButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                int workflowID = Integer.valueOf(workflowInput.getValue());
                int projectID = Integer.valueOf(projectList.getValue().getName());
                try {
                    projectLimsConnection.removeWorkflowFromProject(workflowID, projectID);
                    setMessage("The removal of workflow with id " + workflowID + " to project with id " + projectID + " was successful.");
                } catch (NumberFormatException e) {
                    setMessage("Invalid workflow id.");
                } catch (DatabaseServiceException e) {
                    setMessage("An error occurred while removing workflow with id " + workflowID + " from project with id " + projectID + ": " + e.getMessage());
                }

                refreshLocalMappingOfProjectToWorkflows();
                refreshProjectList();
                refreshWorkflowList();
            }
        });
    }

    private List<OptionValue> getWorkflowsNotAddedToAProjectOptionValues() throws DatabaseServiceException {
        List<OptionValue> workflowsNotAddedToAProjectOptionValues = new ArrayList<OptionValue>();

        for (Map.Entry<Integer, String> idAndName : projectLimsConnection.getIDAndNameOfWorkflowsNotAddedToAProject().entrySet()) {
            workflowsNotAddedToAProjectOptionValues.add(new OptionValue(String.valueOf(idAndName.getKey()), idAndName.getValue()));
        }

        return workflowsNotAddedToAProjectOptionValues;
    }

    private Map<OptionValue, Collection<OptionValue>> getProjectToWorkflowsOptionValues() throws DatabaseServiceException {
        Map<OptionValue, Collection<OptionValue>> projectToWorkflowsOptionValues = new HashMap<OptionValue, Collection<OptionValue>>();

        for (Map.Entry<Project, Collection<Workflow>> projectAndWorkflows : projectLimsConnection.getProjectToWorkflows().entrySet()) {
            Project project = projectAndWorkflows.getKey();
            OptionValue projectOptionValue = new OptionValue(String.valueOf(project.id), project.name);

            Collection<OptionValue> workflowOptionValues = new ArrayList<OptionValue>();
            for (Workflow workflow : projectAndWorkflows.getValue()) {
                workflowOptionValues.add(new OptionValue(String.valueOf(workflow.getId()), workflow.getName()));
            }

            projectToWorkflowsOptionValues.put(projectOptionValue, workflowOptionValues);
        }

        return projectToWorkflowsOptionValues;
    }

    private void refreshProjectList() {
        projectList.setPossibleValues(new ArrayList<OptionValue>(projectToWorkflows.keySet()));
    }

    private void refreshWorkflowList() {
        workflowList.setValue(createWorkflowList(getWorkflows(projectList.getValue())));
    }

    private void setMessage(final String value) {
        message.setValue(value);
    }

    private List<OptionValue> getWorkflows(OptionValue projectValue) {
        List<OptionValue> workflowOptionValues = new ArrayList<OptionValue>();

        for (Map.Entry<OptionValue, Collection<OptionValue>> projectAndWorkflows : projectToWorkflows.entrySet()) {
            if (projectAndWorkflows.getKey().getName().equals(projectValue.getName())) {
                workflowOptionValues.addAll(projectAndWorkflows.getValue());
                break;
            }
        }

        return workflowOptionValues;
    }

    private static String createWorkflowList(Collection<OptionValue> workflowOptionValues) {
        StringBuilder workflowList = new StringBuilder();

        for (OptionValue workflowOptionValue : workflowOptionValues) {
            workflowList.append(workflowOptionValue.getName()).append(" - ").append(workflowOptionValue.getLabel()).append("\n");
        }

        return workflowList.toString();
    }
}