package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;

import javax.ws.rs.*;

/**
 * A tissue entry
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 23/03/14 4:57 PM
 */
@Path("tissues")
public class Tissues {

    @GET
    @Produces("text/plain")
    @Path("extractions")
    public String getForBarcodes(@QueryParam("tissues")String tissueIds) { // todo: Migrate logic to QueryService?
        if(tissueIds == null || tissueIds.trim().isEmpty()) {
            throw new BadRequestException("Must specify tissues");
        }
        try {
            return StringUtilities.join("\n", LIMSInitializationListener.getLimsConnection().
                    getAllExtractionIdsForTissueIds(com.biomatters.plugins.biocode.server.utilities.StringUtilities.getListFromString(tissueIds)));
        } catch (DatabaseServiceException e) {
            throw new InternalServerErrorException(e.getMessage(), e);
        }
    }
}
