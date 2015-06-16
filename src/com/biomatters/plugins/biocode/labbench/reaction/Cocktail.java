package com.biomatters.plugins.biocode.labbench.reaction;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.documents.XMLSerializer;
import com.biomatters.geneious.publicapi.plugin.Options;
import org.jdom.Element;

import java.util.List;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/06/2009 11:17:43 AM
 */
public abstract class Cocktail implements XMLSerializable {

    public abstract int getId();

    public abstract String getName();

    public abstract Options getOptions();

    protected abstract void setOptions(Options options);

    protected abstract void setId(int id);

    protected abstract void setName(String name);

    public abstract String getTableName();

    public abstract Reaction.Type getReactionType();

    public double getReactionVolume(Options options) {
        double sum = 0;
        for (Options.Option o : options.getOptions()) {
            if (o instanceof Options.DoubleOption && ((Options.DoubleOption) o).getUnits().equals("µL") && !o.getName().toLowerCase().contains("template")) {
                sum += (Double) o.getValue();
            }
        }
        return sum;
    }

    public static List<? extends Cocktail> getAllCocktailsOfType(Reaction.Type type) {
        switch (type) {
            case PCR:
                return getCocktailGetter().getPCRCocktails();
            case CycleSequencing:
                return getCocktailGetter().getCycleSequencingCocktails();
            default:
                throw new IllegalArgumentException("Only PCR and Cycle Sequencing reactions have cocktails");
        }
    }

    public abstract String getSQLString();

    public boolean equals(Object o) {
        if(o instanceof Cocktail) {
            return ((Cocktail)o).getId() == getId();
        }
        return false;
    }

    public int hashCode() {
        return getId();
    }

    public Element toXML() {
        Element e = new Element("cocktail");
        e.addContent(new Element("name").setText(getName()));
        e.addContent(new Element("id").setText(""+getId()));
        e.addContent(XMLSerializer.classToXML("options", getOptions()));
        return e;
    }

    public void fromXML(Element element) throws XMLSerializationException {
        setOptions(XMLSerializer.classFromXML(element.getChild("options"), Options.class));
        setName(element.getChildText("name"));
        setId(Integer.parseInt(element.getChildText("id")));
    }

    public static enum Type {
        pcr("pcr_thermocycle", "PCRCocktails.xml", Reaction.Type.PCR),
        cyclesequencing("cyclesequencing_cocktail", "cyclesequencingCocktails.xml", Reaction.Type.CycleSequencing);

        public final String databaseTable;
        public final String cacheFilename;
        public final Reaction.Type reactionType;

        Type(String databaseTable, String cacheFilename, Reaction.Type reactionType) {
            this.databaseTable = databaseTable;
            this.cacheFilename = cacheFilename;
            this.reactionType = reactionType;
        }
    }

    private static CocktailGetter cocktailGetter;

    public static void setCocktailGetter(CocktailGetter getter) {
        cocktailGetter = getter;
    }

    public static CocktailGetter getCocktailGetter() {
        return cocktailGetter;
    }

    public abstract static class CocktailGetter {
        public abstract List<CycleSequencingCocktail> getCycleSequencingCocktails();
        public abstract List<PCRCocktail> getPCRCocktails();
    }
}