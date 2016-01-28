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
import static com.google.common.base.Preconditions.checkNotNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

import javax.xml.stream.XMLStreamException;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.api.ProgressListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.base.Throwables;
import com.google.common.io.ByteStreams;
import com.google.common.io.Closeables;
import com.google.common.io.Files;

/**
 * 
 * @see ChangesetScanner
 * @see ChangesetContentsScanner
 */
class ChangesetDownloader {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChangesetDownloader.class);

    private final String osmAPIUrl;

    private final ExecutorService executor;

    private final File downloadFolder;

    /**
     * @param osmAPIUrl api url, e.g. {@code http://api.openstreetmap.org/api/0.6},
     *        {@code file:/path/to/downloaded/changesets}
     * @param downloadFolder where to download the changeset xml contents to
     */
    public ChangesetDownloader(String osmAPIUrl, File downloadFolder, ExecutorService executor) {

        checkNotNull(osmAPIUrl);
        checkNotNull(downloadFolder);
        checkNotNull(executor);
        checkArgument(downloadFolder.exists() && downloadFolder.isDirectory()
                && downloadFolder.canWrite());

        this.downloadFolder = downloadFolder;

        this.osmAPIUrl = osmAPIUrl;
        this.executor = executor;
    }

    private class FutureSupplier<T> implements Supplier<T> {

        private Future<T> future;

        public FutureSupplier(Future<T> future) {
            this.future = future;
        }

        @Override
        public T get() {
            try {
                return future.get();
            } catch (InterruptedException e) {
                throw Throwables.propagate(e);
            } catch (ExecutionException e) {
                throw Throwables.propagate(e.getCause());
            }
        }
    }

    public List<Changeset> fetchChangesets(List<Long> batchIds) {

        final String url = changesetsUrl(batchIds);
        LOGGER.debug("Fetching " + url);

        InputStream stream;
        try {
            stream = openStream(url, 3, null);
        } catch (FileNotFoundException | SocketTimeoutException e) {
            throw Throwables.propagate(e);
        }

        List<Changeset> changesets = new ArrayList<Changeset>(batchIds.size());

        try {
            stream = new BufferedInputStream(stream, 4096);
            try {
                ChangesetScanner changesetScanner = new ChangesetScanner(stream);
                Changeset changeset = null;
                while ((changeset = changesetScanner.parseNext()) != null) {
                    changesets.add(changeset);
                }
            } catch (XMLStreamException e) {
                throw Throwables.propagate(e);
            }
        } finally {
            Closeables.closeQuietly(stream);
        }

        Collections.sort(changesets);
        return changesets;
    }

    private class FetchChanges implements Callable<Optional<File>> {

        private long changesetId;

        /**
         * @param changesetId
         */
        public FetchChanges(long changesetId) {
            this.changesetId = changesetId;
        }

        @Override
        public Optional<File> call() throws Exception {

            File changesFile = changesFile(changesetId);
            synchronized (changesFile.getAbsolutePath().intern()) {
                if (!changesFile.exists()) {
                    Files.createParentDirs(changesFile);
                    String changeUrl = changeUrl(changesetId);
                    InputStream stream = null;
                    try {
                        stream = tryFetch(changeUrl);
                        copy(stream, changesFile);
                    } catch (FileNotFoundException e) {
                        return Optional.absent();
                    } finally {
                        Closeables.closeQuietly(stream);
                    }
                }
            }
            return Optional.of(changesFile);
        }

        private InputStream tryFetch(final String changeUrl) throws SocketTimeoutException,
                FileNotFoundException {

            final int maxTryCount = 5;

            int timeout = 5;

            for (int i = 0; i < maxTryCount; i++) {
                try {
                    return openStream(changeUrl, timeout, null);
                } catch (SocketTimeoutException e) {
                    String url = changeUrl(changesetId);
                    timeout += 2;
                    System.err.println(String.format(
                            "**** Timeout waiting for %s, retrying with a %d minutes timeout...",
                            url, timeout));
                }
            }

            throw new SocketTimeoutException(String.format("Unable to fetch %s after %d attempts.",
                    changeUrl, maxTryCount));
        }

    }

    /**
     * @param changesetId
     * @return
     */
    public Supplier<Optional<File>> fetchChanges(long changesetId) {
        File changesFile = changesFile(changesetId);
        synchronized (changesFile.getAbsolutePath().intern()) {
            if (changesFile.exists()) {
                return Suppliers.ofInstance(Optional.of(changesFile));
            }
        }
        final Future<Optional<File>> future = executor.submit(new FetchChanges(changesetId));
        return new FutureSupplier<Optional<File>>(future);
    }

    private File changesFile(long changesetId) {
        File parent = new File(downloadFolder, String.valueOf(changesetId));
        return new File(parent, "download.xml");
    }

    private static InputStream openStream(final String uri, final int readTimeoutMinutes,
            @Nullable ProgressListener listener) throws FileNotFoundException,
            SocketTimeoutException {
        InputStream stream;
        URLConnection conn;
        try {
            URL url = new URL(uri);
            conn = url.openConnection();
        } catch (IOException e) {
            throw Throwables.propagate(e);
        }
        try {
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(60 * 1000 * readTimeoutMinutes);
            if (conn instanceof HttpURLConnection) {
                ((HttpURLConnection) conn).addRequestProperty("Accept-Encoding", "gzip, deflate");
                int responseCode = ((HttpURLConnection) conn).getResponseCode();
                if (responseCode == HttpURLConnection.HTTP_INTERNAL_ERROR) {
                    // some changeset contents give a 500 error, skip them
                    System.err.println("**** Server returned HTTP error 500 for " + uri + " ****");
                    throw new FileNotFoundException("Server returned HTTP error 500 for " + uri);
                }
            }
            stream = conn.getInputStream();

            final String encoding = conn.getContentEncoding();
            if (listener != null) {
                final int contentLength = conn.getContentLength();
                if (contentLength > -1) {
                    stream = new ProgressInputStream(stream, contentLength, listener);
                }
            }
            if (encoding != null) {
                if (encoding.equalsIgnoreCase("gzip")) {
                    stream = new GZIPInputStream(stream);
                } else if (encoding.equalsIgnoreCase("deflate")) {
                    stream = new InflaterInputStream(stream);
                }
            }
        } catch (java.net.SocketTimeoutException timeout) {
            throw timeout;
        } catch (Exception e) {
            consumeBody(conn);
            Throwables.propagateIfInstanceOf(e, FileNotFoundException.class);
            throw Throwables.propagate(e);
        }
        return stream;
    }

    private static void consumeBody(URLConnection conn) {
        // do not return without consuming the response body, it may result in stale connections
        // inside the JVM's internal connection pool (as it handles keep-alive transparently)
        // (see <http://docs.oracle.com/javase/1.5.0/docs/guide/net/http-keepalive.html>)
        if (conn instanceof HttpURLConnection) {
            InputStream errorStream = ((HttpURLConnection) conn).getErrorStream();
            try {
                while (errorStream != null && errorStream.read() != -1) {
                    ; // $codepro.audit.disable extraSemicolon
                }
            } catch (IOException e1) {
                // ok, we tried
            } finally {
                Closeables.closeQuietly(errorStream);
            }
        }
    }

    private String changesetsUrl(List<Long> ids) {
        String url = osmAPIUrl + (osmAPIUrl.endsWith("/") ? "" : "/") + "changesets?changesets=";
        StringBuilder sb = new StringBuilder(url);
        Long id;
        for (Iterator<Long> it = ids.iterator(); it.hasNext();) {
            id = it.next();
            sb.append(id);
            if (it.hasNext()) {
                sb.append(',');
            }
        }
        url = sb.toString();
        return url;
    }

    private String canonicalChangesetUrl(long changesetId) {
        String url = osmAPIUrl + (osmAPIUrl.endsWith("/") ? "" : "/") + "changeset/" + changesetId;
        return url;
    }

    private String changeUrl(long changesetId) {
        String url = canonicalChangesetUrl(changesetId) + "/download.xml";
        return url;
    }

    private static class ProgressInputStream extends FilterInputStream {

        private final int contentLength;

        private final ProgressListener listener;

        private int readCount;

        public ProgressInputStream(InputStream stream, int contentLength, ProgressListener listener) {
            super(stream);
            this.contentLength = contentLength;
            this.listener = listener;
        }

        @Override
        public int read() throws IOException {
            int read = super.read();
            if (read != -1) {
                progress(1);
            }
            return read;
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException {
            int read = super.read(b, off, len);
            if (read != -1) {
                progress(read);
            }
            return read;
        }

        /**
         * @param read
         */
        private void progress(int read) {
            readCount += read;
            float percent = (float) (readCount * 100) / contentLength;
            listener.setProgress(percent);
        }
    }

    private static void copy(final InputStream from, final File to) {
        File tmp = new File(to.getAbsolutePath() + ".tmp");
        try {
            tmp.createNewFile();
            OutputStream output = new FileOutputStream(tmp);
            try {
                ByteStreams.copy(from, output);
                output.flush();
            } finally {
                output.close();
            }
            tmp.renameTo(to);
        } catch (Exception e) {
            tmp.delete();
        }
    }
}
