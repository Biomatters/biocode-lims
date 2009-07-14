package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.plugin.DocumentType;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.DocumentSearchCache;
import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;
import com.biomatters.plugins.moorea.ButtonOption;
import com.biomatters.plugins.moorea.MooreaLabBenchService;
import com.biomatters.plugins.moorea.TransactionException;
import com.biomatters.plugins.moorea.TextAreaOption;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.util.List;
import java.util.Arrays;
import java.util.ArrayList;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 23/06/2009 12:45:32 PM
 */
public class PCROptions extends ReactionOptions {

    private ButtonOption cocktailButton;
    private Option<String, ? extends JComponent> labelOption;
    private ComboBoxOption cocktailOption;

    public static final String PRIMER_OPTION_ID = "primer";
    static final String COCKTAIL_BUTTON_ID = "cocktailEdit";
    static final String LABEL_OPTION_ID = "label";
    static final String COCKTAIL_OPTION_ID = "cocktail";


    public PCROptions(Class c) {
        super(c);
        init();
        initListeners();
    }
    
    public PCROptions(Element e) throws XMLSerializationException {
        super(e);
        initListeners();
    }

    public boolean fieldIsFinal(String fieldCode) {
        return "extractionId".equals(fieldCode) || "workflowId".equals(fieldCode);
    }

    public void initListeners() {
        cocktailButton = (ButtonOption)getOption(COCKTAIL_BUTTON_ID);
        labelOption = (LabelOption)getOption(LABEL_OPTION_ID);
        cocktailOption = (ComboBoxOption)getOption(COCKTAIL_OPTION_ID);

        ActionListener cocktailButtonListener = new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                final List<? extends Cocktail> newCocktails = Cocktail.editCocktails(new PCRCocktail().getAllCocktailsOfType(), PCRCocktail.class, null);
                if (newCocktails.size() > 0) {
                    Runnable runnable = new Runnable() {
                        public void run() {
                            try {
                                MooreaLabBenchService.block("Adding Cocktails", getPanel());
                                MooreaLabBenchService.getInstance().addNewPCRCocktails(newCocktails);
                            } catch (final TransactionException e1) {
                                Runnable runnable = new Runnable() {
                                    public void run() {
                                        Dialogs.showDialog(new Dialogs.DialogOptions(Dialogs.OK_ONLY, "Error saving cocktails", getPanel()), e1.getMessage());

                                    }
                                };
                                ThreadUtilities.invokeNowOrLater(runnable);
                            } finally {
                                MooreaLabBenchService.unBlock();
                            }
                        }
                    };
                    new Thread(runnable).start();
                }
                updateCocktailOption(cocktailOption);
            }
        };
        cocktailButton.addActionListener(cocktailButtonListener);

        SimpleListener labelListener = new SimpleListener() {
            public void objectChanged() {
                int sum = 0;
                for (Option o : getOptions()) {
                    if (o instanceof IntegerOption) {
                        sum += (Integer) o.getValue();
                    }
                    if(o.getName().equals("cocktail")) {
                        Integer cocktailId = Integer.parseInt(((Options.OptionValue)o.getValue()).getName());
                        List<Cocktail> cocktailList = MooreaLabBenchService.getInstance().getPCRCocktails();
                        for(Cocktail cocktail : cocktailList) {
                            if(cocktail.getId() == cocktailId) {
                                sum += cocktail.getReactionVolume(cocktail.getOptions());
                            }
                        }

                    }
                }
                labelOption.setValue("Total Volume of Reaction: " + sum + "uL");
            }
        };

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
        addStringOption("workflowId", "Workflow ID", "");


        OptionValue[] passedValues = new OptionValue[] {
                new OptionValue("not run", "not run"),
                new OptionValue("passed", "passed"),
                new OptionValue("failed", "failed"),
        };
        addComboBoxOption("runStatus", "Reaction state", passedValues, passedValues[0]);

        addLabel("");
        beginAlignHorizontally("forward primer", false);
        PrimerOption primerOption = new PrimerOption(PRIMER_OPTION_ID, "Primer", null);
        addCustomOption(primerOption);



        IntegerOption primerAmountOption = addIntegerOption("prAmount", "Primer Amount", 1, 0, Integer.MAX_VALUE);
        primerAmountOption.setUnits("ul");
        endAlignHorizontally();


        List<OptionValue> cocktails = new ArrayList<OptionValue>();
        List<Cocktail> cocktailList = new PCRCocktail().getAllCocktailsOfType();
        for (int i = 0; i < cocktailList.size(); i++) {
            Cocktail cocktail = new PCRCocktail().getAllCocktailsOfType().get(i);
            cocktails.add(new OptionValue(""+cocktail.getId(), cocktail.getName()));
        }
        if(cocktailList.size() == 0) {
            cocktails.add(new OptionValue("-1", "No available cocktails"));
        }

        cocktailOption = addComboBoxOption(COCKTAIL_OPTION_ID, "Reaction Cocktail", cocktails, cocktails.get(0));

        updateCocktailOption(cocktailOption);

        cocktailButton = new ButtonOption(COCKTAIL_BUTTON_ID, "", "Edit Cocktails");
        cocktailButton.setSpanningComponent(true);
        addCustomOption(cocktailButton);
        BooleanOption cleanupOption = addBooleanOption("cleanupPerformed", "Cleanup performed", false);
        StringOption cleanupMethodOption = addStringOption("cleanupMethod", "Cleanup method", "");
        cleanupMethodOption.setDisabledValue("");
        cleanupOption.addDependent(cleanupMethodOption, true);
        TextAreaOption notes = new TextAreaOption("notes", "Notes", "");
        addCustomOption(notes);

        labelOption = new LabelOption(LABEL_OPTION_ID, "Total Volume of Reaction: 0uL");
        addCustomOption(labelOption);
    }

    private void updateCocktailOption(ComboBoxOption<OptionValue> cocktailOption) {
        List<OptionValue> cocktails = new ArrayList<OptionValue>();
        for (int i = 0; i < new PCRCocktail().getAllCocktailsOfType().size(); i++) {
            Cocktail cocktail = new PCRCocktail().getAllCocktailsOfType().get(i);
            cocktails.add(new OptionValue(""+cocktail.getId(), cocktail.getName()));
        }
        if(cocktails.size() == 0) {
            cocktails.add(new OptionValue("-1", "No available cocktails"));
        }
        cocktailOption.setPossibleValues(cocktails);
    }
    
}
