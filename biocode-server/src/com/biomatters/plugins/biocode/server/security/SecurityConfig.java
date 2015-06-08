package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.labbench.lims.DatabaseScriptRunner;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import com.biomatters.plugins.biocode.server.User;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.authentication.configurers.ldap.LdapAuthenticationProviderConfigurer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

import javax.sql.DataSource;
import javax.ws.rs.InternalServerErrorException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Set;

/**
 * @author Matthew Cheung
 * @version $Id$
 *          <p/>
 *          Created on 8/05/14 1:56 PM
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    private static final String BASE_URL      = "/biocode";
    private static final String PROJECTS_URL  = BASE_URL + "/projects";
    private static final String USERS_URL     = BASE_URL + "/users";
    private static final String BCIDROOTS_URL = BASE_URL + "/bcid-roots";
    private static final String INFO_URL      = BASE_URL + "/info";

    private PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Autowired
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        LDAPConfiguration ldapConfiguration = LIMSInitializationListener.getLDAPConfiguration();
        LIMSConnection limsConnection = LIMSInitializationListener.getLimsConnection();
        boolean needMemoryUsers = true;

        if (ldapConfiguration != null) {
            authenticateWithLDAP(auth, ldapConfiguration);
            needMemoryUsers = false;
        } else if (limsConnection instanceof SqlLimsConnection) {
            DataSource dataSource = ((SqlLimsConnection) limsConnection).getDataSource();

            needMemoryUsers = createUserTablesIfNecessary(dataSource);

            auth.jdbcAuthentication().dataSource(dataSource).passwordEncoder(encoder).and();

            initializeAdminUserIfNecessary(dataSource);
        }

        if (needMemoryUsers) {
            // If the use of LDAP authentication isn't specified or the database connection isn't set up or users
            // haven't been added yet then we need to also use memory auth with test users.
            auth.inMemoryAuthentication().withUser("admin").password("admin").roles(BiocodeServerLIMSDatabaseConstants.AUTHORITY_ADMIN_CODE);
        }
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .authorizeRequests()
                .antMatchers(USERS_URL + "/**").hasAuthority(BiocodeServerLIMSDatabaseConstants.AUTHORITY_ADMIN_CODE)
                .antMatchers(INFO_URL + "/**").permitAll()
                .antMatchers(HttpMethod.GET, PROJECTS_URL + "/**").authenticated()
                .antMatchers(PROJECTS_URL + "/**").hasAuthority(BiocodeServerLIMSDatabaseConstants.AUTHORITY_ADMIN_CODE)
                .antMatchers(BASE_URL + "/**", BCIDROOTS_URL + "/**").authenticated()
                .anyRequest().permitAll().and()
                .addFilter(filter())
                .httpBasic();

        if (LIMSInitializationListener.getLDAPConfiguration() != null) {
            String LDAPAdminAuthority = LIMSInitializationListener.getLDAPConfiguration().getAdminAuthority();
            if (LDAPAdminAuthority != null && !LDAPAdminAuthority.isEmpty()) {
                http.authorizeRequests()
                        .antMatchers(PROJECTS_URL + "/**", USERS_URL + "/**").hasAuthority(LDAPAdminAuthority);
            }
        }
    }

    @Bean
    CustomAuthenticationFilter filter() throws Exception {
        return new CustomAuthenticationFilter(super.authenticationManager(), new BasicAuthenticationEntryPoint());
    }

    @Bean
    CustomLdapUserDetailsMapper userDetailsMapper() {
        return new CustomLdapUserDetailsMapper();
    }

    private void initializeAdminUserIfNecessary(DataSource dataSource) throws SQLException {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();

            boolean noJDBCUser = true;

            for (User user : Users.getUserList(connection)) {
                if (!user.isLDAPAccount) {
                    noJDBCUser = false;
                    break;
                }
            }

            if (noJDBCUser) {
                Users.addUser(dataSource, new User("admin", "admin", "admin", "", "", true, true, false));
            }
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    /**
     *
     * @param dataSource
     * @return true if there are currently no user accounts
     * @throws SQLException
     */
    public static boolean createUserTablesIfNecessary(DataSource dataSource) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            Set<String> tables = SqlUtilities.getDatabaseTableNamesLowerCase(connection);

            if (!tables.contains(BiocodeServerLIMSDatabaseConstants.WORKFLOW_PROJECT_TABLE_NAME.toLowerCase())) {
                dropAccessControlTables(connection);
                setupTables(connection);
            }

            return false;
        } catch (SQLException e) {
            e.printStackTrace();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return true;
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    private static void dropAccessControlTables(Connection connection) throws SQLException, IOException {
        String dropAccessControlTablesScriptName = "drop_access_control_tables.sql";

        InputStream dropAccessControlTablesScript = SecurityConfig.class.getResourceAsStream(dropAccessControlTablesScriptName);

        if (dropAccessControlTablesScript == null) {
            throw new IllegalStateException("Could not find " + dropAccessControlTablesScriptName);
        }

        DatabaseScriptRunner.runScript(connection, dropAccessControlTablesScript, true, false);
    }

    private static void setupTables(Connection connection) throws SQLException, IOException {
        LIMSConnection limsConnection = LIMSInitializationListener.getLimsConnection();

        if (limsConnection == null) {
            throw new InternalServerErrorException("A connection to a LIMS database could not be found.");
        }

        String scriptName = limsConnection.isLocal() ? "add_access_control_hsql.sql" : "add_access_control_mysql.sql";

        InputStream script = SecurityConfig.class.getResourceAsStream(scriptName);

        if (script == null) {
            throw new IllegalStateException("Missing " + scriptName + ".  Cannot set up security.");
        }

        DatabaseScriptRunner.runScript(connection, script, false, false);
    }

    private AuthenticationManagerBuilder authenticateWithLDAP(AuthenticationManagerBuilder auth, final LDAPConfiguration config) throws Exception {
        if (config.getServer() == null || config.getServer().isEmpty()) {
            throw new IllegalStateException("Server address was not supplied.");
        }

        if ((config.getUserDNPattern() == null || config.getUserDNPattern().isEmpty()) && (config.getUserSearchFilter() == null || config.getUserSearchFilter().isEmpty())) {
            throw new IllegalStateException("The user dn pattern and/or the user search filter must be supplied.");
        }

        if ((config.getGroupSearchBase() == null || config.getGroupSearchBase().isEmpty()) && (config.getGroupSearchFilter() == null || config.getGroupSearchFilter().isEmpty())) {
            throw new IllegalStateException("The group search base and/or the group search filter must be supplied.");
        }

        LdapAuthenticationProviderConfigurer<AuthenticationManagerBuilder> ldapAuthenticationProviderConfigurer = auth.ldapAuthentication();

        ldapAuthenticationProviderConfigurer.contextSource().url(config.getServer());
        ldapAuthenticationProviderConfigurer.contextSource().port(config.getPort());

        CustomLdapUserDetailsMapper mapper = userDetailsMapper();

        mapper.setFirstnameAttribute(config.getFirstnameAttribute());
        mapper.setLastnameAttribute(config.getLastnameAttribute());
        mapper.setEmailAttribute(config.getEmailAttribute());

        ldapAuthenticationProviderConfigurer.userDetailsContextMapper(mapper);

        if (config.getUserDNPattern() != null && !config.getUserDNPattern().isEmpty()) {
            ldapAuthenticationProviderConfigurer.userDnPatterns(config.getUserDNPattern());
        }

        if (config.getUserSearchFilter() != null && !config.getUserSearchFilter().isEmpty()) {
            ldapAuthenticationProviderConfigurer.userSearchFilter(config.getUserSearchFilter());
        }

        if (config.getUserSearchBase() != null && !config.getGroupSearchBase().isEmpty()) {
            ldapAuthenticationProviderConfigurer.userSearchBase(config.getUserSearchBase());
        }

        if (config.getGroupSearchBase() != null && !config.getGroupSearchBase().isEmpty()) {
            ldapAuthenticationProviderConfigurer.groupSearchBase(config.getGroupSearchBase());
        }

        if (config.getGroupSearchFilter() != null && !config.getGroupSearchFilter().isEmpty()) {
            ldapAuthenticationProviderConfigurer.groupSearchFilter(config.getGroupSearchFilter());
        }

        if (config.getGroupRoleAttribute() != null && !config.getGroupRoleAttribute().isEmpty()) {
            ldapAuthenticationProviderConfigurer.groupRoleAttribute(config.getGroupRoleAttribute());
        }

        if (config.getRolePrefix() != null && !config.getRolePrefix().isEmpty()) {
            ldapAuthenticationProviderConfigurer.rolePrefix(config.getRolePrefix());
        }

        return ldapAuthenticationProviderConfigurer.and();
    }
}