package com.biomatters.plugins.biocode;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.reporting.ListOption;
import jebl.util.ProgressListener;

import java.util.Collections;
import java.util.List;

/**
 *
 * @author Gen Li
 *         Created on 13/04/15 2:36 PM
 */
public class ProjectAssignmentOperation extends DocumentOperation {
    private static final String TITLE = "Assign/unassign workflows to/from projects";
    @Override
    public GeneiousActionOptions getActionOptions() {
        GeneiousActionOptions geneiousActionOptions = new GeneiousActionOptions(TITLE).setInPopupMenu(true, 1);
        return GeneiousActionOptions.createSubmenuActionOptions(BiocodePlugin.getSuperBiocodeAction(), geneiousActionOptions);
    }

    @Override
    public String getHelp() {
        return null;
    }

    @Override
    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[] { new DocumentSelectionSignature(Object.class, 0, 0) };
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if (!BiocodeService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(BiocodeUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }

        Options options1 = new Options(getClass());

        Options.OptionValue noneOptionValue = new Options.OptionValue("name", "label", "description", true);

        options1.addCustomOption(new ListOption("name", "label", Collections.singletonList(noneOptionValue), Collections.singletonList(noneOptionValue)));

        return options1;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        return null;
    }
}
