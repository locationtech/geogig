.. _staging_data:

Staging data
=============

Data imported into GeoGig is not ready yet to be added to the repository database. It first has to be put into the staging area and from there it can be committed to the repository database.

Staging data is done using the ``add`` command. Adding a given feature is telling GeoGig that you want that feature to be versioned.

To add all unstaged features in the working tree, just use the ``add`` command without additional options

::

	$ geogig add
	Counting unstaged features...3
	Staging changes...
	100%
	3 features staged for commit
	0 features not staged for commit

.. note:: ``geogig add`` is equivalent to ``git add .`` (notice the dot)

That will cause all new features to be added to the staging area, and also all those ones that were already in the staging area, but were modified in the index  (that is, the versions in the working index and staging area are not the same). Since this is the first data we work with, all features are new, and we have no modified ones in this case.

If you just want to stage modified features but not add the new ones, you can use the ``--update`` option.

::

	$ geogig add --update

In the above case of our just-created repository, that would have no effect, since there are no modified features.

For a more fine grained selection of which objects to stage, you can directly specify the names of objects to add

::

	$ geogig add parks/1 roads/3

After staging, you can run the ``status`` command to check that the 3 features that we imported in the working tree are now also in the staging area, ready to be committed to the repository database.

::

	# On branch master
	# Changes to be committed:
	#   (use "geogig reset HEAD <path/to/fid>..." to unstage)
	#
	#      added  parks/2
	#      added  parks/3
	#      added  parks/1
	# 3 total.

Changes after importing were "not staged for commit", while now they are "to be committed". The ``status`` command will show both unstaged and uncommitted changes if they both exist at the same time in the repository. Since we have staged all changes (by using the ``add`` command without options), there are no unstaged files now.
