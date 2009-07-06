package com.biomatters.plugins.moorea.lims;

import com.biomatters.plugins.moorea.*;
import com.biomatters.plugins.moorea.plates.Plate;
import com.biomatters.plugins.moorea.plates.GelImage;
import com.biomatters.plugins.moorea.reaction.Reaction;
import com.biomatters.plugins.moorea.reaction.ExtractionReaction;
import com.biomatters.plugins.moorea.reaction.PCRReaction;
import com.biomatters.plugins.moorea.reaction.CycleSequencingReaction;
import com.biomatters.geneious.publicapi.plugin.Options;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;
import com.biomatters.geneious.publicapi.components.Dialogs;
import com.biomatters.geneious.publicapi.utilities.ThreadUtilities;

import java.sql.*;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: steve
 * Date: 27/05/2009
 * Time: 6:28:38 AM
 * To change this template use File | Settings | File Templates.
 */
public class LIMSConnection {
    Driver driver;
    Connection connection;

    public Options getConnectionOptions() {
        Options LIMSOptions = new Options(this.getClass());
        LIMSOptions.addStringOption("server", "Server Address", "");
        LIMSOptions.addIntegerOption("port", "Port", 3306, 1, Integer.MAX_VALUE);
        LIMSOptions.addStringOption("database", "Database Name", "labbench");
        LIMSOptions.addStringOption("username", "Username", "");
        LIMSOptions.addCustomOption(new PasswordOption("password", "Password", ""));
        return LIMSOptions;
    }


    public void connect(Options LIMSOptions) throws ConnectionException {
        driver = MooreaLabBenchService.getDriver();
        //connect to the LIMS
        Properties properties = new Properties();
        properties.put("user", LIMSOptions.getValueAsString("username"));
        properties.put("password", ((PasswordOption)LIMSOptions.getOption("password")).getPassword());
        try {
            DriverManager.setLoginTimeout(20);
            connection = driver.connect("jdbc:mysql://"+LIMSOptions.getValueAsString("server")+":"+LIMSOptions.getValueAsString("port"), properties);
            Statement statement = connection.createStatement();
            statement.execute("USE labbench");
        } catch (SQLException e1) {
            throw new ConnectionException(e1);
        }
    }

