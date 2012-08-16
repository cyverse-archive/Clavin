Description
-----------

Clavin is a basic command-line tool for dealing with configuration files at
iPlant.  Its primary purposes are to:

* load ACLs into Zookeeper;

* load configuration settings into Zookeeper;

* generate configuration files from templates.


Overview
--------

On an admin machine (admin machines are described below):

```
clavin hosts --host 127.0.0.1 --acl myacls.properties
clavin props --host 127.0.0.1 --acl myacls.properties -f myenvironments.clj -t
/path/to/template-dir -a app -e env -d deployment
```

On a machine where WAR files are being deployed:

```
clavin files -f myenvironments.clj -t /path/to/template-dir -a app -e env -d
deployment --dest /path/to/dest-dir
```

Environment listing and validation:

```
clavin envs -l -f myenvironments.clj
clavin envs -v -f myenvironments.clj
```

Getting help:

```
clavin help
clavin envs -h
clavin files -h
clavin hosts -h
clavin props -h
```

Creating an ACLs file
---------------------

An "ACL file" is simply a java properties file that lists the ip-addresses of
machines that are in a particular environment (dev, qa, stage, prod,
etc.). Each environment can have multiple deployments. For instance, dev has
dep-1 and dep-2. Here's the ACLs file for the dep-1 deployment (you can list
multiple deployments in one file).

```
app.dev.dep-1 = 192.168.1.1,\
                192.168.1.2,\
                192.168.1.3,\
                192.168.1.4,\
                192.168.1.5

admin = 192.168.1.5,\
        192.168.1.6,\
        192.168.1.7
```

The naming of the keys is significant. Any machine listed in the "admin" value
will be given admin privileges in Zookeeper for the deployments included in
the ACLs file.

The "app.dev.dep-1" key tells clavin the application name, environment, and
deployment that is being described. In this case, "app" is the application
name, "dev" is the environment, and "dep-1" is the deployment. These values
correspond with the \--app (-a), \--env (-e), and \--deployment (-d) options
that clavin uses when setting properties in Zookeeper. More on that below.

The values associated with each key in the ACLs file are comma separated lists
of IP addresses.



High level guidelines for organizing ACL files
----------------------------------------------

* Don't reuse the same IP address in multiple deployments.

* If an IP address appears in a deployment and as an admin machine, then that
  deployment and admin section should be in the same ACL file.

* If an IP address appears in multiple admin sections in different files, then
  consider merging those files.


Loading ACLs file into Zookeeper
--------------------------------

Once you've got the ACLs file put together, you load it into Zookeeper with
the following command:

```
clavin hosts --host 127.0.0.1 --acl <path-to-acls-file>
```

--host tells clavin which Zookeeper node to run against. That will create
entries in /hosts on Zookeeper for each machine and tell what deployments
it's associated with (deployments are analogous to groups in this case).

For instance, since 192.168.1.5 is listed both as an admin machine and as a
machine in the "app.dev.dep-2" deployment, Zookeeper will create the following
nodes for it:

```
[zk: localhost:2181(CONNECTED) 2] ls /hosts/192.168.1.5

[app.dev.dep-2, admin]
```

In other words, the /hosts/192.168.1.5/app.dev.dep-2 and
/hosts/192.168.1.5/admin nodes both exist. The ACLs on the "admin" node are as
follows:


```
[zk: localhost:2181(CONNECTED) 4] getAcl /hosts/192.168.1.5/admin

'ip,'192.168.1.5

: cdrwa

'ip,'192.168.1.6

: cdrwa

'ip,'192.168.1.7

: cdrwa

'ip,'192.168.1.5

: rw
```

Translation: Every admin node is listed as having admin access (the "cdrwa"
part) and the node itself has read-write access. This means that other
machines can't accidentally or maliciously nuke the group/deployment
memberships unless they are listed as an admin node in the ACLs file.


Creating Configuration File Templates
-------------------------------------

Clavin uses configuration file templates in conjunction with
deployment-specific configuration setting definitions in order to generate
configuration settings.  Once the configuration settings have been generated,
they may either be loaded directly into Zookeeper or written to a file for
inspection or use by a web application.

