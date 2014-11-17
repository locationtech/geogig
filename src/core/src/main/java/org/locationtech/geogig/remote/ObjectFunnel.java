/*******************************************************************************
 * Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.remote;

import java.io.Closeable;
import java.io.IOException;

import org.locationtech.geogig.api.RevObject;

/**
 * A closeable funnel used to transparently send objects to a remote resource.
 */
public interface ObjectFunnel extends Closeable {

    public void funnel(RevObject object) throws IOException;
}
