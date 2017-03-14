.. _repo.commandaliases:

Command aliases
===============

.. note:: Command aliases are not the same as :ref:`repository aliases <repo.remotes>`.

GeoGig supports command aliases. These are alternate name for certain commands and command options.

To add an alias, a new configuration variable has to be added with the name ``alias.<name_of_alias>`` and the value that GeoGig should replace the alias with when it is invoked.

For instance, you can define an alias for the command ``commit --amend`` to be ``am`` by running the following command:

.. code-block:: console

   geogig config alias.am commit --amend

To amend a commit, now you can use the following notation:

.. code-block:: console

   geogig am

If you want the alias to be available for the current repository, just call the config command with no extra options, as in the example above. If you want it to be available system-wide, use the ``--global`` switch to create a global configuration variable.

.. code-block:: console

   geogig config --global alias.am commit --amend
