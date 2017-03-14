Reverting changes
=================

Sometimes, the changes introduced by a given commit are not correct and you might want to undo them. This situation can be solved within GeoGig in different manners, depending on whether the commit is the most recent one or not and whether further changes have been made that are incompatible with undoing the changes in the problem commit.

If the commit to undo is the last one (is the current HEAD), then you can reset your head to the previous commit, by using the following.

::

	$geogig reset --hard HEAD^1

This command rewinds the HEAD reference one commit. The ``--hard`` option indicates that it should also change the working tree to reflect the content of the new head commit, so you should take care and ensure that you do not have unstaged changes in the working three, as they will be overwritten.

Another way of undoing the latest commit is by using the ``revert`` command.

::

	$geogig revert HEAD

Instead of rewinding the HEAD and *erasing* the last command, this adds a new commit of top of the current HEAD, which has exactly the opposite changes to the commit passed (in this case, ``HEAD``). The state of the HEAD of your repo will be the same as it was two commits ago, since you have added one commit and then another commit that cancels the first out. The state of your repository, though, will hold both the change and the undo in history.

You can revert not only the HEAD commit, but any other commit. This makes the ``revert`` command more interesting than ``reset`` if the problematic commit you want to undo is not the most recent. Using ``reset`` will cancel the commit, but also all the other commits after it, since ``reset`` operates rewinding the HEAD reference. The ``revert`` command is more flexible for this task.

Let's assume you have the following log entries in your repository.

::

	$geogig log --oneline --abbrev-commit
	adf66fe Edited wrong geometries
	da1534a Added missing feature
	159b517 Minor changes
	6cda554 First import

Now let's say that you want to undo the second-to-last commit ("Added missing feature") when you found out that the missing feature the commit message refers to is not correct and should not be added. You can revert that commit by running the following command.

::

	$geogig revert da1534a

Your HEAD will no longer contain the changes from the "Added missing feature" commit, but will maintain the edits incorporated by the "Edited wrong geometries" commit. Your log will now look like the following.

	$geogig log --oneline --abbrev-commit
	4ff56da Revert 'Added missing feature'
	adf66fe Edited wrong geometries
	da1534a Added missing feature
	159b517 Minor changes
	6cda554 First import

It is not always possible to apply the revert operation as easily as in the case above. If further changes have been introduced in the features to revert, reverting might cause data losses. For this reason, if a feature affected by the commit to revert has been modified in another commit made after that one, GeoGig considers that to be a conflicting situation and will ask you to manually solve the conflict.

This situation is similar to the one found when merging or rebasing. For a conflicted feature, you will have different versions (the original one you want to revert to, the one created by the commit to revert, and the current one in HEAD) and you have to select one of them or manually combine them. Once you have done that, stage the feature and continue the revert operation by calling the following command.

::

	$geogig revert --continue


To abort the ``revert`` operation after it has been stopped due to conflicts, use the ``--abort`` option.

::

	$geogig revert --abort
