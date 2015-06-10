package com.biomatters.plugins.biocode.labbench.lims;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionSignature;
import com.biomatters.geneious.publicapi.plugin.DocumentViewer;
import com.biomatters.geneious.publicapi.plugin.DocumentViewerFactory;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.PlateDocument;
import com.biomatters.plugins.biocode.labbench.WorkflowDocument;

/**
 *
 * @author Gen Li
 *         Created on 24/04/15 1:50 PM
 */
public class ProjectViewerFactory extends DocumentViewerFactory {
    @Override
    public String getName() {
        return "Projects Assignment";
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public String getHelp() {
        return "";
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] {
                new DocumentSelectionSignature(WorkflowDocument.class, 1, Integer.MAX_VALUE),
                new DocumentSelectionSignature(PlateDocument.class, 1, Integer.MAX_VALUE)
        };
    }

    @Override
    public DocumentViewer createViewer(AnnotatedPluginDocument[] annotatedDocuments) {
        try {
            LIMSConnection activeLimsConnection = BiocodeService.getInstance().getActiveLIMSConnection();
            if (activeLimsConnection instanceof ProjectLimsConnection) {
                return new ProjectViewer(annotatedDocuments, (ProjectLimsConnection)activeLimsConnection);
            }
        } catch (DocumentOperationException e) {
            Dialogs.showMessageDialog("An error occurred: " + e.getMessage());
        } catch (DatabaseServiceException e) {
            Dialogs.showMessageDialog("An error occurred: " + e.getMessage());
        }
        return null;
    }
}
