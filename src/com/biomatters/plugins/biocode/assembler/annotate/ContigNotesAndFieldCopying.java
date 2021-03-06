package com.biomatters.plugins.biocode.assembler.annotate;

import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentOperationException;
import com.biomatters.geneious.publicapi.utilities.GeneralUtilities;
import com.google.common.collect.HashMultimap;

import java.util.*;

/**
 * <b>Note</b>: This class was originally written for the Moorea Biocode Project.  It is duplicated from ContigNotesAndFieldCopying in
 * the Biocode Plugin.  Any changes to this class need to be made to Geneious too.
 */
public class ContigNotesAndFieldCopying {
    /**
     * Code for Sequencing Primer note type defined in GenBank Submission plugin
     */
    static final String SEQ_PRIMER_NOTE_TYPE = "sequencingPrimer";

    @SuppressWarnings("unused")
    public static void copyMatchingFieldsAndNotesToContig(AnnotatedPluginDocument annotatedContig, List<AnnotatedPluginDocument> documentsContainingReads) throws DocumentOperationException {
        copyMatchingFieldsToContig(annotatedContig, documentsContainingReads, Collections.<String>emptySet());
        copyMatchingDocumentNotesToContig(annotatedContig, documentsContainingReads);
    }

    /**
     * <b>Note</b>: This whole class is duplicated. Any changes made must be made in the duplicate too. See note at top of this file

     * @return all documents referenced by this contig, excluding the contig reference sequence. If any sequence does not have a reference, an empty list is returned.
     */
    public static List<AnnotatedPluginDocument> getDocumentsContainingReads(AnnotatedPluginDocument annotatedContig) throws DocumentOperationException {
        Set<AnnotatedPluginDocument> documentsContainingReads = new LinkedHashSet<AnnotatedPluginDocument>();
        SequenceAlignmentDocument contig = (SequenceAlignmentDocument) annotatedContig.getDocument();
        for (int i = 0; i < contig.getNumberOfSequences(); i ++) {
            if (i == contig.getContigReferenceSequenceIndex()) {
                continue;
            }
            AnnotatedPluginDocument referencedDocument = contig.getReferencedDocument(i);
            if (referencedDocument == null) {
                return Collections.emptyList(); //one sequence doesn't have a reference so bail on the whole thing
            }
            documentsContainingReads.add(referencedDocument);
        }
        return new ArrayList<AnnotatedPluginDocument>(documentsContainingReads);
    }

    /**
     * Copies matching document notes from sequences referenced by an assembly to the assembly itself.  Only copies the
     * note if all values are the same.  The only exception is the Sequencing Primer note type defined by the GenBank
     * submission plugin that gets merged so that any non-null field values are copied across if all sequences have the
     * same value.
     * <br><br>
     * <b>Note</b>: This whole class is duplicated. Any changes made must be made in the duplicate too. See note at top of this file
     *
     * @param annotatedContig The contig assembly to copy notes to from it's references
     * @throws DocumentOperationException if documents cannot be loaded or edited
     */
    @SuppressWarnings("WeakerAccess")
    public static void copyMatchingDocumentNotesToContig(AnnotatedPluginDocument annotatedContig, List<AnnotatedPluginDocument> documentsContainingReads) throws DocumentOperationException {
        Map<String, DocumentNote> documentNotesToCopy = null;
        for (AnnotatedPluginDocument referencedDocument : documentsContainingReads) {

            if (documentNotesToCopy == null) {
                documentNotesToCopy = new LinkedHashMap<String, DocumentNote>();
                AnnotatedPluginDocument.DocumentNotes documentNotes = referencedDocument.getDocumentNotes(false);
                for (DocumentNote note : documentNotes.getAllNotes()) {
                    documentNotesToCopy.put(note.getNoteTypeCode(), note);
                }
            } else {
                for (Map.Entry<String, DocumentNote> entry : new LinkedHashSet<Map.Entry<String, DocumentNote>>(documentNotesToCopy.entrySet())) {
                    DocumentNote note = referencedDocument.getDocumentNotes(false).getNote(entry.getKey());
                    if(!notesAreEqual(note, entry.getValue())) {
                        documentNotesToCopy.remove(entry.getKey());
                    }
                }
            }
        }
        if(documentNotesToCopy == null) {
            return;  // Contig had no sequences to copy from
        }

        //noinspection StatementWithEmptyBody
        if(documentNotesToCopy.get(SEQ_PRIMER_NOTE_TYPE) == null) {
            DocumentNote sequencingPrimerNote = hackToGetSequencingPrimerNoteToCopy(documentsContainingReads);
            if(sequencingPrimerNote != null) {
                documentNotesToCopy.put(sequencingPrimerNote.getNoteTypeCode(), sequencingPrimerNote);
            }
        } else {
            //no need to do this if the note is already being copied...
        }

        if (documentNotesToCopy.isEmpty()) return;

        AnnotatedPluginDocument.DocumentNotes contigNotes = annotatedContig.getDocumentNotes(true);
        for (Map.Entry<String, DocumentNote> noteToCopy : documentNotesToCopy.entrySet()) {
            DocumentNote existingNote = noteToCopy.getValue();
            contigNotes.setNote(existingNote);
        }
        contigNotes.saveNotes();


    }

