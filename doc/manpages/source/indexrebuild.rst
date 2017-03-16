
.. _geogig-index-rebuild:

geogig-index-rebuild documentation
##################################

SYNOPSIS
********
geogig index rebuild --tree <treeRefSpec> [--attribute <attributeName>]


DESCRIPTION
***********

Rebuilds the index trees for the full history of a feature type.  This is generally only used when an index has been created or updated without the ``indexHistory`` paramater.  This command provides a way to do that operation if the need arises after the index has been created.

OPTIONS
*******    

--tree <treeRefSpec>			Defines the ref spec that resolves to the feature tree that is already indexed (e.g. ``HEAD:Points``, ``Points``, etc).  If no commit is defined, ``HEAD`` will be used.

-a, --attribute <attributeName>       Defaults to the primary geometry attribute on the feature type.  The name of the attribute that is used on the existing index.



SEE ALSO
********

:ref:`geogig-index-list`

:ref:`geogig-index-create`

:ref:`geogig-index-update`

:ref:`geogig-index-drop`

BUGS
****

Discussion is still open.

