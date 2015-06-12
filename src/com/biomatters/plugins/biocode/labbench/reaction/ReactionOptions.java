package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.server.Project;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 14/07/2009 7:15:35 PM
 */
public abstract class ReactionOptions<T extends Reaction> extends Options {

    public static final String RUN_STATUS = "runStatus";
    public static final String WORKFLOW_ID = "workflowId";
    public static final String COCKTAIL_OPTION_ID = "cocktail";
    public static final OptionValue NOT_RUN_VALUE = new OptionValue("not run", "not run");
    public static final OptionValue RUN_VALUE = new OptionValue("run", "run");
    public static final OptionValue PASSED_VALUE = new OptionValue("passed", "passed");
    public static final OptionValue SUSPECT_VALUE = new OptionValue("suspect", "suspect");
    public static final OptionValue FAILED_VALUE = new OptionValue("failed", "failed");
    public static final OptionValue[] STATUS_VALUES = new OptionValue[] { NOT_RUN_VALUE, RUN_VALUE, PASSED_VALUE, SUSPECT_VALUE, FAILED_VALUE };
    public static final String[] SAMPLE_LOCI = new String[] {"None", "COI", "16s", "18s", "ITS", "ITS1", "ITS2", "28S", "12S", "rbcl", "matK", "trnH-psba", "cytB"};
    protected T reaction;
    protected static final String PROJECT_OPTION_NAME = "project";

    public ReactionOptions() {
        init();
        addProjectOptions();
    }

    public ReactionOptions(Class cl) {
        super(cl);
        init();
        addProjectOptions();
    }

    public ReactionOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
        init();
        addProjectOptions();
    }

    public ReactionOptions(Element element) throws XMLSerializationException {
        super(element);
    }

    public abstract boolean fieldIsFinal(String fieldCode);

    public abstract void refreshValuesFromCaches();

    public abstract Cocktail getCocktail();

    public void setReaction(T r) {
        this.reaction = r;
    }

    protected abstract void init();

    public void setPossibleProjects(Collection<Project> projects, int defaultProjectIndex) {
        if (defaultProjectIndex < 0 || defaultProjectIndex >= projects.size()) {
            throw new IllegalArgumentException(
                    "defaultProjectIndex (" + defaultProjectIndex + ") is out of bounds. " +
                            " valid range: " + 0 + " - " + (projects.size() - 1) + "."
            );
        }
        getProjectOption().setPossibleValues(projectsToOptionValues(projects));
    }

    public List<OptionValue> getPossibleProjects() {
        return getProjectOption().getPossibleOptionValues();
    }

    public int getDefaultProjectId() {
        return Integer.parseInt(getProjectOption().getValue().getName());
    }

    public String getDefaultProjectName() {
        return getProjectOption().getValue().getLabel();
    }

    private void addProjectOptions() {
        OptionValue projectOptionValue = projectToOptionValue(Project.NONE_PROJECT);
        addComboBoxOption(PROJECT_OPTION_NAME, "Project:", Collections.singletonList(projectOptionValue), projectOptionValue);
    }

    private ComboBoxOption<OptionValue> getProjectOption() {
        return (ComboBoxOption<OptionValue>)getOption(PROJECT_OPTION_NAME);
    }

    private static List<OptionValue> projectsToOptionValues(Collection<Project> projects) {
        List<Options.OptionValue> optionValues = new ArrayList<OptionValue>();

        for (Project project : projects) {
            optionValues.add(projectToOptionValue(project));
        }

        return optionValues;
    }

    private static Options.OptionValue projectToOptionValue(Project project) {
        return new Options.OptionValue(Integer.toString(project.id), project.name);
    }
}