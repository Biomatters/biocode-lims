package com.biomatters.plugins.moorea.labbench;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.PluginDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.plugins.moorea.MooreaPlugin;
import com.biomatters.plugins.moorea.MooreaUtilities;
import com.biomatters.plugins.moorea.labbench.plates.Plate;
import com.biomatters.plugins.moorea.labbench.plates.PlateViewer;
import com.biomatters.plugins.moorea.labbench.reaction.Reaction;
import jebl.util.ProgressListener;

import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 4/06/2009 4:53:15 PM
 */
public class NewPlateDocumentOperation extends DocumentOperation {
    public GeneiousActionOptions getActionOptions() {
        return new GeneiousActionOptions("New Reaction", "Create a new reaction (or plate of reactions)", MooreaPlugin.getIcons("newReaction_24.png")).setInMainToolbar(true, 0.533).setProOnly(true);
    }

    public String getHelp() {
        return null;
    }

    public DocumentSelectionSignature[] getSelectionSignatures() {
        return new DocumentSelectionSignature[]{
                new DocumentSelectionSignature(new DocumentSelectionSignature.DocumentSelectionSignatureAtom[0]),
                new DocumentSelectionSignature(PluginDocument.class,1,Integer.MAX_VALUE)
        };
    }

    @Override
     public boolean isDocumentGenerator() {
        return false;
    }

    @Override
    public Options getOptions(AnnotatedPluginDocument... documents) throws DocumentOperationException {
        if(!MooreaLabBenchService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(MooreaUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }
        
        Options options = new Options(this.getClass());
        Options.OptionValue[] plateValues = new Options.OptionValue[] {
                new Options.OptionValue("individualReactions", "Individual Reactions"),
                new Options.OptionValue("48Plate", "48 well plate"),
                new Options.OptionValue("96Plate", "96 well plate"),
                new Options.OptionValue("384Plate", "384 well plate")
        };

        Options.OptionValue[] typeValues = new Options.OptionValue[] {
                new Options.OptionValue("extraction", "Extraction"),
                new Options.OptionValue("pcr", "PCR"),
                new Options.OptionValue("cyclesequencing", "Cycle Sequencing")
        };

        options.addComboBoxOption("reactionType", "Type of reaction", typeValues, typeValues[0]);
        Options.RadioOption<Options.OptionValue> plateOption = options.addRadioOption("plateType", "", plateValues, plateValues[2], Options.Alignment.VERTICAL_ALIGN);

        Options.IntegerOption reactionNumber = options.addIntegerOption("reactionNumber", "Number of reactions", 1, 1, 26);

        plateOption.addDependent(plateValues[0], reactionNumber, true);


        return options;
    }

    @Override
    public List<AnnotatedPluginDocument> performOperation(AnnotatedPluginDocument[] annotatedDocuments, ProgressListener progressListener, Options options) throws DocumentOperationException {
        if(!MooreaLabBenchService.getInstance().isLoggedIn()) {
            throw new DocumentOperationException(MooreaUtilities.NOT_CONNECTED_ERROR_MESSAGE);
        }

        Plate.Size size = null;
        Options.OptionValue plateSize = (Options.OptionValue)options.getValue("plateType");
        Options.OptionValue reactionType = (Options.OptionValue)options.getValue("reactionType");

        Reaction.Type type = null;
        if(reactionType.getName().equals("extraction")) {
            type = Reaction.Type.Extraction;
        }
        else if(reactionType.getName().equals("pcr")) {
            type = Reaction.Type.PCR;
        }
        else if(reactionType.getName().equals("cyclesequencing")) {
            type = Reaction.Type.CycleSequencing;
        }

        if(plateSize.getName().equals("48Plate")) {
            size = Plate.Size.w48;
        }
        else if(plateSize.getName().equals("96Plate")) {
            size = Plate.Size.w96;
        }
        else if(plateSize.getName().equals("384Plate")) {
            size = Plate.Size.w384;
        }

        PlateViewer plateViewer;
        if(size != null) {
            plateViewer = new PlateViewer(size, type);
        }
        else {
            plateViewer = new PlateViewer((Integer)options.getValue("reactionNumber"), type);
        }
        plateViewer.displayInFrame(true, GuiUtilities.getMainFrame());

        return null;
    }
}
