/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.repository;

import java.util.concurrent.ExecutorService;

import org.locationtech.geogig.api.Platform;

public class FileNodeIndexTest extends AbstractNodeIndexTest {

    @Override
    protected NodeIndex createIndex(Platform platform, ExecutorService executorService) {
        return new FileNodeIndex(platform, executorService);
    }

}
