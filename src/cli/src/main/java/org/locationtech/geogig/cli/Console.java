/* Copyright (c) 2015 Boundless.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import org.fusesource.jansi.WindowsAnsiOutputStream;

/**
 * Represents the console (i.e. operating system terminal) from where the program is being executed,
 * allowing to read from the terminal's stdin and write to the terminal's stdout.
 * <p>
 * Additionally, a different pair of input and output streams can be provided explicitly to simulate
 * redirection, for example for unit tests.
 *
 */
public class Console {

    private static final char CARRIAGE_RETURN = '\r';

    private StringBuffer cursorBuffer;

    @SuppressWarnings("unused")
    private InputStream in;

    private PrintStream out;

    private boolean ansiEnabled;

    private boolean ansiSupported;

    /**
     * Creates a console reader that reads from {@code sdtin} and writes to {@code sdout}.
     */
    public Console() {
        this(System.in, System.out);
    }

    /**
     * Creates a Console that reads from and writes to the provided streams.
     * 
     * @param in the console's input stream
     * @param out the console's output stream
     */
    public Console(InputStream in, OutputStream out) {
        this.in = in;
        this.cursorBuffer = new StringBuffer();
        this.ansiEnabled = true;
        this.ansiSupported = checkAnsiSupported(out);
        if (out instanceof PrintStream) {
            this.out = (PrintStream) out;
        } else {
            boolean autoFlush = true;
            this.out = new PrintStream(out, autoFlush);
        }
    }

    /**
     * Returns whether writing ANSI escape sequences are supported by the console.
     * <p>
     * {@code true} will only be returned if the console's output is not redirected to a file or
     * piped, this console's output stream is "stdout", and the JVM has been invoked from a terminal
     * that supports ANSI codes.
     * <p>
     * If {@link #disableAnsi()} has been called, returns {@code false} immediately.
     * 
     * @return {@code true} if ANSI terminal color codes are supported by the console, {@code false}
     *         otherwise.
     */
    public boolean isAnsiSupported() {
        return ansiEnabled && ansiSupported;
    }

    private static boolean checkAnsiSupported(OutputStream out) {
        if (out != System.out) {
            return false;
        }
        if (System.console() == null) {
            return false;
        }

        final String osname = System.getProperty("os.name");
        if (osname.toLowerCase().startsWith("windows")) {
            try {
                new WindowsAnsiOutputStream(out);
            } catch (Throwable e) {
                // The required Windows native lib is not available
                return false;
            }
        }

        return true;
    }

    /**
     * Disables ANSI terminal color support, regardless of the auto-detection performed by
     * {@link #isAnsiSupported()}.
     * 
     * @return {@code this}
     */
    public Console disableAnsi() {
        this.ansiEnabled = false;
        return this;
    }

    /**
     * Writes the given char sequence to the cursor buffer, does not flush the buffer.
     * 
     * @param s the character sequence to write to the console
     * @throws IOException
     */
    public void print(CharSequence s) throws IOException {
        cursorBuffer.append(s);
    }

    /**
     * Print a new line, flushing the cursor buffer.
     * 
     * @throws IOException
     */
    public void println() throws IOException {
        println("");
    }

    /**
     * Prints the {@code line} text to the console and starts a new line, flushing the cursor
     * buffer.
     * 
     * @param line the text to write
     * @throws IOException
     */
    public void println(CharSequence line) throws IOException {
        print(line);
        cursorBuffer.append("\n");
        flush();
    }

    /**
     * Forces flushing the cursor buffer to the console's output stream.
     * 
     * @throws IOException
     */
    public void flush() throws IOException {
        String s = cursorBuffer.toString();
        out.print(s);
        clearBuffer();
    }

    /**
     * Moves the console cursor to the beginning of the line (prints a carriage return {@code '\r'}
     * character) and redraws the contents of the cursor buffer.
     * 
     * @throws IOException
     */
    public void redrawLine() throws IOException {
        cursorBuffer.append(CARRIAGE_RETURN);
        flush();
    }

    /**
     * Clear the console's un-flushed buffer
     */
    public void clearBuffer() {
        this.cursorBuffer.setLength(0);
    }

}
