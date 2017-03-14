.. _repo.history:

Modifying history
=================

When a commit is made, a new version of the data in your repository is created and stored, along with all previous versions. The full set of commits and objects in the repository database constitutes the repository **history**. Ideally, you should change the history of a repository just by adding new commits. However, sometimes it is useful to rewrite parts of the history, or to change its structure, to more clearly reflect the work that has been done. GeoGig provides several commands and options that alter the history of a repository.

Modifying the history of a repository is safe as long as you are not sharing it and are sure that no one else is basing work on it. If this is not the case, pull operations from other repositories whose history has not been rewritten in the same way are likely to involve conflicts.

.. todo:: rewrite when --force option is added to pull


Amending a commit
-----------------

A very typical case of history modification happens when you neglect to add something to a commit. For example, staging a single feature but leaving out a second one created later. When the commit was made, only the staged feature will be committed, while the second one will exist only in the working tree.

You can create a separate commit to include the second feature, but it might be desirable to have both features in the same commit. To accomplish this, you can **amend** the last commit.

To amend a commit, use the ``--amend`` option when making the new commit.

.. code-block:: console

   geogig commit --amend

There is no need to add a commit message, since the commit command will use the one from the previous commit. It will replace the previous commit with a new one that includes the extra changes that you might have introduced, but keep the same message.

If you want to change the message, you can add one with the ``-m`` option. In this case, the previous message will be overwritten.


Squashing commits
-----------------

A larger modification of the history can be made by replacing a given set of consecutive commits with a single commit. This is usually desired when working on data corresponding to a given task required several incremental commits, but, when work is done, you would like to have all those commits in one.

You can squash commits by using the ``squash`` command and providing a range of commits. For instance, to squash the last 3 commits, you can use the following command.

.. code-block:: console

   geogig squash HEAD~2 HEAD

The commit message is, by default, taken from the oldest commit (in this case HEAD~2), but can be supplied, if needed, using the ``-m`` option.

You can squash commits in the middle of the history line as well:

.. code-block:: console

   geogig squash HEAD~5 HEAD~2

Again, a message is not required, but it can be supplied using the ``-m`` option.

If the set of commits to be squashed contain merge commits, the resulting commit will have the secondary parents of those merge commits as their parents as well. The resulting commit will, itself, be a merge commit. If several merge commits are squashed, the resulting commit will resemble an octopus merge (see :ref:`repo.merging` for more information on octopus merges).

When a merge commit appears, the commits to squash must belong to the main branch. That is, the ``since`` commit must be reachable from the ``until`` commit descending in the history using just the first parent of each commit.

Commits at the beginning of a branch (that is, commits where a branch was created that have more that one child commit) cannot be squashed. Because the squash operation rewrites the history of the current branch after the squash commit. This type of commit cannot be squashed as it would require rewriting the history of other branches.

Also, if a new branch has been created after the squashed commits, the squash operation cannot run. As a rule of thumb, branches can start in the commits *before* the squash, but not on or after.

When performing a rebase operation, the commits that are rebased can be squashed into a single commit automatically by adding the ``--squash`` option.

.. note::

  Squashing is performed in ``git`` by doing an interactive rebase. The rebase operation in GeoGig doesn't have an interactive mode, so an additional command has been added for this purpose.


Splitting a commit
------------------

It is also possible to split a commit into several commits. There is no command for doing this in GeoGig, but you can get the same result by running a soft reset.

.. code-block:: console

   geogig reset --soft HEAD~1

This will put your HEAD one commit behind, discarding the most recent commit. However, your working tree and index will not be affected, so you can then commit the changes on the working tree in whichever manner you choose.

This can only be applied if the commit to split is the most recent.
