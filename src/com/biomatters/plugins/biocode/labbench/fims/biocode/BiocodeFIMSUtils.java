package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.utilities.StringUtilities;
import com.biomatters.geneious.publicapi.utilities.SystemUtilities;
import com.biomatters.plugins.biocode.labbench.fims.FusionTableUtils;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.java6.auth.oauth2.FileCredentialStore;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.fusiontables.FusiontablesScopes;
import org.glassfish.jersey.client.oauth2.ClientIdentifier;
import org.glassfish.jersey.client.oauth2.OAuth2ClientSupport;
import org.glassfish.jersey.client.oauth2.OAuth2CodeGrantFlow;
import org.glassfish.jersey.client.oauth2.TokenResult;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.*;
import javax.ws.rs.core.*;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;

/**
 * @author Matthew Cheung
 *         Created on 7/02/14 5:51 AM
 */
public class BiocodeFIMSUtils {

    static boolean login(String hostname, String username, String password) throws MalformedURLException {
        WebTarget path = ClientBuilder.newClient().target(hostname).path("id/authenticationService/login");

        Invocation.Builder request = path
                .request(MediaType.TEXT_HTML_TYPE);
        Response response = request.post(
                Entity.entity(new Form().param("username", username).param("password", password), MediaType.TEXT_PLAIN_TYPE));
        response.close();  // Unfortunately the login service doesn't provide any meaningful response.  It just redirects to the main page.
        return true;  // So we'll have to assume the login worked for now
    }

    static WebTarget getWebTarget(String project, String graph) {
        WebTarget target = ClientBuilder.newClient().target("http://biscicol.org");
        target = target.path("biocode-fims/rest/query/json").
                queryParam("project_id", project);
        if(graph != null) {
            target = target.queryParam("graphs", graph);
        }
        return target;
    }

    public static void main(String[] args) throws DatabaseServiceException {
        List<Project> expeditions = getProjects();
        for (Project expedition : expeditions) {
            System.out.println(expedition.title);
        }
    }
    static List<Project> getProjects() throws DatabaseServiceException {

        WebTarget target = ClientBuilder.newClient().target("http://biscicol.org");
        Invocation.Builder request = target.path("id/projectService/listUserProjects").request(MediaType.APPLICATION_JSON_TYPE);
        try {
            ProjectList fromService = request.get(ProjectList.class);
            List<Project> returnList = new ArrayList<Project>();
            for (Project project : fromService.getProjects()) {
                if(project.code != null) {
                    returnList.add(project);
                }
            }
            return returnList;
        } catch(WebApplicationException e) {
            throw new DatabaseServiceException(e, "Problem contacting biscicol.org: " + e.getMessage(), true);
        } catch(ProcessingException e) {
            // Unfortunately the BCID service doesn't use HTTP error codes and reports errors by returning JSON in a
            // different format than the regular result.  So we have to do some special parsing.
            List<String> errors = request.get(new GenericType<List<String>>() { });
            if(errors != null && !errors.isEmpty()) {
                if(errors.size() == 1) {
                    throw new DatabaseServiceException(errors.get(0), false);
                } else {
                    throw new DatabaseServiceException("Service returned: " + StringUtilities.join("\n", errors), false);
                }
            }
            throw new DatabaseServiceException(e, e.getMessage(), true);
        }
    }

    static List<Graph> getGraphsForProject(String id) throws DatabaseServiceException {
        try {
            WebTarget target = ClientBuilder.newClient().target("http://biscicol.org");
            Invocation.Builder request = target.path("id/projectService/graphs").path(id).request(MediaType.APPLICATION_JSON_TYPE);
            return request.get(GraphList.class).getData();
        } catch(WebApplicationException e) {
            throw new DatabaseServiceException(e, "Problem contacting biscicol.org: " + e.getMessage(), true);
        } catch(ProcessingException e) {
            throw new DatabaseServiceException(e, e.getMessage(), true);  // todo
        }
    }

    static final String EXPEDITION_NAME = "Expedition";

    static BiocodeFimsData getData(String project, Graph graph, String filter) throws DatabaseServiceException {
        if(filter != null && filter.contains(",")) {
            try {
                filter = URLEncoder.encode(filter, "UTF-8");
            } catch(UnsupportedEncodingException e) {
                // todo
                e.printStackTrace();
            }
        }

        List<Graph> graphsToSearch = new ArrayList<Graph>();
        if(graph != null) {
            graphsToSearch.add(graph);
        } else {
            for (Graph g : getGraphsForProject(project)) {
                graphsToSearch.add(g);
            }
        }

        BiocodeFimsData data = new BiocodeFimsData();
        for (Graph g : graphsToSearch) {
            BiocodeFimsData toAdd = getBiocodeFimsData(project, g.getGraphId(), filter);
            if(data.header == null || data.header.isEmpty()) {
                data.header = toAdd.header;
                data.header.add(0, EXPEDITION_NAME);
                data.data = new ArrayList<Row>();
            }
            for (Row row : toAdd.data) {
                row.rowItems.add(0,g.getExpeditionTitle());
                data.data.add(row);
            }
        }
        if(graphsToSearch.isEmpty()) {
            data.header = Collections.emptyList();
            data.data = Collections.emptyList();
        }

        return data;
    }

    private static BiocodeFimsData getBiocodeFimsData(String project, String graph, String filter) throws DatabaseServiceException {
        try {
            WebTarget target = getWebTarget(project, graph);
            if(filter != null) {
                target = target.queryParam("filter", filter);
            }
            System.out.println(target.getUri());
            Invocation.Builder request = target.
                    request(MediaType.APPLICATION_JSON_TYPE);
            return request.get(BiocodeFimsData.class);
        } catch (NotFoundException e) {
            throw new DatabaseServiceException("No data found.", false);
        } catch (WebApplicationException e) {
            throw new DatabaseServiceException(e, "Encountered an error communicating with " + BiocodeFIMSConnection.HOST, false);
        }
    }
}
