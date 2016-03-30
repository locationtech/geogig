/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Erik Merkle (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;

import org.apache.commons.fileupload.FileItemIterator;
import org.apache.commons.fileupload.FileItemStream;
import org.apache.commons.fileupload.FileUploadException;
import org.apache.commons.fileupload.disk.DiskFileItemFactory;
import org.locationtech.geogig.rest.Variants;
import org.locationtech.geogig.web.api.ParameterSet;
import org.restlet.data.Form;
import org.restlet.data.MediaType;
import org.restlet.data.Request;
import org.restlet.ext.fileupload.RepresentationContext;
import org.restlet.ext.fileupload.RestletFileUpload;
import org.restlet.resource.Representation;
import org.restlet.resource.Variant;
import org.restlet.util.ByteUtils;

import com.google.common.base.Optional;

/**
 * CommandResource extension that allows for POSTing file uploads.
 */
public class UploadCommandResource extends CommandResource {

    /**
     * POSTed form data key for the file to upload. Any commands that must upload a file to the
     * server should POST form data with this value as the key for the file to be uploaded.
     */
    public static final String UPLOAD_FILE_KEY = "fileUpload";

    /**
     * Size, in bytes, of the memory threshold for storing uploaded data. Uploads larger than this
     * value will be flushed to disk for processing. Uploads smaller than this value will remain in
     * memory until processed. The threshold is 1MB.
     */
    private static final int UPLOAD_THRESHOLD = 0x1000 * 1000;

    private static final MediaType DEFAULT_OUTPUT_MEDIA_TYPE = Variants.XML.getMediaType();

    @Override
    protected String getCommandName() {
        // Overriding the getCommandName as this Resource is bound only to "import" context.
        return "import";
    }

    @Override
    protected ParameterSet buildParameterSet(Form options) {
        // Override the default ParameterSet to include the uploaded file.
        return new FormParams(options, consumeFileUpload(this.getRequest().getEntity()));
    }

    @Override
    protected MediaType resolveFormat(Form options, Variant variant) {
        if (options.getFirstValue("output_format") != null) {
            // requested output format exists, use that
            return super.resolveFormat(options, variant);
        }
        // if not requested, the Variant MediaType for uploads is multipart. The response needs to
        // be XML or JSON, so let's override it
        return DEFAULT_OUTPUT_MEDIA_TYPE;
    }

    @Override
    public void handlePost() {
        // Request Entity may not be available, as in the case of a file upload.
        // as long as the entity is not null and at least transient, call post.
        final Optional<Representation> optional = Optional.fromNullable(getRequest().getEntity());
        if (optional.isPresent()) {
            final Representation entity = optional.get();
            if (entity.isAvailable() || entity.isTransient()) {
                post(getRequest().getEntity());
                return;
            }
        }
        // just call super
        super.handlePost();
    }

    @Override
    public void post(Representation entity) {
        // invoke the runCommand method on the parent and get the representation
        // set the representation on the response
        getResponse().setEntity(runCommand(entity, getRequest()));
    }

    /**
     * Consumes the data sent from the client and stores it into a temporary file to be processed.
     * This method is just looking through the request entity for form data named
     * {@value #UPLOAD_FILE_KEY}. If present, we will consume the data stream from the request and
     * store it in a temporary file.
     *
     * @param entity POSTed entity containing binary data to be processed.
     *
     * @return local File representation of the data streamed form the client.
     */
    private File consumeFileUpload(Representation entity) {
        File uploadedFile = null;
        // get a File item factory
        final DiskFileItemFactory factory = new DiskFileItemFactory();
        // set the threshold
        factory.setSizeThreshold(UPLOAD_THRESHOLD);
        // build a Restlet file upload with the factory
        final RestletFileUpload fileUploadUtil = new RestletFileUpload(factory);
        // try to extract the uploaded file entity
        try {
            // build a RepresentaionContext of the request entity
            final RepresentationContext context = new RepresentationContext(entity);
            // get an iterator to loop through the entity for the upload data
            final FileItemIterator iterator = fileUploadUtil.getItemIterator(context);
            // look for the the "fileUpload" form data
            while (iterator.hasNext()) {
                final FileItemStream fis = iterator.next();
                // see if this is the data we are looking for
                if (UPLOAD_FILE_KEY.equals(fis.getFieldName())) {
                    // found it, create a temp file
                    uploadedFile = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
                    uploadedFile.deleteOnExit();
                    // consume the streamed contetn into the temp file
                    try (FileOutputStream fos = new FileOutputStream(uploadedFile)) {
                        ByteUtils.write(fis.openStream(), fos);
                        // flush the output stream
                        fos.flush();
                    }
                    // we've processed the uploaded data
                    break;
                }
            }
        } catch (FileUploadException | IOException ex) {
            throw new RuntimeException("Failed to upload and process file", ex);
        }
        // return the uploaded entity data as a file
        return uploadedFile;
    }
}
