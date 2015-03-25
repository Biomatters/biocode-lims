package com.biomatters.plugins.biocode.server.security;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.ExtractionReaction;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
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
            extractionIds.add(reaction.getExtractionId());
        }

        checkUserHasRoleForExtractionIDs(extractionIds, user, role);
    }

    public static void checkUserHasRoleForExtractionIDs(Collection<String> extractionIDs, User user, Role role) throws DatabaseServiceException {
        if (extractionIDs == null) {
            throw new IllegalArgumentException("extractionIDs is null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("userRole is null.");
        }
        if (extractionIDs.isEmpty()) {
            return;
        }

        if (LIMSInitializationListener.getLimsConnection() == null) {
            throw new DatabaseServiceException("The LIMS connection is null.", false);
        }
        if (!(LIMSInitializationListener.getLimsConnection() instanceof SqlLimsConnection)) {
            throw new DatabaseServiceException("The LIMS connection is not an instance of SqlLimsConnection.", false);
        }

        SqlLimsConnection limsConnection = (SqlLimsConnection)LIMSInitializationListener.getLimsConnection();

        checkUserHasRoleForWorkflows(getWorkflowIDsAssociatedWithExtractionsIDs(limsConnection, extractionIDs), user, role);
    }

    public static void checkUserHasRoleForWorkflows(Collection<Integer> workflowIDs, User user, Role role) throws DatabaseServiceException {
        if (workflowIDs == null) {
            throw new IllegalArgumentException("workflowIDs is null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("userRole is null.");
        }
        if (workflowIDs.isEmpty()) {
            return;
        }

        workflowIDs = new HashSet<Integer>();

        Connection connection = null;
        PreparedStatement retrieveIDsOfProjectsThatGovernWorkflowsStatement = null;
        ResultSet retrieveIDsOfProjectsThatGovernWorkflowsResultSet = null;
        try {
            DataSource dataSource = LIMSInitializationListener.getDataSource();

            if (dataSource == null) {
                throw new DatabaseServiceException("The data source is null.", false);
            }

            connection = dataSource.getConnection();
            retrieveIDsOfProjectsThatGovernWorkflowsStatement = connection.prepareStatement(
                    "SELECT * " +
                    "FROM " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                    " WHERE workflow_id IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(workflowIDs.size()) + ")"
            );

            int statementObjectIndex = 1;
            for (Integer workflowID : workflowIDs) {
                retrieveIDsOfProjectsThatGovernWorkflowsStatement.setInt(statementObjectIndex++, workflowID);
            }

            retrieveIDsOfProjectsThatGovernWorkflowsResultSet = retrieveIDsOfProjectsThatGovernWorkflowsStatement.executeQuery();
            Set<Integer> idsOfProjectsUserHasRoleFor = getProjectIDs(Projects.getProjectsUserHasAtLeastRoleFor(LIMSInitializationListener.getDataSource(), user, role));

            while (retrieveIDsOfProjectsThatGovernWorkflowsResultSet.next()) {
                if (!idsOfProjectsUserHasRoleFor.contains(retrieveIDsOfProjectsThatGovernWorkflowsResultSet.getInt("project_id"))) {
                    throw new ForbiddenException("User " + user.username + " does not have access to workflow with ID " + retrieveIDsOfProjectsThatGovernWorkflowsResultSet.getInt("workflow_id") + ".");
                }
            }
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "An error occurred while checking user " + user.username + "'s access rights: " + e.getMessage(), false);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(retrieveIDsOfProjectsThatGovernWorkflowsStatement);
            SqlUtilities.cleanUpResultSets(retrieveIDsOfProjectsThatGovernWorkflowsResultSet);
        }
    }

    /**
     * @param user The user to check for
     * @param role The role to check for
     * @return A list of IDs for samples of the supplied list that are readable
     * @throws com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException if there is a problem communicating with the FIMS or LIMS
     */
    public static Set<Integer> getWorkflowIDsUserHasRoleFor(User user, Role role) throws DatabaseServiceException {
        if (user == null) {
            throw new IllegalArgumentException("user is null.");
        }
        if (role == null) {
            throw new IllegalArgumentException("role is null.");
        }

        Set<Integer> workflowIDsUserHasRoleFor = new HashSet<Integer>();
        Connection connection = null;
        PreparedStatement retrieveWorkflowIDsUserHasRoleForStatement = null;
        ResultSet retrieveWorkflowIDsUserHasRoleForResultSet = null;
        try {
            Set<Integer> idsOfProjectsUserHasRoleFor = getProjectIDs(Projects.getProjectsUserHasAtLeastRoleFor(LIMSInitializationListener.getDataSource(), user, role));

            DataSource dataSource = LIMSInitializationListener.getDataSource();

            if (dataSource == null) {
                throw new DatabaseServiceException("The data source is null.", false);
            }

            connection = dataSource.getConnection();
            retrieveWorkflowIDsUserHasRoleForStatement = connection.prepareStatement(
                    "SELECT workflow_id " +
                    "FROM " + BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME +
                    " WHERE project_id IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(idsOfProjectsUserHasRoleFor.size()) + ")"
            );

            int statementObjectIndex = 1;
            for (Integer projectID : idsOfProjectsUserHasRoleFor) {
                retrieveWorkflowIDsUserHasRoleForStatement.setInt(statementObjectIndex++, projectID);
            }

            retrieveWorkflowIDsUserHasRoleForResultSet = retrieveWorkflowIDsUserHasRoleForStatement.executeQuery();
            while (retrieveWorkflowIDsUserHasRoleForResultSet.next()) {
                workflowIDsUserHasRoleFor.add(retrieveWorkflowIDsUserHasRoleForResultSet.getInt(1));
            }
        } catch (SQLException e) {
            throw new DatabaseServiceException("An error occurred while retrieving the IDs of workflows that user " + user.username + " has access to.", false);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(retrieveWorkflowIDsUserHasRoleForStatement);
            SqlUtilities.cleanUpResultSets(retrieveWorkflowIDsUserHasRoleForResultSet);
        }

        return workflowIDsUserHasRoleFor;
    }

    private static Set<Integer> getWorkflowIDsAssociatedWithExtractionsIDs(LIMSConnection limsConnection, Collection<String> extractionIDs) throws DatabaseServiceException {
        if (limsConnection == null) {
            throw new IllegalArgumentException("limsConnection is null.");
        }
        if (extractionIDs == null) {
            throw new IllegalArgumentException("extractionIDs is null.");
        }
        if (extractionIDs.isEmpty()) {
            return Collections.emptySet();
        }

        try {
            return getWorkflowIDsAssociatedWithExtractionDatabaseIDs(limsConnection, getExtractionDatabaseIDs(limsConnection, extractionIDs));
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "The retrieval of the workflow IDs was unsuccessful.", false);
        }
    }

    private static Set<Integer> getExtractionDatabaseIDs(LIMSConnection limsConnection, Collection<String> extractionIds) throws DatabaseServiceException {
        if (limsConnection == null) {
            throw new IllegalArgumentException("limsConnection is null.");
        }
        if (extractionIds == null) {
            throw new IllegalArgumentException("extractionIds is null.");
        }
        if (extractionIds.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> extractionDatabaseIDs = new HashSet<Integer>();

        for (ExtractionReaction extractionReaction : limsConnection.getExtractionsForIds(new ArrayList<String>(new HashSet<String>(extractionIds)))) {
            extractionDatabaseIDs.add(extractionReaction.getId());
        }

        return extractionDatabaseIDs;
    }

    private static Set<Integer> getWorkflowIDsAssociatedWithExtractionDatabaseIDs(LIMSConnection limsConnection, Collection<Integer> extractionDatabaseIDs) throws SQLException {
        if (limsConnection == null) {
            throw new IllegalArgumentException("limsConnection is null.");
        }
        if (extractionDatabaseIDs == null) {
            throw new IllegalArgumentException("extractionDatabaseIDs is null.");
        }
        if (!(limsConnection instanceof SqlLimsConnection)) {
            throw new IllegalArgumentException("limsConnection is not an instance of SqlLimsConnection.");
        }
        if (extractionDatabaseIDs.isEmpty()) {
            return Collections.emptySet();
        }

        extractionDatabaseIDs = new HashSet<Integer>(extractionDatabaseIDs);

        Set<Integer> workflowIDs = new HashSet<Integer>();
        Connection connection = null;
        PreparedStatement retrieveWorkflowIDsStatement = null;
        ResultSet retrieveWorkflowIDsResultSet = null;
        try {
            connection = ((SqlLimsConnection)limsConnection).getDataSource().getConnection();
            retrieveWorkflowIDsStatement = connection.prepareStatement(
                    "SELECT id " +
                    "FROM workflow " +
                    "WHERE extractionId IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(extractionDatabaseIDs.size()) + ")"
            );

            int statementObjectIndex = 1;
            for (Integer extractionDatabaseID : extractionDatabaseIDs) {
                retrieveWorkflowIDsStatement.setInt(statementObjectIndex++, extractionDatabaseID);
            }

            retrieveWorkflowIDsResultSet = retrieveWorkflowIDsStatement.getResultSet();
            while (retrieveWorkflowIDsResultSet.next()) {
                workflowIDs.add(retrieveWorkflowIDsResultSet.getInt(1));
            }
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(retrieveWorkflowIDsStatement);
            SqlUtilities.cleanUpResultSets(retrieveWorkflowIDsResultSet);
        }

        return workflowIDs;
    }

    private static Set<Integer> getProjectIDs(Collection<Project> projects) {
        if (projects == null) {
            throw new IllegalArgumentException("projects is null.");
        }
        if (projects.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> projectIDs = new HashSet<Integer>();

        for (Project project : projects) {
            if (project.id != null) {
                projectIDs.add(project.id);
            }
        }

        return projectIDs;
    }
}