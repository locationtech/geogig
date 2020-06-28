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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.NumberFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jdt.annotation.Nullable;
import org.locationtech.geogig.cli.annotation.ObjectDatabaseReadOnly;
import org.locationtech.geogig.cli.annotation.ReadOnly;
import org.locationtech.geogig.cli.annotation.RemotesReadOnly;
import org.locationtech.geogig.dsl.Geogig;
import org.locationtech.geogig.hooks.CannotRunGeogigOperationException;
import org.locationtech.geogig.model.ServiceFinder;
import org.locationtech.geogig.plumbing.ResolveGeogigURI;
import org.locationtech.geogig.repository.Context;
import org.locationtech.geogig.repository.DefaultPlatform;
import org.locationtech.geogig.repository.DefaultProgressListener;
import org.locationtech.geogig.repository.Hints;
import org.locationtech.geogig.repository.Platform;
import org.locationtech.geogig.repository.ProgressListener;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.impl.GlobalContextBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Iterators;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import picocli.CommandLine;
import picocli.CommandLine.IExecutionExceptionHandler;
import picocli.CommandLine.IParameterExceptionHandler;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;
import picocli.CommandLine.UnmatchedArgumentException;

/**
 * Command Line Interface for geogig.
 * <p>
 * Looks up and executes {@link CLICommand} implementations provided by any
 * {@link CLICommandExtension} declared in any classpath's
 * {@code META-INF/services/org.locationtech.geogig.cli.CLICommandExtension} file.
 */
@Slf4j
public class GeogigCLI {

    private static final Logger LOGGER = LoggerFactory.getLogger(GeogigCLI.class);

    private Context geogigContext;

    private Platform platform;

    private Geogig geogig;

    private final Geogig providedGeogig;

    private final Console consoleReader;

    protected ProgressListener progressListener;

    private boolean exitOnFinish = true;

    private Hints hints = Hints.readWrite();

    private boolean progressListenerDisabled;

    private String repositoryURI;

    /**
     * Construct a GeogigCLI with the given console reader.
     * 
     * @param consoleReader
     */
    public GeogigCLI(final Console consoleReader) {
        this(null, consoleReader);
    }

    /**
     * Constructor to use the provided {@code GeoGIG} instance and never try to close it.
     */
    public GeogigCLI(final Geogig geogig, final Console consoleReader) {
        this.consoleReader = consoleReader;
        this.platform = new DefaultPlatform();
        this.providedGeogig = geogig;
    }

    /**
     * @return the platform being used by the geogig command line interface.
     * @see Platform
     */
    public Platform getPlatform() {
        return platform;
    }

    /**
     * Sets the platform for the command line interface to use.
     * 
     * @param platform the platform to use
     * @see Platform
     */
    public void setPlatform(@NonNull Platform platform) {
        this.platform = platform;
    }

    /**
     * @param repoURI if given, used as the repository URL when dereferencing the repository,
     *        otherwise the platform's working dir is used.
     */
    public void setRepositoryURI(String repoURI) {
        this.repositoryURI = repoURI;
    }

    public String getRepositoryURI() {
        return repositoryURI;
    }

    public GeogigCLI disableProgressListener() {
        this.progressListenerDisabled = true;
        return this;
    }

    /**
     * Provides a GeoGIG facade configured for the current repository if inside a repository,
     * {@code null} otherwise.
     * <p>
     * Note the repository is lazily loaded and cached afterwards to simplify the execution of
     * commands or command options that do not need a live repository.
     * 
     * @return the GeoGIG facade associated with the current repository, or {@code null} if there's
     *         no repository in the current {@link Platform#pwd() working directory}
     * @see ResolveGeogigURI
     */
    @Nullable
    public synchronized Geogig getGeogig() {
        if (providedGeogig != null) {
            return providedGeogig;
        }
        if (geogig == null) {
            Geogig geogig = loadRepository();
            setGeogig(geogig);
        }
        return geogig;
    }

    public @NonNull Repository repository() {
        return getGeogig().getRepository();
    }

    public @NonNull org.locationtech.geogig.dsl.Geogig geogig() {
        return org.locationtech.geogig.dsl.Geogig.of(repository().context());
    }

    @VisibleForTesting
    public synchronized Geogig getGeogig(Hints hints) {
        close();
        Geogig geogig = loadRepository(hints);
        setGeogig(geogig);
        return geogig;
    }

