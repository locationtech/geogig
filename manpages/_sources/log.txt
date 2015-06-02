
.. _geogig-log:

geogig-log documentation
#########################



SYNOPSIS
********
geogig log [-p <path>...] [[<until>]|[<since>..<until>]] [<options>]


DESCRIPTION
***********

Shows the commit logs. By default, it shows the history of the current branch.

The history shown by the log command can be edited, to include history from branches other than the current one or to restrict it to a certain set of commits.

For each commit, a description is printed. The level of detail and format of that description can also be adjusted using the available options.

OPTIONS
*******

-n <count>, --max-count <count>     Limit log to <count> commits.

--skip <count>              		Skip <count> commits before starting to show the commit output.

--color <auto|never|always>			Specifies whether or not to apply colored output.

-p <path>, --path <path>			Prints only commits that have modified the given path(s)

--since <since_commit>				Shows only commits since the specified 'since' commit

--until <until_commit>				Shows only commits until the specified 'until' commit

--author <name>						Show only commits by authors with names matching the passed regular expression

--committer <name>					Return only commits by committer with names matching the passed regular expression
    
--oneline							Print only commit id and message on a single line per commit
    
--raw								Show raw contents for commits
    
--summary				 			Show summary of changes for each commit
    
--stats								Show stats of changes for each commit

--names-only"						Show names of changed elements
    
--topo-order						Avoid showing commits on multiple lines of history intermixed
    
--first-parent						Use only the first parent of each commit, showing a linear history
    
--all								Show history of all branches
    
--branches 							Show history of selected branch
   
--abbrev-commit						Show abbreviate commit IDs
    
--decoration						Show reference names

--utc                                                   Show date/time in UTC instead of localised time


SEE ALSO
********

BUGS
****

Discussion is still open.

