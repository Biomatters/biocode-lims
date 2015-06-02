package com.biomatters.plugins.biocode.server.security;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.lims.DatabaseScriptRunner;
import com.biomatters.plugins.biocode.labbench.lims.LocalLIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection;
import com.biomatters.plugins.biocode.server.Project;
import com.biomatters.plugins.biocode.server.Role;
import com.biomatters.plugins.biocode.server.User;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Matthew Cheung
 *         Created on 30/06/14 12:05 AM
 */
public class ProjectsTest extends Assert {
    private File tempDir;
    private DataSource dataSource;

    @Before
    public void setupDatabase() throws ConnectionException, IOException, SQLException {
        tempDir = FileUtilities.createTempDir(true);
        String path = tempDir.getAbsolutePath() + File.separator + "database.db";
        System.out.println("Database Path: " + path);
        String connectionString = "jdbc:hsqldb:file:" + path + ";shutdown=true";
        dataSource = SqlLimsConnection.createBasicDataSource(connectionString, LocalLIMSConnection.getLocalDriver(), null, null);
        InputStream preScript = getClass().getResourceAsStream("for_tests.sql");
        InputStream script = getClass().getResourceAsStream("add_access_control_hsql.sql");
        DatabaseScriptRunner.runScript(dataSource.getConnection(), preScript, false, false);
        DatabaseScriptRunner.runScript(dataSource.getConnection(), script, false, false);
    }

    @After
    public void closeDatabase() {
        dataSource = null;
        tempDir = null;
    }

    @Test
    public void canAddProject() throws SQLException {
        Project p = new Project();
        p.name = "Test";

        Project p2 = new Project();
        p2.name = "Test2";

        Project p3 = new Project();
        p3.name = "Test3";

        Projects.addProject(dataSource, p);
        List<Project> inDatabase = Projects.getProjects(dataSource, Collections.<Integer>emptyList());
        assertEquals(1, inDatabase.size());
        assertEquals(p.name, inDatabase.get(0).name);
        assertTrue(p.userRoles.isEmpty());

        Projects.addProject(dataSource, p2);
        Projects.addProject(dataSource, p3);
        inDatabase = Projects.getProjects(dataSource, Collections.<Integer>emptyList());
        assertEquals(3, inDatabase.size());
        assertEquals(p.name, inDatabase.get(0).name);
        assertEquals(p2.name, inDatabase.get(1).name);
        assertEquals(p3.name, inDatabase.get(2).name);
    }

    @Test
    public void canDeleteProject() throws SQLException {
        Project p = new Project();
        p.name = "Test";

        assertTrue(Projects.getProjects(dataSource, Collections.<Integer>emptyList()).isEmpty());
        Projects.addProject(dataSource, p);
        assertEquals(1, Projects.getProjects(dataSource, Collections.singletonList(1)).size());

        Projects.removeProject(dataSource, 1);
        assertTrue(Projects.getProjects(dataSource, Collections.<Integer>emptyList()).isEmpty());
    }

    @Test
    public void deletingParentDeletesChild() throws SQLException {
        Project p = new Project();
        p.name = "parent";
        Projects.addProject(dataSource, p);

        Project p2 = new Project();
        p2.name = "child";
        p2.parentProjectID = 1;
        Projects.addProject(dataSource, p2);

        List<Project> projectList = Projects.getProjects(dataSource, Collections.<Integer>emptyList());

        assertEquals(2, projectList.size());

        Projects.removeProject(dataSource, 1);

        projectList = Projects.getProjects(dataSource, Collections.<Integer>emptyList());

        assertTrue(projectList.isEmpty());
    }

    @Test
    public void canUpdateProject() throws SQLException {
        String oldName = "Test";
        String newName = "Test2";

        Project p = new Project();
        p.name = oldName;
        Projects.addProject(dataSource, p);

        p.name = newName;
        p.id = 1;
        Projects.updateProject(dataSource, p);
        List<Project> inDatabase = Projects.getProjects(dataSource, Collections.<Integer>emptyList());
        assertEquals(1, inDatabase.size());
        assertEquals(newName, inDatabase.get(0).name);
    }

