package org.locationtech.geogig.rest.osm;

import static com.google.common.base.Preconditions.checkState;
import static org.locationtech.geogig.rest.Variants.getVariantByExtension;
import static org.locationtech.geogig.rest.repository.RESTUtils.getGeogig;

import java.net.URL;

import org.locationtech.geogig.api.DefaultProgressListener;
import org.locationtech.geogig.api.GeoGIG;
import org.locationtech.geogig.osm.internal.Mapping;
import org.locationtech.geogig.osm.internal.OSMImportOp;
import org.locationtech.geogig.osm.internal.OSMReport;
import org.locationtech.geogig.rest.AsyncContext;
import org.locationtech.geogig.rest.AsyncContext.AsyncCommand;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.resource.Representation;
import org.restlet.resource.Resource;
import org.restlet.resource.Variant;

import com.google.common.base.Optional;

public class OsmImportWebOp extends Resource {

    @Override
    public boolean allowGet() {
        return true;
    }

    @Override
    public Variant getPreferredVariant() {
        return getVariantByExtension(getRequest(), getVariants()).or(super.getPreferredVariant());
    }

    @Override
    public void handleGet() {
        final Request request = getRequest();
        Optional<GeoGIG> geogig = getGeogig(request);
        checkState(geogig.isPresent());

        Form options = getRequest().getResourceRef().getQueryAsForm();

        final String urlOrFilepath = options.getFirstValue("uri");
        final boolean add = Boolean.valueOf(options.getFirstValue("add"));
        final String mappingFile = options.getFirstValue("mapping");
        Mapping mapping = null;
        if (mappingFile != null) {
            mapping = Mapping.fromFile(mappingFile);
        }
        final boolean noRaw = Boolean.valueOf(options.getFirstValue("noRaw"));
        final String message = options.getFirstValue("message");

        if (urlOrFilepath == null) {
            String msg = "Missing parameter: uri\n"
                    + "Usage: GET <repo context>/osm/import?uri=<osm file URI>[&<arg>=<value>]+\n"
                    + "Arguments:\n"
                    + " * uri: Mandatory. URL or path to OSM data file in the server filesystem\n"
                    + " * add: Optional. true|false. Default: false. If true, do not remove previous data before importing.\n"
                    + " * mapping: Optional. Location of mapping file in the server filesystem\n"
                    + " * noRaw: Optional. true|false. Default: false. If true, do not import raw data when using a mapping\n"
                    + " * message: Optional. Message for the commit to create.";

            throw new CommandSpecException(msg);
        }

        OSMImportOp command = geogig.get().command(OSMImportOp.class);
        command.setAdd(add);
        command.setDataSource(urlOrFilepath);
        command.setMapping(mapping);
        command.setMessage(message);
        command.setNoRaw(noRaw);
        command.setProgressListener(new DefaultProgressListener());

        AsyncCommand<Optional<OSMReport>> asyncCommand;

        URL repo = geogig.get().getRepository().getLocation();
        String description = String.format("osm import %s, repository: %s", urlOrFilepath, repo);
        asyncCommand = AsyncContext.get().run(command, description);

        final String rootPath = request.getRootRef().toString();
        Representation rep = new OSMReportRepresentation(MediaType.APPLICATION_XML, asyncCommand,
                rootPath);
        getResponse().setEntity(rep);
    }
}
