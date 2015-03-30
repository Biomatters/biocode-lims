package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.labbench.fims.FimsProject;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
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
        DataSource dataSource = LIMSInitializationListener.getDataSource();

        if (dataSource == null) {
            throw new InternalServerErrorException("The data source is null.");
        }

        try {
            return Response.ok(new GenericEntity<List<Project>>(getProjects(LIMSInitializationListener.getDataSource(), Collections.<Integer>emptySet())){}).build();
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of the projects was unsuccessful.");
        }
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{id}")
    public Project getProject(@PathParam("id")int projectID) {
        DataSource dataSource = LIMSInitializationListener.getDataSource();

        if (dataSource == null) {
            throw new InternalServerErrorException("The data source is null.");
        }

        List<Project> retrievedProjects;

        try {
            retrievedProjects = getProjects(dataSource, Collections.singleton(projectID));
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of the project with ID " + projectID + " was unsuccessful: " + e.getMessage());
        }

        if (retrievedProjects.isEmpty()) {
            throw new NotFoundException("Project with ID " + projectID + " could not be found.");
        } else if (retrievedProjects.size() > 1) {
            throw new InternalServerErrorException("More than 1 project with ID " + projectID + " was found.");
        }

        return retrievedProjects.get(0);
    }

    @POST
    public void addProject(Project project) {
        DataSource dataSource = LIMSInitializationListener.getDataSource();

        if (dataSource == null) {
            throw new InternalServerErrorException("The data source is null.");
        }

        try {
            addProject(dataSource, project);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The creation of project " + project.name + " was unsuccessful.");
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
            throw new InternalServerErrorException("The updating of project " + project.name + " was unsuccessful.");
        }
    }

    @DELETE
    @Path("{id}")
    public void deleteProject(@PathParam("id")int id) {
        DataSource dataSource = LIMSInitializationListener.getDataSource();

        if (dataSource == null) {
            throw new InternalServerErrorException("The data source is null.");
        }

        try {
            deleteProject(dataSource, id);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The deletion of project with ID " + id + " was unsuccessful.");
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

        DataSource dataSource = LIMSInitializationListener.getDataSource();

        if (dataSource == null) {
            throw new InternalServerErrorException("The data source is null.");
        }

        Role role = null;
        try {
            role = getRole(dataSource, projectID, username);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The retrieval of the project role was unsuccessful: " + e.getMessage());
        }

        if (role == null) {
            throw new NotFoundException("An assigned role for user " + username + " in project with ID " + projectID + " was not found.");
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

        DataSource dataSource = LIMSInitializationListener.getDataSource();

        if (dataSource == null) {
            throw new InternalServerErrorException("The data source is null.");
        }

        try {
            assignRole(dataSource, projectID, username, role);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The assignment of role " + role.name + " to user " + username + " for project with ID " + projectID + " was unsuccessful: " + e.getMessage());
        }
    }

    @DELETE
    @Path("{id}/roles/{username}")
    public void deleteRole(@PathParam("id")int projectID, @PathParam("username")String username) {
        if (username == null) {
            throw new IllegalArgumentException("username is null.");
        }

        DataSource dataSource = LIMSInitializationListener.getDataSource();

        if (dataSource == null) {
            throw new InternalServerErrorException("The data source is null.");
        }

        try {
            deleteRole(dataSource, projectID, username);
        } catch (SQLException e) {
            throw new InternalServerErrorException("The deletion of the project role of user " + username + " for project with ID " + projectID + " was unsuccessful.");
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
                Role userRoleForProject = project.getRoleForUser(user);
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
     * @param idsOfProjectsToRetrieve A list of project IDs for which to retrieve projects or an empty list if all projects are to be retrieved
     * @return A list of projects matching the specified ids or all projects if no ids were specified.
     */
    static List<Project> getProjects(DataSource dataSource, Collection<Integer> idsOfProjectsToRetrieve) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (idsOfProjectsToRetrieve == null) {
            throw new IllegalArgumentException("idsOfProjectsToKeep is null.");
        }

        idsOfProjectsToRetrieve = new HashSet<Integer>(idsOfProjectsToRetrieve);
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
                    (idsOfProjectsToRetrieve.isEmpty() ? "" : "WHERE project.id IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(idsOfProjectsToRetrieve.size()) + ")") +
                    "ORDER BY project.id"
            );

            int statementObjectIndex = 1;
            for (Integer idOfProjectToRetrieve : idsOfProjectsToRetrieve) {
                getProjectsStatement.setInt(statementObjectIndex++, idOfProjectToRetrieve);
            }

            getProjectsResultSet = getProjectsStatement.executeQuery();
            return createProjectsFromResultSet(getProjectsResultSet);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(getProjectsStatement);
            SqlUtilities.cleanUpResultSets(getProjectsResultSet);
        }
    }

    static void addProject(DataSource dataSource, Project project) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (project == null) {
            throw new IllegalArgumentException("project is null.");
        }

        Connection connection = null;
        PreparedStatement retrieveMaxProjectIDStatement = null;
        PreparedStatement addProjectStatement = null;
        ResultSet retrieveMaxProjectIDResultSet = null;
        try {
            connection = dataSource.getConnection();

            retrieveMaxProjectIDStatement = connection.prepareStatement("SELECT MAX(id) FROM " + BiocodeServerLIMSDatabaseConstants.PROJECT_TABLE_NAME);
            retrieveMaxProjectIDResultSet = retrieveMaxProjectIDStatement.executeQuery();
            retrieveMaxProjectIDResultSet.next();
            int projectID = retrieveMaxProjectIDResultSet.getInt(1) + 1;

            addProjectStatement = connection.prepareStatement(
                    "INSERT INTO " + BiocodeServerLIMSDatabaseConstants.PROJECT_TABLE_NAME +
                    "(id, name, description, parent_project_id, is_public) " +
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
            addProjectStatement.setBoolean(5, false);

            SqlUtilities.beginTransaction(connection);

            if (addProjectStatement.executeUpdate() == 0) {
                throw new InternalServerErrorException("The addition of project " + project.name + " was unsuccessful.");
            }

            addProjectRoles(connection, project.userRoles, projectID);

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(retrieveMaxProjectIDStatement, addProjectStatement);
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

        PreparedStatement updateProjectStatement = null;
        Connection connection = null;
        try {
            connection = dataSource.getConnection();

            updateProjectStatement = connection.prepareStatement("UPDATE project SET name=?, description=?, parent_project_id=?, is_public=? WHERE id=?");

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

            if (updateProjectStatement.executeUpdate() != 1) {
                throw new InternalServerErrorException("More than 1 project was updated. The updates will be undone.");
            }
            removeProjectRoles(connection, project.id, Collections.<String>emptySet());
            addProjectRoles(connection, project.userRoles, project.id);

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(updateProjectStatement);
        }
    }

    static void removeProjectRoles(Connection connection, int projectID, Collection<String> usernames) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("connection is null.");
        }
        if (usernames == null) {
            throw new IllegalArgumentException("usernames is null.");
        }

        usernames = new HashSet<String>(usernames);

        String partialQuery =
                " FROM " + BiocodeServerLIMSDatabaseConstants.PROJECT_ROLE_TABLE_NAME +
                " WHERE project_id=?" +
                (usernames.isEmpty() ? "" : " AND username IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(usernames.size()) + ")");

        PreparedStatement removeProjectRolesStatement = null;
        PreparedStatement retrieveProjectRolesStatement = null;
        ResultSet retrieveProjectRolesResultSet = null;
        try {
            removeProjectRolesStatement = connection.prepareStatement("DELETE " + partialQuery);
            retrieveProjectRolesStatement = connection.prepareStatement("SELECT * " + partialQuery);

            removeProjectRolesStatement.setObject(1, projectID);
            retrieveProjectRolesStatement.setObject(1, projectID);

            int i = 2;
            for (String username : usernames) {
                removeProjectRolesStatement.setObject(i, username);
                retrieveProjectRolesStatement.setObject(i, username);
                i++;
            }

            SqlUtilities.beginTransaction(connection);

            removeProjectRolesStatement.executeUpdate();

            retrieveProjectRolesResultSet = retrieveProjectRolesStatement.executeQuery();
            if (retrieveProjectRolesResultSet.next()) {
                throw new InternalServerErrorException("The removal of 1 or more project roles was unsuccessful.");
            }

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.cleanUpStatements(removeProjectRolesStatement, retrieveProjectRolesStatement);
            SqlUtilities.cleanUpResultSets(retrieveProjectRolesResultSet);
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
                throw new InternalServerErrorException("The assignment of role " + role.name + " to user " + username + " for project with ID " + projectID + " was unsuccessful.");
            }
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(assignRoleStatement);
        }
    }

    static void deleteRole(DataSource dataSource, int projectID, String username) throws SQLException {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource is null.");
        }
        if (username == null) {
            throw new IllegalArgumentException("username is null.");
        }

        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            removeProjectRoles(connection, projectID, Collections.singleton(username));
        } finally {
            SqlUtilities.closeConnection(connection);
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
                project.parentProjectID = resultSet.getInt("parent_project_id");
                project.isPublic = resultSet.getBoolean("is_public");

                projectIDToProject.put(projectID, project);
            }

            User user = Users.createUserFromResultSetRow(resultSet);
            if (user != null) {
                project.userRoles.put(user, Role.forId(resultSet.getInt("role")));
            }
        }

        return new ArrayList<Project>(projectIDToProject.values());
    }

    private static void addProjectRoles(Connection connection, Map<User, Role> userToRole, int idOfProjectToAddRoleFor) throws SQLException {
        if (connection == null) {
            throw new IllegalArgumentException("connection is null.");
        }
        if (userToRole == null) {
            throw new IllegalArgumentException("userToRole is null.");
        }
        if (userToRole.isEmpty()) {
            return;
        }

        PreparedStatement addProjectRolesStatement = null;
        try {
            addProjectRolesStatement = connection.prepareStatement(
                    "INSERT INTO " + BiocodeServerLIMSDatabaseConstants.PROJECT_ROLE_TABLE_NAME +
                    "(project_id, username, role) " +
                    "VALUES(" + StringUtilities.generateCommaSeparatedQuestionMarks(3) + ")"
            );

            addProjectRolesStatement.setObject(1, idOfProjectToAddRoleFor);
            for (Map.Entry<User, Role> userAndRole : userToRole.entrySet()) {
                addProjectRolesStatement.setString(2, userAndRole.getKey().username);
                addProjectRolesStatement.setInt(3, userAndRole.getValue().id);
                addProjectRolesStatement.addBatch();
            }

            SqlUtilities.beginTransaction(connection);

            int[] updateResults = addProjectRolesStatement.executeBatch();
            for (int updateResult : updateResults) {
                if (updateResult != 1 && updateResult != PreparedStatement.SUCCESS_NO_INFO) {
                    throw new InternalServerErrorException("The addition of 1 or more project roles was unsuccessful. The successful additions will be undone.");
                }
            }

            SqlUtilities.commitTransaction(connection);
        } finally {
            SqlUtilities.cleanUpStatements(addProjectRolesStatement);
        }
    }

    private static Role getRole(DataSource dataSource, int projectID, String username) throws SQLException {
        Role role = null;

        List<Project> projects = getProjects(dataSource, Collections.singletonList(projectID));

        if (projects.size() != 1) {
            throw new InternalServerErrorException("More than 1 project was retrieved for projectID.");
        }

        for (Map.Entry<User, Role> entry : projects.get(0).userRoles.entrySet()) {
            if (username.equals(entry.getKey().username)) {
                role = entry.getValue();
                break;
            }
        }

        return role;
    }
}