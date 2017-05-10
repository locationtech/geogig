
.. _geogig-tag:

geogig-tag documentation
#########################



SYNOPSIS
********
geogig tag <tag_name> [tag_commit] -m <message> [-d]


DESCRIPTION
***********

This command creates new tags or deletes an existing tag

A name and a tag message are needed to create a new tag. The tag_commit argument specifies the commit the tag will refer to. If no tag_commit is provided, the tag will refer the the current HEAD

Deleting a tag is done just passing the tag name and the -d option

If no tag name is provided and the command is invoked without arguments, it displays a list of current tags



OPTIONS
*******

-m <message>			Defines the message of the tag to create

-d 						Deletes the specified tag

SEE ALSO
********

BUGS
****

Discussion is still open.

