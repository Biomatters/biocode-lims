package com.biomatters.plugins.biocode.server.security;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import com.biomatters.plugins.biocode.server.Project;
import com.biomatters.plugins.biocode.server.Role;
import com.biomatters.plugins.biocode.server.User;
import com.biomatters.plugins.biocode.server.utilities.StringUtilities;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;

import javax.sql.DataSource;
import javax.ws.rs.ForbiddenException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Matthew Cheung
 *         Created on 4/07/14 1:21 PM
 */
public class AccessUtilities {
    /**
     * Throws a {@link javax.ws.rs.ForbiddenException} if the current logged in user cannot edit the plates specified
     * @param plates a list of {@link com.biomatters.plugins.biocode.labbench.plates.Plate}s to check
     * @param role
     */
    public static void checkUserHasRoleForPlates(Collection<Plate> plates, User user, Role role) throws DatabaseServiceException {
        if (plates == null) {
            throw new IllegalArgumentException("plates is null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is null.");
        }
        if (plates.isEmpty()) {
            return;
        }

        List<Reaction> reactionsFromPlates = new ArrayList<Reaction>();

        for (Plate plate : plates) {
            reactionsFromPlates.addAll(Arrays.asList(plate.getReactions()));
        }

        checkUserHasRoleForReactions(reactionsFromPlates, user, role);
    }

