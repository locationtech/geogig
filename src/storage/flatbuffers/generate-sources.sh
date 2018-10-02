#!/bin/bash

#flatc -o target/generated-sources --java -I src/main/resources/fbs src/main/resources/fbs/*.fbs
#flatc -o target/generated-sources --java -I src/main/resources/fbs/values src/main/resources/fbs/values/*.fbs
flatc -o target/generated-sources --java -I src/main/resources/fbs src/main/resources/fbs/*.fbs
