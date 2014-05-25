package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.privateApi.PrivateApiUtilities;
import com.biomatters.geneious.publicapi.databaseservice.QueryFactoryImplementation;
import com.biomatters.geneious.publicapi.documents.DocumentUtilitiesImplementation;
import com.biomatters.geneious.publicapi.documents.XMLSerializerImplementation;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.*;
import com.biomatters.plugins.biocode.labbench.BiocodeService;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.connection.Connection;
import com.biomatters.plugins.biocode.labbench.fims.*;
import com.biomatters.plugins.biocode.labbench.lims.*;
import jebl.util.ProgressListener;

import javax.servlet.*;
import javax.sql.DataSource;
import java.io.*;
import java.util.*;

/**
 * Responsible for making the various LIMS connections on start up
 *
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 20/03/14 3:18 PM
 */
public class LIMSInitializationListener implements ServletContextListener {

    private static final String settingsFolderName = ".biocode-lims";
    private static final String defaultPropertiesFile = "default_connection.properties";
    private static final String propertiesFile = "connection.properties";

    private static LIMSConnection limsConnection;
    public static LIMSConnection getLimsConnection() {
        return limsConnection;
    }

    private static FIMSConnection fimsConnection;
    public static FIMSConnection getFimsConnection() {
        return fimsConnection;
    }

    private static DataSource dataSource;

    public static DataSource getDataSource() {
        return dataSource;
    }

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        initializeGeneiousUtilities();

        BiocodeService biocodeeService;

        File connectionPropertiesFile = getPropertiesFile();
        File dataDir = connectionPropertiesFile.getParentFile();
        if(!dataDir.exists()) {
            boolean created = dataDir.mkdirs();
            if(!created) {
                initializationErrors.add(new IntializationError("Failed to create config directory", "Failed to create directory " + dataDir.getAbsolutePath()));
                return;
            }
        }
        if(!connectionPropertiesFile.exists()) {
            File defaultFile = new File(getWarDirectory(servletContextEvent.getServletContext()), defaultPropertiesFile);
            if(defaultFile.exists()) {
                try {
                    FileUtilities.copyFile(defaultFile, connectionPropertiesFile, FileUtilities.TargetExistsAction.Skip, ProgressListener.EMPTY);
                } catch (IOException e) {
                    initializationErrors.add(new IntializationError("File Access Error",
                            "Failed to copy default properties to " + connectionPropertiesFile.getAbsolutePath() + ": " + e.getMessage()));
                }
            }
        }
        Connection connectionConfig = new Connection("forServer");
        try {
            Properties config = new Properties();
            config.load(new FileInputStream(connectionPropertiesFile));

            biocodeeService = BiocodeService.getInstance();
            biocodeeService.setDataDirectory(dataDir);

            setFimsOptionsFromConfigFile(connectionConfig, config);

            setLimsOptionsFromConfigFile(connectionConfig, config);

            biocodeeService.connect(connectionConfig, false);
            fimsConnection = biocodeeService.getActiveFIMSConnection();
            if(fimsConnection == null) {
                connectFims(connectionConfig); // to get error message.  In the future BiocodeService should be changed to expose it's connection errors
            }
            limsConnection = biocodeeService.getActiveLIMSConnection();
            if(limsConnection == null) {
                connectLims(connectionConfig); // to get error message.  In the future BiocodeService should be changed to expose it's connection errors
            } else if(limsConnection instanceof SqlLimsConnection) {
                dataSource = ((SqlLimsConnection) limsConnection).getDataSource();
            } else {
                throw new IllegalStateException("LIMSConnection was not a SqlLimsConnection.  Was " + limsConnection.getClass());
            }
        } catch (IOException e) {
            initializationErrors.add(new IntializationError("Configuration Error",
                    "Failed to load properties file from " + connectionPropertiesFile.getAbsolutePath() + ": " + e.getMessage()));
        } catch(MissingPropertyException e) {
            initializationErrors.add(new IntializationError("Missing Configuration Property",
                    e.getMessage() + " in configuration file (" + connectionPropertiesFile.getAbsolutePath() + ")"));
        } catch (Exception e) {
            initializationErrors.add(IntializationError.forException(e));
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        // todo
    }

    public static File getPropertiesFile() {
        File settingsFolder = getSettingsFolder();
        return new File(settingsFolder, propertiesFile);
    }

    private static File getSettingsFolder() {
        return new File(System.getProperty("user.home"), settingsFolderName);
    }

    private static LIMSConnection connectLims(Connection connectionConfig) throws ConnectionException {
        LIMSConnection limsConnection = connectionConfig.getLIMSConnection();
        limsConnection.connect(connectionConfig.getLimsOptions());
        return limsConnection;
    }

