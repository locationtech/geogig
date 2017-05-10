
.. _geogig-reset:

geogig-reset documentation
###########################



SYNOPSIS
********
geogig reset [<commit>] -p <path>... 
geogig reset --(hard|soft|mixed) [<commit>]


DESCRIPTION
***********
There are two forms of geogig reset. In the first form, copy entries from <commit> to the index. In the second form, set the current branch head (HEAD) to <commit>, optionally modifying index and working tree to match. The <commit> defaults to HEAD in both forms.

 geogig reset [<commit>] -p <path>...
  This form resets the index entries for all <paths> to their state at <commit>. (It does not affect the working tree, nor the current branch.)
  
 geogig reset --(hard|soft|mixed) [<commit>]
  This form resets the current branch head to <commit> and possibly updates the index (resetting it to the tree of <commit>) and the working tree depending on <mode>.

OPTIONS
*******    
-p <path>..., --path <path>...     Path filters for reset operation.  Only features that match one of the given filters will be affected.

--soft        Does not touch the index file nor the working tree at all (but resets the head to <commit>, just like all modes do). This leaves all your changed files "Changes to be committed", as geogig status would put it.
   
--mixed       Resets the index but not the working tree (i.e., the changed files are preserved but not marked for commit) and reports what has not been updated. This is the default action.

--hard        Resets the index and working tree. Any changes to tracked files in the working tree since <commit> are discarded.

SEE ALSO
********

:ref:`geogig-status`

:ref:`geogig-add`

:ref:`geogig-commit`

BUGS
****

Discussion is still open.

