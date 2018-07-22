/* Copyright (c) 2012-2016 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Michael Fawcett (LMN Solutions) - initial implementation
 */
package org.locationtech.geogig.cli;

import org.locationtech.geogig.cli.plumbing.Cat;
import org.locationtech.geogig.cli.plumbing.DiffTree;
import org.locationtech.geogig.cli.plumbing.Insert;
import org.locationtech.geogig.cli.plumbing.LsTree;
import org.locationtech.geogig.cli.plumbing.MergeBase;
import org.locationtech.geogig.cli.plumbing.RebuildGraph;
import org.locationtech.geogig.cli.plumbing.RevList;
import org.locationtech.geogig.cli.plumbing.RevParse;
import org.locationtech.geogig.cli.plumbing.ShowRef;
import org.locationtech.geogig.cli.plumbing.VerifyPatch;
import org.locationtech.geogig.cli.plumbing.WalkGraph;
import org.locationtech.geogig.cli.porcelain.Add;
import org.locationtech.geogig.cli.porcelain.Apply;
import org.locationtech.geogig.cli.porcelain.Blame;
import org.locationtech.geogig.cli.porcelain.Branch;
import org.locationtech.geogig.cli.porcelain.Checkout;
import org.locationtech.geogig.cli.porcelain.CherryPick;
import org.locationtech.geogig.cli.porcelain.Clean;
import org.locationtech.geogig.cli.porcelain.Commit;
import org.locationtech.geogig.cli.porcelain.Config;
import org.locationtech.geogig.cli.porcelain.Conflicts;
import org.locationtech.geogig.cli.porcelain.Diff;
import org.locationtech.geogig.cli.porcelain.FormatPatch;
import org.locationtech.geogig.cli.porcelain.Help;
import org.locationtech.geogig.cli.porcelain.Init;
import org.locationtech.geogig.cli.porcelain.Log;
import org.locationtech.geogig.cli.porcelain.Ls;
import org.locationtech.geogig.cli.porcelain.Merge;
import org.locationtech.geogig.cli.porcelain.Rebase;
import org.locationtech.geogig.cli.porcelain.Remove;
import org.locationtech.geogig.cli.porcelain.Reset;
import org.locationtech.geogig.cli.porcelain.Revert;
import org.locationtech.geogig.cli.porcelain.Show;
import org.locationtech.geogig.cli.porcelain.Squash;
import org.locationtech.geogig.cli.porcelain.Status;
import org.locationtech.geogig.cli.porcelain.Tag;
import org.locationtech.geogig.cli.porcelain.Version;
import org.locationtech.geogig.cli.porcelain.index.IndexCommandProxy;

import com.google.inject.AbstractModule;

/**
 * Guice module providing builtin commands for the {@link GeogigCLI CLI} app.
 * 
 * @see Add
 * @see Apply
 * @see Branch
 * @see Cat
 * @see Checkout
 * @see CherryPick
 * @see Clean
 * @see Commit
 * @see Config
 * @see Conflicts
 * @see Diff
 * @see FormatPatch
 * @see VerifyPatch
 * @see Help
 * @see Init
 * @see Merge
 * @see Log
 * @see RemoteExtension
 * @see Remove
 * @see Status
 * @see Rebase
 * @see Reset
 * @see Clone
 * @see Push
 * @see Pull
 * @see Show
 * @see Fetch
 * @see Version
 * @see RebuildGraph
 */
public class BuiltinCommandsModule extends AbstractModule implements CLIModule {

    @Override
    protected void configure() {
        bind(RevParse.class);
        bind(Add.class);
        bind(Apply.class);
        bind(Blame.class);
        bind(Branch.class);
        bind(Cat.class);
        bind(Checkout.class);
        bind(CherryPick.class);
        bind(Clean.class);
        bind(Commit.class);
        bind(Config.class);
        bind(Conflicts.class);
        bind(Diff.class);
        bind(DiffTree.class);
        bind(FormatPatch.class);
        bind(VerifyPatch.class);
        bind(Help.class);
        bind(Init.class);
        bind(Insert.class);
        bind(Log.class);
        bind(Ls.class);
        bind(LsTree.class);
        bind(Merge.class);
        bind(Log.class);
        bind(MergeBase.class);
        bind(Remove.class);
        bind(Status.class);
        bind(Rebase.class);
        bind(Reset.class);
        bind(Revert.class);
        bind(RevList.class);
        bind(Show.class);
        bind(ShowRef.class);
        bind(Squash.class);
        bind(Tag.class);
        bind(WalkGraph.class);
        bind(Version.class);
        bind(RebuildGraph.class);
        bind(IndexCommandProxy.class);
    }

}
