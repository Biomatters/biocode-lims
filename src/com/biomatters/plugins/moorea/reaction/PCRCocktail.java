package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.plugins.moorea.MooreaLabBenchService;
import com.biomatters.plugins.moorea.TextAreaOption;

import java.util.List;
import java.util.ArrayList;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 9/06/2009 11:18:12 AM
 */
public class PCRCocktail extends Cocktail{

    int id = -1;
    private Options options;

    public PCRCocktail(ResultSet resultSet) throws SQLException{
        this();
        id = resultSet.getInt("id");
        options.setValue("name", resultSet.getString("name"));
        options.setValue("ddh20", resultSet.getInt("ddH20"));
        options.setValue("buffer", resultSet.getInt("buffer"));
        options.setValue("mg", resultSet.getInt("mg"));
        options.setValue("bsa", resultSet.getInt("bsa"));
        options.setValue("dntp", resultSet.getInt("dNTP"));
        options.setValue("taq", resultSet.getInt("taq"));
        options.setValue("notes", resultSet.getString("notes"));
    }

    public PCRCocktail() {
        options = new Options(this.getClass());
        Options.StringOption nameOption = options.addStringOption("name", "Name", "");
        Options.IntegerOption ddh2oOption = options.addIntegerOption("ddh20", "ddH20 Amount", 1, 0, Integer.MAX_VALUE);
        ddh2oOption.setUnits("ul");
        Options.IntegerOption bufferOption = options.addIntegerOption("buffer", "10x PCR Buffer Amount", 1, 0, Integer.MAX_VALUE);
        bufferOption.setUnits("ul");
        Options.IntegerOption mgOption = options.addIntegerOption("mg", "Mg Amount", 1, 0, Integer.MAX_VALUE);
        mgOption.setUnits("ul");
        Options.IntegerOption bsaOption = options.addIntegerOption("bsa", "BSA Amount", 1, 0, Integer.MAX_VALUE);
        bsaOption.setUnits("ul");
        Options.IntegerOption dntpOption = options.addIntegerOption("dntp", "dNTPs Amount", 1, 0, Integer.MAX_VALUE);
        dntpOption.setUnits("ul");
        Options.IntegerOption taqOption = options.addIntegerOption("taq", "TAQ", 1, 0, Integer.MAX_VALUE);
        taqOption.setUnits("ul");
        TextAreaOption areaOption = new TextAreaOption("notes", "Notes", "");
        options.addCustomOption(areaOption);
    }

    public String getSQLString() {
        return "INSERT INTO pcr_cocktail (name, ddH20, buffer, mg, bsa, dNTP, taq, notes) VALUES ('"+options.getValueAsString("name").replace("'", "''")+"', "+options.getValueAsString("ddh20")+", "+options.getValueAsString("buffer")+", "+options.getValueAsString("mg")+", "+options.getValueAsString("bsa")+", "+options.getValueAsString("dntp")+", "+options.getValueAsString("taq")+", '"+options.getValueAsString("notes")+"')";
    }

    public int getId() {
        return id;
    }

    public PCRCocktail(String name) {
        this();
        options.setValue("name", name);
    }

    public Options getOptions() {
        return options;
    }

    public String getName() {
        return options == null ? "Untitled" : options.getValueAsString("name");
    }

    public List<Cocktail> getAllCocktailsOfType() {
        //todo: retrieve from database
        return MooreaLabBenchService.getInstance().getPCRCocktails();
//        List<Cocktail> cocktails = new ArrayList<Cocktail>();
//        cocktails.add(new PCRCocktail("Steve's Cocktail 1"));
//        cocktails.add(new PCRCocktail("Steve's Cocktail 2"));
//        return cocktails;
    }

    public Cocktail createNewCocktail() {
        return new PCRCocktail();
    }
}
