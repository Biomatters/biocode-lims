package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.implementations.SequenceExtractionUtilities;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.SequenceUtilities;
import com.biomatters.plugins.biocode.labbench.AssembledSequence;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.util.*;

/**
 * @author Gen Li
 *         Created on 18/02/15 5:21 PM
 */
public class ReverseAssemblySequencesOperation extends DocumentOperation {
    private static final String TOOLTIP_TEXT = "Select sequence documents from LIMS to reverse complement.";

    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions("Reverse Complement Sequences", TOOLTIP_TEXT).setInPopupMenu(true, 1);
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] { new DocumentSelectionSignature(SequenceDocument.class, 1, Integer.MAX_VALUE) };
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        CompositeProgressListener operationProgress = new CompositeProgressListener(progressListener, 4);

        operationProgress.beginSubtask("Checking selected sequence documents...");
        checkSequencesAreValid(annotatedDocuments);

        operationProgress.beginSubtask("Generating reverse complemented sequences...");
        Map<Integer, String> assemblyIDToAssemblySequenceReversed = getAssemblyIDToAssemblySequenceReversedMap(annotatedDocuments);

        operationProgress.beginSubtask("Saving generated sequences to LIMS...");
        try {
            BiocodeService.getInstance().getActiveLIMSConnection().setAssemblySequences(assemblyIDToAssemblySequenceReversed, progressListener);
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException(e.getMessage(), e);
        }

        operationProgress.beginSubtask("Generating new sequence documents...");
        return DocumentUtilities.createAnnotatedPluginDocuments(getReversedSequenceDocuments(annotatedDocuments));
    }

    private static void checkSequencesAreValid(AnnotatedPluginDocument[] annotatedDocuments) throws DocumentOperationException {
        checkDocumentsAreSequences(annotatedDocuments);
        checkSequencesExistInLIMS(annotatedDocuments);
    }

    private static Map<Integer, String> getAssemblyIDToAssemblySequenceReversedMap(AnnotatedPluginDocument[] annotatedDocuments) throws DocumentOperationException {
        Map<Integer, String> assemblyIDToAssemblySequenceReversed = new HashMap<Integer, String>();

        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            assemblyIDToAssemblySequenceReversed.put(
                    (Integer)annotatedDocument.getFieldValue(LIMSConnection.SEQUENCE_ID.getCode()),
                    SequenceUtilities.reverseComplement(((SequenceDocument)annotatedDocument.getDocument()).getCharSequence()).toString()
            );
        }

        return assemblyIDToAssemblySequenceReversed;
    }

    private static List<PluginDocument> getReversedSequenceDocuments(AnnotatedPluginDocument[] annotatedDocuments) throws DocumentOperationException {
        List<PluginDocument> sequencesReversed = new ArrayList<PluginDocument>();

        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            sequencesReversed.add(SequenceExtractionUtilities.reverseComplement((SequenceDocument)annotatedDocument.getDocument()));
        }

        return sequencesReversed;
    }

    private static void checkDocumentsAreSequences(AnnotatedPluginDocument[] annotatedDocuments) {
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            if (!SequenceDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
                throw new IllegalArgumentException(
                        "Invalid document type. " +
                                "Expected: " + SequenceDocument.class.getSimpleName() + ", " +
                                "actual: " + annotatedDocument.getDocumentClass().getSimpleName() + "."
                );
            }
        }
    }

    private static void checkSequencesExistInLIMS(AnnotatedPluginDocument[] annotatedDocuments) throws DocumentOperationException {
        List<Integer> assemblyIDsFromDocuments = new ArrayList<Integer>(getAssemblyIDs(annotatedDocuments));
        try {
            Collection<Integer> assembyIDsFromAssembledSequencesRetrievedFromLIMS = getAssemblyIDs(BiocodeService.getInstance().getActiveLIMSConnection().getAssemblySequences(
                    assemblyIDsFromDocuments,
                    ProgressListener.EMPTY,
                    true
            ));

            for (Integer assemblyIDFromDocuments : assemblyIDsFromDocuments) {
                if (!assembyIDsFromAssembledSequencesRetrievedFromLIMS.contains(assemblyIDFromDocuments)) {
                    throw new DocumentOperationException("Sequence with ID " + assemblyIDFromDocuments + " does not exist in the LIMS.");
                }
            }
        } catch (DatabaseServiceException e) {
            throw new DocumentOperationException(e.getMessage(), e);
        }
    }

    private static Collection<Integer> getAssemblyIDs(AnnotatedPluginDocument[] annotatedDocuments) {
        Collection<Integer> assemblyIDs = new HashSet<Integer>();

        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            assemblyIDs.add((Integer)annotatedDocument.getFieldValue(LIMSConnection.SEQUENCE_ID.getCode()));
        }

        return assemblyIDs;
    }

    private static Collection<Integer> getAssemblyIDs(List<AssembledSequence> assembledSequences) {
        Collection<Integer> assemblyIDs = new HashSet<Integer>();

        for (AssembledSequence assembledSequence : assembledSequences) {
            assemblyIDs.add(assembledSequence.id);
        }

        return assemblyIDs;
    }

    public String getHelp() {
        return TOOLTIP_TEXT;
    }
}