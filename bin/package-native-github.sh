#!/usr/bin/env bash
basePath=${1}
mkdir -p ${basePath}
echo "real target folder ${basePath}"

java -version
sh bin/build-info.sh
./mvnw ${2} clean package
./mvnw ${2} -Pnative -Dagent exec:exec@java-agent -U
./mvnw ${2} -Pnative package
binName="plugin-core"
if [ -f "target/${binName}.exe" ];
then
  echo "window"
  mv "target/${binName}.exe" "${basePath}/${binName}-Windows-$(uname -m).exe"
  exit 0;
fi
if [[ "$(uname -s)" == "Linux" ]];
then
  echo "Linux"
  mv target/${binName} ${basePath}/${binName}-$(uname -s)-$(dpkg --print-architecture).bin
else
  echo "MacOS"
  mv target/${binName} ${basePath}/${binName}-$(uname -s)-$(uname -m).bin
fi