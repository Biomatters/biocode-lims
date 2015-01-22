package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideGraphSequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.GeneralUtilities;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import com.biomatters.plugins.biocode.WorkflowBuilder;
import com.biomatters.plugins.biocode.labbench.fims.MySQLFimsConnection;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.reaction.PCROptions;
import com.biomatters.plugins.biocode.labbench.reaction.Reaction;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import jebl.util.CompositeProgressListener;
import jebl.util.ProgressListener;

import java.util.*;
import java.util.regex.Pattern;

public class AnnotateUtilities {
    public static final List<String> FIELDS_TO_NOT_COPY = Arrays.asList(
            DocumentField.AMBIGUITIES.getCode(),
            DocumentField.BIN.getCode(),
            DocumentField.CREATED_FIELD.getCode(),
            DocumentField.DESCRIPTION_FIELD.getCode(),
            DocumentField.FIRST_SEQUENCE_RESIDUES.getCode(),
            DocumentField.HIGH_QUALITY_PERCENT.getCode(),
            DocumentField.LOW_QUALITY_PERCENT.getCode(),
            DocumentField.MEDIMUM_QUALITY_PERCENT.getCode(),
            DocumentField.NAME_FIELD.getCode(),
            DocumentField.POST_TRIM_LENGTH.getCode(),
            DocumentField.SEQUENCE_LENGTH.getCode(),
            DocumentField.TOPOLOGY_FIELD.getCode(),
            DocumentField.UNREAD_FIELD.getCode(),
            PluginDocument.MODIFIED_DATE_FIELD.getCode(),
            "document_size",
            DocumentField.SEQUENCE_COUNT.getCode()
    );

    public static final DocumentField NOTES_FIELD = new DocumentField("Assembly Notes", "", "assemblyNotes", String.class, false, false);
    public static final DocumentField PROGRESS_FIELD = new DocumentField("Progress", "", "progress", String.class, true, false);
    public static final DocumentField EDITS_FIELD = new DocumentField("# Edits", "The number of manual edits made in the assembly", "edits", Integer.class, true, false);
    public static final DocumentField REFERENCE_SEQ_FIELD = new DocumentField("Reference Sequence", "", "refSeqId", String.class, false, false);
    public static final DocumentField TRIM_PARAMS_FWD_FIELD = new DocumentField("Trim Params (fwd)", "", "trimParamsFwd", String.class, false, false);
    public static final DocumentField TRIM_PARAMS_REV_FIELD = new DocumentField("Trim Params (rev)", "", "trimParamsRev", String.class, false, false);
    public static final DocumentField TECHNICIAN_FIELD =  new DocumentField("Technician", "", "technician", String.class, true, false);
    public static final DocumentField AMBIGUITIES_FIELD = new DocumentField("# Ambiguities", "", "ambiguities", Integer.class, true, false);
    public static final DocumentField LIMS_ID = new DocumentField("id", "", "lims_id", Integer.class, false, false);
    public static final DocumentField ASSEMBLY_PARAMS_FIELD = new DocumentField("Assembly Parameters", "", "assemblyParams", String.class, false, false);

    private AnnotateUtilities() {
    }

