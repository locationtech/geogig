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

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.util.IllegalFormatException;

import org.eclipse.jdt.annotation.Nullable;
import org.fusesource.jansi.Ansi;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.cli.porcelain.ColorArg;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * A template command.
 * <p>
 * Services provided to subclasses:
 * <ul>
 * <li>Defines {@link RequiresRepository @RequiresRepository(true)} so its inherited by subclasses
 * and they need only to declare it if they don't require a repository present (which is the least
 * of the cases).
 * <li>Out of the box support for the {@code --help} argument
 * <li>Out of the box support for the hidden {@code --color} argument, allowing any command to
 * seamlessly support output text coloring or disabling it (see {@link ColorArg})
 * <li>The {@link #newAnsi(Terminal)} method provides an {@link Ansi} instance configured to support
 * coloring or not depending on the {@link Terminal} capabilities and the value of the
 * {@code --color} argument, if present.
 * </p>
 * 
 */
@RequiresRepository(true)
public abstract class AbstractCommand implements CLICommand {

    @Parameter(names = "--help", help = true, hidden = true)
    public boolean help;

    @Parameter(hidden = true, names = "--repo", description = "Repository location. Either a backend specific URL or the path to the folder containing the .geogig directory.")
    public String repo;

    @Parameter(hidden = true, names = "--color", description = "Whether to apply colored output. Possible values are auto|never|always.", converter = ColorArg.Converter.class)
    public ColorArg color = ColorArg.auto;

    @Override
    public void run(GeogigCLI cli) throws InvalidParameterException, CommandFailedException {
        checkNotNull(cli, "No GeogigCLI provided");
        if (help) {
            printUsage(cli);
            return;
        }

        if (repo != null) {
            cli.setRepositoryURI(repo);
        }
        try {
            runInternal(cli);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Ansi newAnsi(Console console) {
        boolean useColor;
        switch (color) {
        case never:
            useColor = false;
            break;
        case always:
            useColor = true;
            break;
        default:
            useColor = console.isAnsiSupported();
        }

        Ansi ansi = AnsiDecorator.newAnsi(useColor);
        return ansi;
    }

    protected Ansi newAnsi(Console console, StringBuilder target) {
        boolean useColor;
        switch (color) {
        case never:
            useColor = false;
            break;
        case always:
            useColor = true;
            break;
        default:
            useColor = console.isAnsiSupported();
        }

        Ansi ansi = AnsiDecorator.newAnsi(useColor, target);
        return ansi;
    }

    /**
     * Subclasses shall implement to do the real work, will not be called if the command was invoked
     * with {@code --help}. Also, {@link GeogigCLI#getGeogig() cli.getGeogig()} is guaranteed to be
     * non null (e.g. there's a working repository) if the implementation class is marked with the
     * {@link RequiresRepository @RequiresRepository} annotation.
     * 
     * @throws InvalidParameterException as per {@link CLICommand#run(GeogigCLI)}
     * @throws CommandFailedException as per {@link CLICommand#run(GeogigCLI)}
     * @throws IOException <b>only</b> propagated back if the IOException was thrown while writing
     *         to the {@link GeogigCLI#getConsole() console}.
     * @param cli
     */
    protected abstract void runInternal(GeogigCLI cli) throws InvalidParameterException,
            CommandFailedException, IOException;

    /**
     * Prints the JCommander usage for this command.
     */
    public void printUsage(GeogigCLI cli) {
        JCommander jc = new JCommander(this);
        String commandName = this.getClass().getAnnotation(Parameters.class).commandNames()[0];
        jc.setProgramName("geogig " + commandName);
        cli.printUsage(jc);
    }

    /**
     * Checks the truth of the boolean expression and throws a {@link InvalidParameterException} if
     * its {@code false}.
     * <p>
     * CLI commands may use this helper method to check the validity of user supplied command
     * arguments.
     * 
     * @param expression a boolean expression
     * @param errorMessage the exception message to use if the check fails; will be converted to a
     *        string using {@link String#valueOf(Object)}
     * @throws InvalidParameterException if {@code expression} is false
     */
    public static void checkParameter(boolean expression, @Nullable Object errorMessage) {
        if (!expression) {
            throw new InvalidParameterException(String.valueOf(errorMessage));
        }
    }

    /**
     * /** Checks the truth of the boolean expression and throws a {@link InvalidParameterException}
     * if its {@code false}.
     * <p>
     * CLI commands may use this helper method to check the validity of user supplied command
     * arguments.
     * 
     * @param expression a boolean expression
     * @param errorMessageTemplate a template for the exception message should the check fail. The
     *        message is formed as per {@link String#format(String, Object...)}
     * @param errorMessageArgs the arguments to be substituted into the message template.
     * @throws InvalidParameterException if {@code expression} is {@code false}
     * @throws IllegalFormatException if thrown by {@link String#format(String, Object...)}
     * @throws NullPointerException If the <tt>format</tt> is {@code null}
     * 
     */
    public static void checkParameter(boolean expression, String errorMessageTemplate,
            Object... errorMessageArgs) {
        if (!expression) {
            throw new InvalidParameterException(String.format(errorMessageTemplate,
                    errorMessageArgs));
        }
    }
}
