
.. _geogig-index-create:

geogig-index-create documentation
#################################

SYNOPSIS
********
geogig index create --tree <treeRefSpec> [--attribute <attributeName>]  [--extra-attribute <attributeName>[,<attributeName]+] [--index-history]


DESCRIPTION
***********

Creates a new index on a specified feature tree using a geometry attribute in that tree.  Extra attributes may also be specified in order to improve query performance when the data is filtered on those attributes.

OPTIONS
*******    

--tree <treeRefSpec>			Defines the ref spec that resolves to the feature tree that should be indexed (e.g. ``HEAD:Points``, ``Points``, etc).  If no commit is defined, ``HEAD`` will be used.

-a, --attribute <attributeName>         Defaults to the primary geometry attribute on the feature type.  The name of the attribute that should be used for indexing.

-e, --extra-attributes <attributes>        Comma separated list of extra attribute names to hold inside index

--index-history					If specified, indexes will be created for all commits in the history.

--bounds                        If specified, the max bounds of the spatial index will be set to this parameter. <minx,miny,maxx,maxy>



SEE ALSO
********

:ref:`geogig-index-list`

:ref:`geogig-index-update`

:ref:`geogig-index-rebuild`

:ref:`geogig-index-drop`

BUGS
****

Discussion is still open.

