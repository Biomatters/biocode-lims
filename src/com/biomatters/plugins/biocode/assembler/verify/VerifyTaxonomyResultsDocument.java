package com.biomatters.plugins.biocode.assembler.verify;

import com.biomatters.geneious.publicapi.documents.AbstractPluginDocument;
import com.biomatters.geneious.publicapi.documents.DocumentUtilities;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.plugin.Geneious;
import com.biomatters.plugins.biocode.BiocodePlugin;
import org.jdom.Element;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Richard
 */
public class VerifyTaxonomyResultsDocument extends AbstractPluginDocument {

    /**
     * Map of query to results
     */
    private List<VerifyResult> results;
    private Element binningOptionsValues = null;

    public static final String BINNING_ELEMENT_NAME = "binningParameters";

    public VerifyTaxonomyResultsDocument() {
    }

    public VerifyTaxonomyResultsDocument(List<VerifyResult> results, String keywords) {
        this.results = results;
        String nameForDocument = "Verify Taxonomy Results";
        if (BiocodePlugin.compareVersions(Geneious.getApiVersion(), "4.51") < 0) {
            // from 5.5.1 onwards the document name will be uniqued by core
           nameForDocument = DocumentUtilities.getUniqueNameForDocument("Verify Taxonomy Results");
        }
        setFieldValue("name", nameForDocument);
        setFieldValue("keywords", keywords);
    }

    public String getName() {
        return (String)getFieldValue("name");
    }

    public String getDescription() {
        return null;
    }

    public String toHTML() {
        return null;
    }

    public List<VerifyResult> getResults() {
        return results;
    }

    @Override
    public Element toXML() {
        Element element = super.toXML();
        Element resultsElement = new Element("results");
        for (VerifyResult result : results) {
            resultsElement.addContent(result.toXML());
        }
        element.addContent(resultsElement);
        Element binningElement = new Element(BINNING_ELEMENT_NAME);
        if (binningOptionsValues != null) {
            binningElement.addContent(binningOptionsValues.cloneContent());
        }
        element.addContent(binningElement);
        return element;
    }

    @Override
    public void fromXML(Element root) throws XMLSerializationException {
        super.fromXML(root);
        results = new ArrayList<VerifyResult>();
        for (Element result : root.getChild("results").getChildren()) {
            results.add(new VerifyResult(result));
        }
        binningOptionsValues = root.getChild(BINNING_ELEMENT_NAME);
    }

    public Element getBinningOptionsValues() {
        return binningOptionsValues;
    }

    public void setBinningOptionsValues(Element binningOptionsValues) {
        this.binningOptionsValues = binningOptionsValues;
    }
}