The configuration file templates used by Clavin consist of plain text with
configuration placeholders interspersed throughout the text.  The placeholders
consist of property names surrounded by dollar signs.  For example:

```
foo.bar.baz = $some_property$
```

The property names should be valid java identifiers.  (That is, they should
begin with either an underscore or an alpha character and be followed by zero
or more underscores or alphanumeric characters.)  The preferred naming
convention is to separate words in identifiers with underscores rather than to
use camelCase.  The reason for this has to do with the definition of the
placeholder values, which will be described in the _Creating an Environments
File_ section.

Literal dollar signs can be included the file by preceding the dollar sign
with a backslash:

```
dr.evil.catch-phrase = ...unless you bring me \$1,000,000!
```

Each service has its own configuration file template, with the template file
named after the service with a extension of `.st`.  For example, the service,
`donkey`, would have a template file named `donkey.st`.  (The `.st` file name
extension stands for "String Template", which is the library used to process
the template files.)  Here's an example configuration file template,
`nibblonian.st`:

```
# Jargon-related configuration
nibblonian.irods.host            = $irods_host$
nibblonian.irods.port            = $irods_port$
nibblonian.irods.user            = $irods_user$
nibblonian.irods.password        = $irods_password$
nibblonian.irods.home            = $irods_home$/
nibblonian.irods.zone            = $irods_zone$
nibblonian.irods.defaultResource = $irods_default_resource$

# Application-specific configuration

# Controls the size of the preview. In bytes.
nibblonian.app.preview-size = 8000

# The size (in bytes) at which a preview becomes a rawcontent link in the
# manifest.
nibblonian.app.data-threshold = 8000

# The maximum number of attempts that nibblonian will make to connect if the
# connection gets severed.
nibblonian.app.max-retries = 10

# The amount of time (in milliseconds) that the nibblonian will wait between
# connection attempts.
nibblonian.app.retry-sleep = 1000

# Set this value to 'true' to send deleted files and folders to the Trash.
nibblonian.app.use-trash = true

# A comma delimited list of file/directory names that should be filtered from
# Nibblonian listings.
nibblonian.app.filter-files = cacheServiceTempDir

# The path to the community data directory in iRODS
nibblonian.app.community-data = $irods_home$/shared

#The port nibblonian should listen on
nibblonian.app.listen-port  = $nibblonian_port$

#REPL-related configuration
nibblonian.swank.enabled = 
nibblonian.swank.port = 
```

Creating an Environments File
-----------------------------

The environments file defines the deployment-specific settings that are
plugged into the templates in order to generate the full-blown configuration
settings.  This file is just a plain Clojure source file containing a single
nested map definition:

```clojure
{:env-1
 {:dep-1
  {:first-setting  "foo"
   :second-setting "bar"}}
 {:dep-2
  {:first-setting  "baz"
   :second-setting "quux"}}
 :env-2
 {:dep-3
  {:first-setting  "ni"
   :second-setting "ecky"}}}
```

Note that the keys at the first level correspond to the environment (from the
`-e` or `--env` command-line option) and that the keys at the second level
correspond to the deployment (from the `-d` or `--deployment` command-line
option).  The app (from the `-a` or `--app` command-line option) is not
currently used in this file.

The keywords in the map below the deployment level correspond directly to the
placeholder names.  When clavin defines the values that will be used by the
template, it uses the keyword name with hyphens replaced with underscores as
the placeholder name.  For example, the `:first-setting` keyword mentioned
above will be used to define the value of the `first_setting` placeholder.
For example, suppose that you have the following template file:

```
some.service.first  = $first_setting$
some.service.second = $second_setting$
```

Also suppose that you've chosen the environment, `env-1` and the deployment
`dep-1` in the example environments file above.  If a property file is
generated from these settings, the resulting file would look like this:

```
some.service.first  = foo
some.service.second = bar
```

In some cases, it's convenient to be able to generate deployment-specific
settings from other deployment-specific settings.  For example, a service
generally has to be told which port to use for its listen port.  In addition
to this, clients of that service need to be given a base URL to use when
connecting to this service.  For this purpose, Clavin supports placeholders
inside of property values:

