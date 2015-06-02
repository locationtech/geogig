
.. _geogig-osm-createchangeset:

geogig-osm-createchangeset documentation
#########################################



SYNOPSIS
********
geogig osm create-changeset [<commit> [<commit>]] -f <changesets_file>


DESCRIPTION
***********

Saves the differences between two snapshots as an OSM changeset. It's syntax is similar to the ``diff`` command, but the output uses OSM changeset format and is always written to an output file.

Two commits can be specified for comparison. If no commit is specified, the working tree and index will be compared. If only one commit is specified, it will compare the working tree with the given commit

Only the ``node`` and ``way`` trees are compared to find the differences between the specified commits. Changes in other trees will be ignored, and no changeset entries will be created based on them.

If you are using this command to create a changeset with changes that you have manually introduced in the ``node`` and ``way``, and plan to use it for contributing those changes to the OSM planet, a changeset ID is needed. Changes made to the canonical OSM trees in a repository will have negative changesets, which have to be replaced. Get a changeset ID from the OSM API as detailed at http://wiki.openstreetmap.org/wiki/API_v0.6, and then use it to produce the changeset from GeoGig, passing that id with the ``--id`` option.	

OPTIONS
*******

-f <filename>			The file to write the changesets to.

--id 					ID to use for replacing negative changeset IDs

SEE ALSO
********

:ref:`geogig-osm-download`

:ref:`geogig-osm-map`

:ref:`geogig-osm-unmap`

BUGS
****

Discussion is still open.

