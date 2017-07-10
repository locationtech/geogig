Rebasing
=========

Apart from the merge operation, GeoGig provides a another way of combining two histories: rebasing. The difference between rebasing and  merging can be seen in the following figure.

.. todo:: rebasevsmerge.png


As you see, the commits from the branch that is combined with the current branch are not put on top of that, but on top of the common ancestor of both branches, instead. The commits that were made to the current branch before the other branch was created are then added on top of that already merged branch.

GeoGig rewinds the current HEAD to the ancestor point, then adds the commits in the branch that is to be combined with the current one, and then does the same with the commits that were 'rewinded'. The first set of comments will not cause any conflict, but when adding the second set of them, conflicts might arise. As in the case of a merge operation, this conflicts appear when the same element has been merged in a way that it is not compatible with a change introduced by one of the commits already merged, causing a situation that GeoGig cannot automatically solve.

When a rebase cannot be finished cleanly due to conflicts, GeoGig will stop and tell you to fix the conflict before continuing.


::

	$geogig rebase branch1
	Error: could not apply 0b0e33b Changed units in area field
	CONFLICT: conflict in parks/5

When you have fixed these conflicts, run 'geogig rebase --continue' to continue rebasing. If you would prefer to skip this commit, run 'geogig rebase --skip. To check out the original branch and stop rebasing, run 'geogig rebase --abort'

As you can see in the example above, the particular commit that caused the conflict is shown by GeoGig in the message it displays to inform of the conflicted situation. The ID of the commit, its commit message, and the elements that are in conflict are shown.

Conflicts are solved in the same way as in the case of a merge conflict. Conflicted elements are marked as such in the index and you have to use the GeoGig tools and commands to set them as not-conflicted and select the version to use in each case.

Once the conflict is solved, however, the rebase operation is not finished in the same way as in the case of merging. When a merge is performed, all changes from the merged branch are applied at once on the current branch, and all possible conflicts are marked. After you solve them, the merge operation is over and you have to commit your changes. In the case of a rebase, commits that were rewound are applied one by one, and each of them analyzed for conflicts. When a conflict is found, the rebase process is stopped and you must fix the conflicts before moving to the next commit, which might itself also cause conflicts.

With the conflict solved, you can now continue the rebase process by using ``geogig rebase --continue``. This will commit your new changes using the message of the last commit (the one that caused the conflict), and continue with the next commit, if it exists. If there are no more commits, the rebase process will be finished. If any of the remaining commits causes a conflict, GeoGig will stop the process and you will have to follow the above steps to solve the new conflicts and restart the rebase.

If, for a given commit, you do not want to solve conflicts and prefer to ignore the changes it introduces, you can run ``geogig rebase --skip``. That will reset the head to the last applied commit (that is, ignoring all changes for the currently conflicting commit, including those that did not cause any conflict) and move to the next commit.

Any time during the rebase process, if you want to go back to the state that your repository was in before you started rebasing, you can use ``geogig rebase --abort``. This will abort the rebasing and leave your repository unchanged.


Squashing commits
-------------------

You can use the rebase operation to combine several commits into one in what is known as "squashing" commits. When you perform a rebase and squash your commits, all the commits that are rewound are not applied afterwards. One single commit is created including all the changes from those commits. Instead of using the commit messages from the squashed commits, you must provide a new message.

To squash the commits applied in the rebase operation, you must use ``geogig rebase --squash <commit_message>``. For instance, if you have a New York dataset in your repo and a branch where you have been adding streets corresponding to the Manhattan area, you can rebase and bring in all the changes from the master branch and, at the same time, put all the commits you have made to add those streets into a single commit using the following command.

::

	$ geogig rebase master --squash "Added Manhattan streets"

Apart from that, the process is identical. If conflicts arise, they should be solved in the same manner as a standard rebase.
