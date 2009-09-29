package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.AnnotatedPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentSearchCache;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAnnotation;
import com.biomatters.geneious.publicapi.documents.sequence.SequenceAnnotationInterval;
import com.biomatters.geneious.publicapi.implementations.sequence.OligoSequenceDocument;
import com.biomatters.geneious.publicapi.plugin.DocumentType;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.utilities.SequenceUtilities;
import org.jdom.Element;
import org.virion.jam.util.SimpleListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.lang.ref.WeakReference;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/07/2009 3:06:37 PM
 */
public class PrimerOption extends Options.ComboBoxOption<Options.OptionValue>{
    public static final Options.OptionValue NO_PRIMER_VALUE = new Options.OptionValue("No Primer", "No Primer", "");

    private SequenceAndName extraPrimer;
    private SimpleListener primerListener;

    private static final DocumentSearchCache<OligoSequenceDocument> searchCache = DocumentSearchCache.getDocumentSearchCacheFor(DocumentType.OLIGO_DOC_TYPE);

    private static final List<WeakReference<SimpleListener>> searchCacheChangedListeners = new ArrayList<WeakReference<SimpleListener>>();

    private static List<Options.OptionValue> primerValues;

    private static final SimpleListener masterPrimerListener = new SimpleListener() {
        public void objectChanged() {
            List<AnnotatedPluginDocument> searchCacheDocuments = searchCache.getDocuments();
            List<AnnotatedPluginDocument> documents = searchCacheDocuments == null ? null : new ArrayList<AnnotatedPluginDocument>(searchCacheDocuments);
            primerValues = new ArrayList<Options.OptionValue>();
            primerValues.add(NO_PRIMER_VALUE);

            if (documents != null) {
                primerValues.addAll(getOptionValues(documents));
            }
            for (int i = 0; i < searchCacheChangedListeners.size(); i++) {
                WeakReference<SimpleListener> listenerReference = searchCacheChangedListeners.get(i);
                SimpleListener listener = listenerReference.get();
                if (listener == null) {
                    searchCacheChangedListeners.remove(i);
                    i--;
                }
                else {
                    listener.objectChanged();
                }
            }
        }
    };

    static {
        searchCache.addDocumentsUpdatedListener(masterPrimerListener);
    }



    private static final Options.OptionValue[] noValue = new Options.OptionValue[] {
            new Options.OptionValue("noValues", "No primers found in your database")
    };

    private static void cleanoutSearchCachedChangeListeners() {
        for (int i = 0; i < searchCacheChangedListeners.size(); i++) {
                WeakReference<SimpleListener> listenerReference = searchCacheChangedListeners.get(i);
                SimpleListener listener = listenerReference.get();
                if (listener == null) {
                    searchCacheChangedListeners.remove(i);
                    i--;
                }
            }
    }

    protected PrimerOption(Element e) throws XMLSerializationException {
        super(e);
        Element extraPrimerElement = e.getChild("ExtraPrimer");
        if(extraPrimerElement != null) {
            extraPrimer = new SequenceAndName(extraPrimerElement.getChildText("sequence"), extraPrimerElement.getChildText("name"));
        }
        init();
    }

    private void init() {
        final PrimerOption selfReference = this;
        primerListener = new SimpleListener() {
            public void objectChanged() {
                PrimerOption primerOption = selfReference;
                ArrayList<Options.OptionValue> valueList = new ArrayList<Options.OptionValue>(primerValues);
                if(extraPrimer != null) {
                    boolean alreadyHasPrimer = NO_PRIMER_VALUE.getName().equals(extraPrimer.getName()) && NO_PRIMER_VALUE.getDescription().equals(extraPrimer.getName());
                    Options.OptionValue extraPrimerValue = getOptionValue(extraPrimer.getSequence(), extraPrimer.getName());
                    for(Options.OptionValue seqValue : valueList) {
                        if(seqValue.getName().equals(extraPrimerValue.getName()) && seqValue.getDescription().equalsIgnoreCase(extraPrimerValue.getDescription())) {
                            alreadyHasPrimer = true;
                        }
                    }
                    if(!alreadyHasPrimer) {
                        valueList.add(getOptionValue(extraPrimer.getSequence(), extraPrimer.getName()));
                    }
                }
                setPossibleValuesReal(valueList);
                setDefaultValue(NO_PRIMER_VALUE);
            }
        };
        primerListener.objectChanged();
        cleanoutSearchCachedChangeListeners();
        searchCacheChangedListeners.add(new WeakReference<SimpleListener>(primerListener));
    }

