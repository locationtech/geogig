
.. _geogig-index-drop:

geogig-index-drop documentation
###############################

SYNOPSIS
********
geogig index drop --tree <treeRefSpec> [--attribute <attributeName>]


DESCRIPTION
***********

Removes an existing index from the repository.

OPTIONS
*******    

--tree <treeRefSpec>			Defines the ref spec that resolves to the feature tree that is already indexed (e.g. ``HEAD:Points``, ``Points``, etc).  If no commit is defined, ``HEAD`` will be used.

-a, --attribute <attributeName>        Defaults to the primary geometry attribute on the feature type.  The name of the attribute that is used on the existing index.


SEE ALSO
********

:ref:`geogig-index-list`

:ref:`geogig-index-create`

:ref:`geogig-index-rebuild`

:ref:`geogig-index-update`

BUGS
****

Discussion is still open.