    @Test
    public void canListRoles() throws SQLException {
        User user1 = new User("user1", "password", "", "", "test@test.com", true, false, false);
        Users.addUser(dataSource, user1);

        Project p = new Project();
        p.name = "Test";
        p.userRoles.put(user1, Role.WRITER);
        Projects.addProject(dataSource, p);
        Projects.addProjectRoles(dataSource, 1, p.userRoles);

        List<Project> inDatabase = Projects.getProjects(dataSource, Collections.singletonList(1));
        Map<User, Role> userRole = Projects.getProjectRoles(dataSource, 1, Collections.singleton("user1"));
        assertEquals(1, inDatabase.size());
        assertEquals(1, userRole.size());
        assertEquals(Role.WRITER, userRole.get(user1));
    }

    @Test
    public void canSetRole() throws SQLException {
        User user1 = new User("user1", "password", "", "", "test@test.com", true, false, false);
        Users.addUser(dataSource, user1);

        Project p = new Project();
        p.name = "Test";
        p.userRoles.put(user1, Role.WRITER);
        Projects.addProject(dataSource, p);
        Projects.addProjectRoles(dataSource, 1, p.userRoles);

        List<Project> inDatabase = Projects.getProjects(dataSource, Collections.singletonList(1));
        Map<User, Role> userRole = Projects.getProjectRoles(dataSource, 1, Collections.singleton("user1"));

        assertEquals(1, inDatabase.size());
        assertEquals(1, userRole.size());
        assertEquals(Role.WRITER, userRole.get(user1));

        Projects.removeProjectRoles(dataSource, 1, Collections.singletonList(user1.username));
        inDatabase = Projects.getProjects(dataSource, Collections.singletonList(1));
        assertTrue(inDatabase.get(0).userRoles.isEmpty());
    }

    @Test
    public void canAddHierarchy() throws DatabaseServiceException, SQLException {
        Project root = new Project(1, "root", "", -1, false);
        Project root2 = new Project(2, "root2", "", -1, false);
        Project root3 = new Project(3, "root3", "", -1, false);

        Project rootChild = new Project(4, "rootChild", "", 1, false);
        Project rootChild2 = new Project(5, "rootChild2", "", 1, false);

        Project rootChildChild = new Project(6, "rootChildChild", "", 4, false);

        List<Project> noChildren = Arrays.asList(root, root2, root3);
        setProjectsInDatabase(dataSource, noChildren);
        setProjectsInDatabase(dataSource, noChildren);  // Check it does not create duplicates

        List<Project> withChildren = Arrays.asList(root, root2, root3, rootChild, rootChild2, rootChildChild);
        setProjectsInDatabase(dataSource, withChildren);
    }

    @Test
    public void canUpdateProjectHierarchy() throws DatabaseServiceException, SQLException {
        Project root = new Project(1, "root", "", -1, false);
        Project rootChild = new Project(2, "rootChild", "", 1, false);
        Project rootChildChild = new Project(3, "rootChildChild", "", 2, false);
        Project rootChildChildMoved = new Project(3, "rootChildChild", "", 1, false);
        Project rootChildChildRenamed = new Project(3, "sub", "", 1, false);
        Project rootChildChildNotAChild = new Project(3, "sub", "", -1, false);

        setProjectsInDatabase(dataSource, Arrays.asList(root, rootChild, rootChildChild));
        setProjectsInDatabase(dataSource, Arrays.asList(root, rootChild, rootChildChildMoved));
        setProjectsInDatabase(dataSource, Arrays.asList(root, rootChild, rootChildChildRenamed));
        setProjectsInDatabase(dataSource, Arrays.asList(root, rootChild, rootChildChildNotAChild));
    }

    @Test
    public void deletesOldProjects() throws DatabaseServiceException, SQLException {
        Project root = new Project(1, "root", "", -1, false);
        Project rootChild = new Project(2, "rootChild", "", 1, false);
        Project rootChildChild = new Project(3, "rootChildChild", "", 2, false);

        setProjectsInDatabase(dataSource, Arrays.asList(root, rootChild, rootChildChild));
        setProjectsInDatabase(dataSource, Arrays.asList(root, rootChild));
        setProjectsInDatabase(dataSource, Collections.singletonList(root));
    }

