.. _geogig-checkout:

geogig-checkout documentation
#############################




SYNOPSIS
********
geogig checkout [<options>] <branch> 
geogig checkout [<branchOrCommitName>] -p <paths>...


DESCRIPTION
***********

Used to checkout the specified branch. If -p is used with listed path names then it doesn't switch branches, instead it updates those paths in the working tree from the index tree if <branchOrCommitName> isn't specified. If it is specified then it will use that tree to update from.

OPTIONS
*******

--path <paths>..., -p <paths>...      Prefix before listing path names to update paths in the working tree

--force, -f	                          When switching branches, proceed even if the index or the working tree differs from HEAD. This is used to discard local changes.

SEE ALSO
********


BUGS
****

Discussion is still open.