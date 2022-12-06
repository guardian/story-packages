#!/usr/bin/env bash
set -e

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
DOWNLOAD_DIR=/etc/gu
SECRETS_FILE_NAME=story-packages.application.secrets.conf
PROPERTIES_FILE_NAME=story-packages.properties
green='\x1B[0;32m'
red='\x1B[0;31m'
plain='\x1B[0m' # No Color

fail() {
    exit_code=$?
    if [[ "${exit_code}" -ne 0 ]]; then
        echo -e "\n${red}Setup script failed, please fix errors before starting story-packages"
    else
        echo -e "\n${green}All done! You can run story-packages now.${plain}"
    fi
}


trap fail EXIT

echo "🚀   Preparing to download config. This requires cmsFronts permissions."

if [ ! -d $DOWNLOAD_DIR ]; then
  echo "⚠️   The config directory $DOWNLOAD_DIR does not exist."
  echo "🚀 Creating and chowning config directory."
  mkdir $DOWNLOAD_DIR
  echo "🚀   Config directory created successfully"
fi

echo "🚀   Downloading config."
sudo aws --profile cmsFronts s3 cp s3://facia-private/story-packages-local/${SECRETS_FILE_NAME} \
  ${DOWNLOAD_DIR}/${SECRETS_FILE_NAME}
sudo aws --profile cmsFronts s3 cp s3://facia-private/story-packages-local/${PROPERTIES_FILE_NAME} \
  ${DOWNLOAD_DIR}/${PROPERTIES_FILE_NAME}

echo "🛰   Config successfully downloaded. 🏅"

echo "🚀   Setting nginx mappings."
dev-nginx setup-app ${DIR}/nginx/mapping.yml

echo "🚀   Installing javascript dependencies"
npm install
jspm install

