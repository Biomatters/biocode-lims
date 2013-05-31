package com.biomatters.plugins.biocode.labbench.fims.tapir;

import com.biomatters.geneious.publicapi.databaseservice.AdvancedSearchQueryTerm;
import com.biomatters.geneious.publicapi.databaseservice.CompoundSearchQuery;
import com.biomatters.geneious.publicapi.documents.DocumentField;
import com.biomatters.geneious.publicapi.plugin.Geneious;
import com.biomatters.plugins.biocode.labbench.fims.TAPIRFimsConnection;
import org.jdom.*;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

/**
 * @author steve
 * @version $Id: 19/08/2009 12:01:40 PM steve $
 */
public class TAPIRClient {
    private TapirSchema schema;
    private URL accessPoint;

    public TAPIRClient(TapirSchema schema, String accessPoint) {
        this.schema = schema;
        try {
            this.accessPoint = new URL(accessPoint);
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("You must enter a valid URL", e);
        }
    }


    public List<DocumentField> getSearchAttributes() throws JDOMException, IOException {
        Element query = padQueryXML(new Element("capabilities"));
        Element response = queryServer(query);
        Element capabilities = response.getChild("capabilities", response.getNamespace());
        Element concepts = capabilities.getChild("concepts", capabilities.getNamespace());
        List<Element> conceptSchema = concepts.getChildren("schema", concepts.getNamespace());
        List<DocumentField> fields = new ArrayList<DocumentField>();
        for(Element schema : conceptSchema) {
            List<Element> fieldElements = schema.getChildren("mappedConcept", schema.getNamespace());
            for(Element fieldElement : fieldElements) {
                XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
                out.output(fieldElement,  System.out);
                System.out.println();
                String code = fieldElement.getAttributeValue("id");
                String name;
                if(code.indexOf("/") >= 0) {
                    name = code.substring(code.lastIndexOf("/")+1);
                }
                else {
                    name = code;
                }
                fields.add(new DocumentField(name, "", code, getFieldClass(fieldElement), true, false));
            }
        }
        return fields;
    }

    private Class getFieldClass(Element field) {
        String attribute = field.getAttributeValue("datatype");
        if(attribute.endsWith("date")) {
            return Date.class;
        }
        else if(attribute.endsWith("decimal")) {
            return Double.class;
        }

        return String.class;
    }


    /**
     * surrounds the query with the tapir XML that goes around every request...
     * @param queryToSurround
     * @return
     */
    private Element padQueryXML(Element queryToSurround) {
        Element request = new Element("request");
        Namespace namespace = Namespace.getNamespace("http://rs.tdwg.org/tapir/1.0");
        request.setNamespace(namespace);
        Element headerElement = new Element("header", namespace);
        Element source = new Element("source", namespace);
        source.setAttribute("sendtime", new Date().toString());
        source.addContent(new Element("software").setAttribute("name", "Geneious").setAttribute("version", Geneious.getVersion()));
        headerElement.addContent(source);
        request.addContent(headerElement);
        if(queryToSurround != null) {
            queryToSurround.setNamespace(namespace);
            request.addContent(queryToSurround);
        }
        return request;
    }



    private Element queryServer(Element query) throws IOException, JDOMException {
        URLConnection connection = accessPoint.openConnection();

        XMLOutputter outputter = new XMLOutputter(Format.getPrettyFormat());
        StringWriter stringWriter = new StringWriter();
        outputter.output(query, stringWriter);
        String xmlOut = stringWriter.toString();

        HttpURLConnection httpConnection = (HttpURLConnection)connection;

        httpConnection.setRequestMethod("POST");

        httpConnection.setDoInput(true);
        httpConnection.setDoOutput(true);
        httpConnection.setRequestMethod("POST");
        httpConnection.setRequestProperty("Content-Type", "text/xml; charset=UTF-8");
        httpConnection.setRequestProperty( "Content-Length", String.valueOf( xmlOut.length() ) );
        BufferedOutputStream out = new BufferedOutputStream(connection.getOutputStream());
        out.write(xmlOut.getBytes("UTF-8"));
        out.flush();
        out.close();

        //read in the response...
        BufferedReader in = new BufferedReader(new InputStreamReader((connection.getInputStream())));
        String xml = "";
        String buffer;
        while((buffer = in.readLine()) != null){
            xml += buffer+"\n";
        }
        in.close();
        System.out.println(xml);

        //parse the response to a JDOM element
        SAXBuilder sbuilder = new SAXBuilder();
        Document doc = sbuilder.build(new StringReader(xml));
        return doc.detachRootElement();
    }

