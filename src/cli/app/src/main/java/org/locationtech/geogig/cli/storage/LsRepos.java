/* Copyright (c) 2018 Boundless and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Distribution License v1.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/org/documents/edl-v10.html
 *
 * Contributors:
 * Gabriel Roldan (Boundless) - initial implementation
 */
package org.locationtech.geogig.cli.storage;

import static java.lang.String.format;

import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.StreamSupport;

import org.geotools.io.TableWriter;
import org.locationtech.geogig.cli.AbstractCommand;
import org.locationtech.geogig.cli.CLICommand;
import org.locationtech.geogig.cli.CommandFailedException;
import org.locationtech.geogig.cli.Console;
import org.locationtech.geogig.cli.GeogigCLI;
import org.locationtech.geogig.cli.annotation.RequiresRepository;
import org.locationtech.geogig.data.FindFeatureTypeTrees;
import org.locationtech.geogig.model.NodeRef;
import org.locationtech.geogig.model.ObjectId;
import org.locationtech.geogig.model.Ref;
import org.locationtech.geogig.model.RevCommit;
import org.locationtech.geogig.model.RevTree;
import org.locationtech.geogig.porcelain.BranchListOp;
import org.locationtech.geogig.porcelain.LogOp;
import org.locationtech.geogig.repository.Repository;
import org.locationtech.geogig.repository.RepositoryConnectionException;
import org.locationtech.geogig.repository.RepositoryResolver;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

@RequiresRepository(false)
@Parameters(commandNames = "ls-repos", commandDescription = "List repositories under a base URI")
public class LsRepos extends AbstractCommand implements CLICommand {

    @Parameter(description = "<base URI> The URI without a repository name. (e.g. geogig ls-repos postgresql://localhost:5432/geogig_db?user=...&password=...)", arity = 1)
    private List<URI> baseuri = new ArrayList<>();

    @Parameter(names = { "-v", "--verbose" }, description = "verbose output")
    private boolean verbose;

    @Parameter(names = { "-c",
            "--csv" }, description = "If verbose output, use comma separated list instead of table output")
    private boolean csv;

    protected @Override void runInternal(GeogigCLI cli) throws IOException {

        checkParameter(!baseuri.isEmpty(),
                "Usage: geogig ls-repos <base URI> (e.g. geogig ls-repos postgresql://localhost:5432/geogig_db?user=...&password=...)");

        URI baseURI = baseuri.get(0);
        RepositoryResolver resolver = RepositoryResolver.lookup(baseURI);
        List<String> repoNames = new ArrayList<>(resolver.listRepoNamesUnderRootURI(baseURI));
        Collections.sort(repoNames);
        Console console = cli.getConsole();
        if (verbose) {
            logVerbose(console, baseURI, repoNames, resolver);
        } else {
            for (String name : repoNames) {
                console.println(name);
            }
        }
    }

    private static class RepoInfo {

        public String name;

        public int numBranches;

        public long totalCommits;

        public int uniqueCommits;

        public int uniqueLayerNames;

        public long totalFeatures;

    }

    private static class Formatter {

        protected static final List<String> colNames = Arrays.asList("Name", "Branches",
                "Total commits", "Unique commits", "Feature types", "Total features");

        boolean writeHeader = true;

        protected Collection<RepoInfo> infos = new LinkedBlockingQueue<>();

        protected Console console;

        public Formatter(Console console) {
            this.console = console;
        }

        public void append(RepoInfo info) {
            infos.add(info);
        }

