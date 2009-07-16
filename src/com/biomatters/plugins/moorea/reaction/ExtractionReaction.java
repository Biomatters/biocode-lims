package com.biomatters.plugins.moorea.reaction;

import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.documents.Condition;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.plugins.moorea.*;
import com.biomatters.plugins.moorea.lims.LIMSConnection;
import com.biomatters.plugins.moorea.plates.Plate;
import com.biomatters.plugins.moorea.fims.FIMSConnection;

import java.util.*;
import java.util.List;
import java.awt.*;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.Connection;

import org.jdom.Element;

/**
 * @author Steven Stones-Havas
 * @version $Id$
 *          <p/>
 *          Created on 12/06/2009 5:27:29 PM
 */
public class ExtractionReaction extends Reaction{

    public ExtractionReaction(){}

    public ExtractionReaction(ResultSet r) throws SQLException{
        ReactionOptions options = getOptions();
        init(r, options);
        System.out.println(getWorkflow());
    }

    private void init(ResultSet r, Options options) throws SQLException {
        setId(r.getInt("extraction.id"));
        setCreated(r.getDate("extraction.date"));
        options.setValue("sampleId", r.getString("extraction.sampleId"));
        options.setValue("extractionId", r.getString("extraction.extractionId"));
        options.setValue("extractionMethod", r.getString("extraction.method"));
        options.setValue("parentExtraction", r.getString("extraction.parent"));
        options.setValue("volume", r.getInt("extraction.volume"));
        options.setValue("dilution", r.getInt("extraction.dilution"));
        options.setValue("notes", r.getString("extraction.notes"));
        setPlate(r.getInt("extraction.plate"));
        setPosition(r.getInt("extraction.location"));
        String s = r.getString("workflow.name");
        if(s != null) {
            options.setValue("workflowId", s);
            setWorkflow(new Workflow(r.getInt("workflow.id"), r.getString("workflow.name"), r.getString("pcr.extractionId")));
            options.setValue("workflowId", getWorkflow().getName());
        }
        else {
            assert false : "We should be getting a resultset of at least the CycleSequencing table joined to the Workflow table";
        }
    }

    public String getExtractionId() {
        return getOptions().getValueAsString("extractionId");
    }

    private ReactionOptions options;

    public ReactionOptions getOptions() {
        if(options == null) {
            options = new ExtractionOptions();
        }
        return options;
    }

    public void setOptions(ReactionOptions op) {
        this.options = op;
    }

    public void setThermocycle(Thermocycle tc){}
    public Thermocycle getThermocycle() {
        return null;  //Extractions don't have thermocycles
    }

    public Cocktail getCocktail() {
        return null; //extractions don't have cocktails
    }
    
    public Type getType() {
        return Type.Extraction;
    }

    public List<DocumentField> getDefaultDisplayedFields() {
        return Arrays.asList(
                new DocumentField("Sample Id", "", "sampleId", String.class, false, false),
                new DocumentField("Extraction Id", "", "extractionId", String.class, false, false)
        );
    }


    public Color _getBackgroundColor() {
        return Color.white;
    }


    public String areReactionsValid(List<? extends Reaction> reactions) {
        if(!MooreaLabBenchService.getInstance().isLoggedIn()) {
            return "You are not logged in to the database";
        }
        FIMSConnection fimsConnection = MooreaLabBenchService.getInstance().getActiveFIMSConnection();
        DocumentField tissueField = fimsConnection.getTissueSampleDocumentField();

        List<Query> queries = new ArrayList<Query>();

        for(Reaction reaction : reactions) {
            if(reaction.isEmpty()) {
                continue;
            }
            ReactionOptions option = reaction.getOptions();
            Query fieldQuery = Query.Factory.createFieldQuery(tissueField, Condition.EQUAL, option.getValueAsString("sampleId"));
            if(!queries.contains(fieldQuery)) {
                 queries.add(fieldQuery);
            }
        }

        if(queries.size() == 0) {
            return null;
        }
        Query orQuery = Query.Factory.createOrQuery(queries.toArray(new Query[queries.size()]), Collections.EMPTY_MAP);

        String error = "";

        try {
            List<FimsSample> docList = fimsConnection.getMatchingSamples(orQuery);
            Map<String, FimsSample> docMap = new HashMap<String, FimsSample>();
            for(FimsSample sample : docList) {
                docMap.put(sample.getFimsAttributeValue(tissueField.getCode()).toString(), sample);
            }
            for(Reaction reaction : reactions) {
                if(reaction.isEmpty()) {
                    continue;
                }
                reaction.isError = false;
                ReactionOptions op = reaction.getOptions();
                String tissueId = op.getValueAsString("sampleId");
                FimsSample currentFimsSample = docMap.get(tissueId);
                if(currentFimsSample == null) {
                    error += "The tissue sample "+tissueId+" does not exist in the database.\n";
                    reaction.isError = true;
                }
                else {
                    reaction.setFimsSample(currentFimsSample);
                }
            }

        } catch (ConnectionException e) {
            return "Could not query the FIMS database.  "+e.getMessage();
        }

        try {
            //check that the extraction id's don't already exist in the database...
            List<String> reactionOrs = new ArrayList<String>();
            for(Reaction r : reactions) {
                if(r.getId() < 0) {
                    reactionOrs.add("extractionId=?");
                }
            }
            if(reactionOrs.size() > 0) {
                String sql = "SELECT * FROM extraction WHERE "+StringUtilities.join(" OR ", reactionOrs);
                Connection connection = MooreaLabBenchService.getInstance().getActiveLIMSConnection().getConnection();

                PreparedStatement statement = connection.prepareStatement(sql);
                int count=1;
                for(Reaction r : reactions) {
                    if(r.getId() < 0) {
                        statement.setString(count, r.getExtractionId());
                        count++;
                    }
                }
                ResultSet results = statement.executeQuery();
                while(results.next()) {
                    String extractionId = results.getString("extraction.extractionId");
                    error += "The extraction "+extractionId+" already exists in the database.\n";
                }
            }
        } catch (SQLException e) {
            return "Could not qurey the LIMS database: "+e.getMessage();
        }


        if(error.length() > 0) {
            return "<html><b>There were some errors in your data:</b><br>"+error+"<br>The affected reactions have been highlighted in yellow.";
        }
        return null;
    }

}
