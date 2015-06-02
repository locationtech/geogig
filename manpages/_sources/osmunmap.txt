
.. _geogig-osm-unmap:

geogig-osmunmap documentation
##############################



SYNOPSIS
********
geogig osm unmap <path>


DESCRIPTION
***********

Unmaps the data created by a mapping operation performed on OSM data. It takes the data from a given path an recreates the corresponding canonical OSM representations (stored in the ``way`` and ``node`` folders).

Information about the mapping definition to use is taken from the mapping history, which is kept in the repository after each mapping operation is performed.

All the work done by the unmap command takes place on the working tree. That is, the mapped path ``firestations`` refers to ``WORK_HEAD:firestations``, and the unmapped data is added/replaced in ``WORK_HEAD:node`` and ``WORK_HEAD:way``.

In the case of ways, the ``nodes`` field will be recomputed based on the geometry. If the geometry has changed and new points have been added to the corresponding line of polygon, new nodes will be added accordingly.

The unmapping operation also considers deleted features, by comparing with the state of the mapped tree just after the last mapping operation. All features that have been deleted from those that existed at that commit just after the mapping was performed, will be deleted from the canonical trees as well. A deleted way will not cause its corresponding nodes to be deleted, but only the canonical representation of the way itself.


OPTIONS
*******

<path>		The path that contains the mapped data, which will be used by the unmapping operations.

SEE ALSO
********

:ref:`geogig-osm-download`

:ref:`geogig-osm-map`

:ref:`geogig-osm-import`

BUGS
****

Discussion is still open.