    /**
     * <b>Note</b>: This whole class is duplicated. Any changes made must be made in the duplicate too. See note at top of this file
     */
    private static DocumentNote hackToGetSequencingPrimerNoteToCopy(List<AnnotatedPluginDocument> documentsContainingReads) {
        DocumentNoteType sequencingPrimerNoteType = DocumentNoteUtilities.getNoteType(SEQ_PRIMER_NOTE_TYPE);
        if(sequencingPrimerNoteType == null) {
            return null;  // GenBank Submission plugin not initialized
        }

        HashMultimap<String, Object> seenFieldValues = HashMultimap.create();
        for (AnnotatedPluginDocument refDoc : documentsContainingReads) {
            if(refDoc == null) {
                continue;
            }

            DocumentNote noteOnRef = refDoc.getDocumentNotes(false).getNote(SEQ_PRIMER_NOTE_TYPE);
            if(noteOnRef == null) {
                continue;
            }
            for (DocumentNoteField field : sequencingPrimerNoteType.getFields()) {
                Object value = noteOnRef.getFieldValue(field.getCode());
                if(value != null) {
                    seenFieldValues.put(field.getCode(), value);
                }
            }
        }
        if(seenFieldValues.isEmpty()) {
            return null;
        }

        DocumentNote newNote = sequencingPrimerNoteType.createDocumentNote();
        for (Map.Entry<String, Collection<Object>> entry : seenFieldValues.asMap().entrySet()) {
            Collection<Object> valuesForField = entry.getValue();
            if(valuesForField.size() == 1) {
                newNote.setFieldValue(entry.getKey(), valuesForField.iterator().next());
            }
        }
        return newNote;
    }

    /**
     * <b>Note</b>: This whole class is duplicated. Any changes made must be made in the duplicate too. See note at top of this file
     */
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
     * Attempts to copy across all field values that are equal in all referenced documents to the document that
     * references them.  No existing field values on the document will be overridden unless specified.
     * <br><br>
     * <b>Note</b>: This whole class is duplicated. Any changes made must be made in the duplicate too. See note at top of this file
     *
     * @param annotatedContig The document to copy fields to from the documents it references
     * @param codesOfOverridableFields A set of {@link com.biomatters.geneious.publicapi.documents.DocumentField} codes
     *                                 for which values will be copied across regardless of if the contig already has a
     *                                 value for that field.
     * @throws DocumentOperationException if there is a problem loading or saving the document
     */
    @SuppressWarnings("WeakerAccess")
    public static void copyMatchingFieldsToContig(AnnotatedPluginDocument annotatedContig , List<AnnotatedPluginDocument> sourceDocuments, Set<String> codesOfOverridableFields) throws DocumentOperationException {
        Map<DocumentField, Object> displayableFieldsToCopy = null;

        for (AnnotatedPluginDocument referencedDocument : sourceDocuments) {
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
                for (Map.Entry<DocumentField, Object> fieldToCopy : new LinkedHashSet<Map.Entry<DocumentField, Object>>(displayableFieldsToCopy.entrySet())) {
                    Object value = referencedDocument.getFieldValue(fieldToCopy.getKey());
                    if (value == null || !value.equals(fieldToCopy.getValue())) {
                        displayableFieldsToCopy.remove(fieldToCopy.getKey());
                    }
                }
            }

        }
        if (displayableFieldsToCopy == null || displayableFieldsToCopy.isEmpty()) {
            return;
        }

        for (Map.Entry<DocumentField, Object> fieldAndValue : displayableFieldsToCopy.entrySet()) {
            DocumentField field = fieldAndValue.getKey();
            if (annotatedContig.getFieldValue(field) == null || codesOfOverridableFields.contains(field.getCode())) {
                annotatedContig.setFieldValue(field, fieldAndValue.getValue());
            }
        }

        annotatedContig.save();
    }

    /**
     * <b>Note</b>: This whole class is duplicated. Any changes made must be made in the duplicate too. See note at top of this file
     */
    @SuppressWarnings("WeakerAccess")
    public static final List<String> FIELDS_TO_NOT_COPY = Arrays.asList(
            DocumentField.AMBIGUITIES.getCode(),
            DocumentField.BIN.getCode(),
            DocumentField.BIN_REASON.getCode(),
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
            DocumentField.MINIMUM_SEQUENCE_LENGTH.getCode(),
            DocumentField.MAXIMUM_SEQUENCE_LENGTH.getCode(),
            DocumentField.NUCLEOTIDES_COUNT.getCode(),
            PluginDocument.MODIFIED_DATE_FIELD.getCode(),
            "document_size",
            DocumentField.SEQUENCE_COUNT.getCode()
    );
}
