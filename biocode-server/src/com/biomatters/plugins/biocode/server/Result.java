package com.biomatters.plugins.biocode.server;

import javax.xml.bind.annotation.XmlRootElement;

/**
 *
 * @author Gen Li
 *         Created on 1/04/15 9:59 AM
 */
@XmlRootElement
public class Result<T> {
    public T data;

    public Result() {
        
    }
}