    /**
     * Gives the command line interface a GeoGIG facade to use.
     * 
     * @param geogig
     */
    public void setGeogig(@Nullable Geogig geogig) {
        this.geogig = geogig;
    }

    /**
     * Sets flag controlling whether the cli will call {@link System#exit(int)} when done running
     * the command.
     * <p>
     * Commands should call this method only in cases where the starts a server or creates
     * additional threads.
     * </p>
     * 
     * @param exit <tt>true</tt> will cause the cli to exit.
     */
    public void setExitOnFinish(boolean exit) {
        this.exitOnFinish = exit;
    }

    /**
     * Returns flag controlling whether cli will exit on completion.
     * 
     * @see {@link #setExitOnFinish(boolean)}
     */
    public boolean isExitOnFinish() {
        return exitOnFinish;
    }

    /**
     * Loads the repository _if_ inside a geogig repository and returns a configured {@link Geogig}
     * facade.
     * 
     * @return a geogig for the current repository or {@code null} if not inside a geogig repository
     *         directory.
     */
    @Nullable
    private Geogig loadRepository() {
        return loadRepository(this.hints);
    }

    @Nullable
    private Geogig loadRepository(Hints hints) {
        Geogig geogig = newGeoGIG(hints);

        if (geogig.command(ResolveGeogigURI.class).call().isPresent()) {
            Repository repository = geogig.getRepository();
            if (repository != null) {
                return geogig;
            }
        }
        geogig.close();

        return null;
    }

    /**
     * Constructs and returns a new read-write geogig facade, which will not be managed by this
     * GeogigCLI instance, so the calling code is responsible for closing/disposing it after usage
     * 
     * @return the constructed GeoGIG.
     */
    public Geogig newGeoGIG() {
        return newGeoGIG(Hints.readWrite());
    }

    /**
     * try opening, if present, may return null Repository but the GeoGIG instance is still valid
     * and may being used to init a repo;
     */
    public @NonNull Geogig newGeoGIG(Hints hints) {
        Context context = newGeogigContext(hints);

        Geogig geogig = Geogig.of(context);
        geogig.getRepository();

        return geogig;
    }

    /**
     * @return the Context being used by the command line interface. If one hasn't been made, it
     *         will be created.
     */
    public Context getContext() {
        return getContext(this.hints);
    }

    private Context getContext(Hints hints) {
        if (this.geogigContext == null || !Objects.equal(this.hints, hints)) {
            geogigContext = newGeogigContext(hints);
        }
        return geogigContext;
    }

    private Context newGeogigContext(Hints hints) {
        if (repositoryURI != null) {
            LOGGER.debug("using REPO_URL '{}'", repositoryURI);
            hints.set(Hints.REPOSITORY_URL, repositoryURI);
        }
        if (!hints.get(Hints.PLATFORM).isPresent()) {
            hints.set(Hints.PLATFORM, this.platform);
        }
        Context context = GlobalContextBuilder.builder().build(hints);
        return context;
    }

    /**
     * @return the console reader being used by the command line interface.
     */
    public Console getConsole() {
        return consoleReader;
    }

    /**
     * Closes the GeoGIG facade if it exists.
     */
    public synchronized void close() {
        if (providedGeogig != null) {
            return;
        }
        if (geogig != null) {
            geogig.close();
            geogig = null;
        }
        this.hints = Hints.readWrite();
        this.geogigContext = null;
    }

    /**
     * @return true if a command is being ran
     */
    public synchronized boolean isRunning() {
        return geogig != null;
    }

    /**
     * Finds all commands that are bound do the command SPI.
     * 
     * @return a collection of keys, one for each command
     */
    private List<CLICommand> findCommands() {
        List<CLICommand> subcomands = new ServiceFinder().lookupServices(CLICommand.class);
        return subcomands;
    }

    public CommandLine buildRootCommand() {
        org.locationtech.geogig.cli.Geogig command = new org.locationtech.geogig.cli.Geogig(this);
        CommandLine cmdline = new CommandLine(command);
        cmdline.setCommandName("geogig");
        cmdline.setPosixClusteredShortOptionsAllowed(true);
        cmdline.setTrimQuotes(true);
        for (CLICommand extension : findCommands()) {
            cmdline.addSubcommand(extension.getCommandName(), extension);
            if (log.isDebugEnabled()) {
                log.debug("Registered command extension {}", extension.getCommandName());
            }
        }
        return cmdline;
    }

