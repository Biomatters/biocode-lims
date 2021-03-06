package com.biomatters.plugins.biocode.assembler.verify;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseService;
import com.biomatters.geneious.publicapi.databaseservice.SequenceDatabaseSuperService;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAlignmentDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceDocument;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.DefaultNucleotideSequence;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.BiocodeUtilities;
import jebl.util.ProgressListener;
import org.virion.jam.util.SimpleListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Richard
 */
public class VerifyTaxonomyOptions extends Options {

    private final ComboBoxOption<DatabaseOptionValue> databaseOption;
    private final ComboBoxOption<ProgramOptionValue> programOption;
    private Options currentProgramOptions = null;

    public VerifyTaxonomyOptions(AnnotatedPluginDocument[] documents) throws DocumentOperationException {

        //check the integrity of the documents
        for(AnnotatedPluginDocument doc : documents) {
            if(SequenceAlignmentDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                SequenceAlignmentDocument alignment = (SequenceAlignmentDocument)doc.getDocument();
                if(!alignment.isContig()) {
                    for(int i=0; i < alignment.getNumberOfSequences(); i++) {
                        if(i == alignment.getContigReferenceSequenceIndex()) {
                            continue;
                        }
                        AnnotatedPluginDocument referencedDocument = alignment.getReferencedDocument(i);
                        if(referencedDocument == null) {
                            throw new DocumentOperationException("Your alignment needs to have reference sequences to work with verify taxonomy");
                        }
                        if(referencedDocument.getFieldValue(DocumentField.TAXONOMY_FIELD) == null) {
                            throw new DocumentOperationException("Your referenced sequence for '"+alignment.getSequence(i).getName()+"' does not have any taxonomy annotated.  You need to have a valid taxonomy field on your document.");
                        }
                    }
                }
                else if(SequenceDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                    if(doc.getFieldValue(DocumentField.TAXONOMY_FIELD) == null) {
                        throw new DocumentOperationException("Your sequence '"+doc.getName()+"' does not have any taxonomy annotated.  You need to have a valid taxonomy field on your document.");
                    }
                }
                else if(SequenceListDocument.class.isAssignableFrom(doc.getDocumentClass())) {
                    //just test the first ten of each type - can't load the whole lot
                    SequenceListDocument sequenceList = (SequenceListDocument)doc.getDocument();
                    for(int i=0; i < Math.min(10, sequenceList.getNucleotideSequences().size()); i++) {
                        if(sequenceList.getNucleotideSequences().get(i).getFieldValue(DocumentField.TAXONOMY_FIELD.getCode()) == null) {
                            throw new DocumentOperationException("Your sequence '"+sequenceList.getNucleotideSequences().get(i).getName()+"' does not have any taxonomy annotated.  You need to have a valid taxonomy field on your document.");
                        }
                    }
                    for(int i=0; i < Math.min(10, sequenceList.getAminoAcidSequences().size()); i++) {
                        if(sequenceList.getAminoAcidSequences().get(i).getFieldValue(DocumentField.TAXONOMY_FIELD.getCode()) == null) {
                            throw new DocumentOperationException("Your sequence '"+sequenceList.getNucleotideSequences().get(i).getName()+"' does not have any taxonomy annotated.  You need to have a valid taxonomy field on your document.");
                        }
                    }
                }
            }
            
        }

        GeneiousService blastSuperService = PluginUtilities.getGeneiousService("NCBI_BLAST");
        if (!(blastSuperService instanceof SequenceDatabaseSuperService)) {
            throw new DocumentOperationException("Could not find the NCBI BLAST service. Please make sure the NCBI plugin is installed and enabled.");
        }
        List<DatabaseOptionValue> databaseOptionValues = new ArrayList<DatabaseOptionValue>();
        List<ProgramOptionValue> programOptionValues = new ArrayList<ProgramOptionValue>();
        DatabaseOptionValue defaultDatabase = null;
        Map<String, Options> childOptionsToAdd = new HashMap<String, Options>();
        for (GeneiousService blastService : blastSuperService.getChildServices()) {
            if (!(blastService instanceof DatabaseService) || ((DatabaseService)blastService).getSequenceSearchPrograms(DatabaseService.SequenceSearchQueryType.NUCLEOTIDE).isEmpty()) {
                continue;
            }
            DatabaseService databaseService = (DatabaseService) blastService;
            DatabaseOptionValue databaseOptionValue = new DatabaseOptionValue(databaseService);
            Map<String, String> programs = databaseService.getSequenceSearchPrograms(DatabaseService.SequenceSearchQueryType.NUCLEOTIDE);
            boolean validProgramExists = false;
            for (Map.Entry<String, String> programEntry : programs.entrySet()) {
                String programCode = programEntry.getKey();
                if (programCode.contains("blastx")) continue;
                validProgramExists = true;
                ProgramOptionValue programOptionValue = new ProgramOptionValue(programCode, programEntry.getValue(), databaseService.getSequenceSearchOptions(programCode));
                if (!programOptionValues.contains(programOptionValue)) {
                    programOptionValues.add(programOptionValue);
                    if (programOptionValue.options != null) {
                        childOptionsToAdd.put(programCode, programOptionValue.options);
                    }
                }
            }
            if (!validProgramExists) continue;
            if (databaseService.getUniqueID().equals("NCBI_nr.nt")) defaultDatabase = databaseOptionValue;
            databaseOptionValues.add(databaseOptionValue);
        }
        if (defaultDatabase == null) defaultDatabase = databaseOptionValues.get(0);
        databaseOption = addComboBoxOption("database", "Database:", databaseOptionValues, defaultDatabase);
        programOption = addComboBoxOption("program", "Program:", programOptionValues, programOptionValues.get(0));
        for (Map.Entry<String, Options> childOptionsEntry : childOptionsToAdd.entrySet()) {
            Options options = childOptionsEntry.getValue();
            options.setVisible(false);
            Option maxHitsOption = options.getOption("maxHits");
            if(maxHitsOption != null) {
                //noinspection unchecked
                maxHitsOption.setDefaultValue(5);
                maxHitsOption.setDescription("<html>The maximum number of hits that will be returned for each query.<br><br>" +
                        "<b>Note:</b> This option is different to <i>Max Target Sequences</i> provided on NCBI Blast.<br>" +
                        "Geneious runs the BLAST search with <i>Max Target Sequences</i> = 1000 and<br>" +
                        "returns the number of best hits specified by setting HITLIST_SIZE=<i>Maximum Hits</i>.</html>");
                maxHitsOption.setVisible(true);
            }
            Option hitAnnosOption = options.getOption("getHitAnnos");
            if(hitAnnosOption != null) {
                //noinspection unchecked
                hitAnnosOption.setDefaultValue(true);
            }
            addChildOptions(childOptionsEntry.getKey(), "", null, options);
        }
        SimpleListener programListener = new SimpleListener() {
            public void objectChanged() {
                Options newOptions = programOption.getValue().options;
                if (currentProgramOptions == newOptions) {
                    return;
                }
                if (currentProgramOptions != null) {
                    currentProgramOptions.setVisible(false);
                }
                if (newOptions != null) {
                    newOptions.setVisible(true);
                }
                currentProgramOptions = newOptions;
            }
        };
        programOption.addChangeListener(programListener);
        programListener.objectChanged();

        boolean needConsensus = false;
        Map<AnnotatedPluginDocument, String> sequencesToBlast = VerifyTaxonomyOperation.getSequencesToBlast(documents);
        for (Map.Entry<AnnotatedPluginDocument, String> entry : sequencesToBlast.entrySet()) {
            if (entry.getValue() == null) {
                needConsensus = true;
                break;
            }
        }
        if (needConsensus) {
            Options consensusOptions = BiocodeUtilities.getConsensusOptions(documents);
            if (consensusOptions == null) {
                throw new DocumentOperationException("The consensus plugin must be installed to be able to add assemblies to LIMS");
            }
            consensusOptions.setValue("removeGaps", true);
            addChildOptions("consensus", "Consensus", null, consensusOptions);
        }
    }

