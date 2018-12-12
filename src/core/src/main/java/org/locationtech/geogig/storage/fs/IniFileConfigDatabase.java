/* Copyright (c) 2014-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * David Winslow (Boundless) - initial implementation
 */
package org.locationtech.geogig.storage.fs;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.storage.ConfigDatabase;
import org.locationtech.geogig.storage.ConfigException;
import org.locationtech.geogig.storage.ConfigException.StatusCode;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.inject.Inject;

public class IniFileConfigDatabase implements ConfigDatabase {

    /**
     * Access it through {@link #local()}, not directly.
     */
    private INIFile local;

    private INIFile global;

    @Nullable
    private final File repoDirectory;

    private final boolean globalOnly;

    public IniFileConfigDatabase(final Platform platform) {
        this(platform, null);
    }

    @Inject
    public IniFileConfigDatabase(final Platform platform, final Hints hints) {
        this(platform, hints, false);
    }

    public IniFileConfigDatabase(final Platform platform, final Hints hints,
            final boolean globalOnly) {
        this.globalOnly = globalOnly;
        {
            final Optional<URI> repoURI = new ResolveGeogigURI(platform, hints).call();
            if (repoURI.isPresent()) {
                URI uri = repoURI.get();
                Preconditions.checkState("file".equals(uri.getScheme()));
                repoDirectory = new File(uri);
            } else {
                repoDirectory = null;
            }
        }

        if (globalOnly) {
            this.local = null;
        } else {
            this.local = new INIFile() {
                @Override
                public File iniFile() {
                    if (repoDirectory == null) {
                        throw new ConfigException(StatusCode.INVALID_LOCATION);
                    }

                    File localConfigFile = new File(repoDirectory, "config");

                    return localConfigFile;
                }
            };
        }
        this.global = new INIFile() {
            @Override
            public File iniFile() {
                File home = platform.getUserHome();
                if (home == null) {
                    throw new ConfigException(StatusCode.USERHOME_NOT_SET);
                }
                Preconditions.checkState(home.exists(), "user home does not exist: %s", home);
                Preconditions.checkState(home.isDirectory(), "user home is not a directory: %s",
                        home);

                File globalConfig = new File(home, ".geogigconfig");
                try {
                    globalConfig.createNewFile();
                } catch (IOException e) {
                    throw new ConfigException(e, StatusCode.CANNOT_WRITE);
                }
                return globalConfig;
            }
        };
    }

    private INIFile local() {
        if (this.globalOnly) {
            throw new ConfigException(StatusCode.INVALID_LOCATION);
        }
        return this.local;
    }

    public Optional<String> get(String key) {
        try {
            String[] parsed = parse(key);
            Optional<String> valueOpt = local().get(parsed[0], parsed[1]);
            if (valueOpt.isPresent() && valueOpt.get().length() > 0) {
                return valueOpt;
            } else {
                return Optional.absent();
            }
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    public <T> Optional<T> get(String key, Class<T> c) {
        Optional<String> text = get(key);
        if (text.isPresent()) {
            return Optional.of(cast(c, text.get()));
        } else {
            return Optional.absent();
        }
    }

    public Optional<String> getGlobal(String key) {
        try {
            String[] parsed = parse(key);
            Optional<String> globalValueOpt = global.get(parsed[0], parsed[1]);
            if (globalValueOpt.isPresent() && globalValueOpt.get().length() > 0) {
                return globalValueOpt;
            } else {
                return Optional.absent();
            }
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    public <T> Optional<T> getGlobal(String key, Class<T> c) {
        Optional<String> text = getGlobal(key);
        if (text.isPresent()) {
            return Optional.of(cast(c, text.get()));
        } else {
            return Optional.absent();
        }
    }

    public Map<String, String> getAll() {
        try {
            return local().getAll();
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    public Map<String, String> getAllGlobal() {
        try {
            return global.getAll();
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    public Map<String, String> getAllSection(String section) {
        try {
            return local().getSection(section);
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    public Map<String, String> getAllSectionGlobal(String section) {
        try {
            return global.getSection(section);
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    public List<String> getAllSubsections(String section) {
        try {
            return local().listSubsections(section);
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    public List<String> getAllSubsectionsGlobal(String section) {
        try {
            return global.listSubsections(section);
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    public void put(String key, Object value) {
        String[] parsed = parse(key);
        try {
            local().set(parsed[0], parsed[1], stringify(value));
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    public void putGlobal(String key, Object value) {
        String[] parsed = parse(key);
        try {
            global.set(parsed[0], parsed[1], stringify(value));
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    public void remove(String key) {
        String[] parsed = parse(key);
        try {
            local().remove(parsed[0], parsed[1]);
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    public void removeGlobal(String key) {
        String[] parsed = parse(key);
        try {
            global.remove(parsed[0], parsed[1]);
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    public void removeSection(String key) {
        try {
            local().removeSection(key);
        } catch (NoSuchElementException e) {
            throw new ConfigException(e, StatusCode.MISSING_SECTION);
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    public void removeSectionGlobal(String key) {
        try {
            global.removeSection(key);
        } catch (NoSuchElementException e) {
            throw new ConfigException(e, StatusCode.MISSING_SECTION);
        } catch (StringIndexOutOfBoundsException e) {
            throw new ConfigException(e, StatusCode.SECTION_OR_KEY_INVALID);
        } catch (IllegalArgumentException e) {
            throw new ConfigException(e, null);
        } catch (IOException e) {
            throw new ConfigException(e, null);
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T cast(Class<T> c, String s) {
        if (String.class.equals(c)) {
            return c.cast(s);
        }
        if (int.class.equals(c) || Integer.class.equals(c)) {
            return (T) Integer.valueOf(s);
        }
        if (Boolean.class.equals(c)) {
            return c.cast(Boolean.valueOf(s));
        }
        throw new IllegalArgumentException("Unsupported type: " + c);
    }

    private String stringify(Object o) {
        return o == null ? "" : o.toString();
    }

    private String[] parse(String qualifiedKey) {
        if (qualifiedKey == null) {
            throw new IllegalArgumentException("Config key may not be null.");
        }
        int splitAt = qualifiedKey.lastIndexOf(".");
        return new String[] { qualifiedKey.substring(0, splitAt),
                qualifiedKey.substring(splitAt + 1) };
    }

    @Override
    public void close() throws IOException {
        this.local = null;
        this.global = null;
    }

    /**
     * @return a file config database that only supports global operations against
     *         {@code $HOME/.geogigconfig}
     */
    public static ConfigDatabase globalOnly(Platform platform) {
        return new IniFileConfigDatabase(platform, null, true);
    }
}
