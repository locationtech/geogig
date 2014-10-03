package org.locationtech.geogig.rest;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.concurrent.ExecutionException;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.AsyncContext.Status;
import org.restlet.data.MediaType;

import com.google.common.base.Strings;

public abstract class AsyncCommandRepresentation<T> extends JettisonRepresentation {

    protected final AsyncCommand<T> cmd;

    public AsyncCommandRepresentation(MediaType mediaType, AsyncCommand<T> cmd, String baseURL) {
        super(mediaType, baseURL);
        checkNotNull(mediaType);
        checkNotNull(cmd);
        this.cmd = cmd;
    }

    @Override
    protected void write(XMLStreamWriter w) throws XMLStreamException {
        final String taskId = cmd.getTaskId();
        final Status status = cmd.getStatus();
        final String description = cmd.getDescription();
        checkNotNull(taskId);
        checkNotNull(status);
        w.writeStartElement("task");

        element(w, "id", taskId);
        element(w, "status", status.toString());
        element(w, "description", description);
        String link = "tasks/" + taskId;// relative to baseURL (e.g. /geoserver/geogig)
        encodeAlternateAtomLink(w, link);
        if (cmd.isDone()) {
            T result;
            try {
                result = cmd.get();
                writeResult(w, result);
            } catch (InterruptedException e) {
                writeError(w, e);
            } catch (ExecutionException e) {
                writeError(w, e.getCause());
            }
        } else if (cmd.getStatus() == Status.RUNNING) {
            String statusLine = cmd.getStatusLine();
            if (!Strings.isNullOrEmpty(statusLine)) {
                w.writeStartElement("progress");
                element(w, "task", String.valueOf(statusLine));
                element(w, "amount", String.valueOf(cmd.getProgress()));
                w.writeEndElement();
            }
        }
        w.writeEndElement();// task
    }

    protected void writeResult(XMLStreamWriter w, T result) throws XMLStreamException {
        w.writeStartElement("result");
        writeResultBody(w, result);
        w.writeEndElement();
    }

    protected void writeError(XMLStreamWriter w, Throwable cause) throws XMLStreamException {
        w.writeStartElement("error");

        element(w, "message", cause.getMessage());

        w.writeStartElement("stackTrace");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        cause.printStackTrace(new PrintStream(out));
        String statckTrace = out.toString();
        w.writeCData(statckTrace);
        w.writeEndElement();

        w.writeEndElement();
    }

    protected abstract void writeResultBody(XMLStreamWriter w, T result) throws XMLStreamException;

}