.. _geogig-format-patch:

geogig-format-patch documentation
###################################



SYNOPSIS
********
geogig format-patch <commit1> <commit2> [-- <path>...] -f <patch_file>


DESCRIPTION
***********

Creates a patch containing the differences between two versions of the repository.

If both commit references are missing, the patch will contain the differences between the index and the working tree. 

If only one commit reference is given, the patch will contain the differences between the specified commit and the working tree.

One or more space-separated paths can be added. If so, the patch will contain only differences corresponding to those paths.


OPTIONS
*******    


--cached	Do not use the working tree. Use the index instead


SEE ALSO
********

:ref:`geogig-apply`

:ref:`geogig-diff`

BUGS
****


