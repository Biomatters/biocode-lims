package com.biomatters.plugins.biocode.server.security;

import com.biomatters.plugins.biocode.server.LIMSInitializationListener;
import com.biomatters.plugins.biocode.server.User;
import com.biomatters.plugins.biocode.utilities.SqlUtilities;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.ldap.userdetails.LdapUserDetailsImpl;

import javax.sql.DataSource;
import javax.ws.rs.*;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.Response;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Matthew Cheung
 *         Created on 15/05/14 5:58 PM
 */
@Path("users")
public class Users {
    private static final String LDAP_USERNAME_IDENTIFIER = "(LDAP)";
    private static BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("logged-in-user")
    public User loggedInUser() {
        return getLoggedInUser();
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    public Response list() {
        Connection connection = null;

        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            List<User> users = getUserList(connection);

            return Response.ok(new GenericEntity<List<User>>(users){}).build();
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to list users.", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    public static List<User> getUserList(Connection connection) throws SQLException {
        String query = "SELECT " + BiocodeServerLIMSDatabaseConstants.USERS_TABLE_NAME + "." +
                                   BiocodeServerLIMSDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                   BiocodeServerLIMSDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                   BiocodeServerLIMSDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                   BiocodeServerLIMSDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE + ", "  +
                                   BiocodeServerLIMSDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE + ", " +
                                   BiocodeServerLIMSDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE + ", " +
                                   BiocodeServerLIMSDatabaseConstants.IS_LDAP_ACCOUNT_COLUMN_NAME_USERS_TABLE + " " +
                       "FROM "   + getUserTableJoinedWithAuthTable();

        PreparedStatement statement = connection.prepareStatement(query);

        ResultSet resultSet = statement.executeQuery();

        List<User> users = new ArrayList<User>();

        while (resultSet.next()) {
            User user = createUserFromResultSetRow(resultSet);

            if (user != null) {
                users.add(user);
            }
        }

        resultSet.close();

        return users;
    }

    @GET
    @Produces({"application/json;qs=1", "application/xml;qs=0.5"})
    @Path("{username}")
    public User getUser(@PathParam("username")String username) {
        User user = getUserForUsername(username);

        if (user == null) {
            throw new NotFoundException("No user for " + username);
        }

        return user;
    }

    private static User getUserForUsername(String username) {
        Connection connection = null;
        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            String usernameUserTable = BiocodeServerLIMSDatabaseConstants.USERS_TABLE_NAME + "." + BiocodeServerLIMSDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE;

            String query = "SELECT " + usernameUserTable + ", " +
                                       BiocodeServerLIMSDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                       BiocodeServerLIMSDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE + ", " +
                                       BiocodeServerLIMSDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE + ", " +
                                       BiocodeServerLIMSDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE + ", " +
                                       BiocodeServerLIMSDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE + ", " +
                                       BiocodeServerLIMSDatabaseConstants.IS_LDAP_ACCOUNT_COLUMN_NAME_USERS_TABLE + " " +
                           "FROM "   + getUserTableJoinedWithAuthTable() + " " +
                           "WHERE "  + usernameUserTable + "=?";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setObject(1, username);

            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.next()) {
                throw new NotFoundException("User account '" + username + "' not found.");
            }

            return createUserFromResultSetRow(resultSet);
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to retrieve user account '" + username + "'.", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    /**
     *
     * @param resultSet The resultSet to create a user from.
     * @return A user representing the current row in the result set.  Or null if there is no user account.
     * @throws SQLException if a problem occurs communicating with the database
     */
    static User createUserFromResultSetRow(ResultSet resultSet) throws SQLException {
        if (resultSet == null) {
            throw new IllegalArgumentException("resultSet is null.");
        }

        String username = resultSet.getString(BiocodeServerLIMSDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE);

        if (username == null) {
            return null;
        }

        return new User(
                username,
                null,
                resultSet.getString(BiocodeServerLIMSDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE),
                resultSet.getString(BiocodeServerLIMSDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE),
                resultSet.getString(BiocodeServerLIMSDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE),
                resultSet.getBoolean(BiocodeServerLIMSDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE),
                resultSet.getString(BiocodeServerLIMSDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE).equals(BiocodeServerLIMSDatabaseConstants.AUTHORITY_ADMIN_CODE),
                resultSet.getBoolean(BiocodeServerLIMSDatabaseConstants.IS_LDAP_ACCOUNT_COLUMN_NAME_USERS_TABLE)
        );
    }

    private static String getUserTableJoinedWithAuthTable() {
        return BiocodeServerLIMSDatabaseConstants.USERS_TABLE_NAME + " INNER JOIN " + BiocodeServerLIMSDatabaseConstants.AUTHORITIES_TABLE_NAME +
               " ON " +
               BiocodeServerLIMSDatabaseConstants.USERS_TABLE_NAME + "." + BiocodeServerLIMSDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE +
               "=" +
               BiocodeServerLIMSDatabaseConstants.AUTHORITIES_TABLE_NAME + "." + BiocodeServerLIMSDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE;
    }

    @POST
    @Produces("text/plain")
    @Consumes({"application/json", "application/xml"})
    public String addUser(User user) {
        return addUser(LIMSInitializationListener.getDataSource(), user);
    }

    static String addUser(DataSource dataSource, User user) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();

            SqlUtilities.beginTransaction(connection);

            String addUserQuery = "INSERT INTO " + BiocodeServerLIMSDatabaseConstants.USERS_TABLE_NAME + " VALUES (?, ?, ?, ?, ?, ?, ?)";

            PreparedStatement statement = connection.prepareStatement(addUserQuery);

            statement.setObject(1, user.username);
            statement.setObject(2, encoder.encode(user.password));
            statement.setObject(3, user.firstname);
            statement.setObject(4, user.lastname);
            statement.setObject(5, user.email);
            statement.setObject(6, true);
            statement.setObject(7, false);

            int inserted = statement.executeUpdate();

            if (inserted == 1) {
                String addAuthorityQuery = "INSERT INTO " + BiocodeServerLIMSDatabaseConstants.AUTHORITIES_TABLE_NAME + " VALUES (?,?)";

                PreparedStatement insertAuth = connection.prepareStatement(addAuthorityQuery);

                insertAuth.setObject(1, user.username);
                insertAuth.setObject(2, user.isAdministrator ? BiocodeServerLIMSDatabaseConstants.AUTHORITY_ADMIN_CODE : BiocodeServerLIMSDatabaseConstants.AUTHORITY_USER_CODE);

                int insertedAuth = insertAuth.executeUpdate();

                if(insertedAuth == 1) {
                    SqlUtilities.commitTransaction(connection);

                    return "User added.";
                } else {
                    throw new InternalServerErrorException("Failed to add user account. " +
                                                           "Rows inserted into authorities database, expected: 1, actual: " + insertedAuth);
                }
            } else {
                throw new InternalServerErrorException("Failed to add user account. " +
                                                       "Rows inserted into users database, expected: 1, actual: " + inserted);
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to add user.", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    @PUT
    @Path("{username}")
    @Produces("text/plain")
    @Consumes({"application/json", "application/xml"})
    public String updateUser(@PathParam("username")String username, User user) {
        if (getUserForUsername(username).isLDAPAccount) {
            return "Cannot update LDAP accounts.";
        }

        Connection connection = null;

        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            SqlUtilities.beginTransaction(connection);

            String updateUserQuery = "UPDATE " + BiocodeServerLIMSDatabaseConstants.USERS_TABLE_NAME + " " +
                                     "SET "    + BiocodeServerLIMSDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + "=?, " +
                                                 (user.password == null ? "" : BiocodeServerLIMSDatabaseConstants.PASSWORD_COLUMN_NAME_USERS_TABLE + "=?, ") +
                                                 BiocodeServerLIMSDatabaseConstants.FIRSTNAME_COLUMN_NAME_USERS_TABLE + "=?, " +
                                                 BiocodeServerLIMSDatabaseConstants.LASTNAME_COLUMN_NAME_USERS_TABLE + "=?, " +
                                                 BiocodeServerLIMSDatabaseConstants.EMAIL_COLUMN_NAME_USERS_TABLE + "=?, "  +
                                                 BiocodeServerLIMSDatabaseConstants.ENABLED_COLUMN_NAME_USERS_TABLE + "=? " +
                                     "WHERE " +  BiocodeServerLIMSDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + "=?";

            PreparedStatement statement = connection.prepareStatement(updateUserQuery);

            int i = 1;

            statement.setObject(i++, user.username);
            if (user.password != null) {
                statement.setObject(i++, encoder.encode(user.password));
            }
            statement.setObject(i++, user.firstname);
            statement.setObject(i++, user.lastname);
            statement.setObject(i++, user.email);
            statement.setObject(i++, user.enabled);
            statement.setObject(i++, username);

            int updated = statement.executeUpdate();

            if (updated == 1) {
                String updateAuthorityQuery = "UPDATE " + BiocodeServerLIMSDatabaseConstants.AUTHORITIES_TABLE_NAME + " " +
                                              "SET "    + BiocodeServerLIMSDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE + "=? " +
                                              "WHERE "  + BiocodeServerLIMSDatabaseConstants.USERNAME_COLUMN_NAME_AUTHORITIES_TABLE + "=?";

                PreparedStatement updateAuth = connection.prepareStatement(updateAuthorityQuery);

                updateAuth.setObject(1, user.isAdministrator ?
                        BiocodeServerLIMSDatabaseConstants.AUTHORITY_ADMIN_CODE :
                        BiocodeServerLIMSDatabaseConstants.AUTHORITY_USER_CODE);
                updateAuth.setObject(2, user.username);

                int updatedAuth = updateAuth.executeUpdate();

                if (updatedAuth == 1) {
                    SqlUtilities.commitTransaction(connection);

                    return "User account '" + username + "' updated.";
                } else {
                    throw new InternalServerErrorException("Failed to update user account '" + username + "'. " +
                                                           "Rows updated in authorities database, expected: 1, actual: " + updatedAuth);
                }
            } else {
                throw new InternalServerErrorException("Failed to update user account '" + username + "'. " +
                                                       "Rows updated in users database, expected: 1, actual: " + updated);
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to update user account '" + username + "'.", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    @DELETE
    @Path("{username}")
    @Produces("text/plain")
    public String deleteUser(@PathParam("username")String username) {
        Connection connection = null;

        try {
            if (!isAdmin(getLoggedInUser())) {
                throw new ForbiddenException("Action denied: insufficient permissions.");
            }

            if (getUserForUsername(username).isLDAPAccount) {
                return "LDAP accounts cannot be deleted.";
            }

            connection = LIMSInitializationListener.getDataSource().getConnection();

            SqlUtilities.beginTransaction(connection);

            String query = "DELETE FROM " + BiocodeServerLIMSDatabaseConstants.USERS_TABLE_NAME + " " +
                           "WHERE " + BiocodeServerLIMSDatabaseConstants.USERNAME_COLUMN_NAME_USERS_TABLE + "=?";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setObject(1, username);

            int deleted = statement.executeUpdate();

            if (deleted == 1) {
                SqlUtilities.commitTransaction(connection);

                return "User account '" + username + "' deleted.";
            } else {
                throw new InternalServerErrorException("Failed to delete user account '" + username + "'. " +
                                                       "Rows deleted from users database, expected: 1, actual: " + deleted);
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to delete user account '" + username + "'.", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    /**
     * @return The current logged in {@link com.biomatters.plugins.biocode.server.User}
     */
    public static User getLoggedInUser() throws InternalServerErrorException {
        UserDetails userDetails = getLoggedInUserDetails();

        if (userDetails != null) {
            return getUserForUsername(getUsername(userDetails));
        }

        return null;
    }

    public boolean isAdmin(User user) throws InternalServerErrorException {
        String username = user.username;

        Connection connection = null;

        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            String query = "SELECT " + BiocodeServerLIMSDatabaseConstants.AUTHORITY_COLUMN_NAME_AUTHORITIES_TABLE + " " +
                           "FROM " + BiocodeServerLIMSDatabaseConstants.AUTHORITIES_TABLE_NAME + " " +
                           "WHERE " + BiocodeServerLIMSDatabaseConstants.USERNAME_COLUMN_NAME_AUTHORITIES_TABLE + "=?";

            PreparedStatement statement = connection.prepareStatement(query);

            statement.setObject(1, username);

            ResultSet resultSet = statement.executeQuery();

            if (!resultSet.next()) {
                throw new InternalServerErrorException("No authority associated with user account '" + username + "'.");
            }

            return resultSet.getString("authority").equals(BiocodeServerLIMSDatabaseConstants.AUTHORITY_ADMIN_CODE);
        } catch (SQLException e) {
            throw new InternalServerErrorException("Error verifying authority of user account '" + username + "'", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    public static void handleLDAPUserLogin() {
        UserDetails loggedInUserDetails = getLoggedInUserDetails();

        if (isLDAPUser(loggedInUserDetails)) {
            String username = loggedInUserDetails.getUsername();
            boolean isAdministrator = hasAuthority(loggedInUserDetails, LIMSInitializationListener.getLDAPConfiguration().getAdminAuthority());
            String firstname = "";
            String lastname = "";
            String email = "";

            if (loggedInUserDetails instanceof CustomLdapUserDetailsImpl) {
                CustomLdapUserDetailsImpl customLdapUserDetails = (CustomLdapUserDetailsImpl)loggedInUserDetails;

                firstname = customLdapUserDetails.getFirstname();
                lastname = customLdapUserDetails.getLastname();
                email = customLdapUserDetails.getEmail();
            }

            try {
                if (getLoggedInUser() == null) {
                    addLDAPUser(username, firstname, lastname, email, isAdministrator);
                }
            } catch (NotFoundException e) {
                addLDAPUser(username, firstname, lastname, email, isAdministrator);
            }
        }
    }

    private static UserDetails getLoggedInUserDetails() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof UserDetails) {
            return (UserDetails)principal;
        }

        return null;
    }

    private static boolean isLDAPUser(UserDetails userDetails) {
        return userDetails instanceof LdapUserDetailsImpl;
    }

    private static String getUsername(UserDetails userDetails) {
        if (isLDAPUser(userDetails)) {
            return appendWithLDAPIdentifier(userDetails.getUsername());
        }

        return userDetails.getUsername();
    }

    private static boolean addLDAPUser(String username, String firstname, String lastname, String email, boolean isAdministrator) {
        Connection connection = null;

        try {
            connection = LIMSInitializationListener.getDataSource().getConnection();

            SqlUtilities.beginTransaction(connection);

            String addUserQuery = "INSERT INTO " + BiocodeServerLIMSDatabaseConstants.USERS_TABLE_NAME + " VALUES (?, ?, ?, ?, ?, ?, ?)";

            String usernameWithLDAPIdentifier = appendWithLDAPIdentifier(username);

            PreparedStatement statement = connection.prepareStatement(addUserQuery);

            statement.setObject(1, usernameWithLDAPIdentifier);
            statement.setObject(2, "");
            statement.setObject(3, firstname);
            statement.setObject(4, lastname);
            statement.setObject(5, email);
            statement.setObject(6, true);
            statement.setObject(7, true);

            int inserted = statement.executeUpdate();

            if (inserted == 1) {
                String addAuthorityQuery = "INSERT INTO " + BiocodeServerLIMSDatabaseConstants.AUTHORITIES_TABLE_NAME + " VALUES (?,?)";

                PreparedStatement insertAuth = connection.prepareStatement(addAuthorityQuery);

                insertAuth.setObject(1, usernameWithLDAPIdentifier);
                insertAuth.setObject(2, isAdministrator ? BiocodeServerLIMSDatabaseConstants.AUTHORITY_ADMIN_CODE : BiocodeServerLIMSDatabaseConstants.AUTHORITY_USER_CODE);

                int insertedAuth = insertAuth.executeUpdate();

                if (insertedAuth == 1) {
                    SqlUtilities.commitTransaction(connection);

                    return true;
                } else {
                    throw new InternalServerErrorException("Failed to add user account. Rows inserted into authorities database, expected: 1, actual: " + insertedAuth);
                }
            } else {
                throw new InternalServerErrorException("Failed to add user account. Rows inserted into users database, expected: 1, actual: " + inserted);
            }
        } catch (SQLException e) {
            throw new InternalServerErrorException("Failed to add user.", e);
        } finally {
            SqlUtilities.closeConnection(connection);
        }
    }

    public static String appendWithLDAPIdentifier(String userName) {
        return userName + " " + LDAP_USERNAME_IDENTIFIER;
    }

    private static boolean hasAuthority(UserDetails userDetails, String authority) {
        for (GrantedAuthority authorityOfUser : userDetails.getAuthorities()) {
            if (authorityOfUser.getAuthority().equals(authority)) {
                return true;
            }
        }

        return false;
    }
}