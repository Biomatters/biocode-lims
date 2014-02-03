package com.biomatters.plugins.biocode.labbench.fims.biocode;

import com.biomatters.geneious.publicapi.databaseservice.BasicSearchQuery;
import com.biomatters.geneious.publicapi.databaseservice.DatabaseServiceException;
import com.biomatters.geneious.publicapi.databaseservice.Query;
import com.biomatters.geneious.publicapi.databaseservice.RetrieveCallback;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.plugins.biocode.labbench.ConnectionException;
import com.biomatters.plugins.biocode.labbench.FimsSample;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnection;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsConnectionOptions;
import com.biomatters.plugins.biocode.labbench.fims.TableFimsSample;

import javax.ws.rs.client.WebTarget;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Connection to the new Biocode FIMS
 *
 * Created by matthew on 1/02/14.
 */
public class BiocodeFIMSConnection extends TableFimsConnection {

    static final String HOST = "http://biscicol.org";

    @Override
    public String getLabel() {
        return "Biocode FIMS";
    }

    @Override
    public String getName() {
        return "biocode-fims";
    }

    @Override
    public String getDescription() {
        return "Connection to the new Biocode FIMS (https://code.google.com/p/biocode-fims/)";
    }

    @Override
    public List<FimsSample> _getMatchingSamples(Query query) throws ConnectionException {
        if(query instanceof BasicSearchQuery) {
            try {
                BiocodeFimsData data = BiocodeFIMSConnectionOptions.getData(target, ((BasicSearchQuery) query).getSearchText());
                List<FimsSample> samples = new ArrayList<FimsSample>();
                for (Row row : data.data) {
                    Map<String, Object> values = new HashMap<String, Object>();
                    for(int i=0; i<data.header.size(); i++) {
                        if(i < row.rowItems.size()) {  // todo should we error out?
                            values.put(TableFimsConnection.CODE_PREFIX + data.header.get(i), row.rowItems.get(i));
                        }
                    }
                    samples.add(new TableFimsSample(getSearchAttributes(), getTaxonomyAttributes(), values, getTissueSampleDocumentField(), getSpecimenDocumentField()));
                }
                return samples;
            } catch (DatabaseServiceException e) {
                throw new ConnectionException(e.getMessage(), e);
            }
        } else {
            throw new ConnectionException("The new Biocode FIMS does not support field queries yet.  Use a basic query.");
        }
    }

    @Override
    public void getAllSamples(RetrieveCallback callback) throws ConnectionException {
        // todo
    }

    @Override
    public int getTotalNumberOfSamples() throws ConnectionException {
        return 0;
    }

    @Override
    public boolean requiresMySql() {
        return false;
    }


    @Override
    public TableFimsConnectionOptions _getConnectionOptions() {
        return new BiocodeFIMSOptions();
    }

    private WebTarget target;
    @Override
    public void _connect(TableFimsConnectionOptions options) throws ConnectionException {
        if(!(options instanceof BiocodeFIMSOptions)) {
            throw new IllegalArgumentException("_connect() must be called with Options obtained from calling _getConnectionOptiions()");
        }
        BiocodeFIMSOptions fimsOptions = (BiocodeFIMSOptions) options;
        target = fimsOptions.getWebTarget();
    }

    @Override
    public void _disconnect() {
        target = null;
    }

    @Override
    public List<DocumentField> getTableColumns() throws IOException {
        List<DocumentField> fields = new ArrayList<DocumentField>();
        try {
            BiocodeFimsData fimsData = BiocodeFIMSConnectionOptions.getData(target, "nofilterwejustwantheader");
            for (String column : fimsData.header) {
                fields.add(new DocumentField(column, column, CODE_PREFIX + column, String.class, true, false));
            }
        } catch (DatabaseServiceException e) {
            throw new IOException(e.getMessage(), e);
        }
        return fields;
    }
}