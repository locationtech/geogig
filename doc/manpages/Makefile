# Makefile for Sphinx documentation
#

# You can set these variables from the command line.
SPHINXOPTS    =
SPHINXBUILD   = sphinx-build
PAPER         =

# Internal variables.
PAPEROPT_a4     = -D latex_paper_size=a4
PAPEROPT_letter = -D latex_paper_size=letter
ALLSPHINXOPTS   = -d build/doctrees $(PAPEROPT_$(PAPER)) $(SPHINXOPTS) source
MANOPTS         = -d build/doctrees $(SPHINXOPTS) source

.PHONY: help clean html man

help:
	@echo "Please use \`make <target>' where <target> is one of"
	@echo "  html      to make standalone HTML files"
	@echo "  man       to make standalone man pages"

clean:
	-rm -rf build/*

man:
	mkdir -p build/man1
	$(SPHINXBUILD) -b man $(MANOPTS) build/man1
	@echo
	@echo "Build finished. The man pages are in build/man1."

html:
	mkdir -p build/html build/doctrees
	$(SPHINXBUILD) -b html $(ALLSPHINXOPTS) build/html
	@echo
	@echo "Build finished. The HTML pages are in build/html."

