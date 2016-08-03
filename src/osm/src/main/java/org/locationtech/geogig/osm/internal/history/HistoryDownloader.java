/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal.history;

import static com.google.common.base.Preconditions.checkArgument;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;

import javax.xml.stream.XMLStreamException;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.ContiguousSet;
import com.google.common.collect.DiscreteDomain;
import com.google.common.collect.Iterators;
import com.google.common.collect.Range;
import com.google.common.io.Closeables;

/**
 *
 */
public class HistoryDownloader {

    private final long initialChangeset;

    private final long finalChangeset;

    private final ChangesetDownloader downloader;

    private Predicate<Changeset> filter = Predicates.alwaysTrue();

    /**
     * @param osmAPIUrl api url, e.g. {@code http://api.openstreetmap.org/api/0.6},
     *        {@code file:/path/to/downloaded/changesets}
     * @param initialChangeset initial changeset id
     * @param finalChangeset final changeset id
     * @param preserveFiles
     */
    public HistoryDownloader(final String osmAPIUrl, final File downloadFolder,
            long initialChangeset, long finalChangeset, ExecutorService executor) {

        checkArgument(initialChangeset > 0 && initialChangeset <= finalChangeset);

        this.initialChangeset = initialChangeset;
        this.finalChangeset = finalChangeset;
        this.downloader = new ChangesetDownloader(osmAPIUrl, downloadFolder, executor);
    }

    public void setChangesetFilter(Predicate<Changeset> filter) {
        this.filter = filter;
    }

    /**
    *
    */
    private class ChangesSupplier implements Supplier<Optional<Iterator<Change>>> {

        private Supplier<Optional<File>> changesFile;

        /**
         * @param changesFile2
         */
        public ChangesSupplier(Supplier<Optional<File>> changesFile) {
            this.changesFile = changesFile;
        }

        @Override
        public Optional<Iterator<Change>> get() {
            return parseChanges(changesFile);
        }

    }

    /**
     * @return the next available changeset, or absent if reached the last one
     * @throws IOException
     * @throws InterruptedException
     */
    public Iterator<Changeset> fetchChangesets() {

        Range<Long> range = Range.closed(initialChangeset, finalChangeset);
        ContiguousSet<Long> changesetIds = ContiguousSet.create(range, DiscreteDomain.longs());
        final int fetchSize = 100;
        Iterator<List<Long>> partitions = Iterators.partition(changesetIds.iterator(), fetchSize);

        final Function<List<Long>, Iterator<Changeset>> asChangesets = (batchIds) -> {
            Iterable<Changeset> changesets = downloader.fetchChangesets(batchIds);

            for (Changeset changeset : changesets) {
                if (filter.apply(changeset)) {
                    Supplier<Optional<File>> changesFile;
                    changesFile = downloader.fetchChanges(changeset.getId());
                    Supplier<Optional<Iterator<Change>>> changes = new ChangesSupplier(changesFile);
                    changeset.setChanges(changes);
                }
            }

            return changesets.iterator();
        };

        Iterator<Iterator<Changeset>> changesets = Iterators.transform(partitions, asChangesets);
        Iterator<Changeset> concat = Iterators.concat(changesets);
        return concat;
    }

    private Optional<Iterator<Change>> parseChanges(Supplier<Optional<File>> file) {

        final Optional<File> changesFile;
        try {
            changesFile = file.get();
        } catch (RuntimeException e) {
            Throwable cause = e.getCause();
            if (cause instanceof FileNotFoundException) {
                return Optional.absent();
            }
            throw Throwables.propagate(e);
        }
        if (!changesFile.isPresent()) {
            return Optional.absent();
        }
        final File actualFile = changesFile.get();
        final InputStream stream = openStream(actualFile);
        final Iterator<Change> changes;
        ChangesetContentsScanner scanner = new ChangesetContentsScanner();
        try {
            changes = scanner.parse(stream);
        } catch (XMLStreamException e) {
            throw Throwables.propagate(e);
        }

        Iterator<Change> iterator = new AbstractIterator<Change>() {
            @Override
            protected Change computeNext() {
                if (!changes.hasNext()) {
                    Closeables.closeQuietly(stream);
                    actualFile.delete();
                    actualFile.getParentFile().delete();
                    return super.endOfData();
                }
                return changes.next();
            }
        };
        return Optional.of(iterator);
    }

    private InputStream openStream(File file) {
        InputStream stream;
        try {
            stream = new BufferedInputStream(new FileInputStream(file), 4096);
        } catch (FileNotFoundException e) {
            throw Throwables.propagate(e);
        }
        return stream;
    }

}
