package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.ProgressFrame;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.DocumentSelectionOption;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.FileUtilities;
import com.biomatters.geneious.publicapi.utilities.GuiUtilities;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.TextAreaOption;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;

/**
 * @author Steven Stones-Havas
 *          <p/>
 *          Created on 24/06/2009 7:35:20 PM
 */
public class CycleSequencingOptions extends ReactionOptions<CycleSequencingReaction> {
    public static void main(String[] args) {
        try{
          String url="http://genesetdb.auckland.ac.nz/HyperTestRemote.php?genelist=foxl1,foxl2&id=symbol&db=All&fdr=0.05";
          URL myURL=new URL(url);
          ReadableByteChannel rbc= Channels.newChannel(myURL.openStream());
            File tempFile = File.createTempFile("geneset", ".txt");
            FileOutputStream fos=new FileOutputStream(tempFile);
          fos.getChannel().transferFrom(rbc, 0, 1 << 24);
          fos.flush();
            fos.close();
            System.out.println(FileUtilities.getTextFromFile(tempFile));
        } catch (MalformedURLException e) {
          e.printStackTrace();
        } catch (IOException e) {
          e.printStackTrace();
        }
    }

    private ButtonOption cocktailButton;
    private Option<String, ? extends JComponent> labelOption;
    private com.biomatters.plugins.biocode.labbench.ButtonOption tracesButton;

    static final String SEQ_RESULTS_BUTTON_NAME = "viewSeqResults";
    public static final String PRIMER_OPTION_ID = "primer";
    static final String COCKTAIL_BUTTON_ID = "cocktailEdit";
    static final String LABEL_OPTION_ID = "label";
    static final String TRACES_BUTTON_ID = "traces";
    static final String ADD_PRIMER_TO_LOCAL_ID = "addPrimers";
    private ButtonOption addPrimersButton;


    public static final String FORWARD_VALUE = "forward";
    public static final String REVERSE_VALUE = "reverse";
    public static final String DIRECTION = "direction";

    public CycleSequencingOptions(Class c) {
        super(c);
        init();
        initListeners();
    }

    public CycleSequencingOptions(Element e) throws XMLSerializationException {
        super(e);
        initListeners();
    }

    public boolean fieldIsFinal(String fieldCode) {
        return "extractionId".equals(fieldCode) || WORKFLOW_ID.equals(fieldCode) || "locus".equals(fieldCode);
    }

    public void refreshValuesFromCaches() {
        //noinspection unchecked
        final ComboBoxOption<OptionValue> cocktailsOption = (ComboBoxOption<OptionValue>)getOption(COCKTAIL_OPTION_ID);
        cocktailsOption.setPossibleValues(getCocktails());
    }

    public Cocktail getCocktail() {
        List<? extends Cocktail> cocktailList = Cocktail.getAllCocktailsOfType(Reaction.Type.CycleSequencing);
        Option cocktailOption = getOption(COCKTAIL_OPTION_ID);
        OptionValue cocktailValue = (OptionValue)cocktailOption.getValue();

        int cocktailId = Integer.parseInt(cocktailValue.getName());

        for(Cocktail cocktail : cocktailList) {
            if(cocktail.getId() == cocktailId) {
                return cocktail;
            }
        }
        return null;
    }