    private static FIMSConnection connectFims(Connection connectionConfig) throws ConnectionException {
        FIMSConnection fimsConnection = connectionConfig.getFimsConnection();
        fimsConnection.connect(connectionConfig.getFimsOptions());
        return fimsConnection;
    }

    private void setLimsOptionsFromConfigFile(Connection connectionConfig, Properties config) throws ConfigurationException {
        LimsConnectionOptions parentLimsOptions = (LimsConnectionOptions)connectionConfig.getLimsOptions();
        String limsType = config.getProperty("lims.type");
        if(limsType == null) {
            throw new MissingPropertyException("lims.type");
        }
        parentLimsOptions.setValue(LimsConnectionOptions.CONNECTION_TYPE_CHOOSER, limsType);
        PasswordOptions _limsOptions = parentLimsOptions.getSelectedLIMSOptions();

        if(limsType.equals(LIMSConnection.AvailableLimsTypes.remote.name())) {
            MySqlLIMSConnectionOptions limsOptions = (MySqlLIMSConnectionOptions) _limsOptions;

            List<String> missing = new ArrayList<String>();
            for (String optionName : new String[]{"server", "port", "database", "username", "password"}) {
                String propertyKey = "lims." + optionName;
                String value = config.getProperty(propertyKey);
                if(value == null) {
                    missing.add(propertyKey);
                } else {
                    limsOptions.setValue(optionName, value);
                }
            }
            if(!missing.isEmpty()) {
                throw new MissingPropertyException(missing.toArray(new String[missing.size()]));
            }
        } else if(limsType.equals(LIMSConnection.AvailableLimsTypes.local.name())) {
            LocalLIMSConnectionOptions options = (LocalLIMSConnectionOptions) _limsOptions;
            String name = config.getProperty("lims.name", "BiocodeLIMS");  // Use a default
            options.setValue(LocalLIMSConnectionOptions.DATABASE, name);
        } else {
            throw new ConfigurationException("Invalid lims.type: " + limsType);
        }
    }

    private void setFimsOptionsFromConfigFile(Connection connectionConfig, Properties config) throws ConfigurationException, ConnectionException {
        String type = config.getProperty("fims.type", "biocode");
        boolean isExcel = type.equals("excel");
        connectionConfig.setFims(type);
        PasswordOptions fimsOptions = connectionConfig.getFimsOptions();
        String username = config.getProperty("fims.username");
        String password = config.getProperty("fims.password");
        if(!isExcel && (username == null || password == null)) {
            throw new MissingPropertyException("fims.username", "fims.password");
        }
        if(type.equals("biocode")) {
            fimsOptions.setValue("username", username);
            fimsOptions.setValue("password", password);
        } else if(type.equals("MySql")) {
            fimsOptions.setValue(MySqlFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".username", username);
            fimsOptions.setValue(MySqlFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".password", password);
            fimsOptions.setValue(MySqlFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".serverUrl", config.getProperty("fims.server"));
            fimsOptions.setValue(MySqlFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".serverPort", config.getProperty("fims.port"));
            fimsOptions.setValue(MySqlFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".database", config.getProperty("fims.database"));
            fimsOptions.setValue(MySqlFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".table", config.getProperty("fims.table"));

            setupTableFims(config, fimsOptions, MySQLFimsConnection.FIELD_PREFIX);
        } else if (isExcel) {
            if (!(fimsOptions instanceof ExcelFimsConnectionOptions)) {
                throw new IllegalStateException("expected: ExcelFimsConnectionOptions, actual: " + fimsOptions.getClass().getSimpleName());
            }
            ExcelFimsConnectionOptions excelFimsConnectionOptions = (ExcelFimsConnectionOptions) fimsOptions;
            excelFimsConnectionOptions.setStringValue(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY + "." + ExcelFimsConnectionOptions.FILE_LOCATION, config.getProperty("fims.excelpath"));
            setupTableFims(config, fimsOptions, "");
        } else {
            throw new ConfigurationException("fims.type = " + type + " is unsupported for the alpha");
        }
    }

