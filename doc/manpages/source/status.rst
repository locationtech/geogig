
.. _geogig-status:

geogig-status documentation
############################



SYNOPSIS
********
geogig status [<options>]


DESCRIPTION
***********

Displays features that have differences between the index and the current HEAD commit and features that have differences between the working tree and the index file. The first are what you would commit by running geogig commit; the second are what you could commit by running geogig add before running geogig commit.

OPTIONS
*******

--limit <count>               Limit the number of changes to display.  This value must be greater than 0.

--all                         Override the default limit of 50 and display all changes.

--color <auto|never|always>   Specifies whether or not to apply colored output.              

SEE ALSO
********

:ref:`geogig-add`

:ref:`geogig-commit`

BUGS
****

Discussion is still open.

