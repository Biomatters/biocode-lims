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
    public void deleteProject(@PathParam("id")int id) {
        try {
            deleteProject(LIMSInitializationListener.getDataSource(), id);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The deletion of project with ID " + id + " was unsuccessful: " + e.getMessage());
        }
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{id}/roles")
    public Response listRoles(@PathParam("id")int projectID) {
        return Response.ok(new GenericEntity<List<UserRole>>(UserRole.forMap(getProject(projectID).userRoles)){}).build();
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{id}/roles/{username}")
    public Role listRole(@PathParam("id")int projectID, @PathParam("username")String username) {
        if (username == null) {
            throw new IllegalArgumentException("username is null.");
        }

        Role role;
        try {
            role = getRole(LIMSInitializationListener.getDataSource(), projectID, username);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of user " + username + "'s role for project with ID " + projectID + " was unsuccessful: " + e.getMessage());
        }

        if (role == null) {
            throw new NotFoundException("A role for user " + username + " in project with ID " + projectID + " was not found.");
        }

        return role;
    }

    @PUT
    @Consumes({"application/json", "application/xml"})
    @Path("{id}/roles/{username}")
    public void assignRole(@PathParam("id")int projectID, @PathParam("username")String username, Role role) {
        if (username == null) {
            throw new IllegalArgumentException("username is null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is null.");
        }

        try {
            assignRole(LIMSInitializationListener.getDataSource(), projectID, username, role);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The assignment of role " + role.name + " to user " + username + " in project with ID " + projectID + " was unsuccessful: " + e.getMessage());
        }
    }

    @DELETE
    @Path("{id}/roles/{username}")
    public void deleteRole(@PathParam("id")int projectID, @PathParam("username")String username) {
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
    public void unassignWorkflowFromProjects(@PathParam("id")int projectID, @PathParam("workflowID")int workflowID) {
        try {
            unassignWorkflowsFromProjects(LIMSInitializationListener.getDataSource(), Collections.singletonList(workflowID));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The unassignment of workflow with ID " + workflowID + " from project with ID " + projectID + " was unsuccessful: " + e.getMessage());
        }
    }

    /**
     *
     * @param user The user account that is attempting to retreive data.
     * @param role The role to check for.
     * @return A list of {@link FimsProject}s that the specified user is allowed to view.  Or null if there are no projects in the system.
     */
    public static List<Project> getProjectsUserHasAtLeastRoleFor(DataSource dataSource, User user, Role role) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (user == null) {
            throw new IllegalArgumentException("user is null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is null.");
        }

        List<Project> projectsUserHasAtLeastRoleFor = new ArrayList<Project>();
        List<Project> allProjects = getProjects(dataSource, Collections.<Integer>emptySet());

        if (user.isAdministrator) {
            projectsUserHasAtLeastRoleFor.addAll(allProjects);
        } else {
            for (Project project : allProjects) {
                Role userRoleForProject = getRoleForUser(project, user);
                if (userRoleForProject != null && userRoleForProject.isAtLeast(role)) {
                    projectsUserHasAtLeastRoleFor.add(project);
                }
            }
        }

        return projectsUserHasAtLeastRoleFor;
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

        Connection connection = null;
        PreparedStatement getProjectsStatement = null;
        ResultSet getProjectsResultSet = null;
        try {
            connection = dataSource.getConnection();
            getProjectsStatement = connection.prepareStatement(
                    "SELECT * FROM project " +
                    "LEFT OUTER JOIN project_role ON project.id = project_role.project_id " +
                    "LEFT OUTER JOIN users ON users.username = project_role.username " +
                    "LEFT OUTER JOIN authorities ON users.username = authorities.username " +
                    (projectIDs.isEmpty() ? "" : "WHERE project.id IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(projectIDs.size()) + ") ") +
                    "ORDER BY project.id"
            );

            int statementObjectIndex = 1;
            for (Integer idOfProjectToRetrieve : projectIDs) {
                getProjectsStatement.setInt(statementObjectIndex++, idOfProjectToRetrieve);
            }

            return createProjectsFromResultSet(getProjectsStatement.executeQuery());
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(getProjectsStatement);
            SqlUtilities.cleanUpResultSets(getProjectsResultSet);
        }
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

    static void deleteProject(DataSource dataSource, int projectID) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }

        Connection connection = null;
        PreparedStatement deleteProjectStatement = null;
        try {
            connection = dataSource.getConnection();
            deleteProjectStatement = connection.prepareStatement("DELETE FROM " + BiocodeServerLIMSDatabaseConstants.PROJECT_TABLE_NAME + " WHERE id=?");

            deleteProjectStatement.setInt(1, projectID);

            deleteProjectStatement.executeUpdate();
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(deleteProjectStatement);
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

            SqlUtilities.beginTransaction(connection);

            if (updateProjectStatement.executeUpdate() == 0) {
                throw new InternalServerErrorException("Project with ID " + project.id + " could not be updated.");
            }

            removeProjectRoles(dataSource, project.id, Collections.<String>emptySet());
            addProjectRoles(dataSource, project.userRoles, project.id);

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(updateProjectStatement);
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

    static void assignRole(DataSource dataSource, int projectID, String username, Role role) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (username == null) {
            throw new IllegalArgumentException("username is null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is null.");
        }

        Role existingRole = getRole(dataSource, projectID, username);

        if (existingRole.id == role.id) {
            return;
        }

        String assignRoleQuery;

        if (existingRole == null) {
            assignRoleQuery = "INSERT INTO " + BiocodeServerLIMSDatabaseConstants.PROJECT_ROLE_TABLE_NAME + "(role, username, project_id) VALUES(" + StringUtilities.generateCommaSeparatedQuestionMarks(3) + ")";
        } else {
            if (existingRole.id == role.id) {
                return;
            }

            assignRoleQuery = "UPDATE " + BiocodeServerLIMSDatabaseConstants.PROJECT_ROLE_TABLE_NAME + " SET role=? WHERE username=? AND project_id=?";
        }

        Connection connection = null;
        PreparedStatement assignRoleStatement = null;
        try {
            connection = dataSource.getConnection();
            assignRoleStatement = connection.prepareStatement(assignRoleQuery);

            assignRoleStatement.setObject(1, role.id);
            assignRoleStatement.setObject(2, username);
            assignRoleStatement.setObject(3, projectID);

            if (assignRoleStatement.executeUpdate() == 0) {
                throw new InternalServerErrorException("The assignment of role " + role.name + " to user " + username + " in project with ID " + projectID + " was unsuccessful.");
            }
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(assignRoleStatement);
        }
    }

    private static void addProjectRoles(DataSource dataSource, Map<User, Role> userToRole, int projectID) throws SQLException {
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

    private static Role getRole(DataSource dataSource, int projectID, String username) throws SQLException {
        Role role = null;

        List<Project> projects = getProjects(dataSource, Collections.singletonList(projectID));

        if (projects.isEmpty()) {
            throw new InternalServerErrorException("No project with ID " + projectID + " was found.");
        }
        if (projects.size() != 1) {
            throw new InternalServerErrorException("More than 1 project with projectID " + projectID + " was found.");
        }

        for (Map.Entry<User, Role> entry : projects.get(0).userRoles.entrySet()) {
            if (username.equals(entry.getKey().username)) {
                role = entry.getValue();
                break;
            }
        }

        return role;
    }

    static Set<Workflow> getWorkflowsAssignedToProject(DataSource dataSource, int projectID) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }

        Set<Workflow> workflowsAssignedToProject = new HashSet<Workflow>();

        Connection connection = null;
        PreparedStatement retrieveWorkflowsAssignedToProjectStatement = null;
        ResultSet retrieveWorkflowsAssignedToProjectResultSet = null;
        try {
            connection = dataSource.getConnection();
            retrieveWorkflowsAssignedToProjectStatement = connection.prepareStatement(
                    "SELECT workflow.* " +
                    "FROM workflow, " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                    " WHERE " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME + ".project_id=? " +
                    "AND workflow.id=" + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME+ ".workflow_id "
            );

            retrieveWorkflowsAssignedToProjectStatement.setInt(1, projectID);

            retrieveWorkflowsAssignedToProjectResultSet = retrieveWorkflowsAssignedToProjectStatement.executeQuery();
            while (retrieveWorkflowsAssignedToProjectResultSet.next()) {
                workflowsAssignedToProject.add(new Workflow(retrieveWorkflowsAssignedToProjectResultSet));
            }
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(retrieveWorkflowsAssignedToProjectStatement);
            SqlUtilities.cleanUpResultSets(retrieveWorkflowsAssignedToProjectResultSet);
        }

        return workflowsAssignedToProject;
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
        PreparedStatement retrieveWorkflowsAssignedToProjectsStatement = null;
        PreparedStatement assignWorkflowsToProjectStatement = null;
        ResultSet retrieveWorkflowsAssignedToProjectsResultSet = null;
        ResultSet assignWorkflowsToProjectResultSet = null;
        Set<Integer> idsOfWorkflowsAssignedToProjects = new HashSet<Integer>();
        Set<Integer> idsOfWorkflowsNotAssignedToProjects = new HashSet<Integer>(workflowIDs);
        int[] assignmentResults;
        try {
            connection = dataSource.getConnection();

            retrieveWorkflowsAssignedToProjectsStatement = connection.prepareStatement(
                    "SELECT workflow_id " +
                    "FROM " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                    " WHERE workflow_id IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(workflowIDs.size()) + ")"
            );

            int statementObjectIndex = 1;
            for (Integer workflowID : workflowIDs) {
                retrieveWorkflowsAssignedToProjectsStatement.setInt(statementObjectIndex++, workflowID);
            }

            retrieveWorkflowsAssignedToProjectsResultSet = retrieveWorkflowsAssignedToProjectsStatement.executeQuery();
            while (retrieveWorkflowsAssignedToProjectsResultSet.next()) {
                idsOfWorkflowsAssignedToProjects.add(retrieveWorkflowsAssignedToProjectsResultSet.getInt(1));
            }

            idsOfWorkflowsNotAssignedToProjects.removeAll(idsOfWorkflowsAssignedToProjects);

            SqlUtilities.beginTransaction(connection);

            if (!idsOfWorkflowsNotAssignedToProjects.isEmpty()) {
                assignWorkflowsToProjectStatement = connection.prepareStatement(
                        "INSERT INTO " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME + "(workflow_id, project_id) " +
                        "VALUES(" + StringUtilities.generateCommaSeparatedQuestionMarks(workflowIDs.size()) + ")"
                );

                assignWorkflowsToProjectStatement.setInt(2, projectID);
                for (Integer workflowID : idsOfWorkflowsNotAssignedToProjects) {
                    assignWorkflowsToProjectStatement.setInt(1, workflowID);
                    assignWorkflowsToProjectStatement.addBatch();
                }

                assignmentResults = assignWorkflowsToProjectStatement.executeBatch();
                for (int assignmentResult : assignmentResults) {
                    if (assignmentResult != 1 && assignmentResult != PreparedStatement.SUCCESS_NO_INFO) {
                        throw new InternalServerErrorException("The assignment of 1 or more workflows to project with ID " + projectID + " was unsuccessful. Changes will be undone.");
                    }
                }
            }

            if (!idsOfWorkflowsAssignedToProjects.isEmpty()) {
                assignWorkflowsToProjectStatement = connection.prepareStatement(
                        "UPDATE " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                        " SET project_id=? " +
                        "WHERE workflow_id=?"
                );

                assignWorkflowsToProjectStatement.setInt(1, projectID);
                for (Integer workflowID : idsOfWorkflowsNotAssignedToProjects) {
                    assignWorkflowsToProjectStatement.setInt(2, workflowID);
                    assignWorkflowsToProjectStatement.addBatch();
                }

                assignmentResults = assignWorkflowsToProjectStatement.executeBatch();
                for (int assignmentResult : assignmentResults) {
                    if (assignmentResult != 1 && assignmentResult != PreparedStatement.SUCCESS_NO_INFO) {
                        throw new InternalServerErrorException("The assignment of 1 or more workflows to project with ID " + projectID + " was unsuccessful. Changes will be undone.");
                    }
                }
            }

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(retrieveWorkflowsAssignedToProjectsStatement, assignWorkflowsToProjectStatement);
            SqlUtilities.cleanUpResultSets(retrieveWorkflowsAssignedToProjectsResultSet, assignWorkflowsToProjectResultSet);
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
        PreparedStatement unassignStatementsFromProjectsStatement = null;
        try {
            connection = dataSource.getConnection();
            unassignStatementsFromProjectsStatement = connection.prepareStatement(
                    "DELETE * " +
                    "FROM " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                    " WHERE workflow_id IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(workflowIDs.size()) + ")"
            );

            int statementObjectIndex = 1;
            for (Integer workflowID : workflowIDs) {
                unassignStatementsFromProjectsStatement.setInt(statementObjectIndex++, workflowID);
            }

            unassignStatementsFromProjectsStatement.executeUpdate();
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(unassignStatementsFromProjectsStatement);
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

    private static List<Project> createProjectsFromResultSet(ResultSet resultSet) throws SQLException {
        if (resultSet == null) {
            throw new IllegalArgumentException("resultSet is null.");
        }

        Map<Integer, Project> projectIDToProject = new HashMap<Integer, Project>();

        while (resultSet.next()) {
            int projectID = resultSet.getInt("id");

            Project project = projectIDToProject.get(projectID);

            if (project == null) {
                project = new Project();

                project.id = projectID;
                project.name = resultSet.getString("name");
                project.description = resultSet.getString("description");
                int parentProjectID = resultSet.getInt("parent_project_id");
                project.parentProjectID = parentProjectID > 0 ? parentProjectID : -1;
                project.isPublic = resultSet.getBoolean("is_public");

                projectIDToProject.put(projectID, project);
            }

            User user = Users.createUserFromResultSetRow(resultSet);
            if (user != null) {
                project.userRoles.put(user, Role.getRole(resultSet.getInt("role")));
            }
        }

        return new ArrayList<Project>(projectIDToProject.values());
    }
}