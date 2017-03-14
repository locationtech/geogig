.. _checking_out:

Checking out a previous version
=================================

All the versions stored in a GeoGig repository are available and can be used. We already know how to refer to an object from a previous version by using the reference syntax explained in :ref:`referencing`. That allows us to describe that element or to use it for a certain operation.

A different way of recovering a given version of the data kept in the repository is to bring all the changes to the working tree, so we can actually work on that data. Notice that this could be used, for instance, to export that data and make that version of the repository available for an external application. However, you can export from a given commit without having to checkout and then export from the working tree, as was explained in the :ref:`exporting` section.

The ``add`` and ``commit`` commands *move* the data from the working tree into the staging area, and from there into the repository database. That same data can go the opposite direction, from the repository database to the working tree. In that manner, the index is skipped and the working tree is updated directly from the repository database.

To checkout a past version of the repository data, the ``checkout`` command is used, just in the same way as we use it to move from one branch to another. Instead of a branch name, you must supply the name of a commit (its ID) and data corresponding to that commit will be put in the working tree. Since the data in the working tree will be overwritten, this command cannot be run when the working tree has unstaged changes.

The following is a valid command that will update the version in the working tree from the current one to the snapshot corresponding to 5 commits ago.

::

	$ geogig checkout HEAD~5


Apart from updating the working tree, the ``checkout`` command updates the HEAD ``reference``, which will point to the commit from which the data in the working tree was taken.

You can now export the current working tree to a shapefile and external applications will be able to use the old version of the data, which you have exported and is now in that shapefile.

To go back to the most recent state, where you were before checking out the previous version, you must checkout the latest commit on the current branch. Notice that ``HEAD`` is not pointing to that commit, now, so you will have to use the name of the current branch. Assuming you are in the ``master`` branch, the following will update the working tree to the latest version and change the ``HEAD`` reference to the corresponding commit.

::

	$ geogig checkout master

Resetting to a previous commit
------------------------------

When you perform a checkout using a commit ID, the ``HEAD`` reference will point directly to the commit. Usually, ``HEAD`` is, itself, a symbolic reference and points to the tip of a given branch. If we are in ``master``, then ``HEAD`` points to wherever ``master`` is pointing. If there is a commit, the tip of the branch changes and ``HEAD`` changes automatically.

When ``HEAD`` is pointing to a commit directly, it is said to be in a *detached* state. You should not make commits in that state, as they will not be added to the tip of your current branch.

If what you want to revert to a previous snapshot of the current branch and start working from there, you should use the ``reset`` command instead of ``checkout``. The ``reset`` command will move the tip of the current branch (the ``master`` referenced in this case) back to the specified commit and ``HEAD`` will follow along. Now you can start your work, which will be added on top of the commit to which you have reset the branch.

To reset to the commit 5 commits ago, use the following:

::

	$ geogig reset HEAD~5 --hard

That will update all three areas in GeoGig (working tree, staging area and database) to the specified commit. This is known as a hard reset. You can also perform a mixed reset (only updates the staging area and database, but not the working tree, with the ``--mixed`` option) or a soft reset (only updates the database with the ``--soft`` option).
