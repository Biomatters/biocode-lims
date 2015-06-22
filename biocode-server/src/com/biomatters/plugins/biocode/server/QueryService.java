package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import com.biomatters.plugins.biocode.server.security.*;
import com.biomatters.plugins.biocode.server.utilities.StringUtilities;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import com.biomatters.plugins.biocode.server.query.QueryParser;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

import javax.annotation.Nonnull;
import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

/**
 * Main endpoint for querying the LIMS as a whole.  Returns tissues, workflows, plates and sequences
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 5:21 PM
 */
@Path("search")
public class QueryService {

    // We'll go with XML rather than JSON because that's what Geneious deals with.  Could potentially go JSON, but
    // it would require conversion on server-side and client-side.  Better to work with XML by default and offer
    // JSON as a possible alternative in the future.

    @POST
    @Produces("application/xml")
    @Consumes("text/plain")
    public Response searchWithPost(@QueryParam("q") String query,
                                   @DefaultValue("true")  @QueryParam("showTissues") boolean showTissues,
                                   @DefaultValue("true")  @QueryParam("showWorkflows") boolean showWorkflows,
                                   @DefaultValue("true")  @QueryParam("showPlates") boolean showPlates,
                                   @DefaultValue("false") @QueryParam("showSequences") boolean showSequenceIds,
                                   String tissueIdsToMatch) throws DatabaseServiceException {
        if (query == null) {
            throw new IllegalArgumentException("query is null.");
        }

        return performSearch(query, showTissues, showWorkflows, showPlates, showSequenceIds, tissueIdsToMatch);
    }

    public static LimsSearchResult getSearchResults(String query,
                                                    boolean retrieveTissues,
                                                    boolean retrieveWorkflows,
                                                    boolean retrievePlates,
                                                    boolean retrieveSequenceIds,
                                                    Set<String> tissuesToMatch) throws DatabaseServiceException {
        if (query == null) {
            throw new IllegalArgumentException("query is null.");
        }

        return new QueryParser(LIMSConnection.getSearchAttributes())
                .parseQuery(query)
                .execute(BiocodeService.getSearchDownloadOptions(retrieveTissues, retrieveWorkflows, retrievePlates, retrieveSequenceIds), tissuesToMatch);
    }

    Response performSearch(String query, boolean showTissues, boolean showWorkflows, boolean showPlates, boolean showSequenceIds, @Nonnull String tissueIdsToMatch) throws DatabaseServiceException {
        Set<String> tissueIdsToMatchSet = !tissueIdsToMatch.isEmpty() || query.contains(RestQueryUtils.MATCH_TISSUES_QUERY) ?
                new HashSet<String>(StringUtilities.getListFromString(tissueIdsToMatch)) : null;
        LimsSearchResult result = getSearchResults(query, showTissues, showWorkflows, showPlates, showSequenceIds, tissueIdsToMatchSet);
        LimsSearchResult filteredResult = getPermissionsFilteredResult(result);
        return Response.ok(filteredResult).build();
    }

    LimsSearchResult getPermissionsFilteredResult(LimsSearchResult result) throws DatabaseServiceException {
        LimsSearchResult filteredSearchResult = new LimsSearchResult();

        Set<String> extractionIDsLoggedInUserHasReadAccessTo = AccessUtilities.getExtractionIdsUserHasRoleFor(Users.getLoggedInUser(), Role.READER);

        filteredSearchResult.addAllPlates(filterPlateIDs(result.getPlateIds(), extractionIDsLoggedInUserHasReadAccessTo));
        filteredSearchResult.addAllSequenceIDs(filterSequenceIDs(result.getPlateIds(), extractionIDsLoggedInUserHasReadAccessTo));
        filteredSearchResult.addAllTissueSamples(filterTissueIDs(result.getTissueIds(), extractionIDsLoggedInUserHasReadAccessTo));
        filteredSearchResult.addAllWorkflows(filterWorkflowIDs(result.getWorkflowIds(), extractionIDsLoggedInUserHasReadAccessTo));

        return filteredSearchResult;
    }