    /**
     * Throws a {@link javax.ws.rs.ForbiddenException} if the current logged in user cannot edit the plates specified
     * @param reactions a list of {@link com.biomatters.plugins.biocode.labbench.reaction.Reaction}s to check
     */
    public static void checkUserHasRoleForReactions(Collection<? extends Reaction> reactions, User user, Role role) throws DatabaseServiceException {
        if (reactions == null) {
            throw new IllegalArgumentException("reactions is null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("userRole is null.");
        }
        if (reactions.isEmpty()) {
            return;
        }

        Set<String> extractionIds = new HashSet<String>();

        for (Reaction reaction : reactions) {
            if (reaction != null) {
                extractionIds.add(reaction.getExtractionId());
            }
        }

        checkUserHasRoleForExtractionIds(extractionIds, user, role);
    }

    public static void checkUserHasRoleForExtractionIds(Collection<String> extractionIds, User user, Role role) throws DatabaseServiceException {
        if (extractionIds == null) {
            throw new IllegalArgumentException("extractionIds is null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("userRole is null.");
        }
        if (extractionIds.isEmpty()) {
            return;
        }

        if (LIMSInitializationListener.getLimsConnection() == null) {
            throw new DatabaseServiceException("The LIMS connection is null.", false);
        }
        if (!(LIMSInitializationListener.getLimsConnection() instanceof SqlLimsConnection)) {
            throw new DatabaseServiceException("The LIMS connection is not an instance of SqlLimsConnection.", false);
        }

        SqlLimsConnection limsConnection = (SqlLimsConnection)LIMSInitializationListener.getLimsConnection();

        checkUserHasRoleForWorkflows(getWorkflowIdsAssociatedWithExtractionsIds(limsConnection, extractionIds), user, role);
    }

    public static void checkUserHasRoleForWorkflows(Collection<Integer> workflowIds, User user, Role role) throws DatabaseServiceException {
        if (workflowIds == null) {
            throw new IllegalArgumentException("workflowIds is null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("userRole is null.");
        }
        if (workflowIds.isEmpty()) {
            return;
        }

        workflowIds = new HashSet<Integer>(workflowIds);

        Connection connection = null;
        PreparedStatement retrieveIdsOfProjectsThatGovernWorkflowsStatement = null;
        ResultSet retrieveIdsOfProjectsThatGovernWorkflowsResultSet = null;
        try {
            DataSource dataSource = LIMSInitializationListener.getValidDataSource();

            connection = dataSource.getConnection();
            retrieveIdsOfProjectsThatGovernWorkflowsStatement = connection.prepareStatement(
                    "SELECT * " +
                    "FROM " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                    " WHERE workflow_id IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(workflowIds.size()) + ")"
            );

            int statementObjectIndex = 1;
            for (Integer workflowId : workflowIds) {
                retrieveIdsOfProjectsThatGovernWorkflowsStatement.setInt(statementObjectIndex++, workflowId);
            }

            retrieveIdsOfProjectsThatGovernWorkflowsResultSet = retrieveIdsOfProjectsThatGovernWorkflowsStatement.executeQuery();
            Set<Integer> idsOfProjectsUserHasRoleFor = getProjectIds(Projects.getProjectsUserHasRoleAccessFor(LIMSInitializationListener.getValidDataSource(), user, role));

            while (retrieveIdsOfProjectsThatGovernWorkflowsResultSet.next()) {
                if (!idsOfProjectsUserHasRoleFor.contains(retrieveIdsOfProjectsThatGovernWorkflowsResultSet.getInt("project_id"))) {
                    throw new ForbiddenException("User " + user.username + " does not have access to workflow with ID " + retrieveIdsOfProjectsThatGovernWorkflowsResultSet.getInt("workflow_id") + ".");
                }
            }
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "An error occurred while checking user " + user.username + "'s access rights.", false);
        } finally {
            SqlUtilities.cleanUpStatements(retrieveIdsOfProjectsThatGovernWorkflowsStatement);
            SqlUtilities.cleanUpResultSets(retrieveIdsOfProjectsThatGovernWorkflowsResultSet);
            SqlUtilities.closeConnection(connection);
        }
    }

    /**
     * @param user The user to check for
     * @param role The role to check for
     */
    public static Set<String> getExtractionIdsUserHasRoleFor(User user, Role role) throws DatabaseServiceException {
        if (user == null) {
            throw new IllegalArgumentException("user is null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is null.");
        }

        Set<String> extractionIdsUserHasRoleFor = new HashSet<String>();
        Connection connection = null;
        PreparedStatement retrieveExtractionIdsUserHasRoleForStatement = null;
        ResultSet retrieveExtractionIdsUserHasRoleForResultSet = null;

        try {
            DataSource dataSource = LIMSInitializationListener.getValidDataSource();

            Set<Integer> idsOfProjectsUserHasRoleFor = getProjectIds(Projects.getProjectsUserHasRoleAccessFor(dataSource, user, role));

            connection = dataSource.getConnection();
            retrieveExtractionIdsUserHasRoleForStatement = connection.prepareStatement(
                    "SELECT extraction.extractionId as extractionId, " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME + ".project_id as projectId " +
                    "FROM extraction " +
                    "LEFT OUTER JOIN workflow ON extraction.id=workflow.extractionId " +
                    "LEFT OUTER JOIN " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME + " ON workflow.id=" + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME + ".workflow_id"
            );
            retrieveExtractionIdsUserHasRoleForResultSet = retrieveExtractionIdsUserHasRoleForStatement.executeQuery();
            while (retrieveExtractionIdsUserHasRoleForResultSet.next()) {
                int projectId = retrieveExtractionIdsUserHasRoleForResultSet.getInt("projectId");
                if (projectId == 0 || idsOfProjectsUserHasRoleFor.contains(projectId)) {
                    extractionIdsUserHasRoleFor.add(retrieveExtractionIdsUserHasRoleForResultSet.getString("extractionId"));
                }
            }
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "An error occurred while retrieving the IDs of workflows that user " + user.username + " has access to.", false);
        } finally {
            SqlUtilities.cleanUpStatements(retrieveExtractionIdsUserHasRoleForStatement);
            SqlUtilities.cleanUpResultSets(retrieveExtractionIdsUserHasRoleForResultSet);
            SqlUtilities.closeConnection(connection);
        }

        return extractionIdsUserHasRoleFor;
    }

