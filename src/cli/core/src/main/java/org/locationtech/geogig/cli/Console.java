/* Copyright (c) 2015-2016 Boundless and others.
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
 * Represents the console (i.e. operating system terminal) from where the
 * program is being executed, allowing to read from the terminal's stdin and
 * write to the terminal's stdout.
 * <p>
 * Additionally, a different pair of input and output streams can be provided
 * explicitly to simulate redirection, for example for unit tests.
 *
 */
public class Console {

	private static final char CARRIAGE_RETURN = '\r';
	private static final String CLEAR_FROM_CURSOR_TO_END_OF_LINE = "\u001b[0K";
	private static final String CLEAR_FROM_CURSOR_TO_START_OF_LINE = "\u001b[1K";
	private static final String CLEAR_LINE = "\u001b[2K";

	private StringBuffer cursorBuffer;

	@SuppressWarnings("unused")
	private InputStream in;

	private PrintStream out;

	private boolean ansiEnabled;

	private boolean ansiSupported;

	private boolean forceAnsi;

	/**
	 * Creates a console reader that reads from {@code sdtin} and writes to
	 * {@code sdout}.
	 */
	public Console() {
		this(System.in, System.out);
	}

	/**
	 * Creates a Console that reads from and writes to the provided streams.
	 * 
	 * @param in  the console's input stream
	 * @param out the console's output stream
	 */
	public Console(InputStream in, OutputStream out) {
		this.in = in;
		this.cursorBuffer = new StringBuffer();
		this.ansiEnabled = true;
		this.forceAnsi = false;
		try {
			this.ansiSupported = checkAnsiSupported(out, checkOS());
		} catch (UnsatisfiedLinkError e) {
			this.ansiSupported = false;
		} catch (Throwable e) {
			this.ansiSupported = false;
		}
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
	 * {@code true} will only be returned if the console's output is not redirected
	 * to a file or piped, this console's output stream is "stdout", and the JVM has
	 * been invoked from a terminal that supports ANSI codes. Setting ansi.enabled
	 * as a config option will cause this to return true.
	 * <p>
	 * If {@link #forceAnsi} is true, this returns true immediately. Otherwise if
	 * {@link #disableAnsi()} has been called, returns {@code false} immediately.
	 * 
	 * @return {@code true} if ANSI terminal color codes are supported by the
	 *         console, {@code false} otherwise.
	 */
	public boolean isAnsiSupported() {
		return forceAnsi || (ansiEnabled && ansiSupported);
	}

	public boolean checkAnsiSupported(OutputStream out, String osName) throws Throwable {
		if (out != System.out) {
			return false;
		}

		if (osName.startsWith("windows") && osName.endsWith("10")) {
			new WindowsAnsiOutputStream(out);
		} else if (osName.startsWith("windows") && !osName.endsWith("10")) {
			return false;
		}

		if (System.console() == null) {
			return false;
		}
		return true;
	}

	public String checkOS() {
		return System.getProperty("os.name").toLowerCase();
	}

	/**
	 * Disables ANSI terminal color support, regardless of the auto-detection
	 * performed by {@link #isAnsiSupported()}.
	 * 
	 * @return {@code this}
	 */
	public Console disableAnsi() {
		this.ansiEnabled = false;
		return this;
	}

	/**
	 * Enables ANSI terminal color support, regardless of the auto-detection
	 * performed by {@link #isAnsiSupported()}.
	 *
	 * @return {@code this}
	 */
	public Console enableAnsi() {
		this.ansiEnabled = true;
		return this;
	}

	/**
	 * Flag to force enable ANSI terminal color support, used for windows consoles
	 *
	 * @return {@code this}
	 */
	public Console setForceAnsi(boolean force) {
		this.forceAnsi = force;
		return this;
	}

	/**
	 * Writes the given char sequence to the cursor buffer, does not flush the
	 * buffer.
	 * 
	 * @param s the character sequence to write to the console
	 * @throws IOException
	 */
	public synchronized void print(CharSequence s) throws IOException {
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
	 * Prints the {@code line} text to the console and starts a new line, flushing
	 * the cursor buffer.
	 * 
	 * @param line the text to write
	 * @throws IOException
	 */
	public synchronized void println(CharSequence line) throws IOException {
		print(line);
		cursorBuffer.append("\n");
		flush();
	}

	/**
	 * Forces flushing the cursor buffer to the console's output stream.
	 * 
	 * @throws IOException
	 */
	public synchronized void flush() throws IOException {
		String s = cursorBuffer.toString();
		out.print(s);
		clearBuffer();
	}

	/**
	 * Moves the console cursor to the beginning of the line (prints a carriage
	 * return {@code '\r'} character) and redraws the contents of the cursor buffer.
	 * 
	 * @throws IOException
	 */
	public void redrawLine() throws IOException {
		cursorBuffer.append(CARRIAGE_RETURN);
		cursorBuffer.append(CLEAR_LINE);
		flush();
	}

	/**
	 * Clear the console's un-flushed buffer
	 */
	public void clearBuffer() {
		this.cursorBuffer.setLength(0);
	}

}