    public List<AnnotatedPluginDocument> getQueries(Map<AnnotatedPluginDocument, String> contigMap) throws DocumentOperationException {
        List<AnnotatedPluginDocument> queries = new ArrayList<AnnotatedPluginDocument>();
        Options consensusOptions = getChildOptions().get("consensus");
        DocumentOperation consensusOperation = PluginUtilities.getDocumentOperation("Generate_Consensus");

        for (Map.Entry<AnnotatedPluginDocument, String> contigEntry : contigMap.entrySet()) {
            if (contigEntry.getValue() != null) {
                queries.add(DocumentUtilities.createAnnotatedPluginDocument(new DefaultNucleotideSequence(contigEntry.getKey().getName(), contigEntry.getValue())));
            } else {
                AnnotatedPluginDocument query = consensusOperation.performOperation(new AnnotatedPluginDocument[]{contigEntry.getKey()}, ProgressListener.EMPTY, consensusOptions).get(0);
                query.setName(contigEntry.getKey().getName());
                queries.add(query);
            }
        }
        return queries;
    }

    public String getKeywords() {
        return "COI";
    }

    public DatabaseService getDatabase() {
        return databaseOption.getValue().database;
    }

    public String getProgram() {
        return programOption.getValue().getName();
    }

    public Options getSearchOptions() {
        return programOption.getValue().options;
    }

    private static final class DatabaseOptionValue extends OptionValue {

        final DatabaseService database;

        private DatabaseOptionValue(DatabaseService database) {
            super(database.getUniqueID(), database.getName());
            this.database = database;
        }
    }

    private static final class ProgramOptionValue extends OptionValue {

        final Options options;

        private ProgramOptionValue(String name, String label, Options options) {
            super(name, label);
            this.options = options;
            if(options != null && options.getOption("getHitAnnos") != null) {
                Option option = options.getOption("getHitAnnos");
                option.setEnabled(false);
                option.setDisabledValue(true);
                option.setVisible(false);
            }
        }
    }
}