    public void disconnect() throws ConnectionException{
        if(connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                throw new ConnectionException(e);
            }
        }
    }

    public ResultSet executeQuery(String sql) throws TransactionException{
        try {
            PreparedStatement statement = connection.prepareStatement(sql);
            return statement.executeQuery();
        }
        catch(SQLException ex) {
            throw new TransactionException("Could not execute LIMS query", ex);
        }
    }

    public void executeUpdate(String sql) throws TransactionException{
        Savepoint savepoint = null;
        try {
            connection.setAutoCommit(false);
            savepoint = connection.setSavepoint();
            for(String s : sql.split("\n")) {
                PreparedStatement statement = connection.prepareStatement(s);
                statement.execute();
            }
            connection.commit();
        }
        catch(SQLException ex) {
            try {
                if(savepoint != null) {
                    connection.rollback(savepoint);
                }
            } catch (SQLException e) {
                throw new TransactionException("Could not execute LIMS update query", ex);
            }
            throw new TransactionException("Could not execute LIMS update query", ex);
        }
        finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignore) {}
        }
    }

    public Connection getConnection() {
        return connection;
    }

    public List<WorkflowDocument> getMatchingWorkflowDocuments(CompoundSearchQuery query, List<FimsSample> samples) throws SQLException{
        StringBuilder sql = new StringBuilder("SELECT * FROM workflow LEFT JOIN cycleSequencing ON cycleSequencing.workflow = workflow.id " +
                "LEFT JOIN pcr ON pcr.workflow = workflow.id " +
                "LEFT JOIN extraction ON workflow.extractionId = extraction.id " +
                "LEFT JOIN plate ON (plate.id = extraction.plate OR plate.id = pcr.plate OR plate.id = cycleSequencing.plate) "+
                "WHERE ");

        boolean somethingToSearch = false;
        if(samples != null && samples.size() > 0) {
            somethingToSearch = true;
            sql.append("(");
            for(int i=0; i < samples.size(); i++) {
                sql.append(" extraction.sampleId=?");
                if(i != samples.size()-1) {
                    sql.append(" OR");
                }
            }
            sql.append(")");
            if(query != null && query.getChildren().size() > 0) {
                sql.append(" AND ");
            }
        }
        if(query != null && query.getChildren().size() > 0) {
            somethingToSearch = true;
            sql.append("(");
            String mainJoin;
            switch(query.getOperator()) {
                case AND:
                    mainJoin = "AND";
                    break;
                default:
                    mainJoin = "OR";
            }
            for (int i = 0; i < query.getChildren().size(); i++) {
                if(query.getChildren().get(i) instanceof AdvancedSearchQueryTerm) {
                    AdvancedSearchQueryTerm q = (AdvancedSearchQueryTerm)query.getChildren().get(i);
                    QueryTermSurrounder termSurrounder = getQueryTermSurrounder(q);
                    sql.append(" "+ q.getField().getCode() +" "+ termSurrounder.getJoin() +" ");

                    Object[] queryValues = q.getValues();
                    for (int j = 0; j < queryValues.length; j++) {
                        Object value = queryValues[j];
                        String valueString = value.toString();
                        valueString = termSurrounder.getPrepend()+valueString+termSurrounder.getAppend()+"?";
                        sql.append(valueString);
                        if(i < queryValues.length-1) {
                            sql.append(" AND ");
                        }
                    }
                }
                if(i < query.getChildren().size()-1) {
                    sql.append(" "+mainJoin);
                }
            }
            sql.append(")");
        }
        if(!somethingToSearch) {
            return Collections.EMPTY_LIST;
        }

        //attach the values to the query
        System.out.println(sql.toString());
        PreparedStatement statement = connection.prepareStatement(sql.toString());
        int position = 1;
        if(samples != null && samples.size() > 0) {
            for(FimsSample sample : samples) {
                statement.setString(position, sample.getId());
                position++;
            }
        }
        if(query != null && query.getChildren().size() > 0) {
            for (Query q : query.getChildren()) {
                if(q instanceof AdvancedSearchQueryTerm) {
                    AdvancedSearchQueryTerm aq = (AdvancedSearchQueryTerm)q;
                    Class fclass = aq.getField().getClass();
                    Object[] queryValues = aq.getValues();
                    for (int j = 0; j < queryValues.length; j++) {
                        if(Integer.class.isAssignableFrom(fclass)) {
                            statement.setInt(position, (Integer)queryValues[j]);
                        }
                        else if(Double.class.isAssignableFrom(fclass)) {
                            statement.setDouble(position, (Double)queryValues[j]);
                        }
                        else if(String.class.isAssignableFrom(fclass)) {
                            statement.setString(position, (String)queryValues[j]);
                        }
                        else {
                            throw new SQLException("You have a field parameter with an invalid type: "+aq.getField().getName()+", "+fclass.getCanonicalName());
                        }
                        position++;
                    }
                }
            }
        }
        ResultSet resultSet = statement.executeQuery();
        Map<Integer, WorkflowDocument> workflowDocs = new HashMap<Integer, WorkflowDocument>();
        while(resultSet.next()) {
            int workflowId = resultSet.getInt("workflow.id");
            if(workflowDocs.get(workflowId) != null) {
                workflowDocs.get(workflowId).addRow(resultSet);    
            }
            else {
                WorkflowDocument doc = new WorkflowDocument(resultSet);
                workflowDocs.put(workflowId, doc);
            }
        }
        return new ArrayList<WorkflowDocument>(workflowDocs.values());
    }

    public List<PlateDocument> getMatchingPlateDocuments(CompoundSearchQuery query, List<WorkflowDocument> workflowDocuments) throws SQLException{
        if(workflowDocuments.size() == 0) {
            return Collections.EMPTY_LIST;
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM plate LEFT JOIN cycleSequencing ON cycleSequencing.plate = plate.id " +
                "LEFT JOIN pcr ON pcr.plate = plate.id " +
                "LEFT JOIN extraction ON extraction.plate = plate.id " +
                "RIGHT JOIN workflow ON (workflow.extractionId = extraction.id OR workflow.id = pcr.workflow OR workflow.id = cycleSequencing.workflow) " +
                "WHERE");

        Set<Integer> plateIds = new HashSet<Integer>();
        for(WorkflowDocument doc : workflowDocuments) {
            for(int i=0; i < doc.getNumberOfParts(); i++) {
                WorkflowDocument.ReactionPart p = (WorkflowDocument.ReactionPart)doc.getPart(i);
                Reaction reaction = p.getReaction();
                plateIds.add(reaction.getPlate());
            }
        }
        for (Iterator<Integer> it = plateIds.iterator(); it.hasNext();) {
            Integer intg = it.next();
            sql.append(" plate.id=" + intg);
            if(it.hasNext()) {
                sql.append(" OR");
            }
        }
        System.out.println(sql.toString());
        PreparedStatement statement = connection.prepareStatement(sql.toString());
        ResultSet resultSet = statement.executeQuery();
        Map<Integer, Plate> plateMap = new HashMap<Integer, Plate>();
        List<ExtractionReaction> extractionReactions = new ArrayList<ExtractionReaction>();
        List<PCRReaction> pcrReactions = new ArrayList<PCRReaction>();
        List<CycleSequencingReaction> cycleSequencingReactions = new ArrayList<CycleSequencingReaction>();
        while(resultSet.next()) {
            Plate plate;
            int plateId = resultSet.getInt("plate.id");
            if(plateMap.get(plateId) == null) {
                plate = new Plate(resultSet);  
                plateMap.put(plate.getId(), plate);
            }
            else {
                plate = plateMap.get(plateId);
            }
            Reaction reaction = plate.addReaction(resultSet);
            if(reaction instanceof ExtractionReaction) {
                extractionReactions.add((ExtractionReaction)reaction);
            }
            else if(reaction instanceof PCRReaction) {
                pcrReactions.add((PCRReaction)reaction);
            }
            else if(reaction instanceof CycleSequencingReaction) {
                cycleSequencingReactions.add((CycleSequencingReaction)reaction);
            }
        }
        final StringBuilder totalErrors = new StringBuilder("");
        if(extractionReactions.size() > 0) {
            String extractionErrors = extractionReactions.get(0).areReactionsValid(extractionReactions);
            if(extractionErrors != null) {
                totalErrors.append(extractionErrors+"\n");
            }
        }
        if(pcrReactions.size() > 0) {
            String pcrErrors = pcrReactions.get(0).areReactionsValid(pcrReactions);
            if(pcrErrors != null) {
                totalErrors.append(pcrErrors+"\n");
            }
        }
        if(cycleSequencingReactions.size() > 0) {
            String cycleSequencingErrors = cycleSequencingReactions.get(0).areReactionsValid(cycleSequencingReactions);
            if(cycleSequencingErrors != null) {
                totalErrors.append(cycleSequencingErrors+"\n");
            }
        }
        if(totalErrors.length() > 0) {
            Runnable runnable = new Runnable() {
                public void run() {
                    Dialogs.showMessageDialog("Geneious has detected the following possible errors in your database.  Please contact your system administrator for asistance.\n\n"+totalErrors, "Database errors detected", null, Dialogs.DialogIcon.WARNING);
                }
            };
            ThreadUtilities.invokeNowOrLater(runnable);
        }

        Map<Integer, List<GelImage>> gelImages = getGelImages(plateIds);
        List<PlateDocument> docs = new ArrayList<PlateDocument>();
        for(Plate plate : plateMap.values()) {
            List<GelImage> gelImagesForPlate = gelImages.get(plate.getId());
            if(gelImagesForPlate != null) {
                plate.setImages(gelImagesForPlate);
            }
            docs.add(new PlateDocument(plate));
        }

        return docs;

    }

    private Map<Integer, List<GelImage>> getGelImages(Collection<Integer> plateIds) throws SQLException{
        if(plateIds == null || plateIds.size() == 0) {
            return Collections.EMPTY_MAP;
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM gelImages WHERE (");
        for (Iterator<Integer> it = plateIds.iterator(); it.hasNext();) {
            Integer i = it.next();
            sql.append("gelImages.plate=" + i);
            if (it.hasNext()) {
                sql.append(" OR ");
            }
        }
        sql.append(")");
        PreparedStatement statement = connection.prepareStatement(sql.toString());
        ResultSet resultSet = statement.executeQuery();
        Map<Integer, List<GelImage>> map = new HashMap<Integer, List<GelImage>>();
        while(resultSet.next()) {
            GelImage image = new GelImage(resultSet);
            List<GelImage> imageList;
            List<GelImage> existingImageList = map.get(image.getPlate());
            if(existingImageList != null) {
                imageList = existingImageList;
            }
            else {
                imageList = new ArrayList<GelImage>();
                map.put(image.getPlate(), imageList);
            }
            imageList.add(image);
        }
        return map;
    }

    private static QueryTermSurrounder getQueryTermSurrounder(AdvancedSearchQueryTerm query) {
        String join = "";
        String append = "";
        String prepend = "";
        switch(query.getCondition()) {
                case EQUAL:
                    join = "=";
                    break;
                case APPROXIMATELY_EQUAL:
                    join = "LIKE";
                    break;
                case BEGINS_WITH:
                    join = "LIKE";
                    append="%";
                    break;
                case ENDS_WITH:
                    join = "LIKE";
                    prepend = "%";
                    break;
                case CONTAINS:
                    join = "LIKE";
                    append = "%";
                    prepend = "%";
                    break;
                case GREATER_THAN:
                    join = ">";
                    break;
                case GREATER_THAN_OR_EQUAL_TO:
                    join = ">=";
                    break;
                case LESS_THAN:
                    join = "<";
                    break;
                case LESS_THAN_OR_EQUAL_TO:
                    join = "<=";
                    break;
                case NOT_CONTAINS:
                    join = "NOT LIKE";
                    append = "%";
                    prepend = "%";
                    break;
                case NOT_EQUAL:
                    join = "!=";
                    break;
                case IN_RANGE:
                    join = "BETWEEN";
                    break;
            }
        return new QueryTermSurrounder(prepend, append, join);
    }

    private static class QueryTermSurrounder{
        private final String prepend, append, join;

        private QueryTermSurrounder(String prepend, String append, String join) {
            this.prepend = prepend;
            this.append = append;
            this.join = join;
        }

        public String getPrepend() {
            return prepend;
        }

        public String getAppend() {
            return append;
        }

        public String getJoin() {
            return join;
        }
    }

}