    @VisibleForTesting
    public Exception exception;

    /**
     * Processes a command, catching any exceptions and printing their messages to the console.
     * 
     * @param args
     * @return 0 for normal exit, -1 if there was an exception.
     */
    public int execute(String... args) {
        exception = null;
        String consoleMessage = null;
        boolean printError = true;
        try {
            executeInternal(args);
            consoleReader.flush();
            return 0;
        } catch (ParameterException | IllegalArgumentException
                | InvalidParameterException paramValidationError) {
            exception = paramValidationError;
            consoleMessage = paramValidationError.getMessage();
        } catch (CannotRunGeogigOperationException cannotRun) {

            consoleMessage = cannotRun.getMessage();

        } catch (IllegalStateException | CommandFailedException cmdFailed) {
            exception = cmdFailed;
            if (null == cmdFailed.getMessage()) {
                // this is intentional, see the javadoc for CommandFailedException
                printError = false;
            } else {
                consoleMessage = cmdFailed.getMessage();
                if (!(cmdFailed instanceof CommandFailedException)
                        || !((CommandFailedException) cmdFailed).reportOnly) {
                    LOGGER.error(consoleMessage, Throwables.getRootCause(cmdFailed));
                }
            }
        } catch (RuntimeException e) {
            exception = e;
            consoleMessage = String.format(
                    "An unhandled error occurred: %s. See the log for more details.",
                    e.getMessage());
            LOGGER.error(consoleMessage, e);
        } catch (IOException ioe) {
            exception = ioe;
            // can't write to the console, see the javadocs for CLICommand.run().
            LOGGER.error(
                    "An IOException was caught, should only happen if an error occurred while writing to the console",
                    ioe);
        } finally {
            // close after executing a command for the next one to reopen with its own hints and not
            // to keep the db's open for write meanwhile
            if (isExitOnFinish()) {
                close();
            }
        }
        try {
            if (printError) {
                consoleReader.println(Optional.ofNullable(consoleMessage).orElse("Unknown Error"));
            }
            consoleReader.flush();
        } catch (IOException e) {
            LOGGER.error("Error writing to the console. Original error: {}", consoleMessage, e);
        }
        return -1;
    }

    /**
     * Executes a command.
     * 
     * @param args
     * @throws exceptions thrown by the executed commands.
     */
    private void executeInternal(String... args) throws ParameterException, CommandFailedException,
            IOException, CannotRunGeogigOperationException {
        checkNotNull(args, "args is null");
        INSTANCE.set(this);
        String repoURI = parseRepoURI(args);
        if (repoURI != null) {
            this.repositoryURI = repoURI;
            URI uri = URI.create(repoURI);
            File pwd = this.platform.pwd();
            if (Strings.isNullOrEmpty(uri.getScheme())) {
                pwd = new File(uri.toString());
            } else if ("file".equals(uri.getScheme())) {
                pwd = new File(uri);
            }
            this.platform.setWorkingDir(pwd);
        }

        final AtomicReference<Exception> cmdException = new AtomicReference<Exception>();
        IExecutionExceptionHandler exceptionHandler = new IExecutionExceptionHandler() {
            public @Override int handleExecutionException(Exception ex, CommandLine commandLine,
                    ParseResult parseResult) {
                cmdException.set(ex);
                return 0;
            }
        };
        IParameterExceptionHandler parameterExceptionHandler = new IParameterExceptionHandler() {
            public @Override int handleParseException(ParameterException ex, String[] args) {
                String message = ex.getMessage();
                if (ex instanceof UnmatchedArgumentException) {
                    StringWriter sw = new StringWriter();
                    UnmatchedArgumentException.printSuggestions(ex, new PrintWriter(sw));
                    message = "Unknown command. " + sw.toString();
                }
                cmdException.set(new InvalidParameterException(message, ex));
                return 0;
            }
        };
        CommandLine cmd = buildRootCommand();
        cmd.setOut(new PrintWriter(consoleReader.out()));
        cmd.setExecutionExceptionHandler(exceptionHandler);
        cmd.setParameterExceptionHandler(parameterExceptionHandler);
        cmd.execute(args);
        Exception exception = cmdException.get();
        if (exception != null) {
            Throwables.throwIfInstanceOf(exception, ParameterException.class);
            Throwables.throwIfInstanceOf(exception, CommandFailedException.class);
            Throwables.throwIfInstanceOf(exception, IOException.class);
            Throwables.throwIfInstanceOf(exception, CannotRunGeogigOperationException.class);
            Throwables.throwIfUnchecked(exception);
            throw new RuntimeException(exception);
        }

    }