    /**
     * Annotates documents using a supplied {@link FimsDataGetter}.  In the case of a MySQL FIMS will also offer to
     * remove old FIMS fields.
     *
     * @param annotatedDocuments The documents to annotate
     * @param progressListener To report progress to
     * @param fimsDataGetter Used to retrieve FIMS data
     * @param askAboutOldFields True if we should ask the user if they want to remove old FIMS fields.  False to leave old fields
     *
     * @throws DocumentOperationException if we are unable to annotate for some reason
     */
    public static void annotateFimsData(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, FimsDataGetter fimsDataGetter, boolean askAboutOldFields) throws DocumentOperationException {
        Set<DocumentField> oldFields = new HashSet<DocumentField>();
        Set<DocumentField> newFields = new HashSet<DocumentField>();
        Set<AnnotatedPluginDocument> docsAnnotated = new HashSet<AnnotatedPluginDocument>();

        CompositeProgressListener realProgress = new CompositeProgressListener(progressListener, annotatedDocuments.length);
        realProgress.setMessage("Accessing FIMS...");
        List<String> failBlog = new ArrayList<String>();
        Set<String> noReferencesList = new LinkedHashSet<String>();
        for (AnnotatedPluginDocument annotatedDocument : annotatedDocuments) {
            if (realProgress.isCanceled() || !BiocodeService.getInstance().isLoggedIn()) {
                break;
            }
            realProgress.beginSubtask("Annotating " + annotatedDocument.getName());
            if (SequenceAlignmentDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
                Set<DocumentField> fieldsAdded = new HashSet<DocumentField>();
                SequenceAlignmentDocument alignment = (SequenceAlignmentDocument) annotatedDocument.getDocument();
                CompositeProgressListener progressForAlignment = new CompositeProgressListener(realProgress, alignment.getNumberOfSequences());
                for (int i = 0; i < alignment.getNumberOfSequences(); i ++)  {
                    progressForAlignment.beginSubtask();
                    AnnotatedPluginDocument referencedDocument = alignment.getReferencedDocument(i);
                    if (referencedDocument != null) {
                        docsAnnotated.add(referencedDocument);
                        fieldsAdded.addAll(annotateDocument(fimsDataGetter, failBlog, referencedDocument, true));
                    } else {
                        noReferencesList.add(alignment.getSequence(i).getName());
                    }
                }
                copyMatchingFieldsToContig(annotatedDocument, fieldsAdded);
                copyMatchingDocumentNotesToContig(annotatedDocument);
                newFields.addAll(fieldsAdded);
            } else {
                newFields.addAll(annotateDocument(fimsDataGetter, failBlog, annotatedDocument, true));
            }
            docsAnnotated.add(annotatedDocument);
        }

        for (AnnotatedPluginDocument annotatedDocument : docsAnnotated) {
            for (DocumentField field : annotatedDocument.getDisplayableFields()) {
                if(field.getCode().startsWith(MySQLFimsConnection.FIELD_PREFIX) || Pattern.matches("\\d+", field.getCode())) {
                    if(annotatedDocument.getFieldValue(field) != null) {
                        oldFields.add(field);
                    }
                }
            }
        }

        oldFields.removeAll(newFields);
        if(askAboutOldFields && !newFields.isEmpty() && !oldFields.isEmpty()) {
            Set<String> newNames = new HashSet<String>();
            for (DocumentField newField : newFields) {
                newNames.add(newField.getName());
            }
            Set<String> duplicateNames = new HashSet<String>();
            for (DocumentField oldField : oldFields) {
                if(newNames.contains(oldField.getName())) {
                    duplicateNames.add(oldField.getName());
                }
            }

            String remove = "Remove Fields";
            Dialogs.DialogOptions dialogOptions = new Dialogs.DialogOptions(new String[]{remove, "Keep Fields"},
                    "Old FIMS Fields Detected", null, Dialogs.DialogIcon.QUESTION);
            StringBuilder message = new StringBuilder("<html>Geneious has detected <strong>" + oldFields.size() +
                    "</strong> previously annotated FIMS fields that are not in your current FIMS. ");
            if(!duplicateNames.isEmpty()) {
                message.append("<strong>").append(duplicateNames.size()).append(
                        "</strong> of these have duplicate names with current FIMS fields. ");
            }

            message.append("Do you want to remove these fields?\n\n<strong>Fields</strong>:\n");
            for (DocumentField oldField : oldFields) {
                message.append(oldField.getName());
                if(duplicateNames.contains(oldField.getName())) {
                    message.append(" (duplicate)");
                }
                message.append("\n");
            }
            message.append("</html>");
            Object choice = Dialogs.showDialog(dialogOptions, message.toString());
            if(remove.equals(choice)) {
                for (AnnotatedPluginDocument annotatedDocument : docsAnnotated) {
                    boolean savePluginDocToo = false;
                    for (DocumentField oldField : oldFields) {
                        // The API doesn't let us remove the field from the document, but we can set the value to null.
                        annotatedDocument.setFieldValue(oldField, null);

                        if(annotatedDocument.getFieldValue(oldField) != null) {
                            // Due to a problem elsewhere, when generating a consensus, Geneious copies APD fields into
                            // the new plugin document.  And due to a limitation/bug in core you can't wipe out
                            // PluginDocument fields by using the APD.  So we have to load the PluginDocument and clear the
                            // field on it.
                            PluginDocument pluginDoc = annotatedDocument.getDocumentOrNull();
                            if(pluginDoc instanceof AbstractPluginDocument) {
                                savePluginDocToo = true;
                                ((AbstractPluginDocument) pluginDoc).setFieldValue(oldField, null);
                            } else {
                                // We can't load the doc or it isn't of a type we can edit
                            }
                        }
                    }
                    if(savePluginDocToo) {
                        annotatedDocument.saveDocument();
                    } else {
                        annotatedDocument.save();  // Means we end up saving the same doc twice.  However this should be an infrequent operation.
                    }
                }
            }
        }

        if (!failBlog.isEmpty()) {
            StringBuilder b = new StringBuilder("<html>");
            b.append("Tissue records could not be found for the following sequences (the wells may have been empty):<br><br>");
            for (String s : failBlog) {
                b.append(s).append("<br>");
            }
            b.append("</html>");
            throw new DocumentOperationException(b.toString());
        }
        if (!noReferencesList.isEmpty()) {
            StringBuilder b = new StringBuilder("<html>");
            b.append("The following contigs could not be annotated because they did not have reference documents:<br><br>");
            for (String s : noReferencesList) {
                b.append(s).append("<br>");
            }
            b.append("</html>");
            throw new DocumentOperationException(b.toString());
        }
    }

