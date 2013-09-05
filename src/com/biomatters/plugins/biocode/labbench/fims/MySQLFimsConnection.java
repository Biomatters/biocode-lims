package com.biomatters.plugins.biocode.labbench.fims;

import com.biomatters.plugins.biocode.labbench.*;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.options.PasswordOption;

import java.util.*;
import java.util.Date;
import java.io.IOException;
import java.sql.*;

/**
 * @author Steve
 * @version $Id$
 */
public class MySQLFimsConnection extends TableFimsConnection{
    private Connection connection;
    private Driver driver;
    private String tableName;
    public static final String FIELD_PREFIX = "MYSQLFIMS:";

    static Driver getDriver() throws IOException {
        try {
            Class driverClass = BiocodeService.getInstance().getDriverClass();
            if(driverClass == null) {
                String error = BiocodeService.getInstance().loadMySqlDriver(false);
                if(error != null) {
                    throw new IOException(error);
                }
                driverClass = BiocodeService.getInstance().getDriverClass();
                if(driverClass == null) {
                    throw new IOException("You need to specify the location of your MySQL Driver file");
                }
            }
            return (Driver) driverClass.newInstance();
        } catch (InstantiationException e) {
            throw new IOException("Could not instantiate SQL driver.");
        } catch (IllegalAccessException e) {
            throw new IOException("Could not access SQL driver.");
        }
    }

    public String getLabel() {
        return "Remote MySQL Database";
    }

    public String getName() {
        return "MySql";  //To change body of implemented methods use File | Settings | File Templates.
    }

    public String getDescription() {
        return "Use a MySQL database as your FIMS";
    }

    public TableFimsConnectionOptions _getConnectionOptions() {
        return new MySqlFimsConnectionOptions();
    }

    private Statement createStatement() throws SQLException {
        if(connection == null) {
            throw new SQLException("Not logged into the FIMS");
        }
        Statement statement = connection.createStatement();
        statement.setQueryTimeout(BiocodeService.STATEMENT_QUERY_TIMEOUT);
        return statement;
    }

    public void _connect(TableFimsConnectionOptions optionsa) throws ConnectionException {
        MooreaFimsConnectionOptions connectionOptions = (MooreaFimsConnectionOptions)optionsa.getChildOptions().get(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY);

        try {
            driver = MySQLFimsConnection.getDriver();
        } catch (IOException e) {
            throw new ConnectionException(e.getMessage(), e);
        }

        tableName = connectionOptions.getValueAsString("table");

        Properties properties = new Properties();
        properties.put("user", connectionOptions.getValueAsString("username"));
        properties.put("password", ((PasswordOption)connectionOptions.getOption("password")).getPassword());
        try {
            DriverManager.setLoginTimeout(20);
            String connectionStringring = "jdbc:mysql://" + connectionOptions.getValueAsString("serverUrl") + ":" +
                    connectionOptions.getValueAsString("serverPort") + "/" + connectionOptions.getValueAsString("database");
            connection = driver.connect(connectionStringring, properties);
            if(connection == null) {
                throw new SQLException("The driver "+driver.getClass().getName()+" is not the right kind of driver to connect to "+connectionOptions.getValueAsString("serverUrl"));
            }
        } catch (SQLException e1) {
            throw new ConnectionException("Failed to connect to the MySQL database: "+e1.getMessage());
        }
    }

    public List<DocumentField> getTableColumns() throws IOException {
        try {
            Statement statement = createStatement();
            ResultSet resultSet = statement.executeQuery("DESCRIBE "+tableName);
            List<DocumentField> results = new ArrayList<DocumentField>();

            while(resultSet.next() && connection != null) {
                DocumentField field = SqlUtilities.getDocumentField(resultSet, false);
                if(field != null) {
                    results.add(field);
                }
            }
            if(connection == null) {
                throw new SQLException("You are not connected to the database");
            }

            return results;
        } catch (SQLException e) {
            IOException ioException = new IOException(e.toString());
            ioException.setStackTrace(e.getStackTrace());
            throw ioException;
        }

    }

