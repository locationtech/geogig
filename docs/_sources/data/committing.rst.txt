.. _committing:

Committing changes
==================

Once the data is in the staging area, it has to be committed to be finally stored in the database of the GeoGig repository and to create a persistent version of that data.

Committing data from the staging area to the database is done using the ``commit`` command, as shown below

::

	$ geogig commit -m "First commit"
	100%
	[5852e6949ba71039fded67e7f4980af4f8773869] First commit
	Committed, counting objects...3 features added, 0 changed, 0 deleted.

The ``-m`` option tells geogig that the string following it is to be used as the commit message. All commits must have a message that describe which kind of modifications you are committing, so as to describe the difference that will exist between the new version you are creating and the latest one.

.. note:: The ``-m`` option is mandatory. If not provided, GeoGig will not show the default editor, as Git does, but complain and tell you to provide a message instead.

The list of all commits made so far can be obtained by using the ``log`` command.

::

	$ geogig log
	Commit:  5852e6949ba71039fded67e7f4980af4f8773869
	Author:  volaya <volaya@boundlessgeo.com>
	Date:    (1 day ago) 2013-02-25 15:31:24 +0100
	Subject: First commit


At this time, there is just one single commit, but as you create new ones, the log will get populated. Basically, the history of your repository is made of commits, each of them representing a set of changes and a new version.

Each commit also holds a reference to its parent commits, so that allows to keep track of the changes and define the whole history and the order in which the changes were performed. Also, it allows to find out the differences between the given commits and the stages that might exist between them.

Right now, each commit has just one parent commit, which is the latest one that was made before it, but we will see how it is possible for a commit to have multiple parents. In the case above, since it is our first commit, it has a null parent. All other commits from this point will have a previous commit as parent.

If, after having committed your changes, you run the ``status`` command again, now you will see that you have no changes waiting to be committed and no unstaged objects. All three areas (working tree, staging area and repository database) have no differences between them.

::

	$gegoit status
	# On branch master
	nothing to commit (working directory clean)
