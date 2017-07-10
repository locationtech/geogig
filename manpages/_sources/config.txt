
.. _geogig-config:

geogig-config documentation
###########################



SYNOPSIS
********
* geogig config [--global|--local] name [value]
* geogig config [--global|--local] --get name
* geogig config [--global|--local] --unset name
* geogig config [--global|--local] --remove-section name
* geogig config [--global|--local] -l
 


DESCRIPTION
***********

You can query/set/unset options with this command. The name is actually the section and the key separated by a dot, and the value will be escaped.

By default, the config file of the current repository will be assumed.  If the --global option is set, the global ``.geogigconfig`` file will be used. If the ``--local`` option is set the config file of the current repository will be used if it exists.

OPTIONS
*******

--global        Tells the config command to use the global config file, rather than the repository config file.

--local				Tells the config command to use the repository config file, rather than the global config file.

--get         Query the config file for the given section.key name.

--unset       Remove the line matching the given section.key name.

--remove-section    Remove the given section from the config file.

-l, --list          List all variables from the config file.

GEOGIG CONFIGURATION
********************

This list is not comprehensive; some configuration options are documented in relevant man pages.

**ansi.enabled**

  Boolean value to toggle ANSI escape sequence support. Used mainly on versions of Windows where ANSI escape sequences are not supported by the console. See installation section for instructions to enable ANSI on Windows. Valid values are true or false.

**bdbje.object_durability**

  Determines how safe to be when persisting objects in the BDB object store.  Valid values include: safe (be as safe as possible) and fast (sacrifice some safety to improve performance.)

SEE ALSO
********

geogig-init

BUGS
****

Discussion is still open.

