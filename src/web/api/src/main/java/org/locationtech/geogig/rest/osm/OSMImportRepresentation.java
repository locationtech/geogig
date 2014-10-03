package org.locationtech.geogig.rest.osm;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;

import org.locationtech.geogig.api.AbstractGeoGigOp;
import org.locationtech.geogig.osm.internal.OSMImportOp;
import org.locationtech.geogig.osm.internal.OSMReport;
import org.locationtech.geogig.rest.AsyncCommandRepresentation;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.rest.CommandRepresentationFactory;
import org.restlet.data.MediaType;

import com.google.common.base.Optional;

public class OSMImportRepresentation extends AsyncCommandRepresentation<Optional<OSMReport>> {

    public OSMImportRepresentation(MediaType mediaType, AsyncCommand<Optional<OSMReport>> cmd,
            String baseURL) {
        super(mediaType, cmd, baseURL);
    }

    @Override
    protected void writeResultBody(XMLStreamWriter w, Optional<OSMReport> result)
            throws XMLStreamException {
        if (result.isPresent()) {
            OSMReport report = result.get();
            long latestChangeset = report.getLatestChangeset();
            long latestTimestamp = report.getLatestTimestamp();
            long processedEntities = report.getCount();
            long nodeCount = report.getNodeCount();
            long wayCount = report.getWayCount();
            long unpprocessedCount = report.getUnpprocessedCount();

            w.writeStartElement("OSMReport");
            element(w, "latestChangeset", latestChangeset);
            element(w, "latestTimestamp", latestTimestamp);
            element(w, "processedEntities", processedEntities);
            element(w, "nodeCount", nodeCount);
            element(w, "wayCount", wayCount);
            element(w, "unpprocessedCount", unpprocessedCount);
            w.writeEndElement();
        }
    }

    public static class Factory implements CommandRepresentationFactory<Optional<OSMReport>> {

        @Override
        public boolean supports(Class<? extends AbstractGeoGigOp<?>> cmdClass) {
            return OSMImportOp.class.isAssignableFrom(cmdClass);
        }

        @Override
        public AsyncCommandRepresentation<Optional<OSMReport>> newRepresentation(
                AsyncCommand<Optional<OSMReport>> cmd, MediaType mediaType, String baseURL) {

            return new OSMImportRepresentation(mediaType, cmd, baseURL);
        }

    }
}