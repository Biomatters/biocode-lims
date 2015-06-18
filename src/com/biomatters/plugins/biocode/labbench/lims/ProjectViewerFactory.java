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
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;

import java.util.ArrayList;
import java.util.List;

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
        ProjectViewer projectViewer = null;

        try {
            LIMSConnection activeLimsConnection = BiocodeService.getInstance().getActiveLIMSConnection();
            if (activeLimsConnection instanceof ProjectLimsConnection) {
                AnnotatedPluginDocument[] supportedDocuments = getSupportedDocuments(annotatedDocuments);
                if (supportedDocuments.length > 0) {
                    projectViewer = new ProjectViewer(supportedDocuments, (ProjectLimsConnection)activeLimsConnection);
                }
            }
        } catch (DocumentOperationException e) {
            Dialogs.showMessageDialog("An error occurred: " + e.getMessage());
        } catch (DatabaseServiceException e) {
            Dialogs.showMessageDialog("An error occurred: " + e.getMessage());
        }

        return projectViewer;
    }

    private static AnnotatedPluginDocument[] getSupportedDocuments(AnnotatedPluginDocument[] annotatedDocuments) {
        List<AnnotatedPluginDocument> supportedDocuments = new ArrayList<AnnotatedPluginDocument>();

        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            if (isSupportedDocument(annotatedDocument)) {
                supportedDocuments.add(annotatedDocument);
            }
        }

        return supportedDocuments.toArray(new AnnotatedPluginDocument[supportedDocuments.size()]);
    }

    private static boolean isSupportedDocument(AnnotatedPluginDocument annotatedDocuments) {
        boolean isSupportedDocument = false;

        Class documentClass = annotatedDocuments.getDocumentClass();
        if (WorkflowDocument.class.isAssignableFrom(documentClass)) {
            isSupportedDocument = true;
        } else if (PlateDocument.class.isAssignableFrom(documentClass)) {
            PlateDocument plateDocument = (PlateDocument)annotatedDocuments.getDocumentOrNull();
            if (plateDocument != null) {
                Reaction.Type plateType = plateDocument.getPlate().getReactionType();
                if (plateType.equals(Reaction.Type.PCR) || plateType.equals(Reaction.Type.CycleSequencing)) {
                    isSupportedDocument = true;
                }
            }
        }

        return isSupportedDocument;
    }
}
