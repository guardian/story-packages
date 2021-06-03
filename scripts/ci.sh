#!/usr/bin/env bash

set -e

nvm install
npm install -g grunt-cli
npm install -g jspm #@0.16.55

npm install

grunt --stack validate test
sbt compile test assets
grunt --stack bundle
sbt riffRaffUpload



