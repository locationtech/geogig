#!/bin/bash

#flatc -o target/generated-sources --java -I src/main/resources/fbs src/main/resources/fbs/*.fbs
flatc -o src/main/generated-sources --java -I src/main/resources/fbs src/main/resources/fbs/*.fbs