    private static List<Options.OptionValue> getOptionValues(List<AnnotatedPluginDocument> documents) {
        ArrayList<Options.OptionValue> primerList = new ArrayList<Options.OptionValue>();
        for(AnnotatedPluginDocument doc : documents) {
            OligoSequenceDocument seq = (OligoSequenceDocument)doc.getDocumentOrCrash();
            Options.OptionValue optionValue = getOptionValue(seq, doc.getName());
            primerList.add(optionValue);
        }
        return primerList;
    }

    private static Options.OptionValue getOptionValue(String sequence, String name) {
        Options.OptionValue optionValue = new Options.OptionValue(name, name, sequence);
        return optionValue;
    }

    private static Options.OptionValue getOptionValue(OligoSequenceDocument seq, String overrideName) {
        return new Options.OptionValue(overrideName, overrideName, seq.getPrimerSequence().toString());
    }

    @Override
    public Element toXML() {
        Element element = super.toXML();
        Element extraPrimerElement = new Element("ExtraPrimer");
        if(extraPrimer != null) {
            extraPrimerElement.addContent(new Element("name").setText(extraPrimer.getName()));
            extraPrimerElement.addContent(new Element("sequence").setText(extraPrimer.getSequence()));
        }
        else {
            Options.OptionValue value = getValue();
            extraPrimerElement.addContent(new Element("name").setText(value.getName()));
            extraPrimerElement.addContent(new Element("sequence").setText(value.getDescription()));
        }
        element.addContent(extraPrimerElement);
        return element;
    }

    protected PrimerOption(String name, String label) {
        super(name, label, noValue, noValue[0]);
        init();
    }

    private void setPossibleValuesReal(List<? extends Options.OptionValue> possibleValues) {
        super.setPossibleValues(possibleValues);
    }

    @Override
    public void setPossibleValues(List<? extends Options.OptionValue> possibleValues) {
        return;
    }

    @Override
    public void addPossibleValue(Options.OptionValue newValue) {
        return;
    }

    public void setAndAddValue(String name, String sequence) {
        extraPrimer = new SequenceAndName(sequence, name);
        primerListener.objectChanged();
        setValue(getOptionValue(sequence, name));
    }

    @Override
    protected String getExtraPersistentInformation() {
        if(extraPrimer != null) {
            return extraPrimer.getName()+"\n"+extraPrimer.getSequence()+"\n"+
                    (getValue().getLabel().equals(extraPrimer.getName()) && getValue().getDescription().equals(extraPrimer.getSequence())); //is the extra primer the value?
        }
        return null;
    }

    @Override
    protected void setExtraPersistentInformation(String extras) {
        if(extras != null) {
            String[] primerParts = extras.split("\n");
            extraPrimer = new SequenceAndName(primerParts[1], primerParts[0]);
            primerListener.objectChanged();
            if(Boolean.parseBoolean(primerParts[2])) {
                setValue(getOptionValue(extraPrimer.getSequence(), extraPrimer.getName()));
            }
        }
    }

    private static SequenceAnnotation getOligoAnnotationIfValid(OligoSequenceDocument originalDoc) {
        SequenceAnnotation correctOligo = null;
        List<SequenceAnnotation> oligoAnnotations = new ArrayList<SequenceAnnotation>();
        oligoAnnotations.addAll(SequenceUtilities.getAnnotationsOfType(originalDoc.getSequenceAnnotations(), SequenceAnnotation.TYPE_PRIMER_BIND));
        oligoAnnotations.addAll(SequenceUtilities.getAnnotationsOfType(originalDoc.getSequenceAnnotations(), SequenceAnnotation.TYPE_PRIMER_BIND_REVERSE));
        oligoAnnotations.addAll(SequenceUtilities.getAnnotationsOfType(originalDoc.getSequenceAnnotations(), SequenceAnnotation.TYPE_DNA_PROBE));
        for (SequenceAnnotation annotation : oligoAnnotations) {
            SequenceAnnotationInterval interval = annotation.getIntervals().get(0);
            if(interval.getLength() == originalDoc.getSequenceLength() && interval.getMinimumIndex() == 1 &&
                    interval.getMaximumIndex() == originalDoc.getSequenceLength()) {
                if (correctOligo != null && (!correctOligo.getType().equals(annotation.getType()) ||
                        interval.getDirection() != correctOligo.getIntervals().get(0).getDirection())) {
                    return null; //found another matching primer annotation of a different type or in a different direction
                }
                correctOligo = annotation;
            }
        }
        return correctOligo;
    }

    private static class SequenceAndName{
        private String sequence, name;

        private SequenceAndName(String sequence, String name) {
            this.sequence = sequence;
            this.name = name;
        }

        public String getSequence() {
            return sequence;
        }

        public String getName() {
            return name;
        }
    }
}