    public void initListeners() {
        cocktailButton = (ButtonOption)getOption(COCKTAIL_BUTTON_ID);
        labelOption = (LabelOption)getOption(LABEL_OPTION_ID);
        tracesButton = (com.biomatters.plugins.biocode.labbench.ButtonOption)getOption(TRACES_BUTTON_ID);
        //noinspection unchecked
        final ComboBoxOption<OptionValue> cocktailsOption = (ComboBoxOption<OptionValue>)getOption(COCKTAIL_OPTION_ID);
        addPrimersButton = (ButtonOption)getOption(ADD_PRIMER_TO_LOCAL_ID);

        ButtonOption viewSeqResultsButton = (ButtonOption)getOption(SEQ_RESULTS_BUTTON_NAME);
        if(viewSeqResultsButton != null) {
            viewSeqResultsButton.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    if(reaction.getSequencingResults().isEmpty()) {
                        Dialogs.showMessageDialog("<html>This reaction does not have any sequencing results attached.  Use the <b>Mark as Pass/Fail in LIMS</b> operations to add results.</html>", "No Results");
                    } else {
                        SequencingResult.display("Sequencing Result Revisions", reaction.getSequencingResults());
                    }
                }
            });
        }

        ActionListener cocktailButtonListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final CocktailEditor<CycleSequencingCocktail> editor = new CocktailEditor<CycleSequencingCocktail>();
                if(!editor.editCocktails(BiocodeService.getInstance().getCycleSequencingCocktails(), CycleSequencingCocktail.class, cocktailButton.getComponent())) {
                    return;
                }
                Runnable runnable = new Runnable() {
                    public void run() {
                        ProgressFrame progressFrame = new ProgressFrame("Adding Cocktails", "", GuiUtilities.getMainFrame());
                        progressFrame.setCancelable(false);
                        progressFrame.setIndeterminateProgress();
                        try {
                            BiocodeService.getInstance().addNewCocktails(editor.getNewCocktails());
                            BiocodeService.getInstance().removeCocktails(editor.getDeletedCocktails());
                            final List<OptionValue> cocktails = getCocktails();

                            if(cocktails.size() > 0) {
                                Runnable runnable = new Runnable() {
                                    public void run() {
                                        cocktailsOption.setPossibleValues(cocktails);
                                    }
                                };
                                ThreadUtilities.invokeNowOrLater(runnable);
                            }
                        } catch (final DatabaseServiceException e1) {
                            Runnable runnable = new Runnable() {
                                public void run() {
                                    Dialogs.showDialog(new Dialogs.DialogOptions(Dialogs.OK_ONLY, "Error saving cocktails", getPanel()), e1.getMessage());
                                }
                            };
                            ThreadUtilities.invokeNowOrLater(runnable);
                        } finally {
                            progressFrame.setComplete();
                        }
                    }
                };
                new Thread(runnable).start();
            }
        };
        cocktailButton.addActionListener(cocktailButtonListener);

        tracesButton.addActionListener(new ActionListener(){
            public void actionPerformed(ActionEvent e) {
                if(reaction == null) {
                    Dialogs.showMessageDialog("These options are not linked to a reaction, so you cannot view the traces.", "Cannot view traces", tracesButton.getComponent(), Dialogs.DialogIcon.INFORMATION);
                    return;
                }
                Runnable showEditor = new Runnable() {
                    public void run() {
                        TracesEditor editor = new TracesEditor((reaction.getTraces() == null) ? Collections.<Trace>emptyList() : reaction.getTraces(), getValueAsString("extractionId"), reaction);
                        if(editor.showDialog(tracesButton.getComponent())) {
                            reaction.setTraces(editor.getSourceObjects());
                            for(Trace t : editor.getDeletedObjects()) {
                                reaction.addTraceToRemoveOnSave(t.getId());
                            }
                        }
                    }
                };
                if(reaction.getId() >= 0 && reaction.getTraces() == null) {
                    Runnable backgroundTask = new Runnable() {
                        public void run() {
                            reaction.getChromats();
                        }
                    };
                    BiocodeService.block("Downloading sequences", tracesButton.getComponent(), backgroundTask, showEditor);
                } else {
                    showEditor.run();
                }
            }
        });

        SimpleListener labelListener = new SimpleListener() {
            public void objectChanged() {
                double sum = 0;
                for (Option o : getOptions()) {
//                    if (o instanceof IntegerOption) {
//                        sum += (Integer) o.getValue();
//                    }
                    if(o.getName().equals("cocktail")) {
                        Integer cocktailId = Integer.parseInt(((Options.OptionValue)o.getValue()).getName());
                        List<CycleSequencingCocktail> cocktailList = BiocodeService.getInstance().getCycleSequencingCocktails();
                        for(CycleSequencingCocktail cocktail : cocktailList) {
                            if(cocktail.getId() == cocktailId) {
                                sum += cocktail.getReactionVolume(cocktail.getOptions());    
                            }
                        }

                    }
                }
                labelOption.setValue("Total Volume of Reaction: " + sum + "uL");
            }
        };

        addPrimersButton.addActionListener(new SaveMyPrimersActionListener(){
            public List<DocumentSelectionOption> getPrimerOptions() {
                return Arrays.asList((DocumentSelectionOption)getOption(PRIMER_OPTION_ID));
            }
        });

        for(Option o : getOptions()) {
            if(o instanceof IntegerOption || o.getName().equals("cocktail")) {
                o.addChangeListener(labelListener);
            }
        }
        labelListener.objectChanged();
    }

    public void init() {
        //todo interface for user to pick the sample
        addStringOption("extractionId", "Extraction ID", "");
        addStringOption(WORKFLOW_ID, "Workflow ID", "");
        addEditableComboBoxOption(LIMSConnection.WORKFLOW_LOCUS_FIELD.getCode(), "Locus", "None", SAMPLE_LOCI);
        addDateOption("date", "Date", new Date());


        addComboBoxOption(RUN_STATUS, "Reaction state", STATUS_VALUES, STATUS_VALUES[0]);
        addButtonOption(SEQ_RESULTS_BUTTON_NAME, "", "View " + (reaction != null ? reaction.getSequencingResults().size() : 0) + " Sequencing Results");

        addLabel("");
        addPrimerSelectionOption(PRIMER_OPTION_ID, "Primer", DocumentSelectionOption.FolderOrDocuments.EMPTY, false, Collections.<AnnotatedPluginDocument>emptyList());//new PrimerOption(PRIMER_OPTION_ID, "Primer");

        OptionValue[] directionValues = new OptionValue[] {new OptionValue(FORWARD_VALUE, "Forward"), new OptionValue("reverse", "Reverse")};
        addComboBoxOption(DIRECTION, "Direction", directionValues, directionValues[0]);
        addPrimersButton = addButtonOption(ADD_PRIMER_TO_LOCAL_ID, "", "Add primer to my local database");



        List<OptionValue> cocktails = getCocktails();

        if(cocktails.size() > 0) {
        addComboBoxOption(COCKTAIL_OPTION_ID, "Reaction Cocktail",  cocktails, cocktails.get(0));
        }

        cocktailButton = new ButtonOption(COCKTAIL_BUTTON_ID, "", "Edit Cocktails");
        cocktailButton.setSpanningComponent(true);
        addCustomOption(cocktailButton);
        Options.OptionValue[] cleanupValues = new OptionValue[] {new OptionValue("true", "Yes"), new OptionValue("false", "No")};
        ComboBoxOption<OptionValue> cleanupOption = addComboBoxOption("cleanupPerformed", "Cleanup performed", cleanupValues, cleanupValues[1]);
        StringOption cleanupMethodOption = addStringOption("cleanupMethod", "Cleanup method", "");
        cleanupMethodOption.setDisabledValue("");
        cleanupOption.addDependent(cleanupMethodOption, cleanupValues[0]);
        tracesButton = new com.biomatters.plugins.biocode.labbench.ButtonOption("traces", "", "Add/Edit Traces", false);
        addCustomOption(tracesButton);
        addStringOption("technician", "Technician", "", "May be blank");
        TextAreaOption notesOption = new TextAreaOption("notes", "Notes", "");
        addCustomOption(notesOption);

        labelOption = new LabelOption(LABEL_OPTION_ID, "Total Volume of Reaction: 0uL");
        addCustomOption(labelOption);
    }

    private List<OptionValue> getCocktails() {
        List<OptionValue> cocktails = new ArrayList<OptionValue>();
        List<? extends Cocktail> cycleSequencingCocktails = Cocktail.getAllCocktailsOfType(Reaction.Type.CycleSequencing);
        for (Cocktail cocktail : cycleSequencingCocktails) {
            cocktails.add(new OptionValue("" + cocktail.getId(), cocktail.getName()));
        }
        if(cocktails.size() == 0) {
            cocktails.add(new OptionValue("-1", "No available cocktails"));
        }
        return cocktails;
    }

    @Override
    public void setReaction(CycleSequencingReaction r) {
        super.setReaction(r);
        setValue(SEQ_RESULTS_BUTTON_NAME, "View " + (reaction != null ? reaction.getSequencingResults().size() : 0) + " Sequencing Results");
        getOption(SEQ_RESULTS_BUTTON_NAME).setEnabled(reaction != null && !reaction.getSequencingResults().isEmpty());
    }
}
