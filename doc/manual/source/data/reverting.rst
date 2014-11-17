Reverting changes
=================

Sometimes, the changes introduced by a given commit are not correct and you might want to undo them. This situation can be solved in GeoGig in different ways, depending on whether the commit is the most recent one or not, and, in this last case, whether further changes have been made that are uncompatible with undoing the changes in the problem commit.

If the commits to undo is the last one (is the current HEAD), then you can reset your head to the previous commit, by using

::

	$geogig reset --hard HEAD^1

That rewinds the HEAD reference one commit. the ``--hard`` option indicates that it should also change the working tree to reflect the content of the new head commit, so you should take care and make sure that you do not have unstaged changes in the working three, since they will be overwritten.

Another way of undoing the latest commit is by using the ``revert`` command.

::

	$geogig revert HEAD

Instead of rewinding the HEAD and *erasing* the last command, this adds a new commit of top of the current HEAD, which has exactly the opposite changes to the commit passed (in this case, ``HEAD``). The state of your repo will we the same one as two commits ago, since you have added one commit, but then another one that cancels that one.

You can revert not only the HEAD commit, but any other one. That makes the ``revert`` command more interesting that ``reset`` if the problematic commit you want to undo is not the last one. In that case, using ``reset`` would cancel the commit, but also all the other ones after it, since it operates rewinding the HEAD reference. The ``revert`` command is more flexible for this task.

Let's assume you have the following log entries in your repository:

::

	$geogig log --oneline --abbrev-commit
	adf66fe Edited wrong geometries		
	da1534a Added missing feature
	159b517 Minor changes
	6cda554 First import

Now let's say that you want to undo the second last commit ("Added missing feature"), since you found out that the missing feature the commit message refers to is not correct and should not be added. You can revert that commit by running

::

	$geogig revert da1534a

Your HEAD now will not have that extra feature, but will keep the edits incorporated by the "Edited wrong geometries" commit. Your log now will look like this.

	$geogig log --oneline --abbrev-commit
	4ff56da Revert 'Added missing feature'
	adf66fe Edited wrong geometries		
	da1534a Added missing feature
	159b517 Minor changes
	6cda554 First import

It is not always possible to apply the revert operation as easily as in the case above. If further changes have been introduced in the features to revert, reverting might cause data losses. For this reason, if a feature affected by the commit to revert has been modified in another commit made after that one, GeoGig considers that to be a conflicting situation, and will ask you to manually solve the conflict.

This situation is similar to the one found when merging or rebasing. For a conflicted feature, you will have different versions (the original one you want to revert to, the one created by the commit to revert, and the current one in HEAD), and you have to select one of them or manually combine them. Once you have done that, stage the feature and continue the revert operation by calling

::

	$geogig revert --continue
	

To abort the revert operation after it has been stopped due to conflicts, use the ``--abort`` option.

::

	$geogig revert --abort