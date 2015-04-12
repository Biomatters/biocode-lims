package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.plugin.Options.OptionValue;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 * @author Gen Li
 *         Created on 10/04/15 1:21 PM
 */
public class ProjectUtilities {
    public static List<OptionValue> projectsToOptionValues(Collection<Project> projects) {
        if (projects == null) {
            throw new IllegalArgumentException("projects");
        }

        List<OptionValue> projectsAsOptionValues = new ArrayList<OptionValue>();

        for (Project project : projects) {
            projectsAsOptionValues.add(projectToOptionValue(project));
        }

        return projectsAsOptionValues;
    }

    public static int getProjectID(OptionValue projectOptionValue) {
        if (projectOptionValue == null) {
            throw new IllegalArgumentException("projectOptionValue is null.");
        }


        try {
            return Integer.valueOf(projectOptionValue.getName());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid projectOptionValue: " + e.getMessage());
        }
    }

    public static boolean contains(Collection<OptionValue> optionValues, Project project) {
        if (optionValues == null) {
            throw new IllegalArgumentException("optionValues is null.");
        }
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }

        boolean contains = false;

        for (OptionValue optionValue : optionValues) {
            if (isEqual(project, optionValue)) {
                contains = true;
                break;
            }
        }

        return contains;
    }

    private static OptionValue projectToOptionValue(Project project) {
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }

        return new OptionValue(Integer.toString(project.id), project.name);
    }

    private static boolean isEqual(Project project, OptionValue optionValue) {
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }
        if (optionValue == null) {
            throw new IllegalArgumentException("optionValue is null.");
        }

        return isProjectOptionValue(optionValue) && project.id == Integer.valueOf(optionValue.getName()) && project.name.equals(optionValue.getLabel());
    }

    private static boolean isProjectOptionValue(OptionValue optionValue) {
        if (optionValue == null) {
            throw new IllegalArgumentException("optionValue is null.");
        }

        boolean isProjectOptionValue = true;

        try {
            Integer.valueOf(optionValue.getName());
        } catch (NumberFormatException e) {
            return false;
        }

        return isProjectOptionValue;
    }
}