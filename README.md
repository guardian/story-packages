Story Packages
==============
Create story packages. Heavily inspired by [facia-tool](https://github.com/guardian/facia-tool)


New developers quick-start
===========================

1. [Application dependencies](#application-dependencies)
1. [Run the App](#run-the-app)
1. [Unit tests](#unit-tests)


### Application dependencies

Install each of the things listed:

#### Git

Mac:
```bash
brew install git
echo 'export PATH="/usr/local/bin:$PATH"' >> ~/.bash_profile
```

#### [Homebrew](http://brew.sh/)

This is needed on Mac only:
```bash
ruby -e "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/master/install)"
```

#### JDK 8

Ubuntu:
```bash
sudo apt-get install openjdk-8-jdk
```

Mac: Install from [Oracle web site](http://www.oracle.com/technetwork/java/javase/downloads/jdk8-downloads-2133151.html)

#### Node.JS

Ubuntu:
```bash
sudo apt-get install nodejs
sudo apt-get install npm
sudo ln -s /usr/bin/nodejs /usr/bin/node
```

Mac:
```bash
brew install node
```

#### Grunt (build tool)

Ubuntu/Mac:
```bash
sudo npm -g install grunt-cli
```

#### JSPM (package management)

Ubuntu/Mac:
```bash
sudo npm -g install jspm
jspm registry config github
```

It'll ask for a GitHub access token. Go to GitHub Settings -> Applications and [generate new token](https://github.com/settings/tokens/new). Ensure only the public_repo scope is checked.

#### nginx

Mac:
```bash
brew install nginx
```

#### sbt

Mac:
```bash
brew install sbt
```

#### aws cli
```bash
pip install awscli
```


### Run the App

You will need the following credentials:
- cmsFronts - developer
- workflow - S3 Read
- capi - API Gateway invocation

#### 1. Run the setup script
```bash
./setup.sh
```
This will fetch the required config files, set the nginx mappings, and install the Javascript dependencies.

#### 2. Run the application
```bash
sbt
```

Wait for SBT to be up and running. This may take a while the first time, you'll know it's done when you get a prompt.

If it is your first time, compile the project.
```
compile
```

Then run the project locally by typing
```
run
```
This also can take a while the first time.

Now check that you are up and running by hitting the following URL

[https://packages.local.dev-gutools.co.uk](https://packages.local.dev-gutools.co.uk)


### Unit tests

Unit tests run with `grunt`, `karma` and `jasmine`.

```bash
grunt test
```
Runs the tests once in PhantomJS and exits with an error if tests fails

```bash
grunt test --no-single-run
```
Starts `karma` in debug mode, you can connect your browser at [http://localhost:9876?debug.html](http://localhost:9876?debug.html)

You can run a single test going to [http://localhost:9876/debug.html?test=collections](http://localhost:9876/debug.html?test=collections), spec files are inside `public/test/spec`.

You need to have version node version 4.1 or higher installed to be able to run the tests.

Enjoy!