        public void print(Console console) {
            List<RepoInfo> infos = getSortedInfos();
            TableWriter w = new TableWriter(null);
            w.nextLine(TableWriter.DOUBLE_HORIZONTAL_LINE);
            writeColumn(w, colNames);
            w.nextLine();
            w.nextLine(TableWriter.DOUBLE_HORIZONTAL_LINE);
            infos.forEach(i -> {
                writeColumn(w, formatValues(i));
                w.nextLine();
            });
            w.nextLine(TableWriter.DOUBLE_HORIZONTAL_LINE);
            try {
                console.println(w.toString());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        private List<RepoInfo> getSortedInfos() {
            List<RepoInfo> sorted = new ArrayList<>(this.infos);
            Collections.sort(sorted, (i1, i2) -> i1.name.compareTo(i2.name));
            return sorted;
        }

        private List<String> formatValues(RepoInfo info) {
            return Arrays.asList(info.name, format("%,d", info.numBranches),
                    format("%,d", info.totalCommits), format("%,d", info.uniqueCommits),
                    format("%,d", info.uniqueLayerNames), format("%,d", info.totalFeatures));
        }

        private void writeColumn(TableWriter w, List<String> values) {
            for (int i = 0; i < values.size(); i++) {
                w.write(values.get(i));
                if (i < values.size() - 1) {
                    w.nextColumn();
                }
            }
        }
    }

    private static class CsvFormatter extends Formatter {

        public CsvFormatter(Console console) {
            super(console);
        }

        public @Override void append(RepoInfo info) {
            try {
                if (writeHeader) {
                    console.println(Joiner.on(',').join(colNames));
                    writeHeader = false;
                }
                String line = String.format("%s,%d,%d,%d,%d,%d", info.name, info.numBranches,
                        info.totalCommits, info.uniqueCommits, info.uniqueLayerNames,
                        info.totalFeatures);
                // console.println is synchronized, so we're ok being called from a parallel
                // stream
                console.println(line);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public @Override void print(Console console) {
            // do nothing
        }
    }

    private void logVerbose(Console console, URI baseURI, List<String> repoNames,
            RepositoryResolver resolver) {
        Formatter formatter = this.csv ? new CsvFormatter(console) : new Formatter(console);
        repoNames.stream().parallel().map(name -> toRepoInfo(resolver, baseURI, name))
                .forEach(formatter::append);
        formatter.print(console);
    }

    private RepoInfo toRepoInfo(RepositoryResolver resolver, URI rootRepoURI, String repoName) {
        URI repoURI = resolver.buildRepoURI(rootRepoURI, repoName);
        Repository repo;
        try {
            repo = resolver.open(repoURI);
        } catch (RepositoryConnectionException e) {
            throw new CommandFailedException(e);
        }
        try {
            return toRepoInfo(repoName, repo);
        } finally {
            repo.close();
        }
    }

    private RepoInfo toRepoInfo(String name, Repository repo) {
        RepoInfo info = new RepoInfo();
        info.name = name;
        ImmutableList<Ref> branches = repo.command(BranchListOp.class).call();
        info.numBranches = branches.size();
        final Set<ObjectId> uniqueCommits = Sets.newConcurrentHashSet();
        final Set<String> layerNames = Sets.newConcurrentHashSet();
        final AtomicLong totalCommits = new AtomicLong();
        final AtomicLong totalFeatures = new AtomicLong();
        branches.forEach(ref -> {
            ObjectId commitId = ref.getObjectId();
            Iterator<RevCommit> commits = commitId.isNull() ? Collections.emptyIterator()
                    : repo.command(LogOp.class).setUntil(commitId).call();
            Spliterator<RevCommit> spliterator = Spliterators.spliteratorUnknownSize(commits,
                    Spliterator.DISTINCT | Spliterator.IMMUTABLE | Spliterator.NONNULL);
            StreamSupport.stream(spliterator, true).forEach(c -> {
                totalCommits.incrementAndGet();
                uniqueCommits.add(c.getId());
                ObjectId treeId = c.getTreeId();
                RevTree root = RevTree.EMPTY_TREE_ID.equals(treeId) ? RevTree.EMPTY
                        : repo.objectDatabase().getTree(treeId);
                totalFeatures.addAndGet(root.size());
                if (!root.isEmpty()) {
                    repo.command(FindFeatureTypeTrees.class).setRootTreeRef(root.getId().toString())
                            .call().stream().map(NodeRef::name).forEach(layerNames::add);
                }
            });
        });

        info.totalCommits = totalCommits.get();
        info.uniqueCommits = uniqueCommits.size();
        info.uniqueLayerNames = layerNames.size();
        info.totalFeatures = totalFeatures.get();
        return info;
    }
}
