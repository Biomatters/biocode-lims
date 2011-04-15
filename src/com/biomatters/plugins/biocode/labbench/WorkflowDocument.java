package com.biomatters.plugins.biocode.labbench;

import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.components.OptionsPanel;
import com.biomatters.geneious.publicapi.documents.*;
import com.biomatters.geneious.publicapi.documents.sequence.DefaultSequenceListDocument;
import com.biomatters.geneious.publicapi.documents.sequence.NucleotideSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.sql.*;
import java.util.*;
import java.util.List;
import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 18/06/2009 4:06:40 PM
 */
public class WorkflowDocument extends MuitiPartDocument {
    private Workflow workflow;
    private List<Reaction> reactions;
    private List<ReactionPart> parts;
    Comparator<ReactionPart> reactionComparitor = new Comparator<ReactionPart>() {
            public int compare(ReactionPart o1, ReactionPart o2) {
                return (int) (o1.getReaction().getCreated().getTime() - o2.getReaction().getCreated().getTime());
            }
        };

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof WorkflowDocument)) return false;

        WorkflowDocument that = (WorkflowDocument) o;

        if (workflow != null ? !workflow.equals(that.workflow) : that.workflow != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return workflow != null ? workflow.hashCode() : 0;
    }

    public WorkflowDocument() {} //only for fromXml()

    public WorkflowDocument(Workflow workflow, List<Reaction> reactions) {
        if(workflow == null) {
            throw new IllegalArgumentException("You cannot create a workflow document with a null workflow");
        }
        this.workflow = workflow;
        this.reactions = new ArrayList<Reaction>(reactions);
        parts = new ArrayList<ReactionPart>();
        for(Reaction r : reactions) {
            parts.add(new ReactionPart(r));
        }
        sortReactions();     
    }

    public void sortReactions() {
        Comparator comp = new Comparator<Reaction>(){
            public int compare(Reaction o1, Reaction o2) {
                if(o1 instanceof ExtractionReaction && !(o2 instanceof ExtractionReaction)) {
                    return -Integer.MAX_VALUE;
                }
                return (int)(o1.getDate().getTime()-o2.getDate().getTime());
            }
        };
        Collections.sort(reactions, comp);
    }

    public WorkflowDocument(ResultSet resultSet) throws SQLException{
        int workflowId = resultSet.getInt("workflow.id");
        String workflowName = resultSet.getString("workflow.name");
        String extraction = resultSet.getString("extraction.extractionId");
        java.sql.Date lastModified = resultSet.getDate("workflow.date");      
        this.reactions = new ArrayList<Reaction>();
        workflow = new Workflow(workflowId, workflowName, extraction, resultSet.getString("workflow.locus"), lastModified);
        this.parts = new ArrayList<ReactionPart>();
        addRow(resultSet);
    }

    public List<DocumentField> getDisplayableFields() {
        return Arrays.asList(new DocumentField("Number of Parts", "Number of parts in this workflow", "numberOfParts", Integer.class, true, false),
                new DocumentField("Last Modified", "The date this document was last modified", "lastModified", java.util.Date.class, true, false),
                LIMSConnection.WORKFLOW_LOCUS_FIELD)
        ;
    }

    public Object getFieldValue(String fieldCodeName) {
        if("locus".equals(fieldCodeName)) {
            return workflow.getLocus();
        }
        if("numberOfParts".equals(fieldCodeName)) {
            return getNumberOfParts();
        }
        if("lastModified".equals(fieldCodeName)) {
            return new Date(workflow.getLastModified().getTime());
        }
        return null;
    }

    public String getName() {
        return workflow == null ? "Untitled" : workflow.getName();
    }

    /**
     * @return workflow id or -1 if no workflow associated with this document
     */
    public int getId() {
        return workflow == null ? -1 : workflow.getId();
    }

    public URN getURN() {
        return null;
    }

    public Date getCreationDate() {
        return null;
    }

    public String getDescription() {
        return null;
    }

    public String toHTML() {
        return null;
    }

    public Element toXML() {
        Element element = new Element("WorkflowDocument");
        if(workflow != null) {
            element.addContent(workflow.toXML());
        }
        if(reactions != null) {
            for(Reaction r : reactions) {
                element.addContent(XMLSerializer.classToXML("reaction", r));
            }
        }
        
        return element;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        workflow = new Workflow(element.getChild("workflow"));
        reactions = new ArrayList<Reaction>();
        parts = new ArrayList<ReactionPart>();
        for(Element e : element.getChildren("reaction")) {
            reactions.add(XMLSerializer.classFromXML(e, Reaction.class));
        }
        for(Reaction r : reactions) {
            if(r.getFimsSample() != null) {
                workflow.setFimsSample(r.getFimsSample());
            }
            parts.add(new ReactionPart(r));
        }
    }

    public FimsSample getFimsSample() {
        return getWorkflow().getFimsSample();
    }

    public void setFimsSample(FimsSample sample) {
        getWorkflow().setFimsSample(sample);
    }

    public int getNumberOfParts() {
        return parts.size();
    }

    public Part getPart(int index) {
        return parts.get(index);
    }

    public Workflow getWorkflow() {
        return workflow;
    }

    public Reaction getMostRecentReaction(Reaction.Type type) {
        Reaction r = null;
        for (Reaction reaction : reactions) {
            if (reaction.getType() == type) {
                r = reaction;
            }
        }
        return r;
    }

    public Reaction getMostRecentSequencingReaction(boolean forward) {
        Reaction r = null;
        for (Reaction reaction : reactions) {
            if (reaction.getType() == Reaction.Type.CycleSequencing && CycleSequencingOptions.FORWARD_VALUE.equals(reaction.getOptions().getValueAsString(CycleSequencingOptions.DIRECTION)) == forward) {
                r = reaction;
            }
        }
        return r;
    }

    public List<Reaction> getReactions() {
        return new ArrayList<Reaction> (reactions);
    }

    public List<Reaction> getReactions(Reaction.Type type) {
        List<Reaction> reactionsList = new ArrayList<Reaction>();
        for (Reaction reaction : reactions) {
            if (reaction.getType() == type) {
                reactionsList.add(reaction);
            }
        }
        return reactionsList;
    }

    public void addRow(ResultSet resultSet) throws SQLException{
        //add extractions
        final String plateType = resultSet.getString("plate.type");

        if(plateType == null) {  //workaround for a bug in HSQL
            return;
        }

        Reaction.Type rowType = Reaction.Type.valueOf(plateType);
        switch(rowType) {
            case Extraction :
                int reactionId = resultSet.getInt("extraction.id");
                //check we don't already have it
                boolean alreadyThere = false;
                for(Reaction r : reactions) {
                    if(r.getType() == Reaction.Type.Extraction && r.getId() == reactionId) {
                        alreadyThere = true;
                    }
                }
                if(!alreadyThere) {
                    Reaction r = new ExtractionReaction(resultSet);
                    addReaction(r);
                }
            break;
        case PCR :
            reactionId = resultSet.getInt("pcr.id");
            //check we don't already have it
            alreadyThere = false;
            for(Reaction r : reactions) {
                if(r.getType() == Reaction.Type.PCR && r.getId() == reactionId) {
                    alreadyThere = true;
                }
            }
            if(!alreadyThere) {
                Reaction r = new PCRReaction(resultSet);
                addReaction(r);
            }
            break;
        case CycleSequencing :
            reactionId = resultSet.getInt("cyclesequencing.id");
            //check we don't already have it
            alreadyThere = false;
            for(Reaction r : reactions) {
                if(r.getType() == Reaction.Type.CycleSequencing && r.getId() == reactionId) {
                    alreadyThere = true;
                }
            }
            if(!alreadyThere) {
                Reaction r = new CycleSequencingReaction(resultSet);
                addReaction(r);
            }
            break;
        }
    }

    public void addReaction(Reaction r) {
        if(workflow == null) {
            throw new IllegalStateException("This workflow document does not yet have a workflow - you cannot add reactions");
        }
        if(r == null) {
            return;
        }
        reactions.add(r);
        parts.add(new ReactionPart(r));
        if(r.getFimsSample() != null) {
            workflow.setFimsSample(r.getFimsSample());
        }
        Collections.sort(parts, reactionComparitor);
    }

    public static class ReactionPart extends Part {
        private Reaction reaction;
        private List<SimpleListener> changesListeners;
        private boolean changes = false;

        public ReactionPart(Reaction reaction) {
            super();
            this.reaction = reaction;
            changesListeners = new ArrayList<SimpleListener>();
        }

        @Override
        public void addModifiedStateChangedListener(SimpleListener sl) {
            changesListeners.add(sl);
        }

        @Override
        public void removeModifiedStateChangedListener(SimpleListener sl) {
            changesListeners.remove(sl);
        }

        private void fireChangeListeners() {
            for(SimpleListener listener : changesListeners) {
                listener.objectChanged();
            }
        }

        public JPanel getPanel() {
            final JPanel panel = new JPanel();
            final AtomicReference<OptionsPanel> optionsPanel = new AtomicReference<OptionsPanel>(getReactionPanel(reaction));
            final JPanel holder = new JPanel(new BorderLayout());
            holder.setOpaque(false);
            holder.add(optionsPanel.get(), BorderLayout.CENTER);
            final JButton editButton = new JButton("Edit");
            editButton.setOpaque(false);
            final SimpleListener licenseListener = new SimpleListener() {
                public void objectChanged() {
                    editButton.setEnabled(License.isProVersion());
                    editButton.setText(License.proOnlyName("Edit"));
                }
            };
            editButton.addActionListener(new ActionListener(){
                public void actionPerformed(ActionEvent e) {
                    Element oldOptions = XMLSerializer.classToXML("options", reaction.getOptions());
                    ReactionUtilities.editReactions(Arrays.asList(reaction), panel, false);
                    @SuppressWarnings({"UnusedDeclaration", "UnnecessaryLocalVariable", "UnusedAssignment"}) SimpleListener licenseListenerReference = licenseListener;//to stop it being garbage collected before the panel is nullified
                    if(reaction.hasError()) {
                        try {
                            reaction.setOptions(XMLSerializer.classFromXML(oldOptions, ReactionOptions.class));
                        } catch (XMLSerializationException e1) {
                            Dialogs.showMessageDialog("Error resetting options: could not deserailse your option's values.  It is recommended that you discard changes to this document if possible.");
                        }
                    }
                    else {
                        holder.remove(optionsPanel.get());
                        optionsPanel.set(getReactionPanel(reaction));
                        holder.add(optionsPanel.get(), BorderLayout.CENTER);
                        holder.validate();
                        changes = true;
                        fireChangeListeners();
                    }
                }
            });
            License.addWeakLicenseTypeChangeListener(licenseListener);
            licenseListener.objectChanged();
            JPanel editPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            editPanel.setOpaque(false);
            editPanel.add(editButton);
            holder.add(editPanel, BorderLayout.SOUTH);
            panel.setOpaque(false);
            panel.setLayout(new BorderLayout());
            panel.add(holder, BorderLayout.CENTER);
            ThermocycleEditor.ThermocycleViewer viewer = getThermocyclePanel(reaction);
            if(viewer != null) {
                panel.add(viewer, BorderLayout.EAST);
            }
            return panel;
        }

        private static OptionsPanel getReactionPanel(Reaction reaction) {
            OptionsPanel optionsPanel = new OptionsPanel(false, false);
            List<DocumentField> documentFields = reaction.getDisplayableFields();
            for(DocumentField field : documentFields) {
                if(field.getName().length() > 0) {
                    optionsPanel.addComponentWithLabel("<html><b>"+field.getName()+": </b></html>", new JLabel(reaction.getFieldValue(field.getCode()).toString()), false);
                }
                else {
                    optionsPanel.addSpanningComponent(new JLabel(reaction.getFieldValue(field.getCode()).toString()));
                }
            }
            if(reaction.getPlateId() >= 0) {
                optionsPanel.addComponentWithLabel("<html><b>Plate Name: </b></html>", new JLabel(reaction.getPlateName()), false);
                optionsPanel.addComponentWithLabel("<html><b>Well: </b></html>", new JLabel(reaction.getLocationString()), false);
            }
            return optionsPanel;
        }

        public static ThermocycleEditor.ThermocycleViewer getThermocyclePanel(Reaction reaction) {
            if(reaction.getThermocycle() != null) {
                return new ThermocycleEditor.ThermocycleViewer(reaction.getThermocycle());
            }
            return null;
        }

        public String getName() {
            synchronized(BiocodeService.dateFormat) {
                switch(reaction.getType()) {
                    case Extraction:
                        return "Extraction Reaction: "+ BiocodeService.dateFormat.format(reaction.getCreated());
                    case PCR:
                        return "PCR Reaction: "+ BiocodeService.dateFormat.format(reaction.getCreated());
                    case CycleSequencing:
                        return "Cycle Sequencing Reaction: "+ BiocodeService.dateFormat.format(reaction.getCreated());

                }
            }
            return "Unknown Reaction";
        }

        public Reaction getReaction() {
            return reaction;
        }

        public ExtendedPrintable getExtendedPrintable() {
            return new ExtendedPrintable(){
                public int print(Graphics2D graphics, Dimension dimensions, int pageIndex, Options options) throws PrinterException {
                    if(pageIndex == 1) {
                        JPanel reactionPanel = getReactionPanel(reaction);
                        JPanel thermocyclePanel = getThermocyclePanel(reaction);
                        JLabel headerLabel = new JLabel(getName());
                        headerLabel.setFont(new Font("arial", Font.BOLD, 16));
                        JPanel holderPanel = new JPanel(new BorderLayout());
                        holderPanel.setOpaque(false);
                        holderPanel.add(headerLabel, BorderLayout.NORTH);
                        holderPanel.add(reactionPanel, BorderLayout.CENTER);
                        if(thermocyclePanel != null) {
                            holderPanel.add(thermocyclePanel, BorderLayout.SOUTH);
                        }
                        holderPanel.setBounds(0,0,dimensions.width, dimensions.height);
                        MultiPartDocumentViewerFactory.recursiveDoLayout(holderPanel);
                        holderPanel.validate();
                        holderPanel.invalidate();
                        holderPanel.print(graphics);
                    }
                    else {
                        if(reaction instanceof CycleSequencingReaction) {
                        List<NucleotideSequenceDocument> sequences = ((CycleSequencingOptions) reaction.getOptions()).getSequences();
                        if(sequences != null && sequences.size() > 0) {
                                DefaultSequenceListDocument sequenceList = DefaultSequenceListDocument.forNucleotideSequences(sequences);
                                DocumentViewerFactory factory = TracesEditor.getViewerFactory(sequenceList);
                                DocumentViewer viewer = factory.createViewer(new AnnotatedPluginDocument[]{DocumentUtilities.createAnnotatedPluginDocument(sequenceList)});
                                ExtendedPrintable printable = viewer.getExtendedPrintable();
                                Options op = printable.getOptions(false);
                                return printable.print(graphics, dimensions, pageIndex-2, op);
                            }
                        }
                    }
                    return Printable.PAGE_EXISTS;
                }

                public int getPagesRequired(Dimension dimensions, Options options) {
                    int pagesRequired = 1;
                    if(reaction instanceof CycleSequencingReaction) {
                        List<NucleotideSequenceDocument> sequences = ((CycleSequencingOptions) reaction.getOptions()).getSequences();
                        if(sequences != null && sequences.size() > 0) {
                            DefaultSequenceListDocument sequenceList = DefaultSequenceListDocument.forNucleotideSequences(sequences);
                            DocumentViewerFactory factory = TracesEditor.getViewerFactory(sequenceList);
                            DocumentViewer viewer = factory.createViewer(new AnnotatedPluginDocument[]{DocumentUtilities.createAnnotatedPluginDocument(sequenceList)});
                            ExtendedPrintable printable = viewer.getExtendedPrintable();
                            Options op = printable.getOptions(false);
                            pagesRequired += printable.getPagesRequired(dimensions, op);
                        }
                    }
                    return pagesRequired;
                }
            };
        }

        public boolean hasChanges() {
            return changes;
        }

        public void saveChangesToDatabase(BiocodeService.BlockingProgress progress, Connection connection) throws SQLException{
            reaction.areReactionsValid(Arrays.asList(reaction), null, true);
            Reaction.saveReactions(new Reaction[] {reaction}, reaction.getType(), connection, progress);
            changes = false;
        }
    }
}
