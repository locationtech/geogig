
.. _geogig-clone:

geogig-clone documentation
###########################



SYNOPSIS
********
geogig clone [--branch <name>] <repository> [<directory>] [--filter <file>] [--depth <depth>]


DESCRIPTION
***********

Clones a repository into a newly created directory, creates remote-tracking branches for each branch in the cloned repository (visible using geogig branch -r), and creates and checks out an initial branch that is forked from the cloned repository's currently active branch.

After the clone, a plain geogig fetch without arguments will update all the remote-tracking branches, and a geogig pull without arguments will in addition merge the remote master branch into the current master branch, if any.

This default configuration is achieved by creating references to the remote branch heads under refs/remotes/origin and by initializing remote.origin.url and remote.origin.fetch configuration variables.

Shallow and sparse clones can be created with this command as well. A shallow clone contains just a subset of the full history, not fetching commits at a distance larger than a given threshold from the branch tip. A sparse clone contains only features that pass a given filter, defined in an Ini file.

OPTIONS
*******

-b <name>, --branch <name>		Branch to checkout when clone is finished.

--depth <depth>  				The depth of the clone. This will create a shallow clone of depth ``depth``. If ``depth`` is less than 1, a full clone will be performed.
    
--filter <file>					Ini file containing the filter to be used to create a sparse clone.

--config <config_name>=<config_value>[,<config_name>=<config_value>]  Initial configuration parameters for the new repository.  See the documentation for geogig init

SEE ALSO
********

:ref:`geogig-fetch`

:ref:`geogig-pull`

:ref:`geogig-push`

BUGS
****

Discussion is still open.

