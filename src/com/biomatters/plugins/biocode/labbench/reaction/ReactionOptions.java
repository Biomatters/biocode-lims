package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.biocode.server.Project;
import org.jdom.Element;

import java.util.*;

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
    public static final String PROJECT_OPTION_NAME = "project";
    protected T reaction;
    protected static final OptionValue NONE_PROJECT_OPTION_VALUE = new OptionValue(Integer.toString(Project.NONE_PROJECT.id), Project.NONE_PROJECT.name);

    public ReactionOptions() {
        init();
        addProjectOption();
    }

    public ReactionOptions(Class cl) {
        super(cl);
        init();
        addProjectOption();
    }

    public ReactionOptions(Class cl, String preferenceNameSuffix) {
        super(cl, preferenceNameSuffix);
        init();
        addProjectOption();
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

    public ComboBoxOption<OptionValue> getProjectOption() {
        return (ComboBoxOption<OptionValue>)getOption(PROJECT_OPTION_NAME);
    }

    public void setPossibleProjects(Collection<Project> projects) {
        getProjectOption().setPossibleValues(projectsToOptionValues(projects));
    }

    public void setProject(Project project) {
        getProjectOption().setValue(projectToOptionValue(project));
    }

    public void enableProjectOption(boolean enabled) {
        getProjectOption().setEnabled(enabled);
    }

    protected abstract void init();

    private void addProjectOption() {
        addComboBoxOption(PROJECT_OPTION_NAME, "Project", Collections.singletonList(NONE_PROJECT_OPTION_VALUE), NONE_PROJECT_OPTION_VALUE);
    }

    private List<OptionValue> projectsToOptionValues(Collection<Project> projects) {
        Set<OptionValue> projectOptionValues = new HashSet<OptionValue>();

        for (Project project : projects) {
            projectOptionValues.add(projectToOptionValue(project));
        }

        return new ArrayList<OptionValue>(projectOptionValues);
    }

    private OptionValue projectToOptionValue(Project project) {
        return new OptionValue(Integer.toString(project.id), project.name);
    }
}