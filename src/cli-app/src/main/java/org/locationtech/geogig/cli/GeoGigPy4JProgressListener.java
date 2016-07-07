/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Victor Olaya (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli;

/**
 * An interface for progress listener for the Py4j entry point.
 * 
 * Implementation should be done on the Python side
 */
public interface GeoGigPy4JProgressListener {

    public void setProgress(float i);

    public void setProgressText(String s);

}