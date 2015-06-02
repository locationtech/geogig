
.. _geogig-osm-download:

geogig-osm-download documentation
##################################



SYNOPSIS
********
geogig osm download <url> [--bbox <S W N E>] [--saveto <path>] [--message <message>] [--keep-files] [--update] [-mapping <mapping_file>] [--rebase]


DESCRIPTION
***********

Downloads data from an OSM services supporting the OSM Overpass API. The data is filtered according to a filter that uses the syntax of the `Overpass Query Language <http://wiki.openstreetmap.org/wiki/Overpass_API/Language_Guide>`_. As an alternative, a simple bounding box filter can be used.

The download operation needs a clean working tree and index. Once the data is downloaded and added to the repository, a new commit is made, which represents the state of the repository with the new downloaded OSM data. 

If a download operation has been performed before in the repository, the ``--update`` option can be used, which fetches new data from the OSM service using the same filter that was used in the last download and merges or rebases the current branch with it.

OPTIONS
*******

-b, --bbox <S W N E>		The bounding box to use as filter, defined by its 4 boundary values.

--saveto <path>  			The path were downloaded data is to be stored before importing it into the repository. If not specified, a temporary location will be used.

--message <message>			The custom message to use for the commit to create. If not specified, a default one will be used.

--keep-files				Do not delete the downloaded file after importing it.
    
--update					Update the current OSM data. This can be used only if a previous download operation has been executed in the repository. The filter and mapping used on that previous download will be reused, and the downloaded data is not just imported and commited to the repository, but put into a separate branch and then merged or rebased. When this switch is used, the command can be seen an OSM-based version of the :ref:`geogig-fetch` command

--rebase 					If the ``--update`` option is used, rebase the downloaded data instead of merging it with the current branch.

--mapping <mapping_file>	Perform a data mapping using the specified file

SEE ALSO
********

:ref:`geogig-rebase`

:ref:`geogig-merge`

:ref:`geogig-osm-map`

BUGS
****

Discussion is still open.

