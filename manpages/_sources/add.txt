
.. _geogig-add:

geogig-add documentation
#########################



SYNOPSIS
********
geogig add [<options>] [<pattern>...]


DESCRIPTION
***********

This command updates the index using the current content found in the working tree, to prepare the content staged for the next commit. It typically adds all unstaged changes, but with a defined pattern, only matching features will be added.

The "index" holds a snapshot of the HEAD tree plus any staged changes and is used to determine what will be committed to the repository. Thus after making any changes to the working tree, and before running the commit command, you must use the add command to add any new or modified files to the index.

This command can be performed multiple times before a commit. It only adds the content of the specified feature(s) at the time the add command is run; if you want subsequent changes included in the next commit, then you must run geogig add again to add the new content to the index.

The geogig status command can be used to obtain a summary of which files have changes that are staged for the next commit.

OPTIONS
*******    

-n, --dry-run   Don't actually add the feature(s), just show what would happen if add were performed.

-u --update 	Only add features that have already been tracked.

SEE ALSO
********

:ref:`geogig-status`

:ref:`geogig-commit`

BUGS
****

Discussion is still open.

