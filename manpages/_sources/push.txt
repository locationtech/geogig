
.. _geogig-push:

geogig-push documentation
#########################



SYNOPSIS
********
geogig push [<options>] [<repository> [<refspec>...]]


DESCRIPTION
***********

Updates remote refs using local refs, while sending objects necessary to complete the given refs.

Refspecs can be used to gain more control over the push process.  The format of a push refspec is [+][<localref>][:][<remoteref>] where <localref> is the local branch to be pushed and <remoteref> is the remote branch to push to.  If the format ":<remoteref>" is provided, the ref will be deleted from the remote repository.  If the format "<localref>:<remoteref>" is provided, the commit-ish matching <localref> will be pushed to the remote branch that matches <remoteref>.  If the format "<localref>" is provided, the local branch matching <localref> will be pushed to an identical branch on the remote.

OPTIONS
*******

--all       Fetch all remotes.

SEE ALSO
********

:ref:`geogig-clone`

BUGS
****

Discussion is still open.