```clojure
{:env-1
 {:dep-1
  {:foo-port "8888"
   :foo-base "http://somehost.example.org:${foo-port}/bar"}}}
```

Note that the placeholder format is a little different in this file than it is
in the configuration file templates.  The reason for this difference was to
make the substitution easier while still allowing idiomatic Clojure keywords
to be used in the settings map.

Clavin also supports chained substitutions, in which the substituted value
also contains a substitution string.  Take this (admittedly contrived)
environments file, for example:

```clojure
{:env-1
 {:dep-1
  {:foo-port "8888"
   :foo-host "somehost.example.org:${foo-port}"
   :foo-base "http://${foo-host}"}}}
```

In this case, the value for the setting, `foo-base`, contains a reference to
the setting, `foo-host`, which contains a reference to the setting,
`foo-port`.  There's no practical limit to the number of of settings in the
chain, but the chains should be kept as short as possible.  An extremely long
chain (or a recursive chain) will cause a stack overflow error in Clavin.
Checks for recursive chains can be added sometime in the future if this begins
to cause a problem.

Validating an Environments File
-------------------------------

In order for an environments file to be useful, every environment has to have
the same set of properties defined.  Clavin provides a way to ensure that this
is the case.  Here's an example of a valid file check:

```
$ clavin envs -v -f environments.clj 
environments.clj is valid.
```

Here's an example of an invalid file check:

```
$ clavin envs -v -f environments.clj 
environments.clj is not valid.

Please check the following properties:
	:admib-groups
	:admin-groups
	:cas-base
	:case-base
```

In this case, it appears that some of the property names were mistyped in one
or more environments.

Listing Environments in an Environments File
--------------------------------------------

The environments file can get fairly big, so it's sometimes convenient to be
able to list the environments that are defined within a file.  The envs
subcommand can be used for this purpose as well.

```
$ clavin envs -l -f environments.clj
environment deployment 
----------- -----------
env-1       dep-1      
env-1       dep-2      
```

In this example, the environment definition file has one environment, `env-1`
containing two deployments, `dep-1` and `dep-2`.

Loading properties into Zookeeper
---------------------------------

You will need to have access to configuration file templates for the services
you're going to load the confgurations for and an environment definition file
that contains definitions for all of the placeholder values that are used in
the configuration files.  Then you can run the following command:

```
clavin props --host 127.0.0.1 --acl <path-to-acls-file> -f <path-to-environments-file> -t <path-to-template-dir> -a app -e dev -d dep-2 service-name1 service-name-2
```

The -a option refers to the app that you're loading configurations for.
Currently, the Discovery Environment is the only application supported by
Clavin, so the app will always be `de`.  Because of this, the default value
for the -a option is `de` and the -a option does not have to be specified.

The -e option refers to the environment that you're loading configurations
for, which corresponds to the name of the environment in the environments file
(or the environment and deployment listing described above).  In cases where
the deployment name is unique across all environments, the environment can be
determined from the deployment name, so it's not necessary to specify the -e
option unless another deployment of the same name appears in another
environment.

The -d option refers to the deployment that you're loading configurations
for.  Once again, this is the same as the name of the deployment in the
environments file (or the environment and deployment listing mentioned
above).  This option _must_ correspond to one of the deployments defined in
the environments file.

The values of the -a, -e, and -d options MUST correspond to a deployment
listed in the ACLs file indicated with the --acl option. For instance, in the
above example there must be a key of "app.dev.dep-2" with IP addresses
associated with it in the --acl file. You will get a stack trace if it doesn't
appear and nothing will get changed in Zookeeper.

The value of the -f flag should contain the path to the environment definition
file and the value of the -t flag should contain the path to the directory
containing the template files.  Note that all of the template files must have
an extension of `.st` and the name of the file without the extension
corresponds to the name of the service that is being configured.

The service name arguments correspond to the names of the services whose
configurations you want to load into Zookeeper.  For example, if you wanted to
load only the configurations for metadactyl and donkey, you would mention both
services on the command line:

```
clavin props --host 127.0.0.1 --acl acls.properties -f environments.clj -t templates -d dep-2 metadactyl donkey
```

To load all of the properties files without having to list them all, you can
simply not include any service names on the command line:

