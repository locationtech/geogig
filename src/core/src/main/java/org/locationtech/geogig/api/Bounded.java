/*******************************************************************************
 * Copyright (c) 2013 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api;

import com.vividsolutions.jts.geom.Envelope;

/**
 *
 */
public interface Bounded {

    public boolean intersects(Envelope env);

    public void expand(Envelope env);
}
