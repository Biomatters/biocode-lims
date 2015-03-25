package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.LimsSearchResult;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.security.AccessUtilities;
import com.biomatters.plugins.biocode.server.security.Role;
import com.biomatters.plugins.biocode.server.security.Users;
import com.biomatters.plugins.biocode.server.utilities.StringUtilities;
import jebl.util.ProgressListener;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.NoContentException;
import javax.ws.rs.core.Response;
import java.util.*;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 5:20 PM
 */
@Path("workflows")
public class Workflows {

    @GET
    @Produces("application/xml")
    @Consumes("text/plain")
    public XMLSerializableList<WorkflowDocument> getWorkflows(@QueryParam("ids")String idListAsString) {
        try {
            final Set<String> extractionIds = new HashSet<String>();
            final List<WorkflowDocument> results = LIMSInitializationListener.getLimsConnection().getWorkflowsById(
                    Sequences.getIntegerListFromString(idListAsString), ProgressListener.EMPTY);
            for (WorkflowDocument result : results) {
                extractionIds.add(result.getWorkflow().getExtractionId());
            }
            AccessUtilities.checkUserHasRoleForExtractionIDs(extractionIds, Users.getLoggedInUser(), Role.READER);
            return new XMLSerializableList<WorkflowDocument>(WorkflowDocument.class, results);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST)
                                                       .entity(e.getMessage())
                                                       .type(MediaType.TEXT_PLAIN_TYPE)
                                                       .build());
        }
    }

    @GET
    @Path("{id}")
    @Produces("application/xml")
    public Workflow get(@PathParam("id")String workflowName) throws NoContentException {
        if(workflowName == null || workflowName.trim().isEmpty()) {
            throw new BadRequestException("Must specify ids");
        }
        try {
            List<Workflow> workflows = LIMSInitializationListener.getLimsConnection().getWorkflowsByName(Collections.singletonList(workflowName));
            Workflow result = workflows.get(0);
            AccessUtilities.checkUserHasRoleForExtractionIDs(Collections.singletonList(result.getExtractionId()), Users.getLoggedInUser(), Role.READER);
            return result;
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @PUT
    @Consumes("text/plain")
    @Path("{id}/name")
    public void renameWorkflow(@PathParam("id") int id, String newName) {
        try {
            AccessUtilities.checkUserHasRoleForWorkflows(Collections.singleton(id), Users.getLoggedInUser(), Role.WRITER);
            LIMSInitializationListener.getLimsConnection().renameWorkflow(id, newName);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(e.getMessage(), e);
        }
    }

    @DELETE
    @Path("{workflowId}/sequences/{extractionId}")
    public void deleteSequencesForWorkflow(@PathParam("workflowId")int workflowId, @PathParam("extractionId")String extractionId) {
        try {
            AccessUtilities.checkUserHasRoleForWorkflows(Collections.singletonList(workflowId), Users.getLoggedInUser(), Role.WRITER);
            LIMSInitializationListener.getLimsConnection().deleteSequencesForWorkflowId(workflowId, extractionId);
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @GET
    @Produces("application/xml")
    @Path("workflows")
    public StringMap getWorkflowsForExtractionIds(@QueryParam("extractionIds")String extractionIds,
                                                  @QueryParam("loci")String loci,
                                                  @QueryParam("type")String type) { // todo: Migrate logic to QueryService?
        if(extractionIds == null || extractionIds.trim().isEmpty()) {
            throw new BadRequestException("Must specify extractionIds");
        }

        if(loci == null || loci.trim().isEmpty()) {
            throw new BadRequestException("Must specify loci");
        }

        if(type == null || type.trim().isEmpty()) {
            throw new BadRequestException("Must specify type");
        }

        try {
            return new StringMap(
                    LIMSInitializationListener.getLimsConnection().getWorkflowNames(
                            StringUtilities.getListFromString(extractionIds),
                            StringUtilities.getListFromString(loci),
                            Reaction.Type.valueOf(type)
                    ));
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        } catch(IllegalArgumentException e) {
            throw new BadRequestException(type + " is not valid type.");
        }
    }
}