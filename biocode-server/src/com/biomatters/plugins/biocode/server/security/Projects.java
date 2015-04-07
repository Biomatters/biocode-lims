package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.server.*;
import com.biomatters.plugins.biocode.server.utilities.StringUtilities;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;

import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;
import java.sql.*;
import java.util.*;

/**
 * @author Matthew Cheung
 *         Created on 13/06/14 2:22 PM
 */
@Path("projects")
public class Projects {
    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    public Response list() {
        try {
            return Response.ok(new GenericEntity<List<Project>>(getProjects(LIMSInitializationListener.getDataSource(), Collections.<Integer>emptySet())){}).build();
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of the projects was unsuccessful: " + e.getMessage());
        }
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{id}")
    public Project getProject(@PathParam("id")int projectID) {
        List<Project> retrievedProjects;

        try {
            retrievedProjects = getProjects(LIMSInitializationListener.getDataSource(), Collections.singleton(projectID));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of project with ID " + projectID + " was unsuccessful: " + e.getMessage());
        }

        if (retrievedProjects.isEmpty()) {
            throw new NotFoundException("A project with ID " + projectID + " could not be found.");
        } else if (retrievedProjects.size() > 1) {
            throw new InternalServerErrorException("More than 1 project with ID " + projectID + " was found.");
        }

        return retrievedProjects.get(0);
    }

    @POST
    @Consumes({"application/json", "application/xml"})
    @Produces("text/plain")
    public String addProject(Project project) {
        try {
            return Integer.toString(addProject(LIMSInitializationListener.getDataSource(), project));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The creation of project " + project.name + " was unsuccessful: " + e.getMessage());
        }
    }

    @PUT
    @Consumes({"application/json", "application/xml"})
    @Path("{id}")
    public void updateProject(@PathParam("id")int id, Project project) {
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }

        project.id = id;

        try {
            updateProject(LIMSInitializationListener.getDataSource(), project);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The update of project " + project.name + " was unsuccessful: " + e.getMessage());
        }
    }

