/* Copyright (c) 2017 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (Prominent Edge) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import java.io.File;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.web.api.CommandSpecException;
import org.locationtech.geogig.web.api.ParameterSet;

public abstract class AbstractParameterSet implements ParameterSet {

    protected File uploadedFile = null;

    public AbstractParameterSet(File uploadedFile) {
        this.uploadedFile = uploadedFile;
    }

    @Override
    @Nullable
    public File getUploadedFile() {
        return this.uploadedFile;
    }

    @Override
    public String getRequiredValue(String key) {
        String value = getFirstValue(key);
        if (value == null) {
            throw new CommandSpecException(
                    String.format("Required parameter '%s' was not provided.", key));
        }
        return value;
    }

    @Override
    @Nullable
    public String getFirstValue(String key) {
        return getFirstValue(key, null);
    }
}
