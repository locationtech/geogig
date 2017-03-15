.. _repo.exploring:

Exploring a repository
======================

The entire contents of a GeoGig repository are stored in the ``.geogig`` directory.

Unlike a version control system like Git, the content of the current working tree cannot be explored directly as a normal folder containing files, so they are stored instead in a database which holds the complete structure of the repository. This means that any files stored in the same directory that contains the ``.geogig`` directory will be ignored by GeoGig.

Therefore, exploring a GeoGig repository must be done using GeoGig commands. The main commands used for this task are:

* ``ls``: lists the content of a tree
* ``show``: prints a formatted version of an element of the repository

Listing trees
-------------

The basic ``ls`` command takes the following form:

.. code-block:: console

   geogig ls [ref]:[path]

The ``[ref]:[path]`` parameter defines the path to be listed. If it contains no ``ref`` part, it will list the given path in the current working tree. To list the same path in a different reference, a full reference can be provided. For instance, to list the content of ``parks`` in the current HEAD, the following line should be used.

.. code-block:: console

   geogig ls HEAD:parks

The provided reference and path should resolve to a tree. Since features do not contains other elements, their content cannot be listed with the ``ls`` command, but instead must be examined with the ``show`` command.

An object ID can be used directly, instead of a ``[ref]:[path]``, to list the contents of the tree corresponding to that commit.

The options available for the ``ls`` command control how the list of elements under the given path are printed.

* The ``-v`` option will display a **verbose** list of, not just the name of the objects, but also the object's ID and the ID of the corresponding feature type.
* The ``-r`` option will list the contents of subtrees **recursively**. The names of these subtrees are not shown in the listing, but you can tell GeoGig to display them by using the ``-t`` option along with the ``-d`` option.
* The ``-a`` option will show IDs in their **abbreviated** form. Use a number to determine how many digits of the ID to display (``-a 7`` for seven digit IDs).

Here are some examples of the ``ls`` command:

Recursive list
~~~~~~~~~~~~~~

.. code-block:: console

   geogig ls -r -t

.. code-block:: console

   Root tree/
       parks/
           2
           3
           4
           1

Verbose list
~~~~~~~~~~~~

.. code-block:: console

   geogig ls -v parks

.. code-block:: console

  parks/
      2 49852c03b8dd3c93fcbda7137abda9ad53a9311a bfd1d4bb75e0a4419243ef0ba9d6e9793d31cdab
      3 49852c03b8dd3c93fcbda7137abda9ad53a9311a 84150cc07326358ac70777d4141a8cfdd8038323
      4 49852c03b8dd3c93fcbda7137abda9ad53a9311a 5347d1b1b5d828f83e4065e227dcb848b4371637
      1 49852c03b8dd3c93fcbda7137abda9ad53a9311a ce3e836bcb64f1b647e3dc9dd97700c584063533

Verbose and recursive list
~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: console

   geogig ls -v -r -t

.. code-block:: console

   Root tree/
      parks/ 49852c03b8dd3c93fcbda7137abda9ad53a9311a 224f0086bc4e9b116e7b60dbc414e1cc8d829839
          2 49852c03b8dd3c93fcbda7137abda9ad53a9311a bfd1d4bb75e0a4419243ef0ba9d6e9793d31cdab
          3 49852c03b8dd3c93fcbda7137abda9ad53a9311a 84150cc07326358ac70777d4141a8cfdd8038323
          4 49852c03b8dd3c93fcbda7137abda9ad53a9311a 5347d1b1b5d828f83e4065e227dcb848b4371637
          1 49852c03b8dd3c93fcbda7137abda9ad53a9311a ce3e836bcb64f1b647e3dc9dd97700c584063533

Verbose and recursive list with seven-digit IDs
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

.. code-block:: console

   geogig ls -v -r -t -a 7

.. code-block:: console

   Root tree/
      parks/ 49852c0 224f008
          2 49852c0 bfd1d4b
          3 49852c0 84150cc
          4 49852c0 5347d1b
          1 49852c0 ce3e836


Showing features
----------------

Describing an element in a GeoGig repository is done using the ``show`` command. It can be used to describe any type of object, unlike ``ls`` which needs to resolve to a tree.

The ``show`` command prints a formatted description of a given element. This description is a human-readable version of the element.

The command takes, as input, a string that defines the object to describe. All supported notations are allowed for both commands, as they are described in :ref:`referencing`.

Below, you can find the output of the ``show`` command for certain types of objects.

An exmplae of the ``show`` command with a tree:

.. code-block:: console

   geogig show parks

.. code-block:: console

   TREE ID:  0bbed3603377adfbd3b32afce4d36c2c2e59d9d4
   SIZE:  50
   NUMBER OF SUBTREES:  0
   DEFAULT FEATURE TYPE ID:  6350a6955b124119850f5a6906f70dc02ebb31c9

   DEFAULT FEATURE TYPE ATTRIBUTES
   --------------------------------
   agency: <STRING>
   area: <DOUBLE>
   len: <DOUBLE>
   name: <STRING>
   number_fac: <Long>
   owner: <STRING>
   parktype: <STRING>
   the_geom: <MULTIPOLYGON>
   usage: <STRING>

When specifying a single feature, the ``show`` command prints the values of all attributes and their corresponding names taken from the associated feature type.

.. code-block:: console

   geogig show HEAD:parks/1

.. code-block:: console

   ID:  ff51bfc2a36d02a3a51d72eef3e7f44de9c4e231

   ATTRIBUTES
   ----------
   agency: Medford School District
   area: 636382.400857
   len: 3818.6667552
   name: Abraham Lincoln Elementary
   number_fac: 4
   owner: Medford School District
   parktype: School Field
   the_geom: MULTIPOLYGON (((-122.83646412838807 42.36016644633764, ...
   usage: Public

The following example shows the output of the ``show`` command when used on a commit reference:

.. code-block:: console

   geogig show 509a481257c5791f50f5a35087e432247f9dc8b7

.. code-block:: console

   Commit:        509a481257c5791f50f5a35087e432247f9dc8b7
   Author:        Author <author@example.com>
   Committer:     Author <author@example.com>
   Author date:   (3 hours ago) Mon Jan 21 13:58:55 CET 2013
   Committer date:(3 hours ago) Mon Jan 21 13:58:55 CET 2013
   Subject:       Updated geometry


You can also use a reference like ``HEAD`` to show the current state of the repository:

.. code-block:: console

   geogig show HEAD
