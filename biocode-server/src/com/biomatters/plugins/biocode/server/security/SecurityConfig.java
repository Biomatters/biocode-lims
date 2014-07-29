package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.labbench.lims.DatabaseScriptRunner;
import com.biomatters.plugins.biocode.labbench.lims.LIMSConnection;
import com.biomatters.plugins.biocode.labbench.lims.SqlLimsConnection;
import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import javax.sql.DataSource;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.NotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
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
    private PasswordEncoder encoder = new BCryptPasswordEncoder();

    @Autowired
    public void configure(AuthenticationManagerBuilder auth) throws Exception {
        LIMSConnection limsConnection = LIMSInitializationListener.getLimsConnection();

        boolean needMemoryUsers;

        boolean hasDatabaseConnection = limsConnection instanceof SqlLimsConnection;

        if (hasDatabaseConnection) {
            DataSource dataSource = ((SqlLimsConnection) limsConnection).getDataSource();

            needMemoryUsers = createUserTablesIfNecessary(dataSource);

            auth = auth.jdbcAuthentication().dataSource(dataSource).passwordEncoder(encoder).and();

            initializeAdminUserIfNecessary(dataSource);

            updateFkTracesConstraintIfNecessary(dataSource);
        } else {
            needMemoryUsers = true;
        }

        if(!hasDatabaseConnection || needMemoryUsers) {
            // If the database connection isn't set up or users haven't been added yet then we need to also use memory
            // auth with test users.
            auth.inMemoryAuthentication().withUser("admin").password("admin").roles(Role.ADMIN.name);
        }
    }

    private void updateFkTracesConstraintIfNecessary(DataSource dataSource) throws SQLException {
        Connection connection = null;

        PreparedStatement selectFkTracesConstraintStatement = null;
        PreparedStatement dropExistingFkTracesConstraintStatement = null;
        PreparedStatement addNewFkTracesConstraintStatement = null;

        ResultSet selectFkTracesContraintResult = null;
        ResultSet selectFkTracesConstraintAfterCorrectionResult = null;
        try {
            connection = dataSource.getConnection();

            String selectFkTracesConstraintQuery = "SELECT * " +
                                                   "FROM information_schema.referential_constraints " +
                                                   "WHERE constraint_name=?";
            String dropExistingFkTracesConstraintQuery = "ALTER TABLE lims.traces " +
                                                         "DROP FOREIGN KEY FK_traces_1";
            String addNewFkTracesConstraintQuery = "ALTER TABLE lims.traces " +
                                                   "ADD CONSTRAINT FK_traces_1 " +
                                                   "    FOREIGN KEY (reaction)" +
                                                   "    REFERENCES lims.cyclesequencing (id)" +
                                                   "    ON UPDATE CASCADE " +
                                                   "    ON DELETE CASCADE";

            selectFkTracesConstraintStatement = connection.prepareStatement(selectFkTracesConstraintQuery);
            dropExistingFkTracesConstraintStatement = connection.prepareStatement(dropExistingFkTracesConstraintQuery);
            addNewFkTracesConstraintStatement = connection.prepareStatement(addNewFkTracesConstraintQuery);

            selectFkTracesConstraintStatement.setObject(1, "FK_traces_1");
            selectFkTracesContraintResult = selectFkTracesConstraintStatement.executeQuery();

            if (!selectFkTracesContraintResult.next())             {
                throw new NotFoundException("Could not find FK_traces_1 constraint.");
            }

            if (selectFkTracesContraintResult.getString("DELETE_RULE").equals("CASCADE") && selectFkTracesContraintResult.getString("UPDATE_RULE").equals("CASCADE")) {
                return;
            }

            dropExistingFkTracesConstraintStatement.executeUpdate();
            addNewFkTracesConstraintStatement.executeUpdate();
            selectFkTracesConstraintAfterCorrectionResult = selectFkTracesConstraintStatement.executeQuery();

            if (!selectFkTracesConstraintAfterCorrectionResult.next()) {
                throw new NotFoundException("Could not add FK_traces_1 constraint.");
            }

            if (!selectFkTracesConstraintAfterCorrectionResult.getString("DELETE_RULE").equals("CASCADE") || !selectFkTracesConstraintAfterCorrectionResult.getString("UPDATE_RULE").equals("CASCADE")) {
                throw new InternalServerErrorException("Could not update FK_traces_1 constraint.");
            }
        } finally {
            if (selectFkTracesConstraintStatement != null) {
                selectFkTracesConstraintStatement.close();
            }
            if (dropExistingFkTracesConstraintStatement != null) {
                dropExistingFkTracesConstraintStatement.close();
            }
            if (addNewFkTracesConstraintStatement != null) {
                addNewFkTracesConstraintStatement.close();
            }
            if (selectFkTracesContraintResult != null) {
                selectFkTracesContraintResult.close();
            }
            if (selectFkTracesConstraintAfterCorrectionResult != null) {
                selectFkTracesConstraintAfterCorrectionResult.close();
            }
            SqlUtilities.closeConnection(connection);
        }
    }

    private void initializeAdminUserIfNecessary(DataSource dataSource) throws SQLException {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();

            List<User> users = Users.getUserList(connection);

            Users usersResource = new Users();
            if (users.isEmpty()) {
                User newAdmin = new User("admin", "admin", "admin", "", "", true, true);

                usersResource.addUser(newAdmin);
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
            if(!tables.contains(LimsDatabaseConstants.USERS_TABLE_NAME.toLowerCase())) {
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

    private static void setupTables(Connection connection) throws SQLException, IOException {
        String scriptName = "add_access_control.sql";
        InputStream script = SecurityConfig.class.getResourceAsStream(scriptName);
        if(script == null) {
            throw new IllegalStateException("Missing " + scriptName + ".  Cannot set up security.");
        }
        DatabaseScriptRunner.runScript(connection, script, false, false);
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .authorizeRequests()
                .antMatchers("/biocode/projects/**").hasAuthority(LimsDatabaseConstants.AUTHORITY_ADMIN_CODE)
                .antMatchers("/biocode/users/**").hasAuthority(LimsDatabaseConstants.AUTHORITY_ADMIN_CODE)
                .antMatchers("/biocode/info/**").permitAll()
                .antMatchers("/biocode/**").authenticated()
                .anyRequest().permitAll()
            .and()
            .httpBasic();
    }
}
