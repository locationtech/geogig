package org.locationtech.geogig.rest;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.restlet.data.MediaType;

class SimpleAsyncCommandRepresentation<T> extends AsyncCommandRepresentation<T> {

    SimpleAsyncCommandRepresentation(MediaType mediaType, AsyncCommand<T> c, String baseURL) {
        super(mediaType, c, baseURL);
    }

    @Override
    protected void writeResult(XMLStreamWriter w, Object result) throws XMLStreamException {
        // do nothing
    }

    @Override
    protected void writeResultBody(XMLStreamWriter w, Object result) throws XMLStreamException {
        // do nothing
    }
}