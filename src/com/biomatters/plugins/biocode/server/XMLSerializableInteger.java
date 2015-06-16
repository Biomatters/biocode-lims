package com.biomatters.plugins.biocode.server;

import com.biomatters.geneious.publicapi.documents.XMLSerializable;
import com.biomatters.geneious.publicapi.documents.XMLSerializationException;
import org.jdom.Element;

/**
 *
 * @author Gen Li
 *         Created on 15/06/15 9:57 AM
 */
public class XMLSerializableInteger implements XMLSerializable{
    private static final String VALUE_ELEMENT_NAME = "value";

    private int value;

    public XMLSerializableInteger(int value) {
        setValue(value);
    }

    public XMLSerializableInteger(Element element) throws XMLSerializationException {
        fromXML(element);
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

    @Override
    public Element toXML() {
        Element rootElement = new Element(XMLSerializable.ROOT_ELEMENT_NAME);
        rootElement.addContent(new Element(VALUE_ELEMENT_NAME).setText(Integer.toString(value)));
        return rootElement;
    }

    @Override
    public void fromXML(Element element) throws XMLSerializationException {
        value = Integer.parseInt(element.getChildText(VALUE_ELEMENT_NAME));
    }
}