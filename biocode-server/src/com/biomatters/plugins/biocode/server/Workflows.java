package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.Workflow;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.server.security.AccessUtilities;
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
            List<WorkflowDocument> workflowDocuments = LIMSInitializationListener.getLimsConnection().getWorkflowsById(Sequences.getIntegerListFromString(idListAsString), ProgressListener.EMPTY);

            Set<Integer> workflowIDs = new HashSet<Integer>();
            for (WorkflowDocument workflowDocument : workflowDocuments) {
                workflowIDs.add(workflowDocument.getId());
            }

            AccessUtilities.checkUserHasRoleForWorkflows(workflowIDs, Users.getLoggedInUser(), Role.READER);

            return new XMLSerializableList<WorkflowDocument>(WorkflowDocument.class, workflowDocuments);
        } catch (DatabaseServiceException e) {
            throw new WebApplicationException(Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).type(MediaType.TEXT_PLAIN_TYPE).build());
        }
    }

    @GET
    @Path("{id}")
    @Produces("application/xml")
    public Workflow get(@PathParam("id")String workflowName) throws NoContentException {
        if (workflowName == null || workflowName.trim().isEmpty()) {
            throw new BadRequestException("Must specify ids");
        }

        try {
            List<Workflow> workflows = LIMSInitializationListener.getLimsConnection().getWorkflowsByName(Collections.singletonList(workflowName));

            if (workflows.size() > 1) {
                throw new InternalServerErrorException("More than one workflow with name " + workflowName + " was found.");
            }

            if (workflows.isEmpty()) {
                throw new NotFoundException("No workflow found for \"" + workflowName + "\"");
            }

            Workflow workflow = workflows.get(0);

            AccessUtilities.checkUserHasRoleForWorkflows(Collections.singletonList(workflow.getId()), Users.getLoggedInUser(), Role.READER);

            return workflow;
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }

    @PUT
    @Consumes("text/plain")
    @Path("{id}/name")
    public void renameWorkflow(@PathParam("id")int id, String newName) {
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