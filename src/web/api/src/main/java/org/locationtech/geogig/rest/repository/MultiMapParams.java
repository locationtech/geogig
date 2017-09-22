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
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import com.google.common.collect.ArrayListMultimap;

class MultiMapParams extends AbstractParameterSet {

    protected ArrayListMultimap<String, String> options = ArrayListMultimap.create();

    public MultiMapParams(ArrayListMultimap<String, String> options) {
        this(options, null);
    }

    public MultiMapParams(ArrayListMultimap<String, String> options, File uploadedFile) {
        super(uploadedFile);
        if (options != null) {
            this.options = options;
        }
    }

    @Override
    public String getFirstValue(String key, String defaultValue) {
        String[] array = getValuesArray(key);
        return array.length == 0 ? defaultValue : array[0];
    }

    @Override
    @Nullable
    public String[] getValuesArray(String key) {
        List<String> list = options.get(key);
        return list.toArray(new String[list.size()]);
    }

}
