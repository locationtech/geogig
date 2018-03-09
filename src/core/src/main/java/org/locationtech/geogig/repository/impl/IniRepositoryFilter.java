/* Copyright (c) 2013-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.repository.impl;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.locationtech.geogig.storage.BlobStore;
import org.locationtech.geogig.storage.impl.INIBlob;

import com.google.common.base.Optional;

/**
 * Provides a means of loading a RepositoryFilter from a blob store.
 * 
 * @see RepositoryFilter
 * @see INIBlob
 */
public class IniRepositoryFilter extends RepositoryFilter {

    /**
     * Constructs a new {@code IniRepositoryFilter} from the blob with the specified key.
     * 
     * @param blobStore the blob store
     * @param filterKey the key of the blob that contains the filter
     */
    public IniRepositoryFilter(BlobStore blobStore, String filterKey) {
        try {
            final INIBlob ini = new INIBlob() {
                @Override
                public byte[] iniBytes() throws IOException {
                    Optional<byte[]> bytes = blobStore.getBlob(filterKey);
                    if (bytes.isPresent()) {
                        return bytes.get();
                    } else {
                        throw new IOException("Filter blob did not exist.");
                    }
                }

                @Override
                public void setBytes(byte[] bytes) {
                    blobStore.putBlob(filterKey, bytes);
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
        } catch (IOException e) {
            throw new RuntimeException(e);
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
