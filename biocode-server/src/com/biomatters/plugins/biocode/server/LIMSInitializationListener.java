package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.privateApi.PrivateApiUtilities;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
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
import com.biomatters.plugins.biocode.labbench.fims.biocode.BiocodeFIMSConnectionOptions;
import com.biomatters.plugins.biocode.labbench.lims.*;
import com.biomatters.plugins.biocode.server.security.ConnectionSettingsConstants;
import com.biomatters.plugins.biocode.server.security.LDAPConfiguration;
import com.biomatters.plugins.biocode.server.security.Projects;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import jebl.util.ProgressListener;

import javax.servlet.*;
import javax.sql.DataSource;
import javax.ws.rs.ProcessingException;
import java.io.*;
import java.net.MalformedURLException;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Responsible for making the various LIMS connections on start up
 *
 * @author Matthew Cheung
 *          <p/>
 *          Created on 20/03/14 3:18 PM
 */
public class LIMSInitializationListener implements ServletContextListener {
    public static final String JNDI_PREFIX = "java:/comp/env/";

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

    public static DataSource getDataSource() {
        try {
            if(limsConnection instanceof SqlLimsConnection) {
                return ((SqlLimsConnection) limsConnection).getDataSource();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    private static LDAPConfiguration LDAPConfiguration;

    public static LDAPConfiguration getLDAPConfiguration() { return LDAPConfiguration; }


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

            setLdapAuthenticationSettings(config);

            setFimsOptionsFromConfigFile(connectionConfig, config);

            setLimsOptionsFromConfigFile(connectionConfig, config);

            biocodeeService.connect(connectionConfig, false, false);
            fimsConnection = biocodeeService.getActiveFIMSConnection();
            if(fimsConnection == null) {
                connectFims(connectionConfig); // to get error message.  In the future BiocodeService should be changed to expose it's connection errors
            }
            limsConnection = biocodeeService.getActiveLIMSConnection();
            if(limsConnection == null) {
                connectLims(connectionConfig); // to get error message.  In the future BiocodeService should be changed to expose it's connection errors
            } else if(!(limsConnection instanceof SqlLimsConnection)) {
                throw new IllegalStateException("LIMSConnection was not a SqlLimsConnection.  Was " + limsConnection.getClass());
            }

            startProjectPopulatingThread();

            createBCIDRootsTableIfNecessary(getDataSource());
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

    private void setLdapAuthenticationSettings(Properties config) {
        boolean isLdapEnabled = Boolean.parseBoolean((String)config.get(ConnectionSettingsConstants.LDAP_ENABLED_SETTING_NAME));

        String server =             (String)config.get(ConnectionSettingsConstants.LDAP_SERVER_SETTING_NAME);
        String port =               (String)config.get(ConnectionSettingsConstants.LDAP_PORT_SETTING_NAME);
        String userDNPattern =      (String)config.get(ConnectionSettingsConstants.LDAP_USER_DN_PATTERN_SETTING_NAME);
        String userSearchBase =     (String)config.get(ConnectionSettingsConstants.LDAP_USER_SEARCH_BASE_PATTERN_SETTING_NAME);
        String userSearchFilter =   (String)config.get(ConnectionSettingsConstants.LDAP_USER_SEARCH_FILTER_SETTING_NAME);
        String groupSearchBase =    (String)config.get(ConnectionSettingsConstants.LDAP_GROUP_SEARCH_BASE_SETTING_NAME);
        String groupSearchFilter =  (String)config.get(ConnectionSettingsConstants.LDAP_GROUP_SEARCH_FILTER_SETTING_NAME);
        String groupRoleAttribute = (String)config.get(ConnectionSettingsConstants.LDAP_GROUP_ROLE_ATTRIBUTE_SETTING_NAME);
        String rolePrefix =         (String)config.get(ConnectionSettingsConstants.LDAP_ROLE_PREFIX_SETTING_NAME);
        String adminAuthority =     (String)config.get(ConnectionSettingsConstants.LDAP_ADMIN_AUTHORITY_SETTING_NAME);
        String firstnameAttribute = (String)config.get(ConnectionSettingsConstants.LDAP_FIRST_NAME_ATTRIBUTE_SETTING_NAME);
        String lastnameAttribute =  (String)config.get(ConnectionSettingsConstants.LDAP_LAST_NAME_ATTRIBUTE_SETTING_NAME);
        String emailAttribute =     (String)config.get(ConnectionSettingsConstants.LDAP_EMAIL_ATTRIBUTE_SETTING_NAME);

        if (isLdapEnabled) {
            int portAsInt;

            try {
                portAsInt = Integer.parseInt(port);
            } catch (NumberFormatException e) {
                System.err.println("Invalid LDAP configuration: Invalid port: " + port + ".");
                return;
            }

            LDAPConfiguration = new LDAPConfiguration(
                    server,
                    portAsInt,
                    userDNPattern,
                    userSearchBase,
                    userSearchFilter,
                    groupSearchBase,
                    groupSearchFilter,
                    groupRoleAttribute,
                    rolePrefix,
                    adminAuthority,
                    firstnameAttribute,
                    lastnameAttribute,
                    emailAttribute
            );
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent servletContextEvent) {
        stopProjectPopulatingThread();

        if (LDAPConfiguration != null) {
            LDAPConfiguration = null;
        }

        BiocodeService.getInstance().logOut();
    }

    private long TIME_BETWEEN_NEW_PROJECT_CHECK = 60 * 1000;
    private AtomicBoolean updatingProjects = new AtomicBoolean(false);
    private void startProjectPopulatingThread() {
        updatingProjects.set(true);
        Runnable runnable = new Runnable() {
            public void run() {
                int failureCount = 0;
                while (updatingProjects.get()) {
                    try {
                        Projects.updateProjectsFromFims(getDataSource(), fimsConnection);
                    } catch (DatabaseServiceException e) {
                        System.err.println("Encountered problem updating projects from FIMS: " + e.getMessage());
                        e.printStackTrace();
                        failureCount++;
                        if(failureCount > 10) {
                            System.err.println("Made 10 failed attempts to update projects.  Giving up.");
                            updatingProjects.set(false);
                        }
                    }
                    ThreadUtilities.sleep(TIME_BETWEEN_NEW_PROJECT_CHECK);
                }
            }
        };
        new Thread(runnable).start();
    }

    private void stopProjectPopulatingThread() {
        updatingProjects.set(false);
    }

    public static File getPropertiesFile() {
        File settingsFolder = getSettingsFolder();
        return new File(settingsFolder, propertiesFile);
    }

    private static File getSettingsFolder() {
        return new File(System.getProperty("user.home"), settingsFolderName);
    }

    private static LIMSConnection connectLims(Connection connectionConfig) throws ConnectionException, DatabaseServiceException {
        LIMSConnection limsConnection = connectionConfig.getLIMSConnection();
        limsConnection.connect(connectionConfig.getLimsOptions());
        return limsConnection;
    }

    private static FIMSConnection connectFims(Connection connectionConfig) throws ConnectionException, DatabaseServiceException {
        FIMSConnection fimsConnection = connectionConfig.getFimsConnection();
        fimsConnection.connect(connectionConfig.getFimsOptions());
        return fimsConnection;
    }

    private void setLimsOptionsFromConfigFile(Connection connectionConfig, Properties config) throws ConfigurationException, DatabaseServiceException {
        LimsConnectionOptions parentLimsOptions = (LimsConnectionOptions)connectionConfig.getLimsOptions();
        String limsType = config.getProperty("lims.type");
        if(limsType == null) {
            throw new MissingPropertyException("lims.type");
        }
        parentLimsOptions.setValue(LimsConnectionOptions.CONNECTION_TYPE_CHOOSER, limsType);
        PasswordOptions _limsOptions = parentLimsOptions.getSelectedLIMSOptions();

        if(limsType.equals(LIMSConnection.AvailableLimsTypes.remote.name())) {
            MySqlLIMSConnectionOptions limsOptions = (MySqlLIMSConnectionOptions) _limsOptions;

            if (config.getProperty("lims.jndi") == null) {
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
            } else {
                limsOptions.addStringOption("jndi", "JNDI :", "");
                limsOptions.setStringValue("jndi", JNDI_PREFIX + config.getProperty("lims.jndi"));
            }

        } else if(limsType.equals(LIMSConnection.AvailableLimsTypes.local.name())) {
            LocalLIMSConnectionOptions options = (LocalLIMSConnectionOptions) _limsOptions;
            String name = config.getProperty("lims.name", "BiocodeLIMS");  // Use a default
            options.setValue(LocalLIMSConnectionOptions.DATABASE, name);
        } else {
            throw new ConfigurationException("Invalid lims.type: " + limsType);
        }
    }

    private void setFimsOptionsFromConfigFile(Connection connectionConfig, Properties config) throws ConfigurationException, ConnectionException, DatabaseServiceException {
        String type = config.getProperty("fims.type", "biocode");
        boolean isExcel = type.equals("excel");
        boolean isTapir = type.equals("tapir");
        connectionConfig.setFims(type);
        PasswordOptions fimsOptions = connectionConfig.getFimsOptions();
        String username = config.getProperty("fims.username");
        String password = config.getProperty("fims.password");
        String jndi = config.getProperty("fims.jndi");

        if((type.equals("biocode") && jndi == null || type.equals("MySql") && jndi == null || type.equals("biocode-fims"))  && (username == null || password == null) ) {
            throw new MissingPropertyException("fims.username", "fims.password");
        }

        if(type.equals("biocode")) {
            if (jndi != null) {
                fimsOptions.addStringOption("jndi", "JNDI", "");
                fimsOptions.setStringValue("jndi", JNDI_PREFIX + jndi);
            } else {
                fimsOptions.setValue("username", username);
                fimsOptions.setValue("password", password);
            }
        } else if(type.equals("MySql")) {
            if (jndi != null) {
                fimsOptions.addStringOption("jndi", "JNDI", "");
                fimsOptions.setStringValue("jndi", JNDI_PREFIX + jndi);
            } else {
                fimsOptions.setValue(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".username", username);
                fimsOptions.setValue(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".password", password);
                fimsOptions.setValue(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".serverUrl", config.getProperty("fims.server"));
                fimsOptions.setValue(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".serverPort", config.getProperty("fims.port"));
                fimsOptions.setValue(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".database", config.getProperty("fims.database"));
                fimsOptions.setValue(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".table", config.getProperty("fims.table"));
            }

            setupTableFims(config, fimsOptions);
        } else if (isExcel) {
            if (!(fimsOptions instanceof ExcelFimsConnectionOptions)) {
                throw new IllegalStateException("expected: ExcelFimsConnectionOptions, actual: " + fimsOptions.getClass().getSimpleName());
            }
            ExcelFimsConnectionOptions excelFimsConnectionOptions = (ExcelFimsConnectionOptions) fimsOptions;
            excelFimsConnectionOptions.setStringValue(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY + "." + ExcelFimsConnectionOptions.FILE_LOCATION, config.getProperty("fims.excelPath"));
            setupTableFims(config, fimsOptions);
        } else if (isTapir) {
            fimsOptions.setValue(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".accessPoint", config.getProperty("fims.accessPoint"));
            fimsOptions.setValue(TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".schema", config.getProperty("fims.dataSharingStandard"));
        } else if (type.equals("biocode-fims")) {
            Options childOptions = fimsOptions.getChildOptions().get(
                    TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY);
            if (childOptions == null || !(childOptions instanceof BiocodeFIMSConnectionOptions)) {
                throw new IllegalStateException(childOptions == null ?
                        "No child options for key " + TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY :
                        "expected: BiocodeFIMSConnectionOptions, actual: " + fimsOptions.getClass().getSimpleName());
            }
            BiocodeFIMSConnectionOptions biocodeFimsConnectionOptions = (BiocodeFIMSConnectionOptions) childOptions;
            try {
                biocodeFimsConnectionOptions.login(config.getProperty("fims.host", "http://www.biscicol.org"), username, password);
            } catch (MalformedURLException e1) {
                throw new ConfigurationException("Could not connect to server.  Invalid URL: " + e1.getMessage());
            } catch (ProcessingException e) {
                throw new ConfigurationException("There was a problem communicating with the server: " + e.getMessage());
            }
            setFimsOptionBasedOnLabel(fimsOptions, TableFimsConnectionOptions.CONNECTION_OPTIONS_KEY + ".project", config.getProperty("fims.project"));
            setupTableFims(config, fimsOptions);
        } else {
            throw new ConfigurationException("fims.type = " + type + " is unsupported for the alpha");
        }
    }

    private void setupTableFims(Properties config, PasswordOptions fimsOptions) throws ConnectionException, MissingPropertyException {
        fimsOptions.update();
        ((TableFimsConnectionOptions) fimsOptions).autodetectTaxonFields();
        String tissueId = config.getProperty("fims.tissueId");
        String specimenId = config.getProperty("fims.specimenId");
        if (tissueId == null || specimenId == null) {
            throw new MissingPropertyException("fims.tissueId", "fims.specimenId");
        }

        setFimsOptionBasedOnLabel(fimsOptions, TableFimsConnectionOptions.TISSUE_ID, tissueId);
        setFimsOptionBasedOnLabel(fimsOptions, TableFimsConnectionOptions.SPECIMEN_ID, specimenId);
        String plate = config.getProperty("fims.plate");
        String well = config.getProperty("fims.well");
        if (plate != null && well != null) {
            fimsOptions.setValue(TableFimsConnectionOptions.STORE_PLATES, Boolean.TRUE);
            setFimsOptionBasedOnLabel(fimsOptions, TableFimsConnectionOptions.PLATE_WELL, well);
            setFimsOptionBasedOnLabel(fimsOptions, TableFimsConnectionOptions.PLATE_NAME, plate);
        }
        setMultipleOptionsFromConfig(config, "fims.taxon.", fimsOptions, TableFimsConnectionOptions.TAX_FIELDS, TableFimsConnectionOptions.TAX_COL);
        boolean enableProjects = setMultipleOptionsFromConfig(config, "fims.project.", fimsOptions, TableFimsConnectionOptions.PROJECT_FIELDS, TableFimsConnectionOptions.PROJECT_COLUMN);
        fimsOptions.setValue(TableFimsConnectionOptions.STORE_PROJECTS, enableProjects);
    }

    private boolean setMultipleOptionsFromConfig(Properties config, String configKey, PasswordOptions fimsOptions, String multipleOptionsName, String optionNameToSet) {
        // Get the refernce to the first Option in the MultipleOptions.  The rest will not have been instantiated yet.
        Options.Option firstOption = fimsOptions.getOption(multipleOptionsName + "." + 0 + "." +
                optionNameToSet);
        if (firstOption == null) {
            return false;
        }
        if (!(firstOption instanceof Options.ComboBoxOption)) {
            throw new IllegalStateException("Unexpected Option, expected ComboBoxOption but was " + firstOption.getClass().getSimpleName());
        }

        boolean setSomething = false;
        int optionIndex = 0;
        String valueToSet = config.getProperty(configKey + optionIndex);
        while (valueToSet != null) {
            setSomething = true;
            @SuppressWarnings("unchecked") Options.ComboBoxOption<Options.OptionValue> comboBoxOption = (Options.ComboBoxOption<Options.OptionValue>) firstOption;
            for (Options.OptionValue optionValue : comboBoxOption.getPossibleOptionValues()) {
                if (optionValue.getLabel().equals(valueToSet)) {
                    fimsOptions.setValue(multipleOptionsName + "." + optionIndex + "." + optionNameToSet, optionValue);
                }
            }
            optionIndex++;
            valueToSet = config.getProperty(configKey + optionIndex);
        }
        return setSomething;
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

    private void createBCIDRootsTableIfNecessary(DataSource dataSource) throws SQLException, DatabaseServiceException {
        java.sql.Connection connection = null;

        PreparedStatement selectBCIDRootsTableStatement = null;
        PreparedStatement createBCIDRootsTableStatement = null;
        PreparedStatement populateBCIDRootsTableStatement;
        try {
            connection = dataSource.getConnection();

            String selectBCIDRootsTableQuery = "SELECT * " +
                    "FROM information_schema.tables " +
                    "WHERE table_name=? " +
                    "AND table_schema IN (SELECT DATABASE())";
            String createBCIDRootsTableQuery = "CREATE TABLE " + LimsDatabaseConstants.BCID_ROOTS_TABLE_NAME +
                    "(" +
                    "type VARCHAR(255) NOT NULL," +
                    "bcid_root VARCHAR(255) NOT NULL," +
                    "PRIMARY KEY (type)" +
                    ");";
            String populateBCIDRootsTableQuery = "INSERT INTO " + LimsDatabaseConstants.BCID_ROOTS_TABLE_NAME + " " +
                    "VALUES (?, ?)";

            selectBCIDRootsTableStatement = connection.prepareStatement(selectBCIDRootsTableQuery);
            createBCIDRootsTableStatement = connection.prepareStatement(createBCIDRootsTableQuery);
            populateBCIDRootsTableStatement = connection.prepareStatement(populateBCIDRootsTableQuery);

            SqlUtilities.beginTransaction(connection);

            selectBCIDRootsTableStatement.setObject(1, LimsDatabaseConstants.BCID_ROOTS_TABLE_NAME);
            if (selectBCIDRootsTableStatement.executeQuery().next()) {
                return;
            }

            createBCIDRootsTableStatement.executeUpdate();

            if (!selectBCIDRootsTableStatement.executeQuery().next()) {
                throw new DatabaseServiceException("Could not create bcid_roots table.", false);
            }

            for (String BCIDRootType : LimsDatabaseConstants.SUPPORTED_BCID_ROOT_TYPES) {
                populateBCIDRootsTableStatement.setObject(1, BCIDRootType);
                populateBCIDRootsTableStatement.setObject(2, ""); // Empty BCID roots.
                populateBCIDRootsTableStatement.addBatch();
            }

            int[] BCIDRootsTablePopulationResult = populateBCIDRootsTableStatement.executeBatch();

            for (int BCIDRootInsertionResult : BCIDRootsTablePopulationResult) {
                if (BCIDRootInsertionResult != 1 && BCIDRootInsertionResult != PreparedStatement.SUCCESS_NO_INFO) {
                    throw new DatabaseServiceException("Could not populate bcid_roots table.", false);
                }
            }

            SqlUtilities.commitTransaction(connection);
        } finally {
            if (selectBCIDRootsTableStatement != null) {
                selectBCIDRootsTableStatement.close();
            }
            if (createBCIDRootsTableStatement != null) {
                createBCIDRootsTableStatement.close();
            }
            SqlUtilities.closeConnection(connection);
        }
    }
}