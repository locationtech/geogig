/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.osm.internal;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.locationtech.geogig.porcelain.NothingToCommitException;
import org.locationtech.geogig.repository.AbstractGeoGigOp;

import com.google.common.base.Charsets;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.io.Files;

/**
 * Downloads data from OSM and commits it to the repository.
 * 
 */
public class OSMDownloadOp extends AbstractGeoGigOp<Optional<OSMReport>> {

    private File mappingFile;

    private File filterFile;

    private File saveFile;

    private String osmAPIUrl;

    private String message;

    private List<String> bbox;

    private boolean keepFiles = false;

    /**
     * Sets the mapping file to use.
     * 
     * @param mappingFile the mapping file to use
     * @return {@code this}
     */
    public OSMDownloadOp setMappingFile(File mappingFile) {
        this.mappingFile = mappingFile;
        return this;
    }

    /**
     * Sets the filter file to use.
     * 
     * @param filterFile the filter file to use
     * @return {@code this}
     */
    public OSMDownloadOp setFilterFile(File filterFile) {
        this.filterFile = filterFile;
        return this;
    }

    /**
     * Sets the save file to use.
     * 
     * @param saveFile the save file to use
     * @return {@code this}
     */
    public OSMDownloadOp setSaveFile(File saveFile) {
        this.saveFile = saveFile;
        return this;
    }

    /**
     * Sets the URL of the OSM API.
     * 
     * @param url the url of the OSM API
     * @return {@code this}
     */
    public OSMDownloadOp setOsmAPIUrl(String url) {
        this.osmAPIUrl = url;
        return this;
    }

    /**
     * Sets the commit message to use when committing the updates.
     * 
     * @param message the commit message to use
     * @return {@code this}
     */
    public OSMDownloadOp setMessage(String message) {
        this.message = message;
        return this;
    }

    /**
     * The bounding box to use as filter (S W N E).
     * 
     * @param bbox the bounding box coordinates (S W N E)
     * @return {@code this}
     */
    public OSMDownloadOp setBbox(List<String> bbox) {
        this.bbox = bbox;
        return this;
    }

    /**
     * If specified, downloaded files are kept in the `saveFile` folder
     * 
     * @param keepFiles whether or not to keep the downloaded files
     * @return {@code this}
     */
    public OSMDownloadOp setKeepFiles(boolean keepFiles) {
        this.keepFiles = keepFiles;
        return this;
    }

    /**
     * Executes the {@code OSMDownloadOp} operation.
     * 
     * @return a {@link OSMDownloadReport} of the operation
     */
    @Override
    protected Optional<OSMReport> _call() {

        Mapping mapping = null;
        if (mappingFile != null) {
            mapping = Mapping.fromFile(mappingFile.getAbsolutePath());
        }

        message = message == null ? "Updated OSM data" : message;
        OSMImportOp op = command(OSMImportOp.class).setDataSource(osmAPIUrl)
                .setDownloadFile(saveFile).setMapping(mapping).setKeepFile(keepFiles)
                .setMessage(message);

        String filter = null;
        if (filterFile != null) {
            try {
                filter = readFile(filterFile);
            } catch (IOException e) {
                throw new IllegalArgumentException("Error reading filter file:" + e.getMessage(), e);
            }
        } else if (bbox != null) {
            String bboxString = bbox.get(0) + "," + bbox.get(1) + "," + bbox.get(2) + ","
                    + bbox.get(3);
            filter = "(node(" + bboxString + ");\n" + "way(" + bboxString
                    + "););\n(._;>;);\nout meta;";

        }

        try {
            Optional<OSMReport> report = op.setFilter(filter)
                    .setProgressListener(getProgressListener()).call();

            return report;

        } catch (EmptyOSMDownloadException e) {
            throw new IllegalArgumentException("The specified filter did not return any element.\n"
                    + "No changes were made to the repository.\n"
                    + "To check the downloaded elements, use the --saveto and"
                    + " --keep-files options and verify the intermediate file.");
        } catch (NothingToCommitException alreadyUpToDate) {
            throw alreadyUpToDate;
        } catch (RuntimeException e) {
            throw new IllegalStateException(
                    "Failed to fetch data from overpass server. (Did you try to use a standard OSM server instead?) "
                            + e.getMessage(), e);
        }

    }

    private String readFile(File file) throws IOException {
        List<String> lines = Files.readLines(file, Charsets.UTF_8);
        return Joiner.on("\n").join(lines);
    }
}
