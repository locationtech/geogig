/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import com.beust.jcommander.IStringConverter;

/**
 * This enumeration defines different the various usage options for color in some commands.
 */
public enum ColorArg {
    auto, never, always;

    /**
     * This converter is used to convert a String input into a valid enumeration value.
     */
    public static class Converter implements IStringConverter<ColorArg> {

        /**
         * @param value the string to convert
         * @return the resulting ColorArg enumeration
         */
        @Override
        public ColorArg convert(String value) {
            return ColorArg.valueOf(value);
        }

    }
}
