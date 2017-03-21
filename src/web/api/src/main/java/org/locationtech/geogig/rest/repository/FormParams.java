/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import java.io.File;

import org.restlet.data.Form;

/**
 *
 */
class FormParams extends AbstractParameterSet {

    private Form options;

    /**
     * @param options
     */
    public FormParams(Form options) {
        this(options, null);
    }

    public FormParams(Form options, File uploadedFile) {
        super(uploadedFile);
        this.options = options;
    }

    @Override
    public String[] getValuesArray(String key) {
        String values = options.getValues(key);
        if (values == null) {
            return new String[0];
        }
        String[] split = values.split(",");
        return split;
    }

    @Override
    public String getFirstValue(String key, String defaultValue) {
        return options.getFirstValue(key, defaultValue);
    }
}