    /**
     * Code for Sequencing Primer note type defined in GenBank Submission plugin
     */
    private static final String SEQ_PRIMER_NOTE_TYPE = "sequencingPrimer";

    /**
     * Copies matching document notes from sequences referenced by an assembly to the assembly itself.  Only copies the
     * note if all field values are the same.  The only exception is the Sequencing Primer note type defined by the GenBank
     * submission plugin.  This gets merged so that any non-null field values are copied across if all sequences have the
     * same value.
     * <br><br>
     * <b>Note</b>: This method was originally written for the Moorea Biocode Project.  It is duplicated in the AssemblyOperation in
     * the Alignment Plugin.  Any changes to this method need to be made there too.
     *
     * @param annotatedContig The contig assembly to copy notes to from it's references
     * @throws DocumentOperationException if documents cannot be loaded or edited
     */
    private static void copyMatchingDocumentNotesToContig(AnnotatedPluginDocument annotatedContig) throws DocumentOperationException {
        SequenceAlignmentDocument contig = (SequenceAlignmentDocument) annotatedContig.getDocument();
        Map<String, DocumentNote> documentNotesToCopy = null;
        for (int i = 0; i < contig.getNumberOfSequences(); i++) {
            if (i == contig.getContigReferenceSequenceIndex()) {
                continue;
            }
            AnnotatedPluginDocument referencedDocument = contig.getReferencedDocument(i);
            if (referencedDocument == null) {
                return; //one sequence doesn't have a reference so bail on the whole thing
            }
            if (documentNotesToCopy == null) {
                documentNotesToCopy = new LinkedHashMap<String, DocumentNote>();
                AnnotatedPluginDocument.DocumentNotes documentNotes = referencedDocument.getDocumentNotes(false);
                for (DocumentNote note : documentNotes.getAllNotes()) {
                    documentNotesToCopy.put(note.getNoteTypeCode(), note);
                }
            } else {
                for (Map.Entry<String, DocumentNote> entry : new LinkedHashSet<Map.Entry<String, DocumentNote>>(documentNotesToCopy.entrySet())) {
                    DocumentNote note = referencedDocument.getDocumentNotes(false).getNote(entry.getKey());
                    if (!notesAreEqual(note, entry.getValue())) {
                        documentNotesToCopy.remove(entry.getKey());
                    }
                }
            }
        }

        //hack for the sequencing primer note
        List<Object> values = new ArrayList<Object>();
        for (int i = 0; i < contig.getNumberOfSequences(); i++) {
            if (i == contig.getContigReferenceSequenceIndex()) {
                continue;
            }
            if (documentNotesToCopy.get(SEQ_PRIMER_NOTE_TYPE) != null) {
                continue; //no need to do this if the note is already being copied...
            }
            AnnotatedPluginDocument referencedDocument = contig.getReferencedDocument(i);
            DocumentNote sequencingPrimerNote = referencedDocument.getDocumentNotes(false).getNote(SEQ_PRIMER_NOTE_TYPE);
            if (sequencingPrimerNote == null) {
                break;
            }
            for (int i1 = 0; i1 < sequencingPrimerNote.getFields().size(); i1++) {
                DocumentNoteField field = sequencingPrimerNote.getFields().get(i1);
                Object fieldValue = sequencingPrimerNote.getFieldValue(field.getCode());
                if (values.size() < i1 + 1) {
                    values.add(sequencingPrimerNote.getFieldValue(field.getCode()));
                }
                if (values.get(i1) != null && !values.get(i1).equals(fieldValue)) {
                    continue;
                }
                if (fieldValue != null) {
                    values.set(i1, fieldValue);
                }
            }
            if (i == contig.getNumberOfSequences() - 1) { //once we've been through everything
                DocumentNoteType noteType = DocumentNoteUtilities.getNoteType(SEQ_PRIMER_NOTE_TYPE);
                if (noteType != null) {
                    DocumentNote note = noteType.createDocumentNote();
                    for (int i1 = 0; i1 < note.getFields().size(); i1++) {
                        DocumentNoteField field = note.getFields().get(i1);
                        Object value = values.get(i1);
                        if (value != null) {
                            note.setFieldValue(field.getCode(), value);
                        }
                    }
                    documentNotesToCopy.put(noteType.getCode(), note);
                }
            }
        }

        if (documentNotesToCopy == null || documentNotesToCopy.isEmpty()) return;

        AnnotatedPluginDocument.DocumentNotes contigNotes = annotatedContig.getDocumentNotes(true);
        for (Map.Entry<String, DocumentNote> noteToCopy : documentNotesToCopy.entrySet()) {
            DocumentNote existingNote = noteToCopy.getValue();
            contigNotes.setNote(existingNote);
        }
        contigNotes.saveNotes();
    }