    private void setupTableFims(Properties config, PasswordOptions fimsOptions, String prefix) throws ConnectionException, MissingPropertyException {
        fimsOptions.update();
        ((TableFimsConnectionOptions) fimsOptions).autodetectTaxonFields();
        String tissueId = config.getProperty("fims.tissueId");
        String specimenId = config.getProperty("fims.specimenId");
        if (tissueId == null || specimenId == null) {
            throw new MissingPropertyException("fims.tissueId", "fims.specimenId");
        }

        setFimsOptionBasedOnLabel(fimsOptions, MySqlFimsConnectionOptions.TISSUE_ID, prefix + tissueId);
        setFimsOptionBasedOnLabel(fimsOptions, MySqlFimsConnectionOptions.SPECIMEN_ID, prefix + specimenId);
        String plate = config.getProperty("fims.plate");
        String well = config.getProperty("fims.well");
        if (plate != null && well != null) {
            fimsOptions.setValue(MySqlFimsConnectionOptions.STORE_PLATES, Boolean.TRUE);
            setFimsOptionBasedOnLabel(fimsOptions, MySqlFimsConnectionOptions.PLATE_WELL, prefix + well);
            setFimsOptionBasedOnLabel(fimsOptions, MySqlFimsConnectionOptions.PLATE_NAME, prefix + plate);
        }
        int index = 0;
        String taxonField = config.getProperty("fims.taxon." + index);
        while (taxonField != null) {
            setFimsOptionBasedOnLabel(fimsOptions, MySqlFimsConnectionOptions.TAX_FIELDS + "." + index + "." +
                    MySqlFimsConnectionOptions.TAX_COL, prefix + taxonField);
            index++;
            taxonField = config.getProperty("fims.taxon." + index);
        }
    }

    private void setFimsOptionBasedOnLabel(PasswordOptions fimsOptions, String optionName, String labelToLookFor) {
        Options.Option option = fimsOptions.getOption(optionName);
        if (option == null) {
            return;
        }
        if (!(option instanceof Options.ComboBoxOption)) {
            throw new IllegalStateException("Unexpected Option, expected ComboBoxOption but was " + option.getClass().getSimpleName());
        }
        Options.ComboBoxOption<Options.OptionValue> comboBoxOption = (Options.ComboBoxOption<Options.OptionValue>) option;
        for (Options.OptionValue optionValue : comboBoxOption.getPossibleOptionValues()) {
            if (optionValue.getLabel().equals(labelToLookFor)) {
                fimsOptions.setValue(optionName, optionValue);
            }
        }
    }

    /**
     * Initializes parts of the Geneious core runtime.  As the Biocode LIMS plugin was originally written as a Geneious
     * plugin a lot of the code is tightly coupled with classes such as {@link com.biomatters.geneious.publicapi.databaseservice.Query} and
     * {@link com.biomatters.geneious.publicapi.plugin.Options}.  So we must initialize the runtime to make use of these
     * classes within the server.
     */
    private void initializeGeneiousUtilities() {
        PrivateApiUtilities.setRunningFromServlet(true);
        HeadlessOperationUtilities.setHeadless(true);
        TestGeneious.setNotRunningTest();
        TestGeneious.setRunningApplication();

        DocumentUtilitiesImplementation.setImplementation();
        XMLSerializerImplementation.setImplementation();
        QueryFactoryImplementation.setImplementation();
        PluginUtilitiesImplementation.setImplementation();
    }

    private static class IntializationError {
        private String title;
        private String message;
        private String details;

        private IntializationError() {
        }

        private IntializationError(String title, String message) {
            this(title, message, null);
        }

        private IntializationError(String title, String message, String details) {
            this.title = title;
            this.message = message;
            this.details = details;
        }

        static IntializationError forException(Exception e) {
            IntializationError error = new IntializationError();
            error.title = getExceptionCategory(e);
            error.message = e.getMessage();

            StringWriter stringWriter = new StringWriter();
            e.printStackTrace(new PrintWriter(stringWriter));
            error.details = stringWriter.toString();

            return error;
        }
    }

    private static List<IntializationError> initializationErrors = new ArrayList<IntializationError>();
    public static String getErrorText() {
        if(initializationErrors.isEmpty()) {
            return null;
        }
        StringBuilder text = new StringBuilder("The server encountered the following errors starting up.<br>");
        for (IntializationError error : initializationErrors) {
            text.append("<b>").append(error.title).append("</b>: ");
            text.append(error.message).append("<br>");
            if(error.details != null) {
                text.append("<b>Details</b>:<br>");
                text.append(error.details).append("<br>");
            }
        }
        return text.toString();
    }

    private static String getExceptionCategory(Exception e) {
        String simpleName = e.getClass().getSimpleName();
        int toCut = simpleName.indexOf("Exception");
        if(toCut != -1) {
            simpleName = simpleName.substring(0, toCut);
        }
        return simpleName + " Error";

    }


    public File getWarDirectory(ServletContext context) {
        File warDir = new File(context.getRealPath("."));
        if(warDir.isDirectory()) {
            return warDir;
        } else {
            throw new IllegalStateException("Context directory does not exist!");
        }
    }

    private class ConfigurationException extends Exception {
        private ConfigurationException(String message) {
            super(message);
        }
    }

    private class MissingPropertyException extends ConfigurationException {
        String[] missingValues;
        private MissingPropertyException(String... missing) {
            super("Must specify " + StringUtilities.humanJoin(Arrays.asList(missing)));
            missingValues = missing;
        }
    }
}
