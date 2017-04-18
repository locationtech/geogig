.. _repo.remotes:

Interacting with remote repositories
====================================

A GeoGig repository contains a full history and it is completely autonomous. However, it is possible and useful to interact with remote copies of the same repository, as it allows different people to work collaborateively. By having a designated central repository which keeps a reference history, others can clone it, work on a cloned repo and then add their changes back to the central repository. While designating a repository as "central", it is in fact no different from the other cloned copies.

All copies of a repository relative to the local copy are known as **remotes**.

Cloning a repository
--------------------

To clone a repository from a remote location, use the ``clone`` command. You must specify the name of the directory where the cloned repository is to be stored as well as a valid URL that points to the original repository.

Here is the command line to be used to clone a repository at ``http://myoriginalrepo.com/repos/naturalearth`` into a local directory named ``repo``:

.. code-block:: console

   geogig clone http://myoriginalrepo.com/repos/naturalearth repo

Once the repository is cloned, both copies are identical, and you can start working on your copy independently, following the usual GeoGig workflow.

Aliases
-------

Instead of referring to a remote with its full URL, a GeoGig repository can refer to it by an **alias**. Remotes are added using the ``geogig remote`` command:

.. code-block:: console

   geogig remote add origin https://myoriginalrepo.com

This creates an alias called ``origin`` for the repository at ``http://myoriginalrepo.com/repos/naturalearth``

You can rename an alias with the ``rename`` argument:

.. code-block:: console

   geogig remote rename origin canonical

Pushing and pulling
-------------------

With the remote repositories already configured, you can now interact with them from your local repository via two operations:

* ``push``, or to apply local changes to a remote repository
* ``pull``, or to apply remote changes to a local repository

The following figure summarizes the above mechanism.

.. figure:: ../img/geogig_workflow_remotes.png

To push changes from the current branch in the local repository to a remote repository named ``origin``, the following command is used:

.. code-block:: console

   geogig push origin

Or to specify a branch:

.. code-block:: console

   geogig push origin branch

.. note:: You must have write access to the remote repository to be able to apply local changes.

To be able to push a branch, you must have in your repository the latest changes made in the remote repository in that branch, so your changes can be added on top of them.

Retrieving the changes from a remote repository is done using the ``pull command``:

.. code-block:: console

   geogig pull origin master

That would bring all changes from the ``master`` branch in the ``origin`` repository into the current branch of the local repository. You can be in a branch other than ``master``. There is no need to specify the same branch as the current branch in the local repository. GeoGig will grab the commits that are missing in your local branch after comparing with the remote branch, and will merge them.

A pull or push is not guaranteed to be clean, and conflicts might appear. They are solved in much the same way as a local :ref:`merge <repo.merging>` conflict.

If instead of a merge you want to perform a rebase, then you can use the ``--rebase`` option. It will rewind your ``HEAD`` to the point were it was before you synchronized it the last time with the remote repository, then apply all the new changes that might exist in the repository, and then re-apply all you local changes on top of the updated ``HEAD``. As in the case of a local rebase, conflicts might also arise when pulling with the ``--rebase`` option.


Fetching
--------

The ``pull`` operation is actually a compound of two operations: ``fetch`` and ``merge``. The ``fetch`` operation brings all the changes from a remote branch, creating a branch in your local repository that contains the changes. Once this is done and the data is stored locally, the ``merge`` is performed.

Using the ``fetch`` command allows you to track branches in a remote repository, and also to bring new branches into your repository. This allows for a more fine-grained functionality, which allows you to have more flexibility than jusst using the ``pull`` command.

The ``fetch`` command is used with only the name of the remote repository:

.. code-block:: console

   geogig fetch origin