    private static Set<Integer> getWorkflowIdsAssociatedWithExtractionsIds(LIMSConnection limsConnection, Collection<String> extractionIds) throws DatabaseServiceException {
        if (limsConnection == null) {
            throw new IllegalArgumentException("limsConnection is null.");
        }
        if (extractionIds == null) {
            throw new IllegalArgumentException("extractionIds is null.");
        }
        if (extractionIds.isEmpty()) {
            return Collections.emptySet();
        }

        try {
            return getWorkflowIdsAssociatedWithExtractionDatabaseIds(limsConnection, getExtractionDatabaseIds(limsConnection, extractionIds));
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "The retrieval of the workflow IDs was unsuccessful.", false);
        }
    }

    private static Set<Integer> getExtractionDatabaseIds(LIMSConnection limsConnection, Collection<String> extractionIds) throws DatabaseServiceException {
        if (limsConnection == null) {
            throw new IllegalArgumentException("limsConnection is null.");
        }
        if (extractionIds == null) {
            throw new IllegalArgumentException("extractionIds is null.");
        }
        if (extractionIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> extractionDatabaseIds = new HashSet<Integer>();

        for (ExtractionReaction extractionReaction : limsConnection.getExtractionsForIds(new ArrayList<String>(new HashSet<String>(extractionIds)))) {
            extractionDatabaseIds.add(extractionReaction.getId());
        }

        return extractionDatabaseIds;
    }

    private static Set<Integer> getWorkflowIdsAssociatedWithExtractionDatabaseIds(LIMSConnection limsConnection, Collection<Integer> extractionDatabaseIds) throws SQLException {
        if (limsConnection == null) {
            throw new IllegalArgumentException("limsConnection is null.");
        }
        if (extractionDatabaseIds == null) {
            throw new IllegalArgumentException("extractionDatabaseIds is null.");
        }
        if (!(limsConnection instanceof SqlLimsConnection)) {
            throw new IllegalArgumentException("limsConnection is not an instance of SqlLimsConnection.");
        }
        if (extractionDatabaseIds.isEmpty()) {
            return Collections.emptySet();
        }

        extractionDatabaseIds = new HashSet<Integer>(extractionDatabaseIds);

        Set<Integer> workflowIds = new HashSet<Integer>();
        Connection connection = null;
        PreparedStatement retrieveWorkflowIdsStatement = null;
        ResultSet retrieveWorkflowIdsResultSet = null;
        try {
            connection = ((SqlLimsConnection)limsConnection).getDataSource().getConnection();
            retrieveWorkflowIdsStatement = connection.prepareStatement(
                    "SELECT id " +
                    "FROM workflow " +
                    "WHERE extractionId IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(extractionDatabaseIds.size()) + ")"
            );

            int statementObjectIndex = 1;
            for (Integer extractionDatabaseId : extractionDatabaseIds) {
                retrieveWorkflowIdsStatement.setInt(statementObjectIndex++, extractionDatabaseId);
            }

            retrieveWorkflowIdsResultSet = retrieveWorkflowIdsStatement.executeQuery();
            while (retrieveWorkflowIdsResultSet.next()) {
                workflowIds.add(retrieveWorkflowIdsResultSet.getInt(1));
            }
        } finally {
            SqlUtilities.cleanUpStatements(retrieveWorkflowIdsStatement);
            SqlUtilities.cleanUpResultSets(retrieveWorkflowIdsResultSet);
            SqlUtilities.closeConnection(connection);
        }

        return workflowIds;
    }

    private static Set<Integer> getProjectIds(Collection<Project> projects) {
        if (projects == null) {
            throw new IllegalArgumentException("projects is null.");
        }
        if (projects.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> projectIds = new HashSet<Integer>();

        for (Project project : projects) {
            if (project.id != null) {
                projectIds.add(project.id);
            }
        }

        return projectIds;
    }
}