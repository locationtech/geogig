
.. _geogig-cat:

geogig-cat documentation
#########################



SYNOPSIS
********
geogig cat <reference>


DESCRIPTION
***********

Displays a machine-readable, non-formatted description of the passed reference. The reference can be a feature, a tree, or a commit. In the case of a tree, the default feature type is described along with some other characteristics of the tree. Identical to ``geogig show --raw``.

To get a human-readable description of an element in a geogig repository, the ``show`` command can be used instead.

The reference can be a SHA-1, a full refspec, or just a path. If a path is used, it's assumed to refer to an element in the working head.


OPTIONS
*******

--binary   Shows binary output instead. 


SEE ALSO
********

:ref:`geogig-show`

BUGS
****

Discussion is still open.

