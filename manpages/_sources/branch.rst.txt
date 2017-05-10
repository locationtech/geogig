.. _geogig-branch:

geogig-branch documentation
############################




SYNOPSIS
********
geogig branch [<options>] [<branchname> [<startpoint>]]
geogig branch --delete <branchname>...
geogig branch --rename [--force] [<oldBranchName>] <newBranchName>

DESCRIPTION
***********

With no arguments the command will display all existing branches with the current branch
highlighted with the asterisk. The -r option will list only remote branches and the -a option will list both local and remote branches. Adding the --color option with the value of auto, always, or never will add or remove color from the listing. With the -v option it will list the branches along with the commit id and commit message that the branch is currently on.

With a branch name specified it will create a branch of off the current branch. If a start point is specified as well then it will be created off of the given start point. If the -c option is given it will automatically checkout the branch once it is created.

With the -d option with a branch name specified will delete that branch. You cannot delete the branch that you are currently on, checkout a different branch to delete it. Also with the -d option you can list multiple branches for deletion.

With the -m option you can specify an oldBranchName to rename with the given newBranchName or you can rename the current branch by not specifying oldBranchName. With the --force option you can rename a branch to a name that already exists as a branch, however this will delete the other branch.

OPTIONS
*******

--color							Whether to apply colored output. Possible values are
				 				auto|never|always

--checkout, -c					Automatically checkout the new branch when the command is
							 	used to create a branch

--delete, -d					Deletes the specified branch

--rename, -m					Rename the current branch if no oldBranchName is specified
								or the branch called oldBranchName to the newBranchName
								specified

--force, -f						Forces the renaming of a branch to an existing branch and
								deletes the original branch by that name

--verbose, -v					Verbose output for list mode, shows branch commit id and 
								commit message

--remote, -r					List or delete (if used with --delete or -D) the 
								remote-tracking branches

--all, -a						List all branches, both local and remote

SEE ALSO
********


BUGS
****

Discussion is still open.