    @DELETE
    @Path("{id}")
    public void removeProject(@PathParam("id")int id) {
        try {
            removeProject(LIMSInitializationListener.getDataSource(), id);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The deletion of project with ID " + id + " was unsuccessful: " + e.getMessage());
        }
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{id}/roles")
    public Response listProjectRoles(@PathParam("id")int projectID) {
        try {
            return Response.ok(new GenericEntity<List<UserRole>>(UserRole.forMap(getProjectRoles(LIMSInitializationListener.getDataSource(), projectID, Collections.<String>emptySet()))){}).build();
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of user roles for project with ID " + projectID + " was unsuccessful: " + e.getMessage());
        }
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{id}/roles/{username}")
    public Role getProjectRole(@PathParam("id")int projectID, @PathParam("username")String username) {
        if (username == null) {
            throw new IllegalArgumentException("username is null.");
        }
        if (username.isEmpty()) {
            throw new IllegalArgumentException("username is an empty string.");
        }

        Map<User, Role> userToRole;
        try {
            userToRole = getProjectRoles(LIMSInitializationListener.getDataSource(), projectID, Collections.singleton(username));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of user " + username + "'s role for project with ID " + projectID + " was unsuccessful: " + e.getMessage());
        }

        if (userToRole.isEmpty()) {
            throw new NotFoundException("A role for user " + username + " for project with ID " + projectID + " was not found.");
        }

        return userToRole.entrySet().iterator().next().getValue();
    }

    @PUT
    @Consumes({"application/json", "application/xml"})
    @Path("{id}/roles/{username}")
    public void assignProjectRole(@PathParam("id")int projectID, @PathParam("username")String username, Role role) {
        if (role == null) {
            throw new IllegalArgumentException("role is null.");
        }
        if (username == null) {
            throw new IllegalArgumentException("username is null.");
        }
        if (username.isEmpty()) {
            throw new IllegalArgumentException("username is an empty string.");
        }

        User user = new User();
        user.username = username;
        try {
            addProjectRoles(LIMSInitializationListener.getDataSource(), projectID, Collections.singletonMap(user, role));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The assignment of role " + role.name + " to user " + username + " for project with ID " + projectID + " was unsuccessful: " + e.getMessage());
        }
    }

    @DELETE
    @Path("{id}/roles/{username}")
    public void deleteProjectRole(@PathParam("id")int projectID, @PathParam("username")String username) {
        if (username == null) {
            throw new IllegalArgumentException("username is null.");
        }

        try {
            removeProjectRoles(LIMSInitializationListener.getDataSource(), projectID, Collections.singleton(username));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The deletion of user " + username + "'s role in project with ID " + projectID + " was unsuccessful: " + e.getMessage());
        }
    }

    @GET
    @Produces("application/xml")
    @Path("{id}/workflows")
    public XMLSerializableList<Workflow> listWorkflowsAssignedToProject(@PathParam("id")int projectID) {
        try {
            return new XMLSerializableList<Workflow>(Workflow.class, new ArrayList<Workflow>(getWorkflowsAssignedToProject(LIMSInitializationListener.getDataSource(), projectID)));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of workflows assigned to project with ID " + projectID + " was unsuccessful: " + e.getMessage());
        }
    }

    @POST
    @Consumes({"application/json", "application/xml"})
    @Path("{id}/workflows/{workflowID}")
    public void assignWorkflowToProject(@PathParam("id")int projectID, @PathParam("workflowID")int workflowID) {
        try {
            assignWorkflowsToProject(LIMSInitializationListener.getDataSource(), projectID, Collections.singletonList(workflowID));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The assignment of workflow with ID " + workflowID + " to project with ID " + projectID + " was unsuccessful: " + e.getMessage());
        }
    }

    @DELETE
    @Path("{id}/workflows/{workflowID}")
    public void unassignWorkflowsFromProjects(@PathParam("id")int projectID, @PathParam("workflowID")int workflowID) {
        try {
            unassignWorkflowsFromProjects(LIMSInitializationListener.getDataSource(), Collections.singletonList(workflowID));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The unassignment of workflow with ID " + workflowID + " from project with ID " + projectID + " was unsuccessful: " + e.getMessage());
        }
    }

    /**
     *
     * @param dataSource To use to obtain a connection to the database
     * @param projectIDs A list of project IDs for which to retrieve projects or an empty list if all projects are to be retrieved
     * @return A list of projects matching the specified ids or all projects if no ids were specified.
     */
    static List<Project> getProjects(DataSource dataSource, Collection<Integer> projectIDs) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (projectIDs == null) {
            throw new IllegalArgumentException("projectIDs is null.");
        }

        projectIDs = new HashSet<Integer>(projectIDs);

        List<Project> projects = new ArrayList<Project>();

        Connection connection = null;
        PreparedStatement getProjectsStatement = null;
        ResultSet getProjectsResultSet = null;
        try {
            connection = dataSource.getConnection();
            getProjectsStatement = connection.prepareStatement(
                    "SELECT * FROM project " +
                    (projectIDs.isEmpty() ? "" : "WHERE project.id IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(projectIDs.size()) + ") ")
            );

            int statementObjectIndex = 1;
            for (Integer projectID : projectIDs) {
                getProjectsStatement.setInt(statementObjectIndex++, projectID);
            }

            getProjectsResultSet = getProjectsStatement.executeQuery();

            while (getProjectsResultSet.next()) {
                projects.add(createProjectFromResultSet(getProjectsResultSet));
            }
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(getProjectsStatement);
            SqlUtilities.cleanUpResultSets(getProjectsResultSet);
        }

        return projects;
    }

    /**
     *
     * @param user The user account that is attempting to retreive data.
     * @param role The role to check for.
     * @return A list of {@link FimsProject}s that the specified user is allowed to view.  Or null if there are no projects in the system.
     */
    public static List<Project> getProjectsUserHasRoleAccessFor(DataSource dataSource, User user, Role role) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (user == null) {
            throw new IllegalArgumentException("user is null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is null.");
        }

        List<Project> projectsUserHasRoleAccessFor = new ArrayList<Project>();

        List<Project> allProjects = getProjects(dataSource, Collections.<Integer>emptySet());
        if (user.isAdministrator) {
            projectsUserHasRoleAccessFor.addAll(allProjects);
        } else {
            for (Project project : allProjects) {
                Role userRoleForProject = getRoleForUser(project, user);
                if (userRoleForProject != null && userRoleForProject.isAtLeast(role)) {
                    projectsUserHasRoleAccessFor.add(project);
                }
            }
        }

        return projectsUserHasRoleAccessFor;
    }