    private void setProjectsInDatabase(DataSource dataSource, List<Project> expected) throws DatabaseServiceException, SQLException {
        List<Project> existingProjects = Projects.getProjects(dataSource, Collections.<Integer>emptyList());
        for (Project project : existingProjects) {
            if (!expected.contains(project)) {
                Projects.removeProject(dataSource, project.id);
            }
        }

        for (Project project : expected) {
            if (existingProjects.contains(project)) {
                Projects.updateProject(dataSource, project);
            } else {
                Projects.addProject(dataSource, project);
            }
        }

        List<Project> inDatabase = Projects.getProjects(dataSource, Collections.<Integer>emptyList());
        assertEquals(expected.size(), inDatabase.size());

        Map<Integer, Project> inDatabaseByKey = new HashMap<Integer, Project>();
        for (Project project : inDatabase) {
            inDatabaseByKey.put(project.id, project);
        }

        Set<Integer> idsSeen = new HashSet<Integer>();
        for (Project project : expected) {
            Project toCompare = inDatabaseByKey.get(project.id);
            assertNotNull(toCompare);
            assertFalse(idsSeen.contains(toCompare.id));
            idsSeen.add(toCompare.id);
            assertEquals(project.id, toCompare.id);
            assertEquals(project.name, toCompare.name);
            int parentProjectId = project.parentProjectID;
            Project parent = inDatabaseByKey.get(parentProjectId);
            if (parentProjectId == -1) {
                assertNull(parent);
            } else {
                assertNotNull(parent);
            }
        }
    }

    @Test
    public void testCanGetProjectsForUser() throws DatabaseServiceException, SQLException {
        User user = new User();
        user.username = "me";
        user.password = "password";
        user.firstname = "";
        user.lastname = "";
        user.email = "me@me.com";
        Users.addUser(dataSource, user);

        Project adminOf = new Project(1, "adminOf", "", -1, false);
        Project writerOf = new Project(2, "writerOf", "", -1, false);
        Project readerOf = new Project(3, "readerOf", "", -1, false);

        Projects.addProject(dataSource, adminOf);
        Projects.addProject(dataSource, writerOf);
        Projects.addProject(dataSource, readerOf);

        Projects.addProjectRoles(dataSource, adminOf.id, Collections.singletonMap(user, Role.ADMIN));
        Projects.addProjectRoles(dataSource, writerOf.id, Collections.singletonMap(user, Role.WRITER));
        Projects.addProjectRoles(dataSource, readerOf.id, Collections.singletonMap(user, Role.READER));

        List<Project> result = Projects.getProjectsUserHasRoleAccessFor(dataSource, user, Role.ADMIN);
        assertEquals(1, result.size());
        assertTrue(result.contains(adminOf));

        result = Projects.getProjectsUserHasRoleAccessFor(dataSource, user, Role.WRITER);
        assertEquals(2, result.size());
        assertTrue(result.contains(adminOf));
        assertTrue(result.contains(writerOf));

        result = Projects.getProjectsUserHasRoleAccessFor(dataSource, user, Role.READER);
        assertEquals(3, result.size());
        assertTrue(result.contains(adminOf));
        assertTrue(result.contains(writerOf));
        assertTrue(result.contains(readerOf));
    }

    @Test
    public void roleInheritedFromParent() throws DatabaseServiceException, SQLException {
        User user = new User();
        user.username = "me";
        user.password = "password";
        user.firstname = "";
        user.lastname = "";
        user.email = "me@me.com";
        Users.addUser(dataSource, user);

        Project parent = new Project(1, "parent", "", -1, false);
        Project child = new Project(2, "child", "", 1, false);

        Projects.addProject(dataSource, parent);
        Projects.addProject(dataSource, child);

        for (Role role : new Role[]{Role.ADMIN, Role.WRITER, Role.READER}) {
            Projects.addProjectRoles(dataSource, parent.id, Collections.singletonMap(user, role));
            List<Project> withRole = Projects.getProjectsUserHasRoleAccessFor(dataSource, user, role);
            assertEquals(2, withRole.size());
            assertTrue(withRole.contains(parent));
            assertTrue(withRole.contains(child));
        }
    }
}