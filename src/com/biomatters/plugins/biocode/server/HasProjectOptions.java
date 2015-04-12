package com.biomatters.plugins.biocode.server;

import java.util.Collection;

/**
 *
 * @author Gen Li
 *         Created on 10/04/15 1:01 PM
 */
public interface HasProjectOptions {
    void setPossibleProjects(Collection<Project> projects);
    void setSelectedPossibleProject(Project project);
    int getIDOfSelectedPossibleProject();
}
