package com.biomatters.plugins.biocode.server.security;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.Workflow;
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
    public Response getProjectsLoggedInUserHasRoleAccessTo(@QueryParam("roleId")int roleId) {
        try {
            return Response.ok(new GenericEntity<List<Project>>(getProjectsUserHasRoleAccessFor(LIMSInitializationListener.getDataSource(), Users.getLoggedInUser(), Role.getRole(roleId))){}).build();
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of the project details was unsuccessful: " + e.getMessage(), e);
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
            throw new InternalServerErrorException("The retrieval of the project details was unsuccessful: " + e.getMessage(), e);
        }

        if (retrievedProjects.isEmpty()) {
            throw new NotFoundException("The retrieval of the project details was unsuccessful: Could not find a project of which the id is " + projectId + ".");
        } else if (retrievedProjects.size() > 1) {
            throw new InternalServerErrorException("More than one project of which the id is " + projectId + " was found.");
        }

        return retrievedProjects.get(0);
    }

    @POST
    @Consumes({"application/json", "application/xml"})
    @Produces("text/plain")
    public String addProject(Project project) {
        try {
            if (!Users.getLoggedInUser().isAdministrator) {
                throw new ForbiddenException("The addition of the project was unsuccessful: Administrator access is required.");
            }

            return Integer.toString(addProject(LIMSInitializationListener.getDataSource(), project));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The addition of the project was unsuccessful: " + e.getMessage(), e);
        }
    }

    @PUT
    @Consumes({"application/json", "application/xml"})
    @Path("{id}")
    public void updateProject(@PathParam("id")int id, Project project) {
        if (!Users.getLoggedInUser().isAdministrator) {
            throw new ForbiddenException("The update of the project was unsuccessful: Administrator access is required.");
        }

        if (project == null) {
            throw new BadRequestException("project is null.");
        }

        project.id = id;

        try {
            updateProject(LIMSInitializationListener.getDataSource(), project);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The update of the project was unsuccessful: " + e.getMessage(), e);
        }
    }

    @DELETE
    @Path("{id}")
    public void removeProject(@PathParam("id")int id) {
        try {
            if (!Users.getLoggedInUser().isAdministrator) {
                throw new ForbiddenException("The removal of the project was unsuccessful: Administrator access is required.");
            }

            removeProject(LIMSInitializationListener.getDataSource(), id);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The removal of the project was unsuccessful: " + e.getMessage(), e);
        }
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{id}/roles")
    public Response listProjectRoles(@PathParam("id")int projectId) {
        try {
            Set<String> userNames = new HashSet<String>();

            User loggedInUser = Users.getLoggedInUser();
            if (!loggedInUser.isAdministrator) {
                userNames.add(loggedInUser.username);
            }

            return Response.ok(new GenericEntity<List<UserRole>>(UserRole.forMap(getProjectRoles(LIMSInitializationListener.getDataSource(), projectId, userNames))){}).build();
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of the project roles was unsuccessful: " + e.getMessage(), e);
        }
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{id}/roles/{username}")
    public Role getProjectRole(@PathParam("id")int projectId, @PathParam("username")String username) {
        if (!Users.getLoggedInUser().isAdministrator) {
            throw new ForbiddenException("The retrieval of the project role was unsuccessful: Administrator access is required.");
        }

        if (username == null) {
            throw new BadRequestException("username is null.");
        }
        if (username.isEmpty()) {
            throw new BadRequestException("username is an empty string.");
        }

        Map<User, Role> userToRole;
        try {
            userToRole = getProjectRoles(LIMSInitializationListener.getDataSource(), projectId, Collections.singleton(username));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval the the project role was unsuccessful: " + e.getMessage(), e);
        }

        if (userToRole.isEmpty()) {
            throw new NotFoundException("Could not find a role for user " + username + " in project with id " + projectId + ".");
        }

        return userToRole.entrySet().iterator().next().getValue();
    }

    @PUT
    @Consumes({"application/json", "application/xml"})
    @Path("{id}/roles/{username}")
    public void addProjectRole(@PathParam("id") int projectId, @PathParam("username") String username, Role role) {
        if (!Users.getLoggedInUser().isAdministrator) {
            throw new ForbiddenException("The addition of the project role was unsuccessful: Administrator access is required.");
        }

        if (role == null) {
            throw new BadRequestException("role is null.");
        }
        if (username == null) {
            throw new BadRequestException("username is null.");
        }
        if (username.isEmpty()) {
            throw new BadRequestException("username is an empty string.");
        }

        User user = new User();
        user.username = username;
        try {
            addProjectRoles(LIMSInitializationListener.getDataSource(), projectId, Collections.singletonMap(user, role));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The addition of the project role was unsuccessful: " + e.getMessage(), e);
        }
    }

    @DELETE
    @Path("{id}/roles/{username}")
    public void deleteProjectRole(@PathParam("id")int projectId, @PathParam("username")String username) {
        if (!Users.getLoggedInUser().isAdministrator) {
            throw new ForbiddenException("The deletion of the project role was unsuccessful: Administrator access is required.");
        }

        if (username == null) {
            throw new BadRequestException("username is null.");
        }

        try {
            removeProjectRoles(LIMSInitializationListener.getDataSource(), projectId, Collections.singleton(username));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The deletion the project role was unsuccessful: " + e.getMessage(), e);
        }
    }

    @GET
    @Produces("application/xml")
    @Path("{id}/workflows")
    public XMLSerializableList<Workflow> getWorkflowsFromProject(@PathParam("id")int projectId) {
        try {
            return new XMLSerializableList<Workflow>(Workflow.class, new ArrayList<Workflow>(getWorkflowsFromProject(LIMSInitializationListener.getDataSource(), projectId)));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of the workflows was unsuccessful: " + e.getMessage(), e);
        }
    }

    @POST
    @Consumes("application/xml")
    @Path("{id}/workflows")
    public void addWorkflowsToProject(@PathParam("id")int projectId, XMLSerializableList<XMLSerializableInteger> workflowIds) {
        try {
            Set<Integer> workflowIdsAsIntegers = new HashSet<Integer>();

            for (XMLSerializableInteger workflowId : workflowIds.getList()) {
                workflowIdsAsIntegers.add(workflowId.getValue());
            }

            addWorkflowsToProject(LIMSInitializationListener.getDataSource(), projectId, workflowIdsAsIntegers);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException("The addition of the workflows was unsuccessful: " + e.getMessage(), e);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The addition of the workflows was unsuccessful: " + e.getMessage(), e);
        }
    }

    @POST
    @Consumes("application/xml")
    @Path("workflows/deletion")
    public void removeWorkflowsFromProjects(XMLSerializableList<XMLSerializableInteger> workflowIds) {
        try {
            Set<Integer> workflowIdsAsIntegers = new HashSet<Integer>();

            for (XMLSerializableInteger workflowId : workflowIds.getList()) {
                workflowIdsAsIntegers.add(workflowId.getValue());
            }

            try {
                AccessUtilities.checkUserHasRoleForWorkflows(workflowIdsAsIntegers, Users.getLoggedInUser(), Role.WRITER);
            } catch (DatabaseServiceException e) {
                throw new InternalServerErrorException(e);
            }

            removeWorkflowsFromProjects(LIMSInitializationListener.getDataSource(), workflowIdsAsIntegers);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The removal of the workflows from projects was unsuccessful: " + e.getMessage(), e);
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
            throw new BadRequestException("dataSource is null.");
        }
        if (projectIds == null) {
            throw new BadRequestException("projectIds is null.");
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
            SqlUtilities.cleanUpStatements(getProjectsStatement);
            SqlUtilities.cleanUpResultSets(getProjectsResultSet);
            SqlUtilities.closeConnection(connection);
        }

        return projects;
    }

    /**
     *
     * @param user The user account that is attempting to retreive data.
     * @param role The role to check for.
     * @return A list of {@link Project}s that the specified user is allowed to view.  Or null if there are no projects in the system.
     */
    public static List<Project> getProjectsUserHasRoleAccessFor(DataSource dataSource, User user, Role role) throws SQLException {
        if (dataSource == null) {
            throw new BadRequestException("dataSource is null.");
        }
        if (user == null) {
            throw new BadRequestException("user is null.");
        }
        if (role == null) {
            throw new BadRequestException("role is null.");
        }

        List<Project> projectsUserHasRoleAccessFor = new ArrayList<Project>();

        Map<Integer, Project> projectIdToProject = createProjectIdToProjectMap(getProjects(dataSource, Collections.<Integer>emptySet()));

        if (user.isAdministrator) {
            projectsUserHasRoleAccessFor.addAll(projectIdToProject.values());
        } else {
            for (Project project : projectIdToProject.values()) {
                if (project.isPublic) {
                    projectsUserHasRoleAccessFor.add(project);
                }

                Project potentialProject = project;
                boolean checkingProject = true;
                while (checkingProject) {
                    Map<User, Role> userProjectRole = getProjectRoles(dataSource, potentialProject.id, Collections.singletonList(user.username));

                    if (userProjectRole.size() > 1) {
                        throw new InternalServerErrorException("More than one role was found for user " + user.username + " in project " + potentialProject.name + ".");
                    }

                    if (!userProjectRole.isEmpty() && userProjectRole.get(user).isAtLeast(role)) {
                        projectsUserHasRoleAccessFor.add(project);
                        checkingProject = false;
                    }

                    potentialProject = projectIdToProject.get(potentialProject.parentProjectID);
                    if (potentialProject == null) {
                        checkingProject = false;
                    }
                }
            }
        }

        return projectsUserHasRoleAccessFor;
    }

    static synchronized int addProject(DataSource dataSource, Project project) throws SQLException {
        if (dataSource == null) {
            throw new BadRequestException("dataSource is null.");
        }
        if (project == null) {
            throw new BadRequestException("project is null.");
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
                return -1;
            }

            return projectID;
        } finally {
            SqlUtilities.cleanUpStatements(retrieveMaxExistingProjectIDStatement, addProjectStatement);
            SqlUtilities.cleanUpResultSets(retrieveMaxExistingProjectIDResultSet);
            SqlUtilities.closeConnection(connection);
        }
    }

    static void updateProject(DataSource dataSource, Project project) throws SQLException {
        if (dataSource == null) {
            throw new BadRequestException("dataSource is null.");
        }
        if (project == null) {
            throw new BadRequestException("project is null.");
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

            updateProjectStatement.executeUpdate();
        } finally {
            SqlUtilities.cleanUpStatements(updateProjectStatement);
            SqlUtilities.closeConnection(connection);
        }
    }

    static void removeProject(DataSource dataSource, int projectId) throws SQLException {
        if (dataSource == null) {
            throw new BadRequestException("dataSource is null.");
        }

        Connection connection = null;
        PreparedStatement removeProjectStatement = null;
        try {
            connection = dataSource.getConnection();
            removeProjectStatement = connection.prepareStatement("DELETE FROM " + BiocodeServerLIMSDatabaseConstants.PROJECT_TABLE_NAME + " WHERE id=?");

            removeProjectStatement.setInt(1, projectId);

            removeProjectStatement.executeUpdate();
        } finally {
            SqlUtilities.cleanUpStatements(removeProjectStatement);
            SqlUtilities.closeConnection(connection);
        }
    }

    static Map<User, Role> getProjectRoles(DataSource dataSource, int projectId, Collection<String> usernames) throws SQLException {
        if (dataSource == null) {
            throw new BadRequestException("dataSource is null.");
        }
        if (usernames == null) {
            throw new BadRequestException("usernames is null.");
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
            SqlUtilities.cleanUpStatements(getProjectRolesStatement);
            SqlUtilities.cleanUpResultSets(getProjectRolesResultSet);
            SqlUtilities.closeConnection(connection);
        }

        return projectRoles;
    }

    static void addProjectRoles(DataSource dataSource, int projectId, Map<User, Role> userToRole) throws SQLException {
        if (dataSource == null) {
            throw new BadRequestException("dataSource is null.");
        }
        if (userToRole == null) {
            throw new BadRequestException("userToRole is null.");
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
            SqlUtilities.cleanUpStatements(retrieveProjectRolesStatement);
            SqlUtilities.cleanUpResultSets(retrieveProjectRolesResultSet);
            SqlUtilities.closeConnection(connection);
        }
    }

    static void addNewProjectRoles(DataSource dataSource, int projectId, Map<User, Role> userToRole) throws SQLException {
        if (dataSource == null) {
            throw new BadRequestException("dataSource is null.");
        }
        if (userToRole == null) {
            throw new BadRequestException("userToRole is null.");
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
            SqlUtilities.cleanUpStatements(addProjectRolesStatement);
            SqlUtilities.closeConnection(connection);
        }
    }

    static void updateProjectRoles(DataSource dataSource, int projectId, Map<User, Role> userToRole) throws SQLException {
        if (dataSource == null) {
            throw new BadRequestException("dataSource is null.");
        }
        if (userToRole == null) {
            throw new BadRequestException("username is null.");
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
            SqlUtilities.cleanUpStatements(updateProjectRolesStatement);
            SqlUtilities.closeConnection(connection);
        }
    }

    static void removeProjectRoles(DataSource dataSource, int projectId, Collection<String> usernames) throws SQLException {
        if (dataSource == null) {
            throw new BadRequestException("dataSource is null.");
        }
        if (usernames == null) {
            throw new BadRequestException("usernames is null.");
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
            SqlUtilities.cleanUpStatements(removeProjectRolesStatement);
            SqlUtilities.closeConnection(connection);
        }
    }

    static Set<Workflow> getWorkflowsFromProject(DataSource dataSource, int projectId) throws SQLException {
        if (dataSource == null) {
            throw new BadRequestException("dataSource is null.");
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
            SqlUtilities.cleanUpStatements(getWorkflowsFromProjectStatement);
            SqlUtilities.cleanUpResultSets(getWorkflowsResultSet);
            SqlUtilities.closeConnection(connection);
        }

        return workflows;
    }

    static void addWorkflowsToProject(DataSource dataSource, int projectId, Collection<Integer> workflowIds) throws SQLException, DatabaseServiceException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }

        if (workflowIds == null) {
            throw new IllegalArgumentException("workflowIds is null.");
        }

        User loggedInUser = Users.getLoggedInUser();
        if (!containsProjectWithId(getProjectsUserHasRoleAccessFor(dataSource, loggedInUser, Role.WRITER), projectId)) {
            throw new ForbiddenException("The addition of the workflows was unsuccessful: Write access for the project is required.");
        }

        Connection connection = dataSource.getConnection();

        Set<Integer> idsOfWorkflowsInProjects = getIntersection(workflowIds, getWorkflowsInProjects(connection));

        AccessUtilities.checkUserHasRoleForWorkflows(idsOfWorkflowsInProjects, loggedInUser, Role.WRITER);

        workflowIds.removeAll(idsOfWorkflowsInProjects);

        SqlUtilities.beginTransaction(connection);
        insertWorkflowProjectMapping(connection, projectId, workflowIds);
        updateWorkflowProjectMapping(connection, projectId, idsOfWorkflowsInProjects);
        SqlUtilities.commitTransaction(connection);
    }

    private static boolean containsProjectWithId(Collection<Project> projects, int id) {
        boolean containsProjectWithId = false;

        for (Project project : projects) {
            if (project.id == id) {
                containsProjectWithId = true;
                break;
            }
        }

        return containsProjectWithId;
    }

    private static Set<Integer> getWorkflowsInProjects(Connection connection) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("connection is null.");
        }

        Set<Integer> workflowsInProjects = new HashSet<Integer>();
        ResultSet getWorkflowsResult = null;
        try {
            getWorkflowsResult = connection.prepareStatement("SELECT workflow_id FROM " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME).executeQuery();
            while (getWorkflowsResult.next()) {
                workflowsInProjects.add(getWorkflowsResult.getInt(1));
            }
        } finally {
            SqlUtilities.cleanUpResultSets(getWorkflowsResult);
        }

        return workflowsInProjects;
    }

    private static <T> Set<T> getIntersection(Collection<T> groupOne, Collection<T> groupTwo) {
        Set<T> intersection = new HashSet<T>();

        for (T item : groupOne) {
            if (groupTwo.contains(item)) {
                intersection.add(item);
            }
        }

        return intersection;
    }

    static void insertWorkflowProjectMapping(Connection connection, int projectId, Collection<Integer> workflowIds) throws SQLException {
        if (connection == null) {
            throw new BadRequestException("connection is null.");
        }
        if (workflowIds == null) {
            throw new BadRequestException("workflowIds is null.");
        }
        if (workflowIds.isEmpty()) {
            return;
        }

        workflowIds = new HashSet<Integer>(workflowIds);

        PreparedStatement addWorkflowsToProjectsStatement = null;
        try {
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
            SqlUtilities.cleanUpStatements(addWorkflowsToProjectsStatement);
        }
    }

    static void updateWorkflowProjectMapping(Connection connection, int projectId, Collection<Integer> workflowIds) throws SQLException {
        if (connection == null) {
            throw new BadRequestException("connection is null.");
        }
        if (workflowIds == null) {
            throw new BadRequestException("workflowIds is null.");
        }
        if (workflowIds.isEmpty()) {
            return;
        }

        workflowIds = new HashSet<Integer>(workflowIds);

        PreparedStatement updateProjectToWorkflowsStatement = null;
        try {
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
            SqlUtilities.cleanUpStatements(updateProjectToWorkflowsStatement);
        }
    }

    static void removeWorkflowsFromProjects(DataSource dataSource, Collection<Integer> workflowIds) throws SQLException {
        if (dataSource == null) {
            throw new BadRequestException("dataSource is null.");
        }
        if (workflowIds == null) {
            throw new BadRequestException("workflowIds is null.");
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
            SqlUtilities.cleanUpStatements(removeWorkflowsFromProjectStatement);
            SqlUtilities.closeConnection(connection);
        }
    }

    private static Project createProjectFromResultSet(ResultSet resultSet) throws SQLException {
        if (resultSet == null) {
            throw new BadRequestException("resultSet is null.");
        }

        int parentProjectID = resultSet.getInt("parent_project_id");
        parentProjectID = parentProjectID > 0 ? parentProjectID : -1;

        return new Project(resultSet.getInt("id"), resultSet.getString("name"), resultSet.getString("description"), parentProjectID, resultSet.getBoolean("is_public"));
    }

    private static Map<Integer, Project> createProjectIdToProjectMap(Collection<Project> projects) {
        Map<Integer, Project> projectIdToProject = new HashMap<Integer, Project>();

        for (Project project : projects) {
            projectIdToProject.put(project.id, project);
        }

        return projectIdToProject;
    }
}