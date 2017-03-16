
.. _geogig-index-update:

geogig-index-update documentation
#################################

SYNOPSIS
********
geogig index update --tree <treeRefSpec> [--attribute <attributeName>]  [--extra-attribute <attributeName>[,<attributeName]+] [--index-history] [--add|--overwrite]


DESCRIPTION
***********

Updates an index to contain a different set of extra attributes.

OPTIONS
*******    

--tree <treeRefSpec>			Defines the ref spec that resolves to the feature tree that is already indexed (e.g. ``HEAD:Points``, ``Points``, etc).  If no commit is defined, ``HEAD`` will be used.

-a, --attribute <attributeName>        Defaults to the primary geometry attribute on the feature type.  The name of the attribute that is used on the existing index.

-e, --extra-attributes <attributes>       Comma separated list of extra attribute names to hold inside index

--index-history					If specified, indexes will be rebuilt for all commits in the history.

--add                           If specified, new attributes will be added to the existing set of extra attributes.

--overwrite                     If specified, new attributes will replace the existing set of extra attributes.

--bounds                        If specified, the max bounds of the spatial index will be updated to this parameter. <minx,miny,maxx,maxy>



SEE ALSO
********

:ref:`geogig-index-list`

:ref:`geogig-index-create`

:ref:`geogig-index-rebuild`

:ref:`geogig-index-drop`

BUGS
****

Discussion is still open.

