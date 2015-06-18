package com.biomatters.plugins.biocode.labbench.rest.client;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.*;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.plugins.biocode.labbench.lims.*;
import com.biomatters.plugins.biocode.labbench.plates.GelImage;
import com.biomatters.plugins.biocode.labbench.plates.Plate;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.server.*;
import jebl.util.Cancelable;
import jebl.util.ProgressListener;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 4/04/14 11:14 AM
 */
public class ServerLimsConnection extends ProjectLimsConnection {
    private static final String SEARCH_BASE_PATH            = "search";
    private static final String PROJECTS_BASE_PATH          = "projects";
    private static final String WORKFLOWS_BASE_PATH         = "workflows";
    private static final String PLATES_BASE_PATH            = "plates";
    private static final String FAILURE_REASONS_BASE_PATH   = "failuresReasons";
    private static final String PERMISSIONS_BASE_PATH       = "permissions";
    private static final String SEQUENCES_BASE_PATH         = "sequences";
    private static final String REACTIONS_BASE_PATH         = "reactions";
    private static final String EXTRACTIONS_BASE_PATH       = "extractions";
    private static final String TISSUES_BASE_PATH           = "tissues";
    private static final String INFO_BASE_PATH              = "info";
    private static final String COCKTAILS_BASE_PATH         = "cocktails";
    private static final String THERMOCYCLES_BASE_PATH      = "thermocycles";
    private static final String BCIDROOTS_BASE_PATH         = "bcid-roots";


    private String username;
    WebTarget target;
    private static Map<String, String> BCIDRootsCache = new HashMap<String, String>();

    @Override
    protected void _connect(PasswordOptions options) throws ConnectionException {
        LimsConnectionOptions allLimsOptions = (LimsConnectionOptions)options;
        PasswordOptions selectedLimsOptions = allLimsOptions.getSelectedLIMSOptions();

        if (!(selectedLimsOptions instanceof RESTConnectionOptions)) {
            throw new IllegalArgumentException("Expected instance of " + RESTConnectionOptions.class.getSimpleName() + " but was " + selectedLimsOptions.getClass().getName());
        }

        RESTConnectionOptions connectionOptions = (RESTConnectionOptions)selectedLimsOptions;
        this.username = connectionOptions.getUsername();
        String host = connectionOptions.getHost();
        if (!host.matches("https?://.*")) {
            host = "http://" + host;
        }

        target = RestQueryUtils.getBiocodeWebTarget(host, connectionOptions.getUsername(), connectionOptions.getPassword(), requestTimeout);
        try {
            testConnection();
        } catch (DatabaseServiceException e) {
            throw new ConnectionException("Failed to connect: " + e.getMessage(), e);
        }
    }