```
clavin props --host 127.0.0.1 --acl acls.properties -f environments.clj -t templates -d dep-2
```

Generating Properties Files
---------------------------

Not all iPlant services and web applications associated with the Discovery
Environment use Zookeeper to manage configuration settings.  For the
components that do not use Zookeeper, Clavin provides a way to generate Java
properties files from templates and environments files.  The subcommand used
to generate properties files is `files`.  The command line for the `files`
subcommand is similar to that of the `props` subcommand; the only differences
are that the ACL and Zookeeper connection settings aren't required and a
destination directory is required:

```
clavin files --dest <output-dir> -f <path-to-environments-file> -t <path-to-template-dir> -a app -e dev -d dep-2 service-name1 service-name-2
```

The only command-line option that is unique to the `files` subcommand is
--dest, which is used to specify the path to the output directory.  All of the
generated properties files will be placed in this directory.  The file names
will be the service name with a `.properties` extension.  For example, if you
generate a properties file for the service, `discoveryenvironment` then the
name of the generated file will be `discoveryenvironment.properties`.

See the `Loading properties into Zookeeper` section for details about the rest
of the command-line options.


Clavin command-line
-------------------

Running `clavin` with the `help` subcommand will list out the supported
sub-tasks:

```
$ clavin help
clavin envs|files|help|hosts|props [options]
Each command has its own --help.
```

Running `clavin` without any arguments will provide a brief error message
along with the help text:

```
$ clavin
Something weird happened.
clavin envs|files|help|hosts|props [options]
Each command has its own --help.
```

Currently, the supported subcommands are:

<table border='1'>
    <thead>
        <tr><th>Subcommand</th><th>Description</th></tr>
    </thead>
    <tbody>
        <tr><td>envs</td><td>Performs actions on environment files.</td></tr>
        <tr><td>files</td><td>Generates properties files.</td></tr>
        <tr><td>help</td><td>Displays a brief help message.</td></tr>
        <tr><td>hosts</td><td>Loads admin ACLs into Zookeeper.</td></tr>
        <tr><td>props</td><td>Loads configurations into Zookeeper.</td></tr>
    </tbody>
</table>

Here's the help message for the `envs` subcommand:

```
$ clavin envs --help
Usage:

 Switches                       Default  Desc                                            
 --------                       -------  ----                                            
 -h, --no-help, --help          false    Show help.                                      
 -l, --no-list, --list          false    List environments.                              
 -v, --no-validate, --validate  false    Validate the environments file                  
 -f, --envs-file                         The file containing the environment definitions 
```

Here's the help message for the `files` subcommand:

```
$ clavin files --help
Usage:

 Switches               Default  Desc                                              
 --------               -------  ----                                              
 -h, --no-help, --help  false    Show help.                                        
 -f, --envs-file                 The file containing the environment definitions.  
 -t, --template-dir              The directory containing the templates.           
 -a, --app              de       The application the settings are for.             
 -e, --env                       The environment that the options are for.         
 -d, --deployment                The deployment that the properties files are for. 
 --dest                          The destination directory for the files.          
```

Here's the help message for the `hosts` subcommand:

```
$ clavin hosts --help
Usage:

 Switches               Default  Desc                                         
 --------               -------  ----                                         
 -h, --no-help, --help  false    Show help.                                   
 --acl                           The file containing Zookeeper hostname ACLs. 
 --host                          The Zookeeper host to connection to.         
 --port                 2181     The Zookeeper client port to connection to.  
```

Here's the help message for the `props` subcommand:

```
$ clavin props --help
Usage:

 Switches               Default  Desc                                                            
 --------               -------  ----                                                            
 -h, --no-help, --help  false    Show help.                                                      
 -f, --envs-file                 The file containing the environment definitions.                
 -t, --template-dir              The directory containing the templates.                         
 --host                          The Zookeeper host to connection to.                            
 --port                 2181     The Zookeeper client port to connection to.                     
 --acl                           The file containing Zookeeper hostname ACLs.                    
 -a, --app              de       The application the settings are for.                           
 -e, --env                       The environment that the options should be entered into.        
 -d, --deployment                The deployment inside the environment that is being configured. 
```
