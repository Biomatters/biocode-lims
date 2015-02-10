package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;

import java.util.List;

/**
 *
 * @author Gen Li
 *         Created on 10/02/15 4:55 PM
 */
public class ExtractionReactionRetrieverViaExtractionBarcode implements ExtractionReactionRetriever<List<String>> {
    public List<ExtractionReaction> retrieve(LIMSConnection limsConnection, List<String> extractionBarcodes) throws DatabaseServiceException {
        return limsConnection.getExtractionsFromBarcodes(extractionBarcodes);
    }
}
