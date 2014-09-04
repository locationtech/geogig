#!/bin/bash
function update_header() {
   FILE=$1
   AUTHOR=$2
   YEAR=$3
   MODIFIED=$4   
   if [ "${YEAR}" = "${MODIFIED}" ]
   then
      COPYRIGHT="Copyright (c) ${YEAR} Boundless and others."
   else
      COPYRIGHT="Copyright (c) ${YEAR}-${MODIFIED} Boundless and others."
   fi
   #echo "Looking for ${FILE}"
   if [ -f "${FILE}" ]
   then
      echo "Process: ${FILE} --> ${COPYRIGHT} Contributor: ${AUTHOR}"
      HEADER=$"/* ${COPYRIGHT}\n * All rights reserved. This program and the accompanying materials\n * are made available under the terms of the Eclipse Distribution License v1.0\n * which accompanies this distribution, and is available at\n * https://www.eclipse.org/org/documents/edl-v10.html\n *\n * Contributors:\n * ${AUTHOR} - initial implementation\n */"

      #echo -e "${HEADER}"
      PACKAGE=`grep -n -m 1 package "${FILE}" | cut -d':' -f1`
   
      echo -e "${HEADER}" > tmp
      tail -n +${PACKAGE} ${FILE} >> tmp
   
      mv tmp "${FILE}"
   else 
      echo "Skipped: ${FILE}..."
      echo "${FILE}" >> skipped
   fi
}

HERE=`dirname $0`
FILE_LIST=$1

OIFS=$IFS
IFS=$'\n'
for line in `cat ${FILE_LIST}`
do
   #echo "${line}"
   FILE=$(echo $line | cut -f2 -d ',')
   AUTHOR=$(echo $line | cut -f4 -d ',')
   YEAR=$(echo $line | cut -f5 -d ',')
   MODIFIED=$(echo $line | cut -f6 -d ',')
   TYPE=$(echo $line | cut -f7 -d ',')
   if [ "${TYPE}" = "java" ]
   then
      update_header ${FILE} ${AUTHOR} ${YEAR} ${MODIFIED}
   else
      echo "Ignore ${TYPE} : ${FILE}"
   fi
done
IFS=$OIFS