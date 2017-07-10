
.. _geogig-geojson-import:

geogig-geojson-import documentation
###################################



SYNOPSIS
********
geogig geojson import <geojson> [<geojson>]... [--path <path>] [--fid-attrib <attrib_name>] [--add] [--alter]


DESCRIPTION
***********

This command imports features from one or more GeoJSON files into the GeoGig working tree.


OPTIONS
********

--path <path>                   The path to import to. If not specified, it uses the filename of the GeoJSON file.

--fid-attrib <attrib_name>      Uses the specified attribute as the feature id of each feature to import. If not used, a number indicating the position in the GeoJSON file is used

--add                           Adds the imported feature to the corresponding tree without removing previous features in case the tree already exists

--alter                         Same as the ``--add`` switch, but if the feature type of the imported features is different to that of the destination tree, the default feature type is changed and all previous features are modified to use that feature type

--geom-name	<name>				Instead of using the default name for the geometry field ('geometry'), it will use the passed name.

--geom-name-auto				Uses the name of the geometry attribute in the destination tree, if it already exist, to name the geometry field of the data being imported. If the destination tree does not exist, or if the data contained in the tree has no geometry, it uses the default name ('geometry'). It cannot be used with --geom-name

BUGS
****

Discussion is still open.

