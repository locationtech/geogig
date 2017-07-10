Cherry-picking commits
=======================

The merge and rebase operations combine to different histories into a given one. sometimes, however, you might not want to combine all the differences from a branch or a set of branches, but just one single commit, or a selected number of them. GeoGig has another operation called *cherry-picking*, that can be used for that.

To run a cherry picking operation, the ``cherry-pick`` command has to be used with the following syntax

::

	$geogig cherry-pick <commit>

The commit reference can be specified using any of the syntaxes supported by GeoGig, as long as it resolves to a commit. References such as ``5852e6949ba71039fded67e7f4980af4f8773869`` (a full commit ID), ``5852e69`` (a shortened commit ID), or ``branch1`` (the last commit in a branch named *branch1*) are all valid.

Once the cherry-pick operation has been run, you will have a new commit in your history with the same message and changes as the commit that you passed. You have applied that commit to your current branch.

Your working tree and index will be updated to reflect the changes introduced by the commit. To avoid losing uncommitted changes, the ``cherry-pick`` command cannot be run if your working tree is not clean (that is, if it's different from the current HEAD and you have unstaged changes.)

Conflicts in cherry-picking
----------------------------

As in the case of both the ``merge`` and ``rebase`` commands, ``cherry-pick`` cannot always apply the changes of a given commit, since your current history might have introduced other changes that are not compatible or, at least, that do not allow for an automated merging. In this case, the ``cherry-pick`` command will apply changes that do not cause any conflict and alert you about conflicting changes.

::

	$geogig cherry-pick 5852e694
	CONFLICT: conflict in Parks/1

Conflicts arise whenever the change for a given element may overwrite additional changes introduced in the current branch. For instance, let's say the commit you are picking has changed the value of a given attribute in a given feature from "1" to "2". If in the current branch the value for that attribute and feature is "3", the cherry-picking operation will report that as a conflict. It could just change the value to "2", but that would discard the previous changes, since the original value is not the same. To avoid it, GeoGig will let you manually complete the edit.

Conflicts in the case of cherry-picking are very similar to those found when trying to apply a patch. Reading the corresponding section is recommended in order to understand this mechanism.

Cherry-picking conflicts are resolved manually much in the same way as explained in the case of a conflicted merge operation. You must select the version to use and eventually leave your index without elements marked as conflicted by staging those changes.

To commit your changes, call the ``commit`` command reusing the information from the commit that you have cherry-picked. Use the ``-c`` option followed by the reference to the commit.

::

	$geogig commit -c 5852e694

This will perform a normal commit, but instead of asking you for a commit message, it will use the original from the passed commit, along with its author and author timestamp.
