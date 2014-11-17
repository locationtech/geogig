@ECHO OFF

REM Command file for Sphinx documentation

set SPHINXBUILD=sphinx-build
set ALLSPHINXOPTS=-d build/doctrees %SPHINXOPTS% source
if NOT "%PAPER%" == "" (
	set ALLSPHINXOPTS=-D latex_paper_size=%PAPER% %ALLSPHINXOPTS%
)

if "%1" == "" goto help

if "%1" == "help" (
	:help
	echo.Please use `make ^<target^>` where ^<target^> is one of
	echo.  html      to make standalone HTML files
    echo.  man       to make standalone man files
	goto end
)

if "%1" == "clean" (
	for /d %%i in (build\*) do rmdir /q /s %%i
	del /q /s build\*
	goto end
)

if "%1" == "man" (
    %SPHINXBUILD% -b man %ALLSPHINXOPTS% build/man
    echo.
    echo.Build finished. The man pages are in build/man.
    goto end
)

if "%1" == "html" (
	%SPHINXBUILD% -b html %ALLSPHINXOPTS% build/html
	echo.
	echo.Build finished. The HTML pages are in build/html.
	goto end
)


:end
