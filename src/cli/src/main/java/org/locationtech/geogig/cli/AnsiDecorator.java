/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli;

import java.io.Console;

import org.fusesource.jansi.Ansi;

/**
 *
 */
public class AnsiDecorator extends Ansi {

    private static final Console SYSTEM_CONSOLE = System.console();

    private AnsiDecorator(StringBuilder sb) {
        super(sb);
    }

    public static Ansi newAnsi(boolean ansiSupported) {
        StringBuilder sb = new StringBuilder();
        return newAnsi(ansiSupported, sb);
    }

    public static Ansi newAnsi(boolean ansiSupported, StringBuilder sb) {
        Ansi ansi = new Ansi(sb);
        ansiSupported &= null != SYSTEM_CONSOLE;
        if (ansiSupported) {
            return ansi;
        }

        AnsiDecorator ansiDecorator = new AnsiDecorator(sb);
        return ansiDecorator;
    }

    @Override
    public Ansi a(Attribute ignored) {
        return this;
    }

    @Override
    public Ansi bg(Color c) {
        return this;
    }

    @Override
    public Ansi bold() {
        return this;
    }

    @Override
    public Ansi boldOff() {
        return this;
    }

    @Override
    public Ansi fg(Color c) {
        return this;
    }

}
