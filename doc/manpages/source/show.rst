
.. _geogig-show:

geogig-show documentation
#########################



SYNOPSIS
********
geogig show <reference>


DESCRIPTION
***********

Displays a description of the passed reference. The reference can be a feature, a tree, or a commit. In the case of a tree, the default feature type is described along with some other characteristics of the tree.

To get a machine-readable description of an element in a geogig repository, the ``cat`` command can be used instead

The reference can be a SHA-1, a full refspec, or just a path. If a path is used, it's assumed to refer to an element in the working head.


OPTIONS
*******

--raw	 Shows a raw machine-readable description instead. This matches the output of the ``cat`` command, except in the case of a feature, where it includes the names of the feature attributes, while the ``cat`` command includes the type of them and not the names. The ``show`` command always outputs a decorated version including attribute names taken from the corresponding feature type.


SEE ALSO
********

:ref:`geogig-cat`

BUGS
****

Discussion is still open.