    private static boolean notesAreEqual(DocumentNote note1, DocumentNote note2) {
        if(note1 == null || note2 == null) {
            return false;
        }
        if(!note1.getNoteTypeCode().equals(note2.getNoteTypeCode())) {
            return false;
        }

        List<DocumentNoteField> fields1 = note1.getFields();

        for (DocumentNoteField fields : fields1) {
            Object value1 = note1.getFieldValue(fields.getCode());
            Object value2 = note2.getFieldValue(fields.getCode());
            if(!GeneralUtilities.safeEquals(value1, value2)) {
                return false;
            }
        }
        return true;
    }

    /**
     * This method is a copy of the method with the same name in
     * com.biomatters.plugins.alignment.assembly.AssemblyOperation and should be kept up to date with the original.
     *
     * @param annotatedContig
     * @throws com.biomatters.geneious.publicapi.plugin.DocumentOperationException
     *
     */
    private static void copyMatchingFieldsToContig(AnnotatedPluginDocument annotatedContig, Set<DocumentField> overridableFields) throws DocumentOperationException {
        SequenceAlignmentDocument contig = (SequenceAlignmentDocument)annotatedContig.getDocument();
        Map<DocumentField, Object> displayableFieldsToCopy = null;

        for (int i = 0; i < contig.getNumberOfSequences(); i++) {
            if (i == contig.getContigReferenceSequenceIndex()) {
                continue;
            }

            AnnotatedPluginDocument referencedDocument = contig.getReferencedDocument(i);

            if (referencedDocument == null) {
                return; //one sequence doesn't have a reference so bail on the whole thing
            }

            if (displayableFieldsToCopy == null) {
                displayableFieldsToCopy = new LinkedHashMap<DocumentField, Object>();
                for (DocumentField field : referencedDocument.getDisplayableFields()) {
//                    if (field.getCode().startsWith("biocode") || field.getCode().equalsIgnoreCase("tissueid")
//                            || field.getCode().equals(DocumentField.TAXONOMY_FIELD.getCode())
//                            || field.getCode().equals(DocumentField.ORGANISM_FIELD.getCode())
//                            || field.getCode().equals(DocumentField.COMMON_NAME_FIELD.getCode())) {
//                        displayableFieldsToCopy.put(field, referencedDocument.getFieldValue(field));
//                    }
                    if (!FIELDS_TO_NOT_COPY.contains(field.getCode())) {
                        displayableFieldsToCopy.put(field, referencedDocument.getFieldValue(field));
                    }
                }
            } else {
                for (Map.Entry<DocumentField, Object> fieldToCopy : displayableFieldsToCopy.entrySet()) {
                    Object value = referencedDocument.getFieldValue(fieldToCopy.getKey());
                    if (value == null || !value.equals(fieldToCopy.getValue())) {
                        displayableFieldsToCopy.remove(fieldToCopy.getKey());
                    }
                }
            }

            if (displayableFieldsToCopy == null || displayableFieldsToCopy.isEmpty()) {
                return;
            }
        }

        for (Map.Entry<DocumentField, Object> fieldAndValue : displayableFieldsToCopy.entrySet()) {
            DocumentField field = fieldAndValue.getKey();
            if (annotatedContig.getFieldValue(field) == null || overridableFields.contains(field)) {
                annotatedContig.setFieldValue(field, fieldAndValue.getValue());
            }
        }

        annotatedContig.save();
    }

