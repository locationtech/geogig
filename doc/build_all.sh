#! /bin/bash

# directory list for HTML file generation
declare -a htmlDirs=("manpages" "manual" "technical" "workshops" "upgrade")

# for each, build the HTML pages
for dir in "${htmlDirs[@]}"
do
    echo "Processing directory: ${dir}"
    pushd ${dir}
    make clean html
    popd
    echo "Finished directory: ${dir}"
done
