.. _referencing:

Referencing a GeoGig element
=============================

Many commands in GeoGig need a reference to a repository element. For instance, if you want to list the contents of a tree, you need a reference to that tree (we will see how to do this soon). You can use the name of the tree (i.e. *roads*) in this case, but there are other ways of naming an element.

Imagine that you want to list the content of a tree, but not in the current version, but in a previous one. Let's say you want the version represented by the command before the last one. How would you tell the corresponding command that you want that particular version?

Since a GeoGig repository keeps all the history of your data, it requires a naming scheme that supports referencing an element and a given version. This is done with a notation in the form ``[ref]:[path]``, where ``ref`` denotes the snapshot of the repository we want to refer to (that is, a given version), and ``path`` the path to the element. Think about it as adding an extra dimension to the common way of referring to an element in a filesystem using its path.

The ``ref`` part of the full reference can be specified in several different ways, including the following.

- An object ID referring to an object that eventually resolves to a tree
- The name of a ref object (i.e. ``WORK_HEAD, HEAD``)
- The n-th parent of a commit denoted with its ID. This is denoted as ``ID^n``. For instance, ``509a481257c5791f50f5a35087e432247f9dc8b7^2``
- The n-th historical ancestor of an element denoted with its ID, by first parent. For instance, to refer to the ancestor of the current HEAD (the element the current HEAD pointed before the last change), ``HEAD~1`` should be used.

The ID of an element can be abbreviated and denoted with just the first 7 digits instead of all 40. In the (unlikely) case of collision (more than one ID starting with those 7 digits), GeoGig will show you a warning message and prompt you to use the full, unambiguous, ID.

If the full syntax is used, the first part of the reference has to resolve to a tree, and the second one must be a valid path under that tree. In some cases, you might in the end want to refer to a tree, so just the first part (the ``ref`` part) is needed, and any of the above alternatives can be used. In some other cases, the command might assume that you are referring to an element in the current working tree, so it will expect just the path, or, in case it is missing, assume that you are referring to the working tree (that is, it will automatically prepend ``WORK_HEAD`` to the parameter you specified). Check the documentation of each command to see what it actually expects.

Going back to the proposed example of referencing a given path in a previous commit, there are several ways we can do it. One of them, in case we know the ID of the commit, would be to use ``ID:path``. When you make a commit using the ``geogig commit`` command, GeoGig will show you the Id of the commit, as it is shown next:

::

	$commit -m "modified parks"
	[c3bf45d6539a0d946a9f61e5ec17474d39529bb5] modified parks
	Committed, counting objects...0 features added, 3 changed, 0 deleted.


If, as it happens in this case, you want to refer not to the last commit, but to a commit whose ID you do not know, remember that the ``geogig log`` command allows you to explore the history of the latest commits in your repository. Let's say it gives you an output like this:

::

	$ geogig log
	Commit:  c3bf45d6539a0d946a9f61e5ec17474d39529bb5
	Author:  volaya <volaya@boundlessgeo.com>
	Date:    (43 seconds ago) 2013-02-25 15:31:41 +0100
	Subject: modified parks

	Commit:  955e67d93d230029b748dac2939c76ced6c28bc2
	Author:  volaya <volaya@boundlessgeo.com>
	Date:    (1 day ago) 2013-02-25 15:31:24 +0100
	Subject: first commit

The latest commit (``c3bf45d6539a0d946a9f61e5ec17474d39529bb5``) is the current one, and the ``HEAD`` of the repository is pointing at it, so the following 3 alternatives reference the same element:

* ``c3bf45d6539a0d946a9f61e5ec17474d39529bb5:parks``
* ``c3bf45d:parks``
* ``HEAD:parks``

The previous commit (``955e67d93d230029b748dac2939c76ced6c28bc2``) is the one we are interested in, so we can make a reference to the ``parks`` tree corresponding to that commit by using any of the next alternatives:

* ``955e67d93d230029b748dac2939c76ced6c28bc2:parks``
* ``955e67d:parks``

Since this commit is the ancestor of the one that the current HEAD is pointing to, we can also use ``HEAD~1:parks``.

One special ID in a GeoGig repository is the null ID, which represent the empty repository before any commits are made. It's an object ID with all digits equal to zero: ``0000000000000000000000000000000000000000``.

You can use it whenever you need a reference to the empty repository. For instance, to know all the changes introduced from the very beginning of you history and up to 3 commits ago, the following command could be used.

::

	$ geogig diff HEAD~3 00000000

You can also abbreviate the null ID if needed.

.. note:: the null ID used by Git ``4b825dc642cb6eb9a060e54bf8d69288fbee4904`` is not used by GeoGig to reference an empty repository.
