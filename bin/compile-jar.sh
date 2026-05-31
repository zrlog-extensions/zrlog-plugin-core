 bash -e bin/build-info.sh
 ./mvnw -PnodeBuild clean package assembly:single ${1}