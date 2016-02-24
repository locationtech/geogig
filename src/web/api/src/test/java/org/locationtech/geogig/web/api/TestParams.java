/* Copyright (c) 2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.web.api;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;

public class TestParams implements ParameterSet {

    private ArrayListMultimap<String, String> params = ArrayListMultimap.create();

    @Override
    @Nullable
    public String getFirstValue(String key) {
        return getFirstValue(key, null);
    }

    @Override
    public String getFirstValue(String key, String defaultValue) {
        String[] array = getValuesArray(key);
        return array.length == 0 ? defaultValue : array[0];
    }

    @Override
    @Nullable
    public String[] getValuesArray(String key) {
        List<String> list = params.get(key);
        return list.toArray(new String[list.size()]);
    }

    public static ParameterSet of(@Nullable final String... kvp) {
        Preconditions.checkArgument(kvp == null || kvp.length % 2 == 0);

        TestParams params = new TestParams();
        for (int i = 0; kvp != null && i < kvp.length; i += 2) {
            params.params.put(kvp[i], kvp[i + 1]);
        }
        return params;
    }

}
