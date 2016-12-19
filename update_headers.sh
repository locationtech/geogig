#!/bin/bash
###############################################################################
# Copyright (c) 2016 Boundless and others.
# All rights reserved. This program and the accompanying materials
# are made available under the terms of the Eclipse Distribution License v1.0
# which accompanies this distribution, and is available at
# https://www.eclipse.org/org/documents/edl-v10.html
#
# Contributors:
# Erik Merkle (Boundless)
###############################################################################

###############################################################################
# Description:
# Script to update Java source code headers.
#
# 1) Run from the root of the project (i.e. parent directory of "src"
# 2) Run with no arguments to use the current system date, otherwise provide
#    the desired 4 digit YEAR for the copyright end date.
#
# ex:
#
#  $ ./update_headers.sh
#
#  or
#
#  $ ./update_headers.sh 2017
#
# NOTE: Inspect the results of the script by running "git diff" to ensure
#       only the headers that should be updated are updated. Currently,
#       only these files have a header that should not be overwritten:
#
# src/core/src/main/java/org/locationtech/geogig/plumbing/diff/DiffMatchPatch.java
# src/core/src/main/java/org/locationtech/geogig/storage/datastream/Varint.java
###############################################################################

function findCreateYear() {
  FILE=${1}
  END=`grep -n -m 1 "Contributors" ${FILE}|cut -f1 -d:`
  if [ "$END" = "" ]
  then
    # couldn't find a copyright date already in the file, use the git history
    git log --format=%cI --follow ${FILE}|tail -1|cut -f1 -d\-
  else
    # use the first 20XX date in the existing header
    head -n ${END} ${FILE}|grep -m 1 20[0-9][0-9]|sed 's/^[^0-9]*\(20[0-9][0-9]\).*/\1/g'
  fi
}

function updateHeader() {
  FILE=${1}
  START_DATE=${2}
  END_DATE=${3}
  echo -n "Processing ${FILE}"
  if [ "$START_DATE" = "$END_DATE" ]
  then
    COPYRIGHT="Copyright (c) ${START_DATE} Boundless and others."
    echo -n "."
  else
    COPYRIGHT="Copyright (c) ${START_DATE}-${END_DATE} Boundless and others."
    echo -n "."
  fi
  # header
  HEADER=$"/* ${COPYRIGHT}\n * All rights reserved. This program and the accompanying materials\n * are made available under the terms of the Eclipse Distribution License v1.0\n * which accompanies this distribution, and is available at\n * https://www.eclipse.org/org/documents/edl-v10.html\n *"
  echo -n "."

  # see if the file has Authors. If not, use the initial committer author
  CONTRIB_LINE=`grep -n -m 1 "Contributors" ${FILE}|cut -f1 -d:`
  echo -n "."
  if [ "$CONTRIB_LINE" = "" ]
  then
    # no authors
    AUTHOR=`git log --format=%an --follow ${FILE}|tail -1`
    echo -n "."
    HEADER_ADD=$"\n * Contributors:\n * ${AUTHOR} - initial implementation\n */"
    echo -n "."
    HEADER=$"${HEADER}${HEADER_ADD}"
    echo -n "."
    PKG_LINE=`grep -n -m 1 "package" ${FILE}|cut -f1 -d':'`
    echo -n "."
  else
    PKG_LINE=${CONTRIB_LINE}
    echo -n "."
  fi
  # replace header
  echo -e "${HEADER}" > tmp
  echo -n "."
  tail -n +${PKG_LINE} ${FILE} >> tmp
  echo -n "."
  mv tmp ${FILE}
  echo "DONE!"
}

# main
mdofiedYear=${1}
if [ "$modifiedYear" = "" ]
then
  # No end date provided, use current time
  modifiedYear=`date +%Y`
fi
for javafile in `find src -type f -name "*.java"`
do
  createYear=`findCreateYear ${javafile}`
  updateHeader ${javafile} ${createYear} ${modifiedYear}
done