    @Override
    public LimsSearchResult getMatchingDocumentsFromLims(Query query, Collection<String> tissueIdsToMatch, Cancelable cancelable) throws DatabaseServiceException {
        updateBCIDRootsCache();

        query = removeAdvancedSearchQueryTermsOnNonLimsSearchAttributes(query);

        String tissueIdsToMatchString = tissueIdsToMatch == null ? null : StringUtilities.join(",", tissueIdsToMatch);
        try {
            WebTarget target = this.target.path(SEARCH_BASE_PATH)
                    .queryParam("q", RestQueryUtils.geneiousQueryToRestQueryString(query))
                    .queryParam("matchTissues", tissueIdsToMatch != null)
                    .queryParam("showTissues", BiocodeService.isDownloadTissues(query))
                    .queryParam("showWorkflows", BiocodeService.isDownloadWorkflows(query))
                    .queryParam("showPlates", BiocodeService.isDownloadPlates(query))
                    .queryParam("showSequences", BiocodeService.isDownloadSequences(query));
            Invocation.Builder request = target.request(MediaType.APPLICATION_XML_TYPE);
            return request.post(Entity.entity(tissueIdsToMatchString, MediaType.TEXT_PLAIN_TYPE), LimsSearchResult.class);
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    private static Query removeAdvancedSearchQueryTermsOnNonLimsSearchAttributes(Query query) {
        Map<String, Object> querySearchDownloadOptions = getSearchDownloadOptions(query);
        Query resultQuery = Query.Factory.createExtendedQuery("", querySearchDownloadOptions);

        if (query instanceof BasicSearchQuery) {
            resultQuery = query;
        } else if (query instanceof AdvancedSearchQueryTerm) {
            if (LIMSConnection.getSearchAttributes().contains(((AdvancedSearchQueryTerm)query).getField())) {
                resultQuery = query;
            }
        } else if (query instanceof CompoundSearchQuery) {
            List<Query> limsSearchAttributeQueries = new ArrayList<Query>();
            List<DocumentField> limsSearchAttributes = LIMSConnection.getSearchAttributes();

            CompoundSearchQuery queryAsCompoundSearchQuery = (CompoundSearchQuery)query;
            for (Query childQuery : queryAsCompoundSearchQuery.getChildren()) {
                if (limsSearchAttributes.contains(((AdvancedSearchQueryTerm)childQuery).getField())) {
                    limsSearchAttributeQueries.add(childQuery);
                }
            }

            if (!limsSearchAttributeQueries.isEmpty()) {
                Query compoundQuery = createCompoundQuery(
                        limsSearchAttributeQueries.toArray(new Query[limsSearchAttributeQueries.size()]),
                        queryAsCompoundSearchQuery.getOperator(),
                        querySearchDownloadOptions
                );

                if (compoundQuery != null) {
                    resultQuery = compoundQuery;
                }
            }
        }

        return resultQuery;
    }

    private static Map<String, Object> getSearchDownloadOptions(Query query) {
        return BiocodeService.getSearchDownloadOptions(
                BiocodeService.isDownloadTissues(query),
                BiocodeService.isDownloadWorkflows(query),
                BiocodeService.isDownloadPlates(query),
                BiocodeService.isDownloadSequences(query)
        );
    }

    private static Query createCompoundQuery(Query[] childQueries, CompoundSearchQuery.Operator operator, Map<String, Object> downloadSearchOptions) {
        Query compoundQuery = null;

        switch (operator) {
            case AND:
                compoundQuery = Query.Factory.createAndQuery(childQueries, downloadSearchOptions);
                break;
            case OR:
                compoundQuery = Query.Factory.createOrQuery(childQueries, downloadSearchOptions);
                break;
            default:

        }

        return compoundQuery;
    }

    @Override
    public List<WorkflowDocument> getWorkflowsById_(Collection<Integer> workflowIds, Cancelable cancelable) throws DatabaseServiceException {
        try {
            return target.path(WORKFLOWS_BASE_PATH)
                    .queryParam("ids", StringUtilities.join(",", workflowIds))
                    .request(MediaType.APPLICATION_XML_TYPE)
                    .get(new GenericType<XMLSerializableList<WorkflowDocument>>() {
                    }).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<Plate> getPlates__(Collection<Integer> plateIds, Cancelable cancelable) throws DatabaseServiceException {
        if (plateIds.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return target.path(PLATES_BASE_PATH)
                    .queryParam("ids", StringUtilities.join(",", plateIds))
                    .request(MediaType.APPLICATION_XML_TYPE)
                    .get(new GenericType<XMLSerializableList<Plate>>() {
                    }).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void savePlates_(List<Plate> plates, ProgressListener progress) throws BadDataException, DatabaseServiceException {
        try {
            Invocation.Builder request = target.path(PLATES_BASE_PATH).request();
            Response response = request.put(Entity.entity(new XMLSerializableList<Plate>(Plate.class, plates), MediaType.APPLICATION_XML_TYPE));
            if (response.getStatus() != Response.Status.OK.getStatusCode() && response.getStatus() != Response.Status.NO_CONTENT.getStatusCode()) {
                Dialogs.showMessageDialog("Could not add plate: " + response.readEntity(String.class));
            }
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<Plate> getEmptyPlates(Collection<Integer> plateIds) throws DatabaseServiceException {
        try {
            return target.path(PLATES_BASE_PATH).path("empty").request(MediaType.APPLICATION_XML_TYPE).get(new GenericType<XMLSerializableList<Plate>>() {
            }).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void saveReactions(Reaction[] reactions, Reaction.Type type, ProgressListener progress) throws DatabaseServiceException {
        try {
            Invocation.Builder request = target.path(PLATES_BASE_PATH).path("reactions").queryParam("type", type.name()).request();
            request.put(Entity.entity(
                    new XMLSerializableList<Reaction>(Reaction.class, Arrays.asList(reactions)),
                    MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public Set<Integer> deletePlates(List<Plate> plates, ProgressListener progress) throws DatabaseServiceException {
        String result;
        try {
            Invocation.Builder request = target.path(PLATES_BASE_PATH).path("delete").request(MediaType.TEXT_PLAIN_TYPE);
            Response response = request.post(Entity.entity(new XMLSerializableList<Plate>(Plate.class, plates), MediaType.APPLICATION_XML_TYPE));
            result = response.readEntity(String.class);
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }

        if (result == null || result.isEmpty()) {
            return Collections.emptySet();
        } else {
            HashSet<Integer> set = new HashSet<Integer>();
            for (String idString : result.split("\\n")) {
                try {
                    set.add(Integer.parseInt(idString));
                } catch (NumberFormatException e) {
                    throw new DatabaseServiceException("Server returned bad plate IDs: " + result, false);
                }
            }
            return set;
        }
    }

    @Override
    public void renamePlate(int id, String newName) throws DatabaseServiceException {
        try {
            Invocation.Builder request = target.path(PLATES_BASE_PATH).path(id + "/name").request();
            Response response = request.put(Entity.entity(newName, MediaType.TEXT_PLAIN_TYPE));
            response.close();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<FailureReason> getPossibleFailureReasons() {
        try {
            return target.path(FAILURE_REASONS_BASE_PATH).request(MediaType.APPLICATION_XML_TYPE).get(
                    new GenericType<List<FailureReason>>() {
                    }
            );
        } catch (WebApplicationException e) {
            return Collections.emptyList();
        } catch (ProcessingException e) {
            // todo Handle this better. Perhaps cache on start up and have an updating thread
            return Collections.emptyList();
        }
    }

    @Override
    public boolean deleteAllowed(String tableName) {
        try {
            return target.path(PERMISSIONS_BASE_PATH).path("delete").path(tableName).request(MediaType.TEXT_PLAIN_TYPE).get(Boolean.class);
        } catch (WebApplicationException e) {
            return false;
        } catch (ProcessingException e) {
            return false;
        }
    }

    @Override
    public List<AssembledSequence> getAssemblySequences_(Collection<Integer> sequenceIds, Cancelable cancelable, boolean includeFailed) throws DatabaseServiceException {
        try {
            return target.path(SEQUENCES_BASE_PATH).
                    queryParam("includeFailed", includeFailed).
                    queryParam("ids", StringUtilities.join(",", sequenceIds)).
                    request(MediaType.APPLICATION_XML_TYPE).get(
                    new GenericType<List<AssembledSequence>>() {
                    }
            );
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void setAssemblySequences(Map<Integer, String> assemblyIDToAssemblySequenceToSet, ProgressListener progressListener) throws DatabaseServiceException {
        Map<String, String> assemblyIDAsStringToAssemblySequenceToSet = new HashMap<String, String>();

        for (Map.Entry<Integer, String> assemblyIDAndAssemblySequenceToSet : assemblyIDToAssemblySequenceToSet.entrySet()) {
            assemblyIDAsStringToAssemblySequenceToSet.put(String.valueOf(assemblyIDAndAssemblySequenceToSet.getKey()), assemblyIDAndAssemblySequenceToSet.getValue());
        }
        try {
            target.path(SEQUENCES_BASE_PATH).path("update").request().put(Entity.entity(new StringMap(assemblyIDAsStringToAssemblySequenceToSet), MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public int addAssembly(boolean isPass, String notes, String technician, FailureReason failureReason, String failureNotes, boolean addChromatograms, AssembledSequence seq, List<Integer> reactionIds, Cancelable cancelable) throws DatabaseServiceException {
        //not sure if this need batch
        try {
            WebTarget resource = target.path(SEQUENCES_BASE_PATH).
                    queryParam("isPass", isPass).
                    queryParam("notes", notes).
                    queryParam("technician", technician).
                    queryParam("failureReason", failureReason != null ? failureReason.getId() : null).
                    queryParam("addChromatograms", addChromatograms).
                    queryParam("reactionIds", StringUtilities.join(",", reactionIds));
            System.out.println(resource.getUri());
            Response response = resource.request(MediaType.TEXT_PLAIN_TYPE).
                    post(Entity.entity(seq, MediaType.APPLICATION_XML_TYPE));
            return response.readEntity(Integer.class);
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void deleteSequences(List<Integer> sequencesToDelete) throws DatabaseServiceException {
        try {
            for (int id : sequencesToDelete) {
                target.path(SEQUENCES_BASE_PATH).path(Integer.toString(id)).request().delete();
            }
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void setSequenceStatus(boolean submitted, List<Integer> ids) throws DatabaseServiceException {
        try {
            for (Integer id : ids) {
                target.path(SEQUENCES_BASE_PATH).path("" + id).path("submitted").request().put(Entity.entity(submitted, MediaType.TEXT_PLAIN_TYPE));
            }
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void deleteSequencesForWorkflowId(Integer workflowId, String extractionId) throws DatabaseServiceException {
        try {
            target.path(WORKFLOWS_BASE_PATH).path("" + workflowId).path("sequences").path(extractionId).request().delete();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public Map<String, String> getTissueIdsForExtractionIds_(String tableName, List<String> extractionIds) throws DatabaseServiceException {
        try {
            return target.path(PLATES_BASE_PATH).path("tissues").
                    queryParam("type", tableName).
                    queryParam("extractionIds", StringUtilities.join(",", extractionIds)).
                    request(MediaType.APPLICATION_XML_TYPE).
                    get(StringMap.class).getMap();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<ExtractionReaction> getExtractionsForIds_(List<String> extractionIds) throws DatabaseServiceException {
        try {
            return target.path(PLATES_BASE_PATH).path("extractions").
                    queryParam("ids", StringUtilities.join(",", extractionIds)).
                    request(MediaType.APPLICATION_XML_TYPE).
                    get(new GenericType<XMLSerializableList<ExtractionReaction>>() {
                    }).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public Map<Integer, List<MemoryFile>> downloadTraces(List<Integer> reactionIds, ProgressListener progressListener) throws DatabaseServiceException {
        try {
            Map<Integer, List<MemoryFile>> result = new HashMap<Integer, List<MemoryFile>>();
            for (int reactionId : reactionIds) {
                List<MemoryFile> memoryFiles;
                try {
                    Response response = target.path(REACTIONS_BASE_PATH).path("" + reactionId).path("traces").
                            request(MediaType.APPLICATION_XML_TYPE).get();
                    memoryFiles = getListFromResponse(response, new GenericType<List<MemoryFile>>() { });
                } catch (NotFoundException e) {
                    continue;
                }
                if (memoryFiles != null) {
                    result.put(reactionId, memoryFiles);
                }
            }
            return result;
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public PasswordOptions getConnectionOptions() {
        return new RESTConnectionOptions();
    }

    @Override
    public boolean isLocal() {
        return false;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public void disconnect() {
        target = null;
    }

    @Override
    public void doAnyExtraInitialization(ProgressListener progressListener) throws DatabaseServiceException {
        // Nothing required on the client side
    }

    @Override
    public Set<String> getAllExtractionIdsForTissueIds_(List<String> tissueIds) throws DatabaseServiceException {
        try {
            final Set<String> ret = new HashSet<String>();
            ret.addAll(Arrays.asList(
                    target.path(TISSUES_BASE_PATH).path("extractions").queryParam("tissues",
                            StringUtilities.join(",", tissueIds)).request(MediaType.TEXT_PLAIN_TYPE).get(String.class).split("\\n")
            ));
            return ret;
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }

    }

    @Override
    public List<ExtractionReaction> getExtractionsFromBarcodes_(List<String> barcodes) throws DatabaseServiceException {
        try {
            return target.path(EXTRACTIONS_BASE_PATH).
                    queryParam("barcodes", StringUtilities.join(",", barcodes)).
                    request(MediaType.APPLICATION_XML_TYPE).
                    get(new GenericType<XMLSerializableList<ExtractionReaction>>() {
                    }).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public Map<Integer, List<GelImage>> getGelImages(Collection<Integer> plateIds) throws DatabaseServiceException {
        try {
            Map<Integer, List<GelImage>> images = new HashMap<Integer, List<GelImage>>();
            for (Integer plateId : plateIds) {
                Response response = target.path(PLATES_BASE_PATH).path(String.valueOf(plateId)).path("gels").
                        request(MediaType.APPLICATION_XML_TYPE).get();
                if(response.getStatus() == 204) {
                    return Collections.emptyMap();
                }
                images.put(plateId, getListFromResponse(response, new GenericType<List<GelImage>>() { }));
            }
            return images;
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void setProperty(String key, String value) throws DatabaseServiceException {
        try {
            target.path(INFO_BASE_PATH).path("properties").path(key).request().put(Entity.entity(value, MediaType.TEXT_PLAIN_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public String getProperty(String key) throws DatabaseServiceException {
        try {
            return target.path(INFO_BASE_PATH).path("properties").path(key).request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<Workflow> getWorkflowsByName(Collection<String> workflowNames) throws DatabaseServiceException {
        if(workflowNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<Workflow> data = new ArrayList<Workflow>();
        try {
            for (String id : workflowNames) {
                if (id.isEmpty()) {
                    continue;
                }
                data.add(target.path(WORKFLOWS_BASE_PATH).path(id).request(MediaType.APPLICATION_XML_TYPE).get(Workflow.class));
            }
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
        return data;
    }

    @Override
    public Map<String, String> getWorkflowNames(List<String> idsToCheck, List<String> loci, Reaction.Type reactionType) throws DatabaseServiceException {
        try {
            return target.path(EXTRACTIONS_BASE_PATH).path("workflows").
                    queryParam("extractionIds", StringUtilities.join(",", idsToCheck)).
                    queryParam("loci", StringUtilities.join(",", loci)).
                    queryParam("type", reactionType.name()).
                    request(MediaType.APPLICATION_XML_TYPE).get(StringMap.class).getMap();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void renameWorkflow(int id, String newName) throws DatabaseServiceException {
        try {
            Invocation.Builder request = target.path(WORKFLOWS_BASE_PATH).path(id + "/name").request();
            request.put(Entity.entity(newName, MediaType.TEXT_PLAIN_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void testConnection() throws DatabaseServiceException {
        try {
            target.path(INFO_BASE_PATH).path("details").request(MediaType.TEXT_PLAIN_TYPE).get();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public Collection<String> getPlatesUsingCocktail(Reaction.Type type, int cocktailId) throws DatabaseServiceException {
        try {
            String platesList = target.path(COCKTAILS_BASE_PATH).path(type.name()).path("" + cocktailId).path("plates").request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
            if (platesList == null || platesList.isEmpty()) {
                return Collections.emptyList();
            } else {
                return Arrays.asList(platesList.split("\\n"));
            }
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void addCocktails(List<? extends Cocktail> cocktails) throws DatabaseServiceException {
        try {
            target.path(COCKTAILS_BASE_PATH).request().post(Entity.entity(
                    new XMLSerializableList<Cocktail>(Cocktail.class, new ArrayList<Cocktail>(cocktails)),
                    MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void deleteCocktails(List<? extends Cocktail> deletedCocktails) throws DatabaseServiceException {
        try {
            target.path(COCKTAILS_BASE_PATH).path("delete").request().post(Entity.entity(
                    new XMLSerializableList<Cocktail>(Cocktail.class, new ArrayList<Cocktail>(deletedCocktails)),
                    MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<PCRCocktail> getPCRCocktailsFromDatabase() throws DatabaseServiceException {
        try {
            return target.path(COCKTAILS_BASE_PATH).path(Cocktail.Type.pcr.name()).request(MediaType.APPLICATION_XML_TYPE).get(
                    new GenericType<XMLSerializableList<PCRCocktail>>() {
                    }
            ).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<CycleSequencingCocktail> getCycleSequencingCocktailsFromDatabase() throws DatabaseServiceException {
        try {
            return target.path(COCKTAILS_BASE_PATH).path(Cocktail.Type.cyclesequencing.name()).request(MediaType.APPLICATION_XML_TYPE).get(
                    new GenericType<XMLSerializableList<CycleSequencingCocktail>>() {
                    }
            ).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<Thermocycle> getThermocyclesFromDatabase(Thermocycle.Type type) throws DatabaseServiceException {
        try {
            return target.path(THERMOCYCLES_BASE_PATH).path(type.name()).request(MediaType.APPLICATION_XML_TYPE).get(new GenericType<XMLSerializableList<Thermocycle>>(){}).getList();
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    private void updateBCIDRootsCache() throws DatabaseServiceException {
        try {
            List<BCIDRoot> BCIDRoots = target.path(BCIDROOTS_BASE_PATH).request(MediaType.APPLICATION_JSON_TYPE).get(new GenericType<List<BCIDRoot>>() {});

            BCIDRootsCache.clear();

            for (BCIDRoot val : BCIDRoots) {
                BCIDRootsCache.put(val.type, val.value);
            }
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    public Map<String, String> getBCIDRoots() {
        return Collections.unmodifiableMap(BCIDRootsCache);
    }

    @Override
    public void addThermoCycles(Thermocycle.Type type, List<Thermocycle> cycles) throws DatabaseServiceException {
        try {
            target.path(THERMOCYCLES_BASE_PATH).path(type.name()).request().post(Entity.entity(
                    new XMLSerializableList<Thermocycle>(Thermocycle.class, cycles), MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void deleteThermoCycles(Thermocycle.Type type, List<Thermocycle> cycles) throws DatabaseServiceException {
        try {
            target.path(THERMOCYCLES_BASE_PATH).path(type.name()).path("delete").request().post(Entity.entity(
                    new XMLSerializableList<Thermocycle>(Thermocycle.class, cycles), MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public List<String> getPlatesUsingThermocycle(int thermocycleId) throws DatabaseServiceException {
        try {
            String result = target.path(THERMOCYCLES_BASE_PATH).path("" + thermocycleId).path("plates").request(MediaType.TEXT_PLAIN_TYPE).get(String.class);
            if (result == null || result.isEmpty()) {
                return Collections.emptyList();
            } else {
                return Arrays.asList(result.split("\\n"));
            }
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public Map<Project, Collection<Workflow>> getProjectToWorkflows() throws DatabaseServiceException {
        try {
            Map<Project, Collection<Workflow>> projectToWorkflows = new HashMap<Project, Collection<Workflow>>();

            for (Project project : target.path(PROJECTS_BASE_PATH).request(MediaType.APPLICATION_XML_TYPE).get(new GenericType<List<Project>>(){})) {
                projectToWorkflows.put(
                        project,
                        target.path(PROJECTS_BASE_PATH).path(Integer.toString(project.id)).path("workflows").request(MediaType.APPLICATION_XML_TYPE).get(new GenericType<XMLSerializableList<Workflow>>(){}).getList()
                );
            }

            return projectToWorkflows;
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void addWorkflowsToProject(int projectId, Collection<Integer> workflowIds) throws DatabaseServiceException {
        try {
            target.path(PROJECTS_BASE_PATH)
                    .path(Integer.toString(projectId))
                    .path("workflows")
                    .request()
                    .post(Entity.entity(new XMLSerializableList<XMLSerializableInteger>(XMLSerializableInteger.class, toXMLSerializableIntegers(workflowIds)), MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    @Override
    public void removeWorkflowsFromProject(Collection<Integer> workflowIds) throws DatabaseServiceException {
        try {
            target.path(PROJECTS_BASE_PATH)
                    .path("workflows")
                    .path("deletion")
                    .request()
                    .post(Entity.entity(new XMLSerializableList<XMLSerializableInteger>(XMLSerializableInteger.class, toXMLSerializableIntegers(workflowIds)), MediaType.APPLICATION_XML_TYPE));
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        } catch (ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), false);
        }
    }

    private static List<XMLSerializableInteger> toXMLSerializableIntegers(Collection<Integer> integers) {
        List<XMLSerializableInteger> xmlSerializableIntegers = new ArrayList<XMLSerializableInteger>();

        for (Integer integer : integers) {
            xmlSerializableIntegers.add(new XMLSerializableInteger(integer));
        }

        return xmlSerializableIntegers;
    }

    @Override
    public boolean supportReporting() {
        return false;
    }

    @Override
    protected Connection getConnectionInternal() throws SQLException {
        throw new UnsupportedOperationException("Does not support getting a SQL Connection");
    }

    public static <T> List<T> getListFromResponse(Response response, GenericType<List<T>> type) {
        if (response.getStatus() == 204) {  // HTTP 204 is No Content
            return Collections.emptyList();
        }
        return response.readEntity(type);
    }
}