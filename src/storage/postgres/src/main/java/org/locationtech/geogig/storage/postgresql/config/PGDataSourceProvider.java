/* Copyright (c) 2020 Gabriel Roldan
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan - initial implementation
 */
package org.locationtech.geogig.storage.postgresql.config;

import javax.sql.DataSource;

public interface PGDataSourceProvider {

    DataSource get(ConnectionConfig config);

    void close(DataSource dataSource);
}
