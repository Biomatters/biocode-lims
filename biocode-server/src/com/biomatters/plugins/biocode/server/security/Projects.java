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
    public Project getProject(@PathParam("id")int projectId) {
        List<Project> retrievedProjects;

        try {
            retrievedProjects = getProjects(LIMSInitializationListener.getDataSource(), Collections.singleton(projectId));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of project with id " + projectId + " was unsuccessful: " + e.getMessage());
        }

        if (retrievedProjects.isEmpty()) {
            throw new NotFoundException("A project with ID " + projectId + " could not be found.");
        } else if (retrievedProjects.size() > 1) {
            throw new InternalServerErrorException("More than 1 project with id " + projectId + " was found.");
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
            throw new InternalServerErrorException("The deletion of project with id " + id + " was unsuccessful: " + e.getMessage());
        }
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{id}/roles")
    public Response listProjectRoles(@PathParam("id")int projectId) {
        try {
            return Response.ok(new GenericEntity<List<UserRole>>(UserRole.forMap(getProjectRoles(LIMSInitializationListener.getDataSource(), projectId, Collections.<String>emptySet()))){}).build();
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of user roles for project with id " + projectId + " was unsuccessful: " + e.getMessage());
        }
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{id}/roles/{username}")
    public Role getProjectRole(@PathParam("id")int projectId, @PathParam("username")String username) {
        if (username == null) {
            throw new IllegalArgumentException("username is null.");
        }
        if (username.isEmpty()) {
            throw new IllegalArgumentException("username is an empty string.");
        }

        Map<User, Role> userToRole;
        try {
            userToRole = getProjectRoles(LIMSInitializationListener.getDataSource(), projectId, Collections.singleton(username));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of user " + username + "'s role for project with id " + projectId + " was unsuccessful: " + e.getMessage());
        }

        if (userToRole.isEmpty()) {
            throw new NotFoundException("A role for user " + username + " for project with id " + projectId + " was not found.");
        }

        return userToRole.entrySet().iterator().next().getValue();
    }

    @PUT
    @Consumes({"application/json", "application/xml"})
    @Path("{id}/roles/{username}")
    public void assignProjectRole(@PathParam("id")int projectId, @PathParam("username")String username, Role role) {
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
            addProjectRoles(LIMSInitializationListener.getDataSource(), projectId, Collections.singletonMap(user, role));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The assignment of role " + role.name + " to user " + username + " for project with id " + projectId + " was unsuccessful: " + e.getMessage());
        }
    }

    @DELETE
    @Path("{id}/roles/{username}")
    public void deleteProjectRole(@PathParam("id")int projectId, @PathParam("username")String username) {
        if (username == null) {
            throw new IllegalArgumentException("username is null.");
        }

        try {
            removeProjectRoles(LIMSInitializationListener.getDataSource(), projectId, Collections.singleton(username));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The deletion of user " + username + "'s role in project with id " + projectId + " was unsuccessful: " + e.getMessage());
        }
    }

    @GET
    @Produces("application/xml")
    @Path("{id}/workflows")
    public XMLSerializableList<Workflow> listWorkflowsAssignedToProject(@PathParam("id")int projectId) {
        try {
            return new XMLSerializableList<Workflow>(Workflow.class, new ArrayList<Workflow>(getWorkflowsFromProject(LIMSInitializationListener.getDataSource(), projectId)));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of workflows assigned to project with id " + projectId + " was unsuccessful: " + e.getMessage());
        }
    }

    @PUT
    @Consumes("text/plain")
    @Path("{id}/workflows")
    public void addWorkflowToProject(@PathParam("id")int projectId, String workflowIds) {
        try {
            String[] workflowIdsSplitByComma = workflowIds.split(",");
            Set<Integer> workflowIdSet = new HashSet<Integer>();
            for (String workflowId : workflowIdsSplitByComma) {
                workflowIdSet.add(Integer.valueOf(workflowId));
            }
            addWorkflowsToProject(LIMSInitializationListener.getDataSource(), projectId, workflowIdSet);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The assignment of workflows with ids " + workflowIds + " to project with id " + projectId + " was unsuccessful: " + e.getMessage());
        } catch (NumberFormatException e) {
            throw new InternalServerErrorException("Invalid workflow id: " + e.getMessage());
        }
    }

    @DELETE
    @Path("{id}/workflows/{workflowIds}")
    public void removeWorkflowsFromProjects(@PathParam("id")int projectId, @PathParam("workflowIds")String workflowIds) {
        try {
            String[] workflowIdsSplitByComma = workflowIds.split(",");
            Set<Integer> workflowIdSet = new HashSet<Integer>();
            for (String workflowId : workflowIdsSplitByComma) {
                workflowIdSet.add(Integer.valueOf(workflowId));
            }
            removeWorkflowsFromProjects(LIMSInitializationListener.getDataSource(), workflowIdSet);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The unassignment of workflow with ids " + workflowIds + " from project with ID " + projectId + " was unsuccessful: " + e.getMessage());
        }
    }

    /**
     *
     * @param dataSource To use to obtain a connection to the database
     * @param projectIds A list of project IDs for which to retrieve projects or an empty list if all projects are to be retrieved
     * @return A list of projects matching the specified ids or all projects if no ids were specified.
     */
    static List<Project> getProjects(DataSource dataSource, Collection<Integer> projectIds) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (projectIds == null) {
            throw new IllegalArgumentException("projectIds is null.");
        }

        projectIds = new HashSet<Integer>(projectIds);

        List<Project> projects = new ArrayList<Project>();

        Connection connection = null;
        PreparedStatement getProjectsStatement = null;
        ResultSet getProjectsResultSet = null;
        try {
            connection = dataSource.getConnection();
            getProjectsStatement = connection.prepareStatement(
                    "SELECT * FROM project " +
                    (projectIds.isEmpty() ? "" : "WHERE project.id IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(projectIds.size()) + ") ")
            );

            int statementObjectIndex = 1;
            for (Integer projectID : projectIds) {
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

    static void removeProject(DataSource dataSource, int projectId) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }

        Connection connection = null;
        PreparedStatement removeProjectStatement = null;
        try {
            connection = dataSource.getConnection();
            removeProjectStatement = connection.prepareStatement("DELETE FROM " + BiocodeServerLIMSDatabaseConstants.PROJECT_TABLE_NAME + " WHERE id=?");

            removeProjectStatement.setInt(1, projectId);

            removeProjectStatement.executeUpdate();
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(removeProjectStatement);
        }
    }

    static Map<User, Role> getProjectRoles(DataSource dataSource, int projectId, Collection<String> usernames) throws SQLException {
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

            getProjectRolesStatement.setInt(1, projectId);
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

    static void addProjectRoles(DataSource dataSource, int projectId, Map<User, Role> userToRole) throws SQLException {
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

            retrieveProjectRolesStatement.setInt(1, projectId);
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

            addNewProjectRoles(dataSource, projectId, newProjectRoles);
            updateProjectRoles(dataSource, projectId, existingProjectRoles);

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(retrieveProjectRolesStatement);
            SqlUtilities.cleanUpResultSets(retrieveProjectRolesResultSet);
        }
    }

    static void addNewProjectRoles(DataSource dataSource, int projectId, Map<User, Role> userToRole) throws SQLException {
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

            addProjectRolesStatement.setObject(1, projectId);
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

    static void updateProjectRoles(DataSource dataSource, int projectId, Map<User, Role> userToRole) throws SQLException {
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

            updateProjectRolesStatement.setInt(3, projectId);
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

    static void removeProjectRoles(DataSource dataSource, int projectId, Collection<String> usernames) throws SQLException {
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

            removeProjectRolesStatement.setObject(1, projectId);
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

    static Set<Workflow> getWorkflowsFromProject(DataSource dataSource, int projectId) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }

        Set<Workflow> workflows = new HashSet<Workflow>();

        Connection connection = null;
        PreparedStatement getWorkflowsFromProjectStatement = null;
        ResultSet getWorkflowsResultSet = null;
        try {
            connection = dataSource.getConnection();
            getWorkflowsFromProjectStatement = connection.prepareStatement(
                    "SELECT workflow.* FROM workflow " +
                            "LEFT OUTER JOIN " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                            " ON workflow.id=" + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME + ".workflow_id" +
                            " WHERE " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME + ".project_id=?"
            );

            getWorkflowsFromProjectStatement.setInt(1, projectId);

            getWorkflowsResultSet = getWorkflowsFromProjectStatement.executeQuery();
            while (getWorkflowsResultSet.next()) {
                workflows.add(new Workflow(getWorkflowsResultSet));
            }
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(getWorkflowsFromProjectStatement);
            SqlUtilities.cleanUpResultSets(getWorkflowsResultSet);
        }

        return workflows;
    }

    static void addWorkflowsToProject(DataSource dataSource, int projectId, Collection<Integer> workflowIds) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (workflowIds == null) {
            throw new IllegalArgumentException("workflowIds is null.");
        }
        if (workflowIds.isEmpty()) {
            return;
        }

        workflowIds = new HashSet<Integer>(workflowIds);

        Connection connection = null;
        PreparedStatement retrieveIDsOfWorkflowsFromProjectStatement = null;
        ResultSet retrieveIDsOfWorkflowsFromProjectResultSet = null;
        Set<Integer> idsOfWorkflowsAddedToAProject = new HashSet<Integer>();
        Set<Integer> idsOfWorkflowsNotAddedToAProject = new HashSet<Integer>(workflowIds);
        try {
            connection = dataSource.getConnection();
            retrieveIDsOfWorkflowsFromProjectStatement = connection.prepareStatement(
                    "SELECT workflow_id FROM " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                    " WHERE workflow_id IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(workflowIds.size()) + ")"
            );

            int statementObjectIndex = 1;
            for (Integer workflowID : workflowIds) {
                retrieveIDsOfWorkflowsFromProjectStatement.setInt(statementObjectIndex++, workflowID);
            }

            retrieveIDsOfWorkflowsFromProjectResultSet = retrieveIDsOfWorkflowsFromProjectStatement.executeQuery();
            while (retrieveIDsOfWorkflowsFromProjectResultSet.next()) {
                idsOfWorkflowsAddedToAProject.add(retrieveIDsOfWorkflowsFromProjectResultSet.getInt(1));
            }

            idsOfWorkflowsNotAddedToAProject.removeAll(idsOfWorkflowsAddedToAProject);

            SqlUtilities.beginTransaction(connection);

            if (!idsOfWorkflowsNotAddedToAProject.isEmpty()) {
                insertWorkflowProjectMapping(dataSource, projectId, idsOfWorkflowsNotAddedToAProject);
            }
            if (!idsOfWorkflowsNotAddedToAProject.isEmpty()) {
                updateWorkflowProjectMapping(dataSource, projectId, idsOfWorkflowsAddedToAProject);
            }

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(retrieveIDsOfWorkflowsFromProjectStatement);
            SqlUtilities.cleanUpResultSets(retrieveIDsOfWorkflowsFromProjectResultSet);
        }
    }

    static void insertWorkflowProjectMapping(DataSource dataSource, int projectId, Collection<Integer> workflowIds) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (workflowIds == null) {
            throw new IllegalArgumentException("workflowIds is null.");
        }
        if (workflowIds.isEmpty()) {
            return;
        }

        workflowIds = new HashSet<Integer>(workflowIds);

        Connection connection = null;
        PreparedStatement addWorkflowsToProjectsStatement = null;
        ResultSet addWorkflowsToProjectsResultSet = null;
        try {
            connection = dataSource.getConnection();
            addWorkflowsToProjectsStatement = connection.prepareStatement(
                    "INSERT INTO " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME + "(workflow_id, project_id) " +
                    "VALUES(" + StringUtilities.generateCommaSeparatedQuestionMarks(2) + ")"
            );

            addWorkflowsToProjectsStatement.setInt(2, projectId);
            for (Integer workflowID : workflowIds) {
                addWorkflowsToProjectsStatement.setInt(1, workflowID);
                addWorkflowsToProjectsStatement.addBatch();
            }

            SqlUtilities.beginTransaction(connection);

            int[] additionResults = addWorkflowsToProjectsStatement.executeBatch();
            for (int additionResult : additionResults) {
                if (additionResult != 1 && additionResult != PreparedStatement.SUCCESS_NO_INFO) {
                    throw new InternalServerErrorException("The assignment of 1 or more workflows to project with ID " + projectId + " was unsuccessful. Changes will be undone.");
                }
            }

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(addWorkflowsToProjectsStatement);
            SqlUtilities.cleanUpResultSets(addWorkflowsToProjectsResultSet);
        }
    }

    static void updateWorkflowProjectMapping(DataSource dataSource, int projectId, Collection<Integer> workflowIds) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (workflowIds == null) {
            throw new IllegalArgumentException("workflowIds is null.");
        }
        if (workflowIds.isEmpty()) {
            return;
        }

        workflowIds = new HashSet<Integer>(workflowIds);

        Connection connection = null;
        PreparedStatement updateProjectToWorkflowsStatement = null;
        try {
            connection = dataSource.getConnection();
            updateProjectToWorkflowsStatement = connection.prepareStatement(
                    "UPDATE " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                    " SET project_id=? " +
                    "WHERE workflow_id=?"
            );

            updateProjectToWorkflowsStatement.setInt(1, projectId);
            for (Integer workflowID : workflowIds) {
                updateProjectToWorkflowsStatement.setInt(2, workflowID);
                updateProjectToWorkflowsStatement.addBatch();
            }

            SqlUtilities.beginTransaction(connection);

            int[] assignmentResults = updateProjectToWorkflowsStatement.executeBatch();
            for (int assignmentResult : assignmentResults) {
                if (assignmentResult != 1 && assignmentResult != PreparedStatement.SUCCESS_NO_INFO) {
                    throw new InternalServerErrorException("The assignment of project with ID " + projectId + " to 1 or more workflows was unsuccessful. Changes will be undone.");
                }
            }

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(updateProjectToWorkflowsStatement);
        }
    }

    static void removeWorkflowsFromProjects(DataSource dataSource, Collection<Integer> workflowIds) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (workflowIds == null) {
            throw new IllegalArgumentException("workflowIds is null.");
        }
        if (workflowIds.isEmpty()) {
            return;
        }

        workflowIds = new HashSet<Integer>(workflowIds);

        Connection connection = null;
        PreparedStatement removeWorkflowsFromProjectStatement = null;
        try {
            connection = dataSource.getConnection();
            removeWorkflowsFromProjectStatement = connection.prepareStatement(
                    "DELETE FROM " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                    " WHERE workflow_id IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(workflowIds.size()) + ")"
            );

            int statementObjectIndex = 1;
            for (Integer workflowID : workflowIds) {
                removeWorkflowsFromProjectStatement.setInt(statementObjectIndex++, workflowID);
            }

            removeWorkflowsFromProjectStatement.executeUpdate();
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(removeWorkflowsFromProjectStatement);
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