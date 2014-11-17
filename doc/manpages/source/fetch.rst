
.. _geogig-fetch:

geogig-fetch documentation
###########################



SYNOPSIS
********
geogig fetch [--depth <depth>] [--all] [--prune] [--fulldepth] [<repository>...]


DESCRIPTION
***********

Fetches named heads or tags from one or more other repositories, along with the objects necessary to complete them.

This command can fetch from either a single named repository, or from several repositories at once.

OPTIONS
*******

--all          			Fetch all remotes.

-p, --prune    			After fetching, remove any remote-tracking branches which no longer exist on the remote.

--depth <depth>			In the case of a shallow clone, it fetches history only up to the specified depth

--fulldepth 			In the case of a shallow clone, fetch the full history from the repository. This will turn the repository into a full clone.

SEE ALSO
********

:ref:`geogig-clone`

:ref:`geogig-pull`

BUGS
****

Discussion is still open.

