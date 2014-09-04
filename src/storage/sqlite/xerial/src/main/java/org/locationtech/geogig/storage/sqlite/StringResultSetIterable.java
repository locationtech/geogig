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
import java.util.Iterator;

import com.google.common.collect.AbstractIterator;

/**
 * Wraps a ResultSet consisting of a single string column in an iterable.
 * 
 * @author Justin Deoliveira, Boundless
 * 
 */
public class StringResultSetIterable implements Iterable<String> {

    ResultSet rs;

    Connection cx;

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
                        rs.close();
                        cx.close();
                        return endOfData();
                    }

                    return rs.getString(1);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }
}