    /**
     * Annotates a document with data from a {@link FimsDataGetter}
     *
     *
     * @param fimsDataGetter Used to get the FIMS fields and values
     * @param failBlog To add failure messages to, for example when there are no FIMs fields associated with the document
     * @param annotatedDocument The document to annotate
     * @param updateModifiedDate true to update the modified date when saving.  False to leave it as is.
     * @return The set of FIMS {@link DocumentField}s that were annotated onto the document
     * @throws DocumentOperationException
     */
    public static Set<DocumentField> annotateDocument(FimsDataGetter fimsDataGetter, List<String> failBlog, AnnotatedPluginDocument annotatedDocument, boolean updateModifiedDate) throws DocumentOperationException {
        FimsData fimsData = fimsDataGetter.getFimsData(annotatedDocument);

        if (fimsData == null || fimsData.fimsSample == null) {
            failBlog.add(annotatedDocument.getName());

            return Collections.emptySet();
        }

        HashSet<DocumentField> fields = new HashSet<DocumentField>();
        fields.addAll(fimsData.fimsSample.getFimsAttributes());
        fields.addAll(fimsData.fimsSample.getTaxonomyAttributes());

        for (DocumentField documentField : fimsData.fimsSample.getFimsAttributes()) {
            annotatedDocument.setFieldValue(documentField, fimsData.fimsSample.getFimsAttributeValue(documentField.getCode()));
        }

        if (fimsData.sequencingPlateName != null) {
            annotatedDocument.setFieldValue(BiocodeUtilities.SEQUENCING_PLATE_FIELD, fimsData.sequencingPlateName);
        }

        if (fimsData.reactionStatus != null) {
            annotatedDocument.setFieldValue(BiocodeUtilities.REACTION_STATUS_FIELD, fimsData.reactionStatus);
        }

        if (fimsData.sequencingPlateName != null && fimsData.well != null) {
            annotatedDocument.setFieldValue(BiocodeUtilities.SEQUENCING_WELL_FIELD, fimsData.well.toString());
            annotatedDocument.setFieldValue(BiocodeUtilities.TRACE_ID_FIELD, fimsData.sequencingPlateName + "." + fimsData.well.toString());
        }

        if (fimsData.workflow != null) {
            annotatedDocument.setFieldValue(BiocodeUtilities.WORKFLOW_NAME_FIELD, fimsData.workflow.getName());
        }

        if (fimsData.extractionId != null) {
            annotatedDocument.setFieldValue(LIMSConnection.EXTRACTION_ID_FIELD, fimsData.extractionId);
        }

        if (fimsData.extractionBarcode != null && fimsData.extractionBarcode.length() > 0) {
            annotatedDocument.setFieldValue(BiocodeUtilities.EXTRACTION_BARCODE_FIELD, fimsData.extractionBarcode);
        }

        final String TAXONOMY_FIELD_INTRA_SEPARATOR = "; ";
        final String ORGANISM_FIELD_INTRA_SEPARATOR = " ";
        StringBuilder taxonomyFieldValuesBuilder = new StringBuilder();
        StringBuilder organismBuilder = new StringBuilder();

        for (DocumentField documentField : fimsData.fimsSample.getTaxonomyAttributes()) {
            String documentFieldName = documentField.getName();

            Object taxon = fimsData.fimsSample.getFimsAttributeValue(documentField.getCode());

            if (taxon == null) {
                continue;
            }

            if (taxon != null && !(taxon instanceof String)) {
                throw new DocumentOperationException("The tissue record " + fimsData.fimsSample.getId() + " has an invalid taxon value (" + taxon + ") for the taxon field " + documentField.getName());
            }


            String taxonAsString = String.valueOf(taxon);

            if (taxonAsString.isEmpty()) {
                continue;
            }

            annotatedDocument.setFieldValue(new DocumentField(documentFieldName, documentField.getDescription(), documentField.getCode(), documentField.getValueType(), false, false), fimsData.fimsSample.getFimsAttributeValue(documentField.getCode()));

            if (organismBuilder.length() == 0) {
                if (documentFieldName.equalsIgnoreCase("genus")) {
                    organismBuilder.append(taxonAsString);
                }

                if (taxonomyFieldValuesBuilder.length() != 0) {
                    taxonomyFieldValuesBuilder.append(TAXONOMY_FIELD_INTRA_SEPARATOR);
                }

                taxonomyFieldValuesBuilder.append(taxonAsString);
            } else {
                organismBuilder.append(ORGANISM_FIELD_INTRA_SEPARATOR).append(taxonAsString);
            }
        }

        String taxonomy = taxonomyFieldValuesBuilder.length() == 0 ? null : taxonomyFieldValuesBuilder.toString();
        annotatedDocument.setFieldValue(DocumentField.TAXONOMY_FIELD, taxonomy);

        String organism = organismBuilder.length() == 0 ? null : organismBuilder.toString();
        annotatedDocument.setFieldValue(DocumentField.ORGANISM_FIELD, organism);

        //annotate the primers...
        AnnotatedPluginDocument.DocumentNotes notes = annotatedDocument.getDocumentNotes(true);
        DocumentNote sequencingPrimerNote = notes.getNote(SEQ_PRIMER_NOTE_TYPE);
        if (sequencingPrimerNote == null) {
            DocumentNoteType sequencingPrimerType = DocumentNoteUtilities.getNoteType(SEQ_PRIMER_NOTE_TYPE);
            if (sequencingPrimerType != null) {
                sequencingPrimerNote = sequencingPrimerType.createDocumentNote();
            }
        }
        boolean savedDocument = false;
        if (sequencingPrimerNote != null && fimsData.workflow != null && fimsData.workflow.getMostRecentReaction(Reaction.Type.PCR) != null) {
            Reaction pcrReaction = fimsData.workflow.getMostRecentReaction(Reaction.Type.PCR);
            Boolean directionForTrace = getDirectionForTrace(annotatedDocument);

            AnnotatedPluginDocument forwardPrimer = getPrimer(pcrReaction, PCROptions.PRIMER_OPTION_ID);
            AnnotatedPluginDocument reversePrimer = getPrimer(pcrReaction, PCROptions.PRIMER_REVERSE_OPTION_ID);

            if (forwardPrimer != null && (directionForTrace == null || directionForTrace)) {
                sequencingPrimerNote.setFieldValue("fwd_primer_name", forwardPrimer.getName());
                OligoSequenceDocument sequence = (OligoSequenceDocument) forwardPrimer.getDocument();
                sequencingPrimerNote.setFieldValue("fwd_primer_seq", sequence.getBindingSequence().toString());
            }
            if (reversePrimer != null && (directionForTrace == null || !directionForTrace)) {
                sequencingPrimerNote.setFieldValue("rev_primer_name", reversePrimer.getName());
                OligoSequenceDocument sequence = (OligoSequenceDocument) reversePrimer.getDocument();
                sequencingPrimerNote.setFieldValue("rev_primer_seq", sequence.getBindingSequence().toString());
            }
            notes.setNote(sequencingPrimerNote);
            notes.saveNotes();
            savedDocument = true;
        }
        if(!savedDocument) {
            annotatedDocument.save(updateModifiedDate);
        }
        return fields;
    }

