/* Copyright (c) 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.rest.repository;

import org.locationtech.geogig.web.api.ParameterSet;
import org.restlet.data.Form;

/**
 *
 */
class FormParams implements ParameterSet {

    private Form options;

    /**
     * @param options
     */
    public FormParams(Form options) {
        this.options = options;
    }

    @Override
    public String getFirstValue(String key) {
        return options.getFirstValue(key);
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
