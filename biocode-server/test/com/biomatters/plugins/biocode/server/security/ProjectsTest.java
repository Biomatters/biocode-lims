package com.biomatters.plugins.biocode.server.security;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.labbench.lims.DatabaseScriptRunner;
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
        dataSource = SqlLimsConnection.createBasicDataSource(connectionString, BiocodeService.getInstance().getLocalDriver(), null, null);
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
        System.out.println("Test");
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
    public void canAddHierarchy() throws DatabaseServiceException {
        FimsProject root = new FimsProject("1", "root", null);

        FimsProject root2 = new FimsProject("2", "root2", null);
        FimsProject root3 = new FimsProject("3", "root3", null);

        FimsProject rootChild = new FimsProject("4", "rootChild", root);
        FimsProject rootChild2 = new FimsProject("5", "rootChild2", root);

        FimsProject rootChildChild = new FimsProject("6", "rootChildChild", rootChild);

        List<FimsProject> noChildren = Arrays.asList(root, root2, root3);
        checkDatabaseMatchesList(dataSource, noChildren);
        checkDatabaseMatchesList(dataSource, noChildren);  // Check it does not create duplicates

        List<FimsProject> withChildren = Arrays.asList(root, root2, root3, rootChild, rootChild2, rootChildChild);
        checkDatabaseMatchesList(dataSource, withChildren);
    }

    @Test
    public void canUpdateProjectHierarchy() throws DatabaseServiceException {
        FimsProject root = new FimsProject("1", "root", null);
        FimsProject rootChild = new FimsProject("2", "rootChild", root);
        FimsProject rootChildChild = new FimsProject("3", "rootChildChild", rootChild);
        FimsProject rootChildChildMoved = new FimsProject("3", "rootChildChild", root);
        FimsProject rootChildChildRenamed = new FimsProject("3", "sub", root);
        FimsProject rootChildChildNotAChild = new FimsProject("3", "sub", null);

        checkDatabaseMatchesList(dataSource, Arrays.asList(root, rootChild, rootChildChild));
        checkDatabaseMatchesList(dataSource, Arrays.asList(root, rootChild, rootChildChildMoved));
        checkDatabaseMatchesList(dataSource, Arrays.asList(root, rootChild, rootChildChildRenamed));
        checkDatabaseMatchesList(dataSource, Arrays.asList(root, rootChild, rootChildChildNotAChild));
    }

    @Test
    public void deletesOldProjects() throws DatabaseServiceException {
        FimsProject root = new FimsProject("1", "root", null);
        FimsProject rootChild = new FimsProject("2", "rootChild", root);
        FimsProject rootChildChild = new FimsProject("3", "rootChildChild", rootChild);

        checkDatabaseMatchesList(dataSource, Arrays.asList(root, rootChild, rootChildChild));
        checkDatabaseMatchesList(dataSource, Arrays.asList(root, rootChild));
        checkDatabaseMatchesList(dataSource, Arrays.asList(root));
    }

    private void checkDatabaseMatchesList(DataSource dataSource, List<FimsProject> expected) throws DatabaseServiceException {
//        Projects.updateProject(dataSource, new FimsWithProjects(expected));
//        List<Project> inDatabase = Projects.getProjectsForId(dataSource);
//        assertEquals(expected.size(), inDatabase.size());
//
//        Map<String, Project> inDatabaseByKey = new HashMap<String, Project>();
//        for (Project project : inDatabase) {
//            inDatabaseByKey.put(project.globalId, project);
//        }
//
//        Set<Integer> idsSeen = new HashSet<Integer>();
//        for (FimsProject fimsProject : expected) {
//            Project toCompare = inDatabaseByKey.get(fimsProject.getId());
//            assertNotNull(toCompare);
//            assertFalse(idsSeen.contains(toCompare.id));
//            idsSeen.add(toCompare.id);
//            assertEquals(fimsProject.getId(), toCompare.globalId);
//            assertEquals(fimsProject.getName(), toCompare.name);
//            FimsProject parent = fimsProject.getParent();
//            if(parent != null) {
//                Project parentInDatabase = inDatabaseByKey.get(parent.getId());
//                assertTrue(parentInDatabase.id == toCompare.parentProjectId);
//                assertNotNull(parentInDatabase);
//                assertEquals(parent.getName(), parentInDatabase.name);
//            } else {
//                assertTrue(-1 == toCompare.parentProjectId);
//            }
//        }
    }

    @Test
    public void testCanGetProjectsForUser() throws DatabaseServiceException {
//        User user = new User();
//        user.username = "me";
//        user.password = "password";
//        user.firstname = "";
//        user.lastname = "";
//        user.email = "me@me.com";
//        Users.addUser(dataSource, user);
//
//        FimsProject adminOf = new FimsProject("1", "adminOf", null);
//        FimsProject writerOf = new FimsProject("2", "writerOf", null);
//        FimsProject readerOf = new FimsProject("3", "readerOf", null);
//        FimsProject noAccess = new FimsProject("4", "noAccess", null);
//
//        FimsWithProjects fimsConnection = new FimsWithProjects(Arrays.asList(
//                adminOf, writerOf, readerOf, noAccess
//        ));
//        Projects.updateProjectsFromFims(dataSource, fimsConnection);
//
//        List<Project> projects = Projects.getProjectsForId(dataSource);
//        setRoleForProjet(user, adminOf, projects, Role.ADMIN);
//        setRoleForProjet(user, writerOf, projects, Role.WRITER);
//        setRoleForProjet(user, readerOf, projects, Role.READER);
//
//        List<FimsProject> result = Projects.getFimsProjectsUserHasAtLeastRole(dataSource, fimsConnection, user, Role.ADMIN);
//        assertEquals(1, result.size());
//        assertTrue(result.contains(adminOf));
//
//        result = Projects.getFimsProjectsUserHasAtLeastRole(dataSource, fimsConnection, user, Role.WRITER);
//        assertEquals(2, result.size());
//        assertTrue(result.contains(adminOf));
//        assertTrue(result.contains(writerOf));
//
//        result = Projects.getFimsProjectsUserHasAtLeastRole(dataSource, fimsConnection, user, Role.READER);
//        assertEquals(3, result.size());
//        assertTrue(result.contains(adminOf));
//        assertTrue(result.contains(writerOf));
//        assertTrue(result.contains(readerOf));
    }

    @Test
    public void roleInheritedFromParent() throws DatabaseServiceException {
//        User user = new User();
//        user.username = "me";
//        user.password = "password";
//        user.firstname = "";
//        user.lastname = "";
//        user.email = "me@me.com";
//        Users.addUser(dataSource, user);
//
//        FimsProject parent = new FimsProject("1", "parent", null);
//        FimsProject child = new FimsProject("2", "child", parent);
//
//        FimsWithProjects fimsConnection = new FimsWithProjects(Arrays.asList(
//                parent, child
//        ));
//        Projects.updateProjectsFromFims(dataSource, fimsConnection);
//
//        List<Project> projects = Projects.getProjectsForId(dataSource);
//        for (Role role : new Role[]{Role.ADMIN, Role.WRITER, Role.READER, null}) {
//            setRoleForProjet(user, parent, projects, role);
//            List<FimsProject> withRole = Projects.getFimsProjectsUserHasAtLeastRole(dataSource, fimsConnection, user, role);
//            assertEquals(2, withRole.size());
//            assertTrue(withRole.contains(parent));
//            assertTrue(withRole.contains(child));
//        }
    }

    void setRoleForProjet(User user, FimsProject toSetFor, List<Project> projectsInDatabase, Role role) {
//        for (Project project : projectsInDatabase) {
//            if(project.globalId.equals(toSetFor.getId())) {
//                if(role != null) {
//                    Projects.setProjectRoleForUsername(dataSource, project.id, user.username, role);
//                } else {
//                    Projects.removeUserFromProject(dataSource, project.id, user.username);
//                }
//            }
//        }
    }
}
