package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;

import javax.xml.bind.annotation.XmlRootElement;
import java.util.*;

/**
 * @author Matthew Cheung
 *         Created on 13/06/14 1:51 PM
 */
@XmlRootElement
public class Project {
    public Integer id;
    public String name;
    public String description = "";
    public Integer parentProjectID = -1;
    public boolean isPublic = false;

    public Map<User, Role> userRoles = new HashMap<User, Role>();

    public Project(Integer id, String name, String description, Integer parentProjectID, boolean isPublic) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.parentProjectID = parentProjectID;
        this.isPublic = isPublic;
    }

    public Project() {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Project project = (Project)o;

        if (id != null ? !id.equals(project.id) : project.id != null) {
            return false;
        }

        if (!name.equals(project.name)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return 31*(id != null ? id.hashCode() : 0) + name.hashCode();
    }
}