    public void _disconnect() {
        //we used to call connection.close(), but this can cause problems when the user disconnects while we're in the
        // middle of a search - instead set it to null and let the garbage collector deal with it when the queries have finished
        driver = null;
        connection = null;
    }

    public List<FimsSample> _getMatchingSamples(Query query) throws ConnectionException {
        StringBuilder queryBuilder = new StringBuilder();


        queryBuilder.append("SELECT * FROM "+tableName+" WHERE ");

        String sqlString = SqlUtilities.getQuerySQLString(query, getSearchAttributes(), FIELD_PREFIX, false);
        if(sqlString == null) {
            return Collections.emptyList();
        }
        queryBuilder.append(sqlString);


        String queryString = queryBuilder.toString();

        return getFimsSamplesFromSql(queryString, null);
    }

    private List<FimsSample> getFimsSamplesFromSql(String queryString, RetrieveCallback callback) throws ConnectionException {
        System.out.println(queryString);
        try {
            Statement statement = createStatement();
            ResultSet resultSet = statement.executeQuery(queryString);
            List<FimsSample> samples = new ArrayList<FimsSample>();
            ResultSetMetaData metadata = resultSet.getMetaData();
            while(resultSet.next() && connection != null){
                Map<String, Object> data = new HashMap<String, Object>();
                for(DocumentField f : getSearchAttributes()) {
                    if(String.class.isAssignableFrom(f.getValueType()) ) {
                        data.put(f.getCode(), resultSet.getString(f.getCode().substring(FIELD_PREFIX.length())));
                    }
                    else if(Integer.class.isAssignableFrom(f.getValueType()) ) {
                        data.put(f.getCode(), resultSet.getInt(f.getCode().substring(FIELD_PREFIX.length())));
                    }
                    else if(Double.class.isAssignableFrom(f.getValueType()) ) {
                        data.put(f.getCode(), resultSet.getDouble(f.getCode().substring(FIELD_PREFIX.length())));
                    }
                    else if(Date.class.isAssignableFrom(f.getValueType()) ) {
                        java.util.Date date = resultSet.getDate(f.getCode().substring(FIELD_PREFIX.length()));
                        if(date != null) {
                            date = new Date(date.getTime());
                        }
                        data.put(f.getCode(), date);
                    }
                    else if(Boolean.class.isAssignableFrom(f.getValueType())) {
                        data.put(f.getCode(), resultSet.getBoolean(f.getCode().substring(FIELD_PREFIX.length())));
                    }
                    else {
                        assert false : "Unrecognised field type: "+f.toString();
                    }
                }
                TableFimsSample sample = new TableFimsSample(getSearchAttributes(), getTaxonomyAttributes(), data, getTissueSampleDocumentField(), getSpecimenDocumentField());
                if(callback != null) {
                    callback.add(new TissueDocument(sample), Collections.<String, Object>emptyMap());
                }
                else {
                    samples.add(sample);
                }
            }
            resultSet.close();
            if(connection == null) {
                throw new SQLException("You are not connected to the database");
            }
            return samples;
        } catch (SQLException e) {
            throw new ConnectionException(e);
        }
    }

    public void getAllSamples(RetrieveCallback callback) throws ConnectionException {
        getFimsSamplesFromSql("SELECT * FROM "+tableName, callback);
    }

    public int getTotalNumberOfSamples() throws ConnectionException {
        String query = "SELECT count(*) FROM "+tableName;
        try {
            Statement statement = createStatement();

            ResultSet resultSet = statement.executeQuery(query);
            resultSet.next();
            return resultSet.getInt(1);

        } catch (SQLException e) {
            throw new ConnectionException(e.getMessage(), e);
        }
    }

    public boolean requiresMySql() {
        return true;
    }
}