    /**
     *
     * @param annotatedDocument The document to get the direction for
     * @return The direction of a trace or null if the annotatedDocument is not a trace or has not had the direction set.
     * @throws DocumentOperationException if there is a problem loading the document
     */
    static Boolean getDirectionForTrace(AnnotatedPluginDocument annotatedDocument) throws DocumentOperationException {
        Boolean directionForTrace = null;
        if(NucleotideGraphSequenceDocument.class.isAssignableFrom(annotatedDocument.getDocumentClass())) {
            NucleotideGraphSequenceDocument graphSeq = (NucleotideGraphSequenceDocument) annotatedDocument.getDocument();
            if(graphSeq.getChromatogramLength() > 0) {
                Object isForwardString = annotatedDocument.getFieldValue(WorkflowBuilder.IS_FORWARD_FIELD.getCode());
                directionForTrace = isForwardString == null ? null : Boolean.valueOf(isForwardString.toString());
            }
        }
        return directionForTrace;
    }

    private static AnnotatedPluginDocument getPrimer(Reaction pcrReaction, String optionKey) {
        AnnotatedPluginDocument forwardPrimer = null;
        DocumentSelectionOption option = (DocumentSelectionOption)pcrReaction.getOptions().getOption(optionKey);
        List<AnnotatedPluginDocument> value = option.getDocuments();
        if (value.size() > 0) {
            forwardPrimer = value.get(0);
        }
        return forwardPrimer;
    }

    static List<Options.OptionValue> getOptionValuesForFimsFields() {
        List<DocumentField> fields = BiocodeService.getInstance().getActiveFIMSConnection().getSearchAttributes();
        List<Options.OptionValue> values = new ArrayList<Options.OptionValue>();
        for(DocumentField field : fields) {
            values.add(new Options.OptionValue(field.getCode(), field.getName(), field.getDescription()));
        }
        return values;
    }

    static DocumentField getDocumentFieldForOptionValue(Options.OptionValue optionValue) {
        for (DocumentField candidate : BiocodeService.getInstance().getActiveFIMSConnection().getSearchAttributes()) {
            if(candidate.getCode().equals(optionValue.getName())) {
                return candidate;
            }
        }
        return null;
    }
}