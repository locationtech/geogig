
.. _geogig-osm-import:

geogig-osm-import documentation
################################



SYNOPSIS
********
geogig osm import <filename> [--add] <repository> [<directory>] [--filter <file>] [--depth <depth>]


DESCRIPTION
***********

Imports OSM data from a file into the working tree of the repository. Optionally, a data mapping can be performed. See :ref:`geogig-osm-map` for more information about how to define a mapping.

Data in the specified file is imported into GeoGig and put into two trees: ``way`` and ``node``, with default feature types in both cases.

if a mapping is used and the canonical representations are also added to the repository (that is, if the ``--no-raw`` switch is not used) the working tree and index have to be clean, and after the import and mapping, a commit will be made . The comit message is automatically generated. A custom commit message cna be defined using the  ``--message`` option.


OPTIONS
*******

<filename> 			The filename to import. Both pbf and OSM XML formats are supported

--add 				Do not remove previous OSM data and replace with the imported data. Instead, add the imported data to the existing OSM trees.

--message			The message of the commit to create after the import operation.

--mapping <file>	Imports using a mapping. ``file`` is the filename that contains the mapping to use.

--no-raw			If a mapping is used, the canonical representations of the original OSM data are not stored in the ``way`` and ``node`` trees. Only the mapped data is added to the repository.

SEE ALSO
********

:ref:`geogig-osm-download`

:ref:`geogig-osm-map`

:ref:`geogig-osm-unmap`

BUGS
****

Discussion is still open.

