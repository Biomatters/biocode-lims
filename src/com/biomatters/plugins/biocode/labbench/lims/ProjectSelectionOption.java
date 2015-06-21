package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.server.Project;

import java.util.*;

/**
 * @author Gen Li
 *         Created on 24/04/15 3:01 PM
 */
public class ProjectSelectionOption extends Options {
    private ComboBoxOption<OptionValue> projectComboBox;

    public ProjectSelectionOption(Collection<Project> projects) {
        List<OptionValue> projectOptionValues = createProjectOptionValues(projects);
        projectComboBox = addComboBoxOption("project", "Project:", projectOptionValues, projectOptionValues.get(0));
        addLabel("Note: Changes will be applied immediately.");
    }

    public int getIdOfSelectedProject() {
        return Integer.valueOf(projectComboBox.getValue().getName());
    }

    private static List<OptionValue> createProjectOptionValues(Collection<Project> projects) {
        List<OptionValue> projectOptionValues = new ArrayList<OptionValue>();

        for (Project project : projects) {
            projectOptionValues.add(createProjectOptionValue(project));
        }

        Collections.sort(projectOptionValues, new ProjectOptionValueComparator());

        return projectOptionValues;
    }

    private static OptionValue createProjectOptionValue(Project project) {
        return new OptionValue(String.valueOf(project.id), project.name);
    }

    private static class ProjectOptionValueComparator implements Comparator<OptionValue> {
        @Override
        public int compare(OptionValue lhs, OptionValue rhs) {
            return String.CASE_INSENSITIVE_ORDER.compare(lhs.getLabel(), rhs.getLabel());
        }
    }
}