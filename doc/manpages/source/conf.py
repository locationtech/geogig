# -*- coding: utf-8 -*-
#
# GeoGig documentation build configuration file, created by
# sphinx-quickstart on Tue Oct 28 10:01:09 2008.
#
# This file is execfile()d with the current directory set to its containing dir.
#
# The contents of this file are pickled, so don't put values in the namespace
# that aren't pickleable (module imports are okay, they're removed automatically).
#
# All configuration values have a default value; values that are commented out
# serve to show the default value.

import sys, os, string

# If your extensions are in another directory, add it here. If the directory
# is relative to the documentation root, use os.path.abspath to make it
# absolute, like shown here.
#sys.path.append(os.path.abspath('some/directory'))

# -- Options for manual page output --------------------------------------------

# One entry per manual page. List of tuples
# (source start file, name, description, authors, manual section).
man_pages = [
    ('geogig', 'geogig', 'Runs a geogig command', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('init', 'geogig-init', 'Create and initialize a new geogig repository', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('add', 'geogig-add', 'Stage changes to the index to prepare for commit', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('branch', 'geogig-branch', 'Create, delete, or list branches', ['OpenGeo <http://opengeo.org'], '1'),
    ('checkout', 'geogig-checkout', 'Checkout a branch', ['OpenGeo <http://opengeo.org'], '1'),
    ('commit', 'geogig-commit', 'Commits staged changes to the repository', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('config', 'geogig-config', 'Get and set repository or global options', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('cherrypick', 'geogig-cherrypick', 'Apply the changes introduced by some existing commits', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('diff', 'geogig-diff', 'Show changes between two tree-ish references.', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('log', 'geogig-log', 'Show commit logs', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('help', 'geogig-help', 'Get help for a command', ['OpenGeo <http://opengeo.org'], 1),
    ('indexing', 'geogig-index', 'Index command extension', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('indexcreate', 'geogig-index-create', 'Create a new index on a feature tree', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('indexupdate', 'geogig-index-update', 'Change the extra attributes tracked by an index', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('indexrebuild', 'geogig-index-rebuild', 'Rebuild indexes for the whole history of a feature tree', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('indexlist', 'geogig-index-list', 'List indexes in the repository', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('status', 'geogig-status', 'Show the working tree and index status', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('merge', 'geogig-merge', 'Merge two or more histories into one', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('rebase', 'geogig-rebase', 'Forward-port local commits to the updated upstream head', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('reset', 'geogig-reset', 'Reset current HEAD to the specified state', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('remote', 'geogig-remote', 'Remote management command extension', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('remoteadd', 'geogig-remote-add', 'Add a repository whose branches should be tracked', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('remotelist', 'geogig-remote-list', 'List all repositories being tracked', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('remoteremove', 'geogig-remote-remove', 'Remove a repository whose branches are being tracked', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('revert', 'geogig-revert', 'Revert changes that were committed', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('clone', 'geogig-clone', 'Clone a repository into a new directory', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('fetch', 'geogig-fetch', 'Download objects and refs from another repository', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('pull', 'geogig-pull', 'Fetch from and merge with another repository or a local branch', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('push', 'geogig-push', 'Update remote refs along with associated objects', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('pg', 'geogig-pg', 'PostGIS command extension', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('pgimport', 'geogig-pg-import', 'Import features from a PostGIS database', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('pgexport', 'geogig-pg-export', 'Export features to a PostGIS database', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('pglist', 'geogig-pg-list', 'List tables in a PostGIS database', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('pgdescribe', 'geogig-pg-describe', 'Describe properties of a table in a PostGIS database', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('shp', 'geogig-shp', 'Shapefile command extension', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('shpimport', 'geogig-shp-import', 'Import features from shapefiles', ['Boundless <http://boundlessgeo.com>'], '1'),
    ('shpexport', 'geogig-shp-export', 'Import features to shapefiles', ['Boundless <http://boundlessgeo.com>'], '1')
]

# General configuration
# ---------------------

# Add any Sphinx extension module names here, as strings. They can be extensions
# coming with Sphinx (named 'sphinx.ext.*') or your custom ones.
extensions = ['sphinx.ext.todo']

#todo_include_todos = True

# -- Options for HTML output ---------------------------------------------------

# Theme options are theme-specific and customize the look and feel of a theme
# further.  For a list of options available for each theme, see the
# documentation.
#html_theme_options = {}

# Add any paths that contain custom themes here, relative to this directory.
html_theme_path = ['../../themes/']
html_theme = 'geogig_docs'

# The suffix of source filenames.
source_suffix = '.rst'

# The master toctree document.
master_doc = 'geogig'

# General substitutions.
project = u'GeoGig'
manual = u'Man Pages'
copyright = u'Boundless <http://boundlessgeo.com>'

# The default replacements for |version| and |release|, also used in various
# other places throughout the built documents.
#
# The short X.Y version.
version = '1.3'
# The full version, including alpha/beta/rc tags.
release = '1.3-SNAPSHOT'
# Users don't need to see the "SNAPSHOT" notation when it's there
if release.find('SNAPSHOT') != -1:
   release = '1.3'

# There are two options for replacing |today|: either, you set today to some
# non-false value, then it is used:
#today = ''
# Else, today_fmt is used as the format for a strftime call.
today_fmt = '%B %d, %Y'

# List of documents that shouldn't be included in the build.
#unused_docs = []

# List of directories, relative to source directories, that shouldn't be searched
# for source files.
exclude_trees = []

# The reST default role (used for this markup: `text`) to use for all documents.
#default_role = None

# If true, '()' will be appended to :func: etc. cross-reference text.
#add_function_parentheses = True

# If true, the current module name will be prepended to all description
# unit titles (such as .. function::).
#add_module_names = True

# If true, sectionauthor and moduleauthor directives will be shown in the
# output. They are ignored by default.
#show_authors = False

# The name of the Pygments (syntax highlighting) style to use.
pygments_style = 'sphinx'


# Options for HTML output
# -----------------------
html_theme = 'geogig_docs'
html_theme_path = ['../../themes']

if os.environ.get('HTML_THEME_PATH'):
  html_theme_path.append(os.environ.get('HTML_THEME_PATH'))

# The style sheet to use for HTML and HTML Help pages. A file of that name
# must exist either in Sphinx' static/ path, or in one of the custom paths
# given in html_static_path.
#html_style = 'default.css'

# The name for this set of Sphinx documents.  If None, it defaults to
# "<project> v<release> documentation".
html_title = project + " " + release + " " + manual

# A shorter title for the navigation bar.  Default is the same as html_title.
#html_short_title = None

# The name of an image file (relative to this directory) to place at the top
# of the sidebar.
#html_logo = None

# The name of an image file (within the static path) to use as favicon of the
# docs.  This file should be a Windows icon file (.ico) being 16x16 or 32x32
# pixels large.
#html_favicon = favicon.ico 

# Add any paths that contain custom static files (such as style sheets) here,
# relative to this directory. They are copied after the builtin static files,
# so a file named "default.css" will overwrite the builtin "default.css".
#html_static_path = ['../../theme/_static']

# If not '', a 'Last updated on:' timestamp is inserted at every page bottom,
# using the given strftime format.
html_last_updated_fmt = '%b %d, %Y'

# If true, SmartyPants will be used to convert quotes and dashes to
# typographically correct entities.
#html_use_smartypants = True

# Custom sidebar templates, maps document names to template names.
#html_sidebars = {}

# Additional templates that should be rendered to pages, maps page names to
# template names.
#html_additional_pages = {}

# If false, no module index is generated.
html_use_modindex = False

# If false, no index is generated.
html_use_index = True 

# If true, the index is split into individual pages for each letter.
#html_split_index = False

# If true, the reST sources are included in the HTML build as _sources/<name>.
#html_copy_source = True

# If true, an OpenSearch description file will be output, and all pages will
# contain a <link> tag referring to it.  The value of this option must be the
# base URL from which the finished HTML is served.
#html_use_opensearch = ''

# If nonempty, this is the file name suffix for HTML files (e.g. ".xhtml").
#html_file_suffix = ''

# Output file base name for HTML help builder.
htmlhelp_basename = 'GeoGigUserManual'


# Options for LaTeX output
# ------------------------

# The paper size ('letter' or 'a4').
#latex_paper_size = 'letter'

# The font size ('10pt', '11pt' or '12pt').
#latex_font_size = '10pt'

# Grouping the document tree into LaTeX files. List of tuples
# (source start file, target name, title, author, document class [howto/manual]).
latex_documents = [
  ('index', 'GeoGigUserManual.tex', u'GeoGig User Manual',
   u'GeoGig', 'manual'),
]

# The name of an image file (relative to this directory) to place at the top of
# the title page.
latex_logo = '../../themes/geogig/static/GeoGig.png'

# For "manual" documents, if this is true, then toplevel headings are parts,
# not chapters.
#latex_use_parts = False

# Additional stuff for the LaTeX preamble.
latex_elements = {
  'fontpkg': '\\usepackage{palatino}',
  'fncychap': '\\usepackage[Sonny]{fncychap}',
  'preamble': #"""\\usepackage[parfill]{parskip}
  """
    \\hypersetup{
    colorlinks = true,
    linkcolor = [rgb]{0,0.46,0.63},
    anchorcolor = [rgb]{0,0.46,0.63},
    citecolor = blue,
    filecolor = [rgb]{0,0.46,0.63},
    pagecolor = [rgb]{0,0.46,0.63},
    urlcolor = [rgb]{0,0.46,0.63}
    }
"""
}

# Documents to append as an appendix to all manuals.
#latex_appendices = []

# If false, no module index is generated.
#latex_use_modindex = True
