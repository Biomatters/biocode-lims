package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.privateApi.PrivateApiUtilities;
import com.biomatters.geneious.privateApi.PrivateApiUtilitiesImplementation;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.QueryFactoryImplementation;
import com.biomatters.geneious.publicapi.documents.DocumentUtilitiesImplementation;
import com.biomatters.geneious.publicapi.documents.XMLSerializerImplementation;
import com.biomatters.geneious.publicapi.plugin.*;
import com.biomatters.geneious.publicapi.utilities.*;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.PasswordOptions;
import com.biomatters.plugins.biocode.labbench.connection.Connection;
import com.biomatters.plugins.biocode.labbench.lims.*;
import com.biomatters.plugins.biocode.labbench.reaction.*;
import com.biomatters.plugins.biocode.server.security.BiocodeServerLIMSDatabaseConstants;
import com.biomatters.plugins.biocode.server.security.ConnectionSettingsConstants;
import com.biomatters.plugins.biocode.server.security.LDAPConfiguration;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import jebl.util.ProgressListener;

import javax.servlet.*;
import javax.sql.DataSource;
import java.io.*;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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
    public static final String JNDI_PREFIX = "java:/comp/env/";

    private static final String settingsFolderName = ".biocode-lims";
    private static final String defaultPropertiesFile = "default_connection.properties";
    private static final String propertiesFile = "connection.properties";

    private static LIMSConnection limsConnection;
    public static LIMSConnection getLimsConnection() {
        return limsConnection;
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

    private List<Thermocycle> pcrThermocycles = new ArrayList<Thermocycle>();
    private List<Thermocycle> cycleSequencingThermocycles = new ArrayList<Thermocycle>();
    private List<PCRCocktail> pcrCocktails = new ArrayList<PCRCocktail>();
    private List<CycleSequencingCocktail> cycleSequencingCocktails = new ArrayList<CycleSequencingCocktail>();

    @Override
    public void contextInitialized(ServletContextEvent servletContextEvent) {
        initializeGeneiousUtilities();
        LocalLIMSConnection.setDataDirectory(getSettingsFolder());

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

            setLdapAuthenticationSettings(config);

            setLimsOptionsFromConfigFile(connectionConfig, config);

            limsConnection = connectLims(connectionConfig); // to get error message.  In the future BiocodeService should be changed to expose it's connection errors

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

        try {
            pcrCocktails.addAll(limsConnection.getPCRCocktailsFromDatabase());
            cycleSequencingCocktails.addAll(limsConnection.getCycleSequencingCocktailsFromDatabase());
            pcrThermocycles.addAll(limsConnection.getThermocyclesFromDatabase(Thermocycle.Type.pcr));
            cycleSequencingThermocycles.addAll(limsConnection.getThermocyclesFromDatabase(Thermocycle.Type.cyclesequencing));
        } catch (DatabaseServiceException e) {
            initializationErrors.add(IntializationError.forException(e));
        }

        Cocktail.setCocktailGetter(new Cocktail.CocktailGetter() {
            @Override
            public List<CycleSequencingCocktail> getCycleSequencingCocktails() {
                return cycleSequencingCocktails;
            }

            @Override
            public List<PCRCocktail> getPCRCocktails() {
                return pcrCocktails;
            }
        });

        Thermocycle.setThermocycleGetter(new Thermocycle.ThermocycleGetter() {
            @Override
            public List<Thermocycle> getPCRThermocycles() {
                return pcrThermocycles;
            }

            @Override
            public List<Thermocycle> getCycleSequencingThermocycles() {
                return cycleSequencingThermocycles;
            }
        });
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
        if (limsConnection != null) {
            limsConnection.disconnect();
            limsConnection = null;
        }
        if (LDAPConfiguration != null) {
            LDAPConfiguration = null;
        }
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

    private void setLimsOptionsFromConfigFile(Connection connectionConfig, Properties config) throws ConfigurationException, DatabaseServiceException {
        LimsConnectionOptions parentLimsOptions = (LimsConnectionOptions)connectionConfig.getLimsOptions();
        String limsType = config.getProperty("lims.type");
        if (limsType == null) {
            throw new MissingPropertyException("lims.type");
        }
        parentLimsOptions.setValue(LimsConnectionOptions.CONNECTION_TYPE_CHOOSER, limsType);
        PasswordOptions _limsOptions = parentLimsOptions.getSelectedLIMSOptions();

        if (limsType.equals(LIMSConnection.AvailableLimsTypes.remote.name())) {
            MySqlLIMSConnectionOptions limsOptions = (MySqlLIMSConnectionOptions) _limsOptions;

            if (config.getProperty("lims.jndi") == null) {
                List<String> missing = new ArrayList<String>();
                for (String optionName : new String[]{"server", "port", "database", "username", "password"}) {
                    String propertyKey = "lims." + optionName;
                    String value = config.getProperty(propertyKey);
                    if (value == null) {
                        missing.add(propertyKey);
                    } else {
                        limsOptions.setValue(optionName, value);
                    }
                }
                if (!missing.isEmpty()) {
                    throw new MissingPropertyException(missing.toArray(new String[missing.size()]));
                }
            } else {
                limsOptions.addStringOption("jndi", "JNDI :", "");
                limsOptions.setStringValue("jndi", JNDI_PREFIX + config.getProperty("lims.jndi"));
            }

        } else if (limsType.equals(LIMSConnection.AvailableLimsTypes.local.name())) {
            LocalLIMSConnectionOptions options = (LocalLIMSConnectionOptions) _limsOptions;
            String name = config.getProperty("lims.name", "BiocodeLIMS");  // Use a default
            options.setValue(LocalLIMSConnectionOptions.DATABASE, name);
        } else {
            throw new ConfigurationException("Invalid lims.type: " + limsType);
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

        PrivateApiUtilitiesImplementation.setImplementation();
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
            String createBCIDRootsTableQuery = "CREATE TABLE " + BiocodeServerLIMSDatabaseConstants.BCID_ROOTS_TABLE_NAME +
                    "(" +
                    "type VARCHAR(255) NOT NULL," +
                    "bcid_root VARCHAR(255) NOT NULL," +
                    "PRIMARY KEY (type)" +
                    ");";
            String populateBCIDRootsTableQuery = "INSERT INTO " + BiocodeServerLIMSDatabaseConstants.BCID_ROOTS_TABLE_NAME + " " +
                    "VALUES (?, ?)";

            selectBCIDRootsTableStatement = connection.prepareStatement(selectBCIDRootsTableQuery);
            createBCIDRootsTableStatement = connection.prepareStatement(createBCIDRootsTableQuery);
            populateBCIDRootsTableStatement = connection.prepareStatement(populateBCIDRootsTableQuery);

            SqlUtilities.beginTransaction(connection);

            selectBCIDRootsTableStatement.setObject(1, BiocodeServerLIMSDatabaseConstants.BCID_ROOTS_TABLE_NAME);
            if (selectBCIDRootsTableStatement.executeQuery().next()) {
                return;
            }

            createBCIDRootsTableStatement.executeUpdate();

            if (!selectBCIDRootsTableStatement.executeQuery().next()) {
                throw new DatabaseServiceException("Could not create bcid_roots table.", false);
            }

            for (String BCIDRootType : BiocodeServerLIMSDatabaseConstants.SUPPORTED_BCID_ROOT_TYPES) {
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