    private static Set<Integer> filterPlateIDs(Collection<Integer> plateIDsToFilter, Collection<String> extractionIDsLoggedInUserHasReadAccessTo) throws DatabaseServiceException {
        if (plateIDsToFilter == null) {
            throw new IllegalArgumentException("plateIDsToFilter is null.");
        }
        if (extractionIDsLoggedInUserHasReadAccessTo == null) {
            throw new IllegalArgumentException("extractionIDsLoggedInUserHasReadAccessTo is null.");
        }
        if (plateIDsToFilter.isEmpty() || extractionIDsLoggedInUserHasReadAccessTo.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> filteredPlateIDs = new HashSet<Integer>();

        for (Map.Entry<Integer, Collection<String>> plateIDAndExtractionIDs : getExtractionIdsForPlates(new ArrayList<Integer>(new HashSet<Integer>(plateIDsToFilter))).entrySet()) {
            boolean hasReadAccessToPlate = true;

            for (String extractionID : plateIDAndExtractionIDs.getValue()) {
                if (!extractionIDsLoggedInUserHasReadAccessTo.contains(extractionID)) {
                    hasReadAccessToPlate = false;
                    break;
                }
            }

            if (hasReadAccessToPlate) {
                filteredPlateIDs.add(plateIDAndExtractionIDs.getKey());
            }
        }

        return filteredPlateIDs;
    }

    private static Set<Integer> filterSequenceIDs(Collection<Integer> sequenceIDsToFilter, Collection<String> extractionIDsLoggedInUserHasReadAccessTo) throws DatabaseServiceException {
        if (sequenceIDsToFilter == null) {
            throw new IllegalArgumentException("sequenceIDsToFilter is null.");
        }
        if (extractionIDsLoggedInUserHasReadAccessTo == null) {
            throw new IllegalArgumentException("extractionIDsLoggedInUserHasReadAccessTo is null.");
        }
        if (sequenceIDsToFilter.isEmpty() || extractionIDsLoggedInUserHasReadAccessTo.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> filteredSequenceIDs = new HashSet<Integer>();

        for (Map.Entry<Integer, String> sequenceIDAndExtractionID : getExtractionIdsForSequences(new ArrayList<Integer>(new HashSet<Integer>(sequenceIDsToFilter))).entrySet()) {
            if (extractionIDsLoggedInUserHasReadAccessTo.contains(sequenceIDAndExtractionID.getValue())) {
                filteredSequenceIDs.add(sequenceIDAndExtractionID.getKey());
            }
        }

        return filteredSequenceIDs;
    }

    private static Set<String> filterTissueIDs(Collection<String> tissueIDsToFilter, Collection<String> extractionIDsLoggedInUserHasReadAccessTo) throws DatabaseServiceException {
        if (tissueIDsToFilter == null) {
            throw new IllegalArgumentException("tissueIDsToFilter is null.");
        }
        if (extractionIDsLoggedInUserHasReadAccessTo == null) {
            throw new IllegalArgumentException("extractionIDsLoggedInUserHasReadAccessTo is null.");
        }
        if (tissueIDsToFilter.isEmpty() || extractionIDsLoggedInUserHasReadAccessTo.isEmpty()) {
            return Collections.emptySet();
        }

        LIMSConnection limsConnection = LIMSInitializationListener.getLimsConnection();

        if (limsConnection == null) {
            throw new DatabaseServiceException("The lims connection is null.", false);
        }

        Set<String> filteredTissueIDs = new HashSet<String>();

        for (Map.Entry<String, String> tissueIDAndExtractionID : getExtractionIdsForTissues(new ArrayList<String>(new HashSet<String>(tissueIDsToFilter))).entrySet()) {
            if (extractionIDsLoggedInUserHasReadAccessTo.contains(tissueIDAndExtractionID.getValue())) {
                filteredTissueIDs.add(tissueIDAndExtractionID.getKey());
            }
        }

        return filteredTissueIDs;
    }

    private static Set<Integer> filterWorkflowIDs(Collection<Integer> workflowIDsToFilter, Collection<String> extractionIDsLoggedInUserHasReadAccessTo) throws DatabaseServiceException {
        if (workflowIDsToFilter == null) {
            throw new IllegalArgumentException("workflowIDsToFilter is null.");
        }
        if (extractionIDsLoggedInUserHasReadAccessTo == null) {
            throw new IllegalArgumentException("extractionIDsLoggedInUserHasReadAccessTo is null.");
        }
        if (workflowIDsToFilter.isEmpty() || extractionIDsLoggedInUserHasReadAccessTo.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Integer> filteredWorkflowIDs = new HashSet<Integer>();

        for (Map.Entry<Integer, String> workflowIDAndExtractionID : getExtractionIdsForWorkflows(new ArrayList<Integer>(new HashSet<Integer>(workflowIDsToFilter))).entrySet()) {
            if (extractionIDsLoggedInUserHasReadAccessTo.contains(workflowIDAndExtractionID.getValue())) {
                filteredWorkflowIDs.add(workflowIDAndExtractionID.getKey());
            }
        }

        return filteredWorkflowIDs;
    }

    private static Map<Integer, Collection<String>> getExtractionIdsForPlates(List<Integer> plateIds) throws DatabaseServiceException {
        if (plateIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Multimap<Integer, String> mapping = HashMultimap.create();
        DataSource dataSource = LIMSInitializationListener.getDataSource();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();

            StringBuilder queryBuilder = new StringBuilder("SELECT plate.id, E.extractionId FROM plate " +
                    "LEFT OUTER JOIN extraction ON extraction.plate = plate.id " +
                    "LEFT OUTER JOIN workflow W ON extraction.id = W.extractionId " +
                    "LEFT OUTER JOIN pcr ON pcr.plate = plate.id  " +
                    "LEFT OUTER JOIN cyclesequencing ON cyclesequencing.plate = plate.id " +
                    "LEFT OUTER JOIN workflow ON workflow.id = " +
                    "CASE WHEN pcr.workflow IS NOT NULL THEN pcr.workflow ELSE " +
                    "CASE WHEN W.id IS NOT NULL THEN W.id ELSE " +
                    "cyclesequencing.workflow END " +
                    "END " +
                    "LEFT OUTER JOIN extraction E ON E.id = " +
                    "CASE WHEN extraction.id IS NULL THEN workflow.extractionId ELSE extraction.id END " +
                    "WHERE plate.id IN ");
            SqlUtilities.appendSetOfQuestionMarks(queryBuilder, plateIds.size());
            queryBuilder.append(" ORDER BY plate.id");
            PreparedStatement select = connection.prepareStatement(queryBuilder.toString());
            SqlUtilities.fillStatement(plateIds, select);
            SqlUtilities.printSql(queryBuilder.toString(), plateIds);
            ResultSet resultSet = select.executeQuery();
            while(resultSet.next()) {
                String extractionId = resultSet.getString("E.extractionId");
                if(extractionId != null) {
                    extractionId = extractionId.trim();
                    if(extractionId.length() > 0) {
                        mapping.put(resultSet.getInt("plate.id"), extractionId);
                    }
                }
            }
            return mapping.asMap();
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    private static Map<Integer, String> getExtractionIdsForSequences(List<Integer> sequenceIds) throws DatabaseServiceException {
        if(sequenceIds.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Integer, String> mapping = new HashMap<Integer, String>();
        DataSource dataSource = LIMSInitializationListener.getDataSource();
        Connection connection = null;
        try {
            StringBuilder queryBuilder = new StringBuilder("select assembly.id, extraction.extractionId from assembly, workflow, extraction where assembly.id IN ");
            SqlUtilities.appendSetOfQuestionMarks(queryBuilder, sequenceIds.size());
            queryBuilder.append(" and assembly.workflow = workflow.id and workflow.extractionId = extraction.id ");

            connection = dataSource.getConnection();
            PreparedStatement select = connection.prepareStatement(queryBuilder.toString());
            SqlUtilities.fillStatement(sequenceIds, select);
            SqlUtilities.printSql(queryBuilder.toString(), sequenceIds);
            ResultSet resultSet = select.executeQuery();
            while(resultSet.next()) {
                String extractionId = resultSet.getString("extractionId");
                if(extractionId != null) {
                    extractionId = extractionId.trim();
                    if(extractionId.length() > 0) {
                        mapping.put(resultSet.getInt("id"), extractionId);
                    }
                }
            }
            return mapping;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    private static Map<String, String> getExtractionIdsForTissues(Collection<String> tissueIDs) throws DatabaseServiceException {
        if (tissueIDs == null) {
            throw new IllegalArgumentException("tissueIDs is null.");
        }
        if (tissueIDs.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, String> tissueIDsToExtractionIDs = new HashMap<String, String>();

        Connection connection = null;
        PreparedStatement retrieveExtractionIDsStatement = null;
        ResultSet retrieveExtractionIDsResultSet = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();
            retrieveExtractionIDsStatement = connection.prepareStatement(
                    "SELECT sampleId, extractionId " +
                    "FROM extraction " +
                    "WHERE sampleId IN (" + StringUtilities.generateCommaSeparatedQuestionMarks(tissueIDs.size()) + ")"
            );

            int statementObjectIndex = 1;
            for (String tissueID : new HashSet<String>(tissueIDs)) {
                retrieveExtractionIDsStatement.setString(statementObjectIndex++, tissueID);
            }

            retrieveExtractionIDsResultSet = retrieveExtractionIDsStatement.executeQuery();
            while (retrieveExtractionIDsResultSet.next()) {
                tissueIDsToExtractionIDs.put(retrieveExtractionIDsResultSet.getString("sampleId"), retrieveExtractionIDsResultSet.getString("extractionId"));
            }
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, "The retrieval of extraction IDs was unsuccessful: " + e.getMessage(), false);
        } finally {
            SqlUtilities.closeConnection(connection);
            SqlUtilities.cleanUpStatements(retrieveExtractionIDsStatement);
            SqlUtilities.cleanUpResultSets(retrieveExtractionIDsResultSet);
        }

        return tissueIDsToExtractionIDs;
    }

    private static Map<Integer, String> getExtractionIdsForWorkflows(List<Integer> workflowIds) throws DatabaseServiceException {
        if(workflowIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Integer, String> mapping = new HashMap<Integer, String>();
        DataSource dataSource = LIMSInitializationListener.getDataSource();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();

            StringBuilder queryBuilder = new StringBuilder("SELECT workflow.id, extraction.extractionId FROM workflow " +
                    "INNER JOIN extraction ON extraction.id = workflow.extractionId WHERE workflow.id IN ");
            SqlUtilities.appendSetOfQuestionMarks(queryBuilder, workflowIds.size());
            PreparedStatement select = connection.prepareStatement(queryBuilder.toString());
            SqlUtilities.fillStatement(workflowIds, select);
            SqlUtilities.printSql(queryBuilder.toString(), workflowIds);
            ResultSet resultSet = select.executeQuery();
            while(resultSet.next()) {
                String extractionId = resultSet.getString("extractionId");
                if(extractionId != null) {
                    extractionId = extractionId.trim();
                    if(extractionId.length() > 0) {
                        mapping.put(resultSet.getInt("id"), extractionId);
                    }
                }
            }
            return mapping;
        } catch (SQLException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }
}