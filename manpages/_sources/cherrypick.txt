
.. _geogig-cherrypick:

geogig-cherrypick documentation
###############################



SYNOPSIS
********
geogig cherry-pick <commitish>


DESCRIPTION
***********
Given an existing commit, apply the change it introduces, recording a new commit. This requires your working tree to be clean (no modifications from the HEAD commit).

If the ommit to apply is not compatible with the changes introduced in the current branch since, the operation will not be performed and will leave the index with unmerged (conflicting) changes. Fix those unmerged changes and then run the ``commit`` command using the ``-c`` option followed by the reference to the commitish that you were trying trying to cherry-pik, to finish the operation.

OPTIONS
********



SEE ALSO
********

:ref:`geogig-commit`

BUGS
****

Discussion is still open.

