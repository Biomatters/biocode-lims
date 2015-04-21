package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.BiocodePlugin;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.ProjectLIMSConnection;
import jebl.util.ProgressListener;

import java.util.Collections;
import java.util.List;

/**
 *
 * @author Gen Li
 *         Created on 20/04/15 9:25 AM
 */
public class ProjectManagementOperation extends DocumentOperation {
    @Override
    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Manage Projects", "").setInPopupMenu(true, 1);
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    @Override
    public String getHelp() {
        return null;
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[0];
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if (!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException("Please log into the biocode plugin.");
        }

        try {
            LIMSConnection limsConnection = BiocodeService.getInstance().getActiveLIMSConnection();
            if (!(limsConnection instanceof ProjectLIMSConnection)) {
                throw new DocumentOperationException("Please connect to a lims database that supports projects.");
            }

            return new ProjectManagementOptions((ProjectLIMSConnection)limsConnection);
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException(e.getMessage(), e);
        }
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        return Collections.emptyList();
    }
}