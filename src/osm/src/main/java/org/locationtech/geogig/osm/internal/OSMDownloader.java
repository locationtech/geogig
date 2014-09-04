/* Copyright (c) 2013-2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.osm.internal;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

import javax.annotation.Nullable;

import org.locationtech.geogig.api.ProgressListener;

import com.google.common.io.Closeables;

public class OSMDownloader {

    private final String osmAPIUrl;

    /**
     * @param osmAPIUrl api url, e.g. {@code http://api.openstreetmap.org/api/0.6},
     * @param downloadFolder where to download the data xml contents to
     */
    public OSMDownloader(String osmAPIUrl, ProgressListener progress) {
        checkNotNull(osmAPIUrl);
        checkNotNull(progress);
        this.osmAPIUrl = osmAPIUrl;
    }

    private class DownloadOSMData {

        private String filter;

        private String osmAPIUrl;

        private File downloadFile;

        public DownloadOSMData(String osmAPIUrl, String filter, @Nullable File downloadFile) {
            this.filter = filter;
            this.osmAPIUrl = osmAPIUrl;
            this.downloadFile = downloadFile;
        }

        public InputStream call() throws Exception {
            URL url = new URL(osmAPIUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(180000);
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");

            DataOutputStream printout = new DataOutputStream(conn.getOutputStream());
            printout.writeBytes("data=" + URLEncoder.encode(filter, "utf-8"));
            printout.flush();
            printout.close();

            InputStream inputStream;
            if (downloadFile != null) {
                OutputStream out = new BufferedOutputStream(new FileOutputStream(downloadFile),
                        16 * 1024);
                inputStream = new TeeInputStream(new BufferedInputStream(conn.getInputStream(),
                        16 * 1024), out);
            } else {
                inputStream = new BufferedInputStream(conn.getInputStream(), 16 * 1024);
            }
            return inputStream;
        }
    }

    public InputStream download(String filter, @Nullable File destination) throws Exception {
        InputStream downloadedFile = new DownloadOSMData(osmAPIUrl, filter, destination).call();
        return downloadedFile;
    }

    private static class TeeInputStream extends FilterInputStream {

        private OutputStream out;

        protected TeeInputStream(InputStream in, OutputStream out) {
            super(in);
            this.out = out;
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                Closeables.close(out, true);
            }
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b > -1) {
                out.write(b);
            }
            return b;
        }

        @Override
        public int read(byte b[]) throws IOException {
            int c = super.read(b);
            if (c > -1) {
                out.write(b, 0, c);
            }
            return c;
        }

        @Override
        public int read(byte b[], int off, int len) throws IOException {
            int c = super.read(b, off, len);
            if (c > -1) {
                out.write(b, off, c);
            }
            return c;
        }

    }
}