    public Element getStructure(List<DocumentField> fieldsToSearch) {
        Element structure = new Element("structure");

        Namespace namespace = Namespace.getNamespace("xs", "http://www.w3.org/2001/XMLSchema");
        Element schema = new Element("schema", namespace);
        schema.setAttribute("targetNamespace", "http://example.net/simple_specimen");
        //schema.setAttribute("xsi:schemaLocation", "http://www.w3.org/2001/XMLSchema http://www.w3.org/2001/XMLSchema.xsd");
        structure.addContent(schema);

        Element records = new Element("element", namespace).setAttribute("name", "records");
        Element element = new Element("element", namespace).setAttribute("name", "record").setAttribute("minOccurs", "0").setAttribute("maxOccurs", "unbounded").setAttribute("type", "unitType");
        records.addContent(new Element("complexType", namespace).addContent(new Element("sequence", namespace).addContent(element)));
        schema.addContent(records);

        Element unitType = new Element("complexType", namespace).setAttribute("name", "unitType");
        unitType.addContent(new Element("attribute", namespace).setAttribute("name", "catnum").setAttribute("type", "xs:int").setAttribute("use", "required"));
        Element sequence = new Element("sequence", namespace);
        unitType.addContent(sequence);
        for(DocumentField field : fieldsToSearch) {
            if(field.equals(new TAPIRFimsConnection().getTissueSampleDocumentField())) {
                continue;
            }
            Element unitElement = new Element("element", namespace);
            unitElement.setAttribute("name", field.getName());
            unitElement.setAttribute("type", getElementType(field.getValueType()));
            unitElement.setAttribute("minOccurs", "0");
            sequence.addContent(unitElement);
        }
        schema.addContent(unitType);


        return structure;
    }

    public String getElementType(Class dataType) {
        if(Double.class.equals(dataType)) {
            return "xs:decimal";
        }
        else if(Integer.class.equals(dataType)) {
            return "xs:int";
        }
        else if(Date.class.equals(dataType)) {
            return "xs:date";
        }
        return "xs:string";
    }

    public Element searchTapirServer(List<AdvancedSearchQueryTerm> queries, CompoundSearchQuery.Operator operator, List<DocumentField> fieldsToSearch) throws JDOMException, IOException {
        Element searchXML = generateSearchXML(queries, operator, fieldsToSearch);
        searchXML = padQueryXML(searchXML);
        XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
        out.output(searchXML, System.out);
        System.out.println("---------------------------------");
        return queryServer(searchXML);
    }

    public static void clearNamespace(Element e) {
        e.setNamespace(null);
        for(Element el : e.getChildren()) {
            clearNamespace(el);
        }
    }



    public Element generateSearchXML(List<AdvancedSearchQueryTerm> queries, CompoundSearchQuery.Operator operator, List<DocumentField> fieldsToSearch) {
        Element searchElement = new Element("search");
        searchElement.setAttribute("count", "true").setAttribute("start", "0").setAttribute("envelope", "true");

        Element outputModel = new Element("outputModel");
        searchElement.addContent(outputModel);


        outputModel.addContent(getStructure(fieldsToSearch));

        outputModel.addContent(new Element("indexingElement").setAttribute("path", "/records/record"));

        Element mappingElement = new Element("mapping");

        Element catNumElement = new Element("node").setAttribute("path", "/records/record/@catnum");
        catNumElement.addContent(new Element("concept").setAttribute("id", schema.getSpecimenIdField()));
        mappingElement.addContent(catNumElement);

        for(DocumentField field : fieldsToSearch) {
            Element unitElement = new Element("node").setAttribute("path", "/records/record/"+field.getName());
            unitElement.addContent(new Element("concept").setAttribute("id", field.getCode()));
            mappingElement.addContent(unitElement);
        }
        outputModel.addContent(mappingElement);

        Element filterElement = new Element("filter");
        Element filterParent = new Element(operator == CompoundSearchQuery.Operator.AND ? "and" : "or");
        //filterElement.addContent(filterParent);
        if(queries.size() > 1) {
            for(AdvancedSearchQueryTerm q : queries) {
                if(q.getValues().length == 1 && q.getValues()[0].toString().trim().length() == 0) {
                    continue;
                }
                filterParent.addContent(getQueryXml(q));
            }
            if(!filterParent.getChildren().isEmpty()) {
                filterElement.addContent(filterParent);
            }
        }
        else {
            filterElement.addContent(getQueryXml(queries.get(0)));
        }
        searchElement.addContent(filterElement);
        return searchElement;
    }

    private Element getQueryXml(AdvancedSearchQueryTerm query) {
        Element conditionElement;
        boolean not = false;
        switch(query.getCondition()) {
            case NOT_CONTAINS:
                not = true;
            case CONTAINS:
                conditionElement = new Element("like");
                break;
            case GREATER_THAN_OR_EQUAL_TO:
                conditionElement = new Element("greaterThanOrEquals");
                break;
            case LESS_THAN:
                conditionElement = new Element("lessThan");
                break;
            case LESS_THAN_OR_EQUAL_TO:
                conditionElement = new Element("lessThanOrEquals");
                break;
            case NOT_EQUAL:
                    not = true;
            case EQUAL:
                default:
                conditionElement = new Element("equals");
        }
        conditionElement.addContent(new Element("concept").setAttribute("id", query.getField().getCode()));
        conditionElement.addContent(new Element("literal").setAttribute("value", ""+query.getValues()[0]));
        if(not) {
            Element notElement = new Element("not");
            notElement.addContent(conditionElement);
            return notElement;
        }
        else {
            return conditionElement;
        }
    }
}
