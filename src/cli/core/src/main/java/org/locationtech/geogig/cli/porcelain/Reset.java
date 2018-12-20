/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Johnathan Garrett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli.porcelain;

import java.io.IOException;
import java.util.List;

import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.InvalidParameterException;
import org.locationtech.geogig.model.DiffEntry;
import org.locationtech.geogig.model.DiffEntry.ChangeType;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.plumbing.DiffWorkTree;
import org.locationtech.geogig.plumbing.RevParse;
import org.locationtech.geogig.porcelain.ResetOp;
import org.locationtech.geogig.porcelain.ResetOp.ResetMode;
import org.locationtech.geogig.repository.impl.GeoGIG;
import org.locationtech.geogig.storage.AutoCloseableIterator;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Optional;
import com.google.common.base.Suppliers;

/**
 * There are two forms of geogig reset. In the first form, copy entries from {@code <commit>} to the
 * index. In the second form, set the current branch head (HEAD) to {@code <commit>}, optionally
 * modifying index and working tree to match. The {@code <commit>} defaults to HEAD in both forms.
 * <p>
 * {@code geogig reset [<commit>] --path <path>...}
 * <p>
 * This form resets the index entries for all {@code <path>} to their state at {@code <commit>}. (It
 * does not affect the working tree, nor the current branch.)
 * <p>
 * {@code geogig reset --(hard|soft|mixed) [<commit>]}
 * <p>
 * This form resets the current branch head to {@code <commit>} and possibly updates the index
 * (resetting it to the tree of {@code <commit>}) and the working tree depending on {@code <mode>},
 * which must be one of the following:
 * <p>
 * {@code --soft} Does not touch the index file nor the working tree at all (but resets the head to
 * {@code <commit>}, just like all modes do). This leaves all your changed files
 * "Changes to be committed", as {@code geogig status} would put it.
 * <p>
 * {@code --mixed} Resets the index but not the working tree (i.e., the changed files are preserved
 * but not marked for commit) and reports what has not been updated. This is the default action.
 * <p>
 * {@code --hard} Resets the index and working tree. Any changes to tracked files in the working
 * tree since {@code <commit>} are discarded.
 * <p>
 * CLI proxy for {@link ResetOp}
 * <p>
 * Usage:
 * <ul>
 * <li> {@code geogig reset [<commit>] --path <path>...}
 * <li> {@code geogig reset --(hard|soft|mixed) [<commit>]}
 * </ul>
 * 
 * @see ResetOp
 */
@Parameters(commandNames = { "reset" }, commandDescription = "Reset current HEAD to the specified state, optionally modifying index and working tree to match")
public class Reset extends AbstractCommand implements CLICommand {

    @Parameter(names = { "--hard" }, description = "Resets the index and the working tree.")
    private boolean hard;

    @Parameter(names = { "--mixed" }, description = "Resets the index, but not the working tree.")
    private boolean mixed;

    @Parameter(names = { "--soft" }, description = "Does not affect index or working tree.")
    private boolean soft;

    @Parameter(description = "[<commit>]", arity = 1)
    private List<String> commit;

    @Parameter(names = { "-p", "--path" }, description = "<path>...", variableArity = true)
    private List<String> args;

    /**
     * Executes the reset command using the provided options.
     * 
     * @param cli
     * @see org.locationtech.geogig.cli.AbstractCommand#runInternal(org.locationtech.geogig.cli.GeogigCLI)
     */
    @Override
    public void runInternal(GeogigCLI cli) {
        final GeoGIG geogig = cli.getGeogig();

        ResetMode mode = resolveResetMode();

        ResetOp reset = cli.getGeogig().command(ResetOp.class);
        try {
            for (int i = 0; args != null && i < args.size(); i++) {
                reset.addPattern(args.get(i));
            }

            if (commit != null && commit.size() > 0) {
                Optional<ObjectId> commitId = geogig.command(RevParse.class)
                        .setRefSpec(commit.get(0)).call();
                checkParameter(commitId.isPresent(), "Commit could not be resolved.");
                reset.setCommit(Suppliers.ofInstance(commitId.get()));
            }

            reset.setMode(mode);

            reset.call();
        } catch (IllegalArgumentException iae) {
            throw new InvalidParameterException(iae.getMessage(), iae);
        } catch (IllegalStateException ise) {
            throw new CommandFailedException(ise.getMessage(), ise);
        }

        if (!geogig.getRepository().workingTree().isClean()) {
            try {
                try (AutoCloseableIterator<DiffEntry> unstaged = geogig.command(DiffWorkTree.class)
                        .setFilter(null).call()) {
                    cli.getConsole().println("Unstaged changes after reset:");
                    while (unstaged.hasNext()) {
                        DiffEntry entry = unstaged.next();
                        ChangeType type = entry.changeType();
                        switch (type) {
                        case ADDED:
                            cli.getConsole().println("A\t" + entry.newPath());
                            break;
                        case MODIFIED:
                            cli.getConsole().println("M\t" + entry.newPath());
                            break;
                        case REMOVED:
                            cli.getConsole().println("D\t" + entry.oldPath());
                            break;
                        }
                    }
                }
            } catch (IOException e) {

            }
        }

    }

    private ResetMode resolveResetMode() {
        ResetMode mode = ResetMode.NONE;
        if (hard) {
            mode = ResetMode.HARD;
        }
        if (mixed) {
            if (mode != ResetMode.NONE) {
                throw new InvalidParameterException("You may only specify one mode.");
            }
            mode = ResetMode.MIXED;
        }
        if (soft) {
            if (mode != ResetMode.NONE) {
                throw new InvalidParameterException("You may only specify one mode.");
            }
            mode = ResetMode.SOFT;
        }
        return mode;
    }

}
