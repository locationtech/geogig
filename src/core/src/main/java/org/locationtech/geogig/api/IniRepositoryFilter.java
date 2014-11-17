/*******************************************************************************
 * Copyright (c) 2013, 2014 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *******************************************************************************/
package org.locationtech.geogig.api;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.locationtech.geogig.storage.fs.INIFile;

import com.google.common.base.Throwables;

/**
 * Provides a means of loading a RepositoryFilter from an Ini file.
 * 
 * @see RepositoryFilter
 */
public class IniRepositoryFilter extends RepositoryFilter {

    /**
     * Constructs a new {@code IniRepositoryFilter} from the provided file.
     * 
     * @param filterFile the file with the filter definition
     * @throws FileNotFoundException
     */
    public IniRepositoryFilter(final String filterFile) throws FileNotFoundException {
        final File f = new File(filterFile);
        if (f.exists()) {
            try {
                final INIFile ini = new INIFile() { 
                    @Override
                    public File iniFile() {
                        return f;
                    }
                };

                final Map<String, String> pairs = ini.getAll();

                Set<String> seen = new HashSet<String>();
                for (Entry<String, String> pair : pairs.entrySet()) {
                    String qualifiedName = pair.getKey();
                    String[] split = qualifiedName.split("\\.");
                    if (split.length == 2 && seen.add(split[0])) {
                        parseFilter(split[0], pairs);
                    }
                }
            } catch (Exception e) {
                Throwables.propagate(e);
            }
        } else {
            throw new FileNotFoundException();
        }
    }

    /**
     * Parses an ini section and adds it as a filter.
     * 
     * @param featurePath the path of the features to filter
     * @param config the ini
     */
    private void parseFilter(String featurePath, Map<String, String> config) {
        if (featurePath != null) {
            String type = config.get(featurePath + ".type");
            String filter = config.get(featurePath + ".filter");
            addFilter(featurePath, type, filter);
        }
    }
}
