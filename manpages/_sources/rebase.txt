
.. _geogig-rebase:

geogig-rebase documentation
############################



SYNOPSIS
********
geogig rebase [--onto <newbase>] [<upstream>] [<branch>] [--abort] [--continue] [--continue <message>] [--skip]


DESCRIPTION
***********
Forward-port local commits to the updated upstream head.

If <branch> is specified, geogig rebase will perform an automatic geogig checkout <branch> before doing anything else. Otherwise it remains on the current branch.

All changes made by commits in the current branch but that are not in <upstream> are saved to a temporary area.

The current branch is reset to <upstream>, or <newbase> if the --onto option was supplied.

The commits that were previously saved into the temporary area are then reapplied to the current branch, one by one, in order.

If conflicts are found while aplying any of the commits, the rebase operation is stopped. Conflicts should be resolved and the rebase commands has to be run again with the --continue switch to continue aplying the remaining commits

OPTIONS
*******    

--onto <newbase>    Starting point at which to create the new commits. If the --onto option is not specified, the starting point is <upstream>. May be any valid commit, and not just an existing branch name.

--abort 			Aborts a rebase operation that was stopped after conflicts were found. It reverts the repository to its pre-rebase state.

--continue			Continues a rebase operation that was stopped after conflicts were found. It should be run after conflicts have been resolved.

--skip				Skips the commit that caused the rebase operation to stop due to conflicts and moves into the next one.

--squash <message>	Squash all commits into a single one. The provided message will be used as commit message.


SEE ALSO
********

:ref:`geogig-log`

BUGS
****

Discussion is still open.

