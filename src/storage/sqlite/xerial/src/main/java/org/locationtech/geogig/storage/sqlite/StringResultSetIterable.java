/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Justin Deoliveira (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.sqlite;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.AbstractIterator;

/**
 * Wraps a ResultSet consisting of a single string column in an iterable.
 * 
 * @author Justin Deoliveira, Boundless
 * 
 */
class StringResultSetIterable implements Iterable<String> {

    private static final Logger LOG = LoggerFactory.getLogger(StringResultSetIterable.class);

    private ResultSet rs;

    private Connection cx;

    StringResultSetIterable(ResultSet rs, Connection cx) {
        this.rs = rs;
        this.cx = cx;
    }

    @Override
    public Iterator<String> iterator() {

        return new AbstractIterator<String>() {
            @Override
            protected String computeNext() {
                try {
                    if (!rs.next()) {
                        close();
                        return endOfData();
                    }

                    return rs.getString(1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }

            private void close() {
                if (rs == null) {
                    return;
                }
                try {
                    try {
                        rs.close();
                    } catch (SQLException swallow) {
                        LOG.warn("Ignoring exception closing resultset", swallow);
                    }
                    try {
                        cx.close();
                    } catch (SQLException swallow) {
                        LOG.warn("Ignoring exception closing connection", swallow);
                    }
                } finally {
                    rs = null;
                    cx = null;
                }
            }

            @Override
            protected void finalize() throws Throwable {
                try {
                    close();
                } finally {
                    super.finalize();
                }
            }
        };

    }
}