    static synchronized int addProject(DataSource dataSource, Project project) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }

        Connection connection = null;
        PreparedStatement retrieveMaxExistingProjectIDStatement = null;
        PreparedStatement addProjectStatement = null;
        ResultSet retrieveMaxExistingProjectIDResultSet = null;
        try {
            connection = dataSource.getConnection();

            retrieveMaxExistingProjectIDStatement = connection.prepareStatement("SELECT MAX(id) FROM " + BiocodeServerLIMSDatabaseConstants.PROJECT_TABLE_NAME);
            retrieveMaxExistingProjectIDResultSet = retrieveMaxExistingProjectIDStatement.executeQuery();
            retrieveMaxExistingProjectIDResultSet.next();
            int projectID = retrieveMaxExistingProjectIDResultSet.getInt(1) + 1;

            addProjectStatement = connection.prepareStatement(
                    "INSERT INTO " + BiocodeServerLIMSDatabaseConstants.PROJECT_TABLE_NAME + "(id, name, description, parent_project_id, is_public) " +
                    "VALUES(" + StringUtilities.generateCommaSeparatedQuestionMarks(5) + ")"
            );

            addProjectStatement.setInt(1, projectID);
            addProjectStatement.setString(2, project.name);
            addProjectStatement.setString(3, project.description);
            if (project.parentProjectID < 0) {
                addProjectStatement.setNull(4, Types.INTEGER);
            } else {
                addProjectStatement.setInt(4, project.parentProjectID);
            }
            addProjectStatement.setBoolean(5, project.isPublic);

            if (addProjectStatement.executeUpdate() == 0) {
                throw new InternalServerErrorException("The addition of project " + project.name + " was unsuccessful.");
            }

            return projectID;
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(retrieveMaxExistingProjectIDStatement, addProjectStatement);
            SqlUtilities.cleanUpResultSets(retrieveMaxExistingProjectIDResultSet);
        }
    }

    static void updateProject(DataSource dataSource, Project project) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }

        Connection connection = null;
        PreparedStatement updateProjectStatement = null;
        try {
            connection = dataSource.getConnection();

            updateProjectStatement = connection.prepareStatement(
                    "UPDATE " + BiocodeServerLIMSDatabaseConstants.PROJECT_TABLE_NAME +
                    " SET name=?, description=?, parent_project_id=?, is_public=? " +
                    "WHERE id=?"
            );

            updateProjectStatement.setString(1, project.name);
            updateProjectStatement.setString(2, project.description);
            if (project.parentProjectID == -1) {
                updateProjectStatement.setNull(3, Types.INTEGER);
            } else {
                updateProjectStatement.setInt(3, project.parentProjectID);
            }
            updateProjectStatement.setBoolean(4, project.isPublic);
            updateProjectStatement.setInt(5, project.id);

            if (updateProjectStatement.executeUpdate() == 0) {
                throw new InternalServerErrorException("Project with ID " + project.id + " could not be updated.");
            }
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(updateProjectStatement);
        }
    }

    static void removeProject(DataSource dataSource, int projectID) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }

        Connection connection = null;
        PreparedStatement removeProjectStatement = null;
        try {
            connection = dataSource.getConnection();
            removeProjectStatement = connection.prepareStatement("DELETE FROM " + BiocodeServerLIMSDatabaseConstants.PROJECT_TABLE_NAME + " WHERE id=?");

            removeProjectStatement.setInt(1, projectID);

            removeProjectStatement.executeUpdate();
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(removeProjectStatement);
        }
    }

    static Map<User, Role> getProjectRoles(DataSource dataSource, int projectID, Collection<String> usernames) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (usernames == null) {
            throw new IllegalArgumentException("usernames is null.");
        }

        usernames = new HashSet<String>(usernames);

        Map<User, Role> projectRoles = new HashMap<User, Role>();

        Connection connection = null;
        PreparedStatement getProjectRolesStatement = null;
        ResultSet getProjectRolesResultSet = null;
        try {
            connection = dataSource.getConnection();
            getProjectRolesStatement = connection.prepareStatement(
                    "SELECT * FROM " + BiocodeServerLIMSDatabaseConstants.PROJECT_ROLE_TABLE_NAME + ", " + BiocodeServerLIMSDatabaseConstants.USERS_TABLE_NAME + ", " + BiocodeServerLIMSDatabaseConstants.AUTHORITIES_TABLE_NAME +
                    " WHERE " + BiocodeServerLIMSDatabaseConstants.PROJECT_ROLE_TABLE_NAME + ".project_id=? " +
                    "AND " + BiocodeServerLIMSDatabaseConstants.PROJECT_ROLE_TABLE_NAME + ".username=" + BiocodeServerLIMSDatabaseConstants.USERS_TABLE_NAME + ".username " +
                    (usernames.isEmpty() ? "" : "AND " + BiocodeServerLIMSDatabaseConstants.PROJECT_ROLE_TABLE_NAME + ".username IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(usernames.size()) + ")")
            );

            getProjectRolesStatement.setInt(1, projectID);
            int statementObjectIndex = 2;
            for (String username : usernames) {
                getProjectRolesStatement.setString(statementObjectIndex++, username);
            }

            getProjectRolesResultSet = getProjectRolesStatement.executeQuery();
            while (getProjectRolesResultSet.next()) {
                projectRoles.put(Users.createUserFromResultSetRow(getProjectRolesResultSet), Role.getRole(getProjectRolesResultSet.getInt(BiocodeServerLIMSDatabaseConstants.PROJECT_ROLE_TABLE_NAME + ".role")));
            }
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(getProjectRolesStatement);
            SqlUtilities.cleanUpResultSets(getProjectRolesResultSet);
        }

        return projectRoles;
    }

    static void addProjectRoles(DataSource dataSource, int projectID, Map<User, Role> userToRole) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (userToRole == null) {
            throw new IllegalArgumentException("userToRole is null.");
        }
        if (userToRole.isEmpty()) {
            return;
        }

        Connection connection = null;
        PreparedStatement retrieveProjectRolesStatement = null;
        ResultSet retrieveProjectRolesResultSet = null;
        Map<User, Role> existingProjectRoles = new HashMap<User, Role>();
        Map<User, Role> newProjectRoles = new HashMap<User, Role>(userToRole);
        try {
            connection = dataSource.getConnection();
            retrieveProjectRolesStatement = connection.prepareStatement(
                    "SELECT username FROM " + BiocodeServerLIMSDatabaseConstants.PROJECT_ROLE_TABLE_NAME +
                    " WHERE project_id=? " +
                    "AND username IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(userToRole.size()) + ")"
            );

            retrieveProjectRolesStatement.setInt(1, projectID);
            int statementObjectIndex = 2;
            for (User user : userToRole.keySet()) {
                retrieveProjectRolesStatement.setString(statementObjectIndex++, user.username);
            }

            retrieveProjectRolesResultSet = retrieveProjectRolesStatement.executeQuery();
            while (retrieveProjectRolesResultSet.next()) {
                String username = retrieveProjectRolesResultSet.getString(1);
                for (Map.Entry<User, Role> userAndRole : userToRole.entrySet()) {
                    if (userAndRole.getKey().username.equals(username)) {
                        existingProjectRoles.put(userAndRole.getKey(), userAndRole.getValue());
                        newProjectRoles.remove(userAndRole.getKey());
                    }
                }
            }

            SqlUtilities.beginTransaction(connection);

            addNewProjectRoles(dataSource, projectID, newProjectRoles);
            updateProjectRoles(dataSource, projectID, existingProjectRoles);

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(retrieveProjectRolesStatement);
            SqlUtilities.cleanUpResultSets(retrieveProjectRolesResultSet);
        }
    }

    static void addNewProjectRoles(DataSource dataSource, int projectID, Map<User, Role> userToRole) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (userToRole == null) {
            throw new IllegalArgumentException("userToRole is null.");
        }
        if (userToRole.isEmpty()) {
            return;
        }

        Connection connection = null;
        PreparedStatement addProjectRolesStatement = null;
        try {
            connection = dataSource.getConnection();

            addProjectRolesStatement = connection.prepareStatement(
                    "INSERT INTO " + BiocodeServerLIMSDatabaseConstants.PROJECT_ROLE_TABLE_NAME + "(project_id, username, role) " +
                    "VALUES(" + StringUtilities.generateCommaSeparatedQuestionMarks(3) + ")"
            );

            addProjectRolesStatement.setObject(1, projectID);
            for (Map.Entry<User, Role> userAndRole : userToRole.entrySet()) {
                addProjectRolesStatement.setString(2, userAndRole.getKey().username);
                addProjectRolesStatement.setInt(3, userAndRole.getValue().id);
                addProjectRolesStatement.addBatch();
            }

            SqlUtilities.beginTransaction(connection);

            int[] additionResults = addProjectRolesStatement.executeBatch();
            for (int additionResult : additionResults) {
                if (additionResult != 1 && additionResult != PreparedStatement.SUCCESS_NO_INFO) {
                    throw new InternalServerErrorException("The addition of 1 or more project roles was unsuccessful. Changes will be undone.");
                }
            }

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(addProjectRolesStatement);
        }
    }

    static void updateProjectRoles(DataSource dataSource, int projectID, Map<User, Role> userToRole) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (userToRole == null) {
            throw new IllegalArgumentException("username is null.");
        }
        if (userToRole.isEmpty()) {
            return;
        }

        Connection connection = null;
        PreparedStatement updateProjectRolesStatement = null;
        try {
            connection = dataSource.getConnection();
            updateProjectRolesStatement = connection.prepareStatement(
                    "UPDATE " + BiocodeServerLIMSDatabaseConstants.PROJECT_ROLE_TABLE_NAME +
                    " SET role=? " +
                    "WHERE username=? " +
                    "AND project_id=?"
            );

            updateProjectRolesStatement.setInt(3, projectID);
            for (Map.Entry<User, Role> userAndRole : userToRole.entrySet()) {
                updateProjectRolesStatement.setInt(1, userAndRole.getValue().id);
                updateProjectRolesStatement.setString(2, userAndRole.getKey().username);
                updateProjectRolesStatement.addBatch();
            }

            SqlUtilities.beginTransaction(connection);

            int[] updateResults = updateProjectRolesStatement.executeBatch();
            for (int updateResult : updateResults) {
                if (updateResult != 1 && updateResult != PreparedStatement.SUCCESS_NO_INFO) {
                    throw new InternalServerErrorException("The update of 1 or more project roles was unsuccessful. Changes will be undone.");
                }
            }

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(updateProjectRolesStatement);
        }
    }

    static void removeProjectRoles(DataSource dataSource, int projectID, Collection<String> usernames) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (usernames == null) {
            throw new IllegalArgumentException("usernames is null.");
        }
        if (usernames.isEmpty()) {
            return;
        }

        usernames = new HashSet<String>(usernames);

        Connection connection = null;
        PreparedStatement removeProjectRolesStatement = null;
        try {
            connection = dataSource.getConnection();
            removeProjectRolesStatement = connection.prepareStatement(
                    "DELETE FROM " + BiocodeServerLIMSDatabaseConstants.PROJECT_ROLE_TABLE_NAME +
                    " WHERE project_id=? " +
                    "AND username IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(usernames.size()) + ")");

            removeProjectRolesStatement.setObject(1, projectID);
            int statementObjectIndex = 2;
            for (String username : usernames) {
                removeProjectRolesStatement.setObject(statementObjectIndex++, username);
            }

            removeProjectRolesStatement.executeUpdate();
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(removeProjectRolesStatement);
        }
    }

    static Set<Workflow> getWorkflowsAssignedToProject(DataSource dataSource, int projectID) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }

        Set<Workflow> workflows = new HashSet<Workflow>();

        Connection connection = null;
        PreparedStatement getWorkflowsAssignedToProjectStatement = null;
        ResultSet getWorkflowsResultSet = null;
        try {
            connection = dataSource.getConnection();
            getWorkflowsAssignedToProjectStatement = connection.prepareStatement(
                    "SELECT workflow.* FROM workflow, " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                    " WHERE " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME + ".project_id=? " +
                    "AND workflow.id=" + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME+ ".workflow_id "
            );

            getWorkflowsAssignedToProjectStatement.setInt(1, projectID);

            getWorkflowsResultSet = getWorkflowsAssignedToProjectStatement.executeQuery();
            while (getWorkflowsResultSet.next()) {
                workflows.add(new Workflow(getWorkflowsResultSet));
            }
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(getWorkflowsAssignedToProjectStatement);
            SqlUtilities.cleanUpResultSets(getWorkflowsResultSet);
        }

        return workflows;
    }

    static void assignWorkflowsToProject(DataSource dataSource, int projectID, Collection<Integer> workflowIDs) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (workflowIDs == null) {
            throw new IllegalArgumentException("workflowIDs is null.");
        }
        if (workflowIDs.isEmpty()) {
            return;
        }

        workflowIDs = new HashSet<Integer>(workflowIDs);

        Connection connection = null;
        PreparedStatement retrieveIDsOfWorkflowsAssignedToProjectStatement = null;
        ResultSet retrieveIDsOfWorkflowsAssignedToProjectResultSet = null;
        Set<Integer> idsOfWorkflowsAssignedToAProject = new HashSet<Integer>();
        Set<Integer> idsOfWorkflowsNotAssignedToAProject = new HashSet<Integer>(workflowIDs);
        try {
            connection = dataSource.getConnection();
            retrieveIDsOfWorkflowsAssignedToProjectStatement = connection.prepareStatement(
                    "SELECT workflow_id FROM " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                    " WHERE workflow.id IN (" + workflowIDs.size() + ")"
            );

            retrieveIDsOfWorkflowsAssignedToProjectStatement.setInt(1, projectID);
            int statementObjectIndex = 2;
            for (Integer workflowID : workflowIDs) {
                retrieveIDsOfWorkflowsAssignedToProjectStatement.setInt(statementObjectIndex++, workflowID);
            }

            retrieveIDsOfWorkflowsAssignedToProjectResultSet = retrieveIDsOfWorkflowsAssignedToProjectStatement.executeQuery();
            while (retrieveIDsOfWorkflowsAssignedToProjectResultSet.next()) {
                idsOfWorkflowsAssignedToAProject.add(retrieveIDsOfWorkflowsAssignedToProjectResultSet.getInt(1));
            }

            idsOfWorkflowsNotAssignedToAProject.removeAll(idsOfWorkflowsAssignedToAProject);

            SqlUtilities.beginTransaction(connection);

            insertWorkflowProjectMapping(dataSource, projectID, idsOfWorkflowsNotAssignedToAProject);
            updateWorkflowProjectMapping(dataSource, projectID, idsOfWorkflowsAssignedToAProject);

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(retrieveIDsOfWorkflowsAssignedToProjectStatement);
            SqlUtilities.cleanUpResultSets(retrieveIDsOfWorkflowsAssignedToProjectResultSet);
        }
    }

    static void insertWorkflowProjectMapping(DataSource dataSource, int projectID, Collection<Integer> workflowIDs) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (workflowIDs == null) {
            throw new IllegalArgumentException("workflowIDs is null.");
        }
        if (workflowIDs.isEmpty()) {
            return;
        }

        workflowIDs = new HashSet<Integer>(workflowIDs);

        Connection connection = null;
        PreparedStatement addWorkflowsToProjectsStatement = null;
        ResultSet addWorkflowsToProjectsResultSet = null;
        try {
            connection = dataSource.getConnection();
            addWorkflowsToProjectsStatement = connection.prepareStatement(
                    "INSERT INTO " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME + "(workflow_id, project_id) " +
                    "VALUES(" + StringUtilities.generateCommaSeparatedQuestionMarks(workflowIDs.size()) + ")"
            );

            addWorkflowsToProjectsStatement.setInt(2, projectID);
            for (Integer workflowID : workflowIDs) {
                addWorkflowsToProjectsStatement.setInt(1, workflowID);
                addWorkflowsToProjectsStatement.addBatch();
            }

            SqlUtilities.beginTransaction(connection);

            int[] additionResults = addWorkflowsToProjectsStatement.executeBatch();
            for (int additionResult : additionResults) {
                if (additionResult != 1 && additionResult != PreparedStatement.SUCCESS_NO_INFO) {
                    throw new InternalServerErrorException("The assignment of 1 or more workflows to project with ID " + projectID + " was unsuccessful. Changes will be undone.");
                }
            }

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(addWorkflowsToProjectsStatement);
            SqlUtilities.cleanUpResultSets(addWorkflowsToProjectsResultSet);
        }
    }

    static void updateWorkflowProjectMapping(DataSource dataSource, int projectID, Collection<Integer> workflowIDs) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (workflowIDs == null) {
            throw new IllegalArgumentException("workflowIDs is null.");
        }
        if (workflowIDs.isEmpty()) {
            return;
        }

        workflowIDs = new HashSet<Integer>(workflowIDs);

        Connection connection = null;
        PreparedStatement assignProjectToWorkflowsStatement = null;
        try {
            connection = dataSource.getConnection();
            assignProjectToWorkflowsStatement = connection.prepareStatement(
                    "UPDATE " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                    " SET project_id=? " +
                    "WHERE workflow_id=?"
            );

            assignProjectToWorkflowsStatement.setInt(1, projectID);
            for (Integer workflowID : workflowIDs) {
                assignProjectToWorkflowsStatement.setInt(2, workflowID);
                assignProjectToWorkflowsStatement.addBatch();
            }

            SqlUtilities.beginTransaction(connection);

            int[] assignmentResults = assignProjectToWorkflowsStatement.executeBatch();
            for (int assignmentResult : assignmentResults) {
                if (assignmentResult != 1 && assignmentResult != PreparedStatement.SUCCESS_NO_INFO) {
                    throw new InternalServerErrorException("The assignment of project with ID " + projectID + " to 1 or more workflows was unsuccessful. Changes will be undone.");
                }
            }

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(assignProjectToWorkflowsStatement);
        }
    }

    static void unassignWorkflowsFromProjects(DataSource dataSource, Collection<Integer> workflowIDs) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (workflowIDs == null) {
            throw new IllegalArgumentException("workflowIDs is null.");
        }
        if (workflowIDs.isEmpty()) {
            return;
        }

        workflowIDs = new HashSet<Integer>(workflowIDs);

        Connection connection = null;
        PreparedStatement removeWorkflowsAssignedToProjectStatement = null;
        try {
            connection = dataSource.getConnection();
            removeWorkflowsAssignedToProjectStatement = connection.prepareStatement(
                    "DELETE * FROM " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                    " WHERE workflow_id IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(workflowIDs.size()) + ")"
            );

            int statementObjectIndex = 1;
            for (Integer workflowID : workflowIDs) {
                removeWorkflowsAssignedToProjectStatement.setInt(statementObjectIndex++, workflowID);
            }

            removeWorkflowsAssignedToProjectStatement.executeUpdate();
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(removeWorkflowsAssignedToProjectStatement);
        }
    }

    /**
     *
     * @return The role the current user has in the project.  Will fetch from parent groups if the user is not
     * part of the current project.
     */
    public static Role getRoleForUser(Project project, User user) throws SQLException {
        Role role = project.userRoles.get(user);
        if (role != null) {
            return role;
        } else if (project.parentProjectID != -1) {
            return getRoleForUser(new Projects().getProject(project.parentProjectID), user);
        } else {
            return null;
        }
    }

    private static Project createProjectFromResultSet(ResultSet resultSet) throws SQLException {
        if (resultSet == null) {
            throw new IllegalArgumentException("resultSet is null.");
        }

        int parentProjectID = resultSet.getInt("parent_project_id");
        parentProjectID = parentProjectID > 0 ? parentProjectID : -1;

        return new Project(resultSet.getInt("id"), resultSet.getString("name"), resultSet.getString("description"), parentProjectID, resultSet.getBoolean("is_public"));
    }
}