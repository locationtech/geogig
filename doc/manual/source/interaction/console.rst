The GeoGig Console
====================

GeoGig is a command-line application that is run by calling commands in the form ``geogig <command> [options]``. Each time you call it with a sentence like that, GeoGig has to be initialized. If your session involves running several commands, a better option is to run the geogig console, which lets you run a set of commands, initializing GeoGig just once at the begining of the session.

To start the GeoGig console, type ``geogig-console``

The prompt will show you the current folder, and in case it is a GeoGig folder, the name of the current branch (or the current commit pointed by HEAD, in case you are in a detached HEAD state).

::
	
	(geogig):my/current/folder (master) $

You can enter commands just like you do on a normal console when calling GeoGig, but without having to add ``geogig`` at the beginning of each sentence. For instance, to get the history of your repository, just type ``log`` (instead of ``geogig log``).

Console commands (like  ``ls`` if you are on Linux or ``dir`` if you are running Windows) are not available in the GeoGig console.

The GeoGig console has autocompletion, which is available pressing the tab key.

When you finish working with GeoGig and want to go back to you shell environment, type ``exit``.


Running GeoGig console in batch mode
------------------------------------

If you need to run several GeoGig operations in a batch script, you will be initializing GeoSgit as many times as commands you execute. Instead, you can pass the whole set of commands to the GeoGig console, and reduce the number of initializations to just one.

To do so, create a text file with all the GeoGig commands that you want to run, and run the ``geogig-console`` option followed by the path to that text file.

::

	$ geogig-console myscript.txt

As when using the GeoGig console in its interactive mode, the command calls in the text file should not start with the ``geogig`` command name, but with the operation name instead. Here is an example of a simple batch file.

::

	import shp myfile.shp
	add
	commit -m "First commit"

.. note:: If you use GeoGig on Windows and you create a batch file to call several GeoGig commands using the normal ``geogig`` script (not ``geogig-console``), notice that the ``geogig`` command is itself a batch process. To be able to run more than a single GeoGig command, make each call in your batch file using the ``call`` command. For instance, this will not work (it will only execute the first line):

	::	
		geogig import shp myfile.shp
		geogig add
		geogig commit -m "First commit"

	Instead, use this:

	::	
		call geogig import shp myfile.shp
		call geogig add
		call geogig commit -m "First commit"
