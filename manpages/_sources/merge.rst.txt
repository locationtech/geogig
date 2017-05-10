
.. _geogig-merge:

geogig-merge documentation
##########################



SYNOPSIS
********
geogig merge [-m <message>] <commitish>...

DESCRIPTION
***********
Incorporates changes from the named commits (since the time their histories diverged from the current branch) into the current branch. This command is used by ``geogig pull`` to incorporate changes from another repository and can be used by hand to merge changes from one branch into another.

Merging two branches can result in unmerged changes if there are conflict between changes introduced in the histories being merged. Conflicted elements have to be fied, and then the changes have to be commited. No commit message is needed in that case when calling the ``commit`` command, since it will reuse the merge commit message.

If conflicts exist and there are more that two branches being merged (more than one commitish specified), the merge operation will not be performed. 

OPTIONS
*******    

--m <message>   	Commit message.  If a message is not provided, one will be created automatically.

--ours				If conflict arise during the merge operation, it uses the element from the current branch

--theirs 			If conflict arise during the merge operation, it uses the element from the current branch

--no-commit			Perform the merge, but do not make any commit. Leave merged changes only in the working tree

--abort				Aborts a merged that ended in a conflicted state. It reverts back to the pre-merge situation.

SEE ALSO
********

:ref:`geogig-log`

:ref:`geogig-pull`

:ref:`geogig-commit`

BUGS
****

Discussion is still open.