    private String parseRepoURI(String[] args) {
        for (Iterator<String> it = Iterators.forArray(args); it.hasNext();) {
            String a = it.next();
            if ("--repo".equals(a)) {
                Preconditions.checkArgument(it.hasNext(),
                        "Argument --repo must be followed by a repository URI");
                String uri = it.next();
                try {
                    new URI(uri);
                } catch (URISyntaxException e) {
                    throw new IllegalArgumentException("Invalid repository URI: '" + uri + "'");
                }
                return uri;
            }
        }
        return null;
    }

    private Hints gatherHints(Class<? extends CLICommand> cmdClass) {
        Hints hints = new Hints();

        checkAnnotationHint(cmdClass, ReadOnly.class, Hints.OBJECTS_READ_ONLY, hints);

        checkAnnotationHint(cmdClass, ObjectDatabaseReadOnly.class, Hints.OBJECTS_READ_ONLY, hints);
        checkAnnotationHint(cmdClass, RemotesReadOnly.class, Hints.REMOTES_READ_ONLY, hints);

        return hints;
    }

    private void checkAnnotationHint(Class<? extends CLICommand> cmdClass,
            Class<? extends Annotation> annotation, String key, Hints hints) {

        if (cmdClass.isAnnotationPresent(annotation)) {
            hints.set(key, Boolean.TRUE);
        }
    }

    /**
     * /**
     * 
     * @return the ProgressListener for the command line interface. If it doesn't exist, a new one
     *         will be constructed.
     * @see ProgressListener
     */
    public synchronized ProgressListener getProgressListener() {
        if (this.progressListener == null) {
            if (progressListenerDisabled) {
                this.progressListener = new DefaultProgressListener();
                return this.progressListener;
            }
            this.progressListener = new DefaultProgressListener() {

                private final Platform platform = getPlatform();

                private final Console console = getConsole();

                private final long delayNanos = TimeUnit.NANOSECONDS.convert(150,
                        TimeUnit.MILLISECONDS);

                // Don't skip the first update
                private volatile long lastRun = 0;

                public @Override void started() {
                    super.started();
                    lastRun = -(delayNanos + 1);
                }

                public @Override void setDescription(String s, Object... args) {
                    lastRun = platform.nanoTime();
                    try {
                        console.println();
                        console.println(String.format(s, args));
                        console.flush();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                public @Override synchronized void complete() {
                    // avoid double logging if caller missbehaves
                    if (super.isCompleted()) {
                        return;
                    }
                    try {
                        logProgress();
                        console.clearBuffer();
                        super.complete();
                        super.dispose();
                    } catch (Exception e) {
                        Throwables.throwIfUnchecked(e);
                        throw new RuntimeException(e);
                    }
                }

                public @Override void setProgress(float percent) {
                    super.setProgress(percent);
                    long nanoTime = platform.nanoTime();
                    if ((nanoTime - lastRun) > delayNanos) {
                        lastRun = nanoTime;
                        logProgress();
                    }
                }

                float lastProgress = -1;

                private void logProgress() {
                    float progress = getProgress();
                    if (lastProgress == progress) {
                        return;
                    }
                    lastProgress = progress;
                    console.clearBuffer();
                    String description = getDescription();
                    try {
                        if (description != null) {
                            console.print(description);
                        }
                        String progressDescription = super.getProgressDescription();
                        console.redrawLine();
                        console.print(progressDescription);
                        console.flush();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                }
            };

            this.progressListener.setProgressIndicator((p) -> {
                final NumberFormat percentFormat = NumberFormat.getPercentInstance();
                final NumberFormat numberFormat = NumberFormat.getIntegerInstance();

                float percent = p.getProgress();
                if (percent > 100) {
                    return numberFormat.format(percent);
                }
                return percentFormat.format(percent / 100f);

            });
        }
        return this.progressListener;
    }

    private static ThreadLocal<GeogigCLI> INSTANCE = new ThreadLocal<>();

    public static @NonNull GeogigCLI get() {
        return INSTANCE.get();
    }

}
