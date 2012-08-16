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

Loading properties into Zookeeper
---------------------------------

You will need to have the Java properties files for the services you're going
to load and the ACLs file for the deployment you're working with. Then you can
run the following command:

```
clavin props --host 127.0.0.1 --acl <path-to-acls-file> --file <path-to-properties-file> -a app -e dev -d dep-2
```

Remember: the -a is the app you're loading, the -e tells what enviroment
you're loading it into, and the -d tells which deployment in the environment
you're setting up.

The -a, -e, and -d options MUST correspond to a deployment listed in the ACLs
file indicated with the --acl option. For instance, in the above example there
must be a key of "app.dev.dep-2" with IP addresses associated with it in the
--acl file. You will get a stack trace if it doesn't appear and nothing will
get changed in Zookeeper.

The name of the properties file is significant. The name shouldn't contain any
"."'s and should be of the form <service-name>.properties. The service name is
extracted from the filename and is used to separate the settings for each
service in Zookeeper.

Here's nibblonian.properties as it appears in the configulon git repo:

```
# Jargon-related configuration
nibblonian.irods.host=lolinternal.somethingorother.org
nibblonian.irods.port=1247
nibblonian.irods.user=rods
nibblonian.irods.password=lol
nibblonian.irods.home=/tempZone/home/
nibblonian.irods.zone=iplant
nibblonian.irods.defaultResource=

# Controls the size of the preview. In bytes.
nibblonian.app.preview-size=8000

# The size (in bytes) at which a preview becomes a rawcontent link in the manifest.
nibblonian.app.data-threshold=8000

# The maximum number of attempts that nibblonian will make to connect if the connection gets severed.
nibblonian.app.max-retries=10

# The amount of time (in milliseconds) that the nibblonian will wait between connection attempts.
nibblonian.app.retry-sleep=1000

# The path to the community data directory in iRODS
nibblonian.app.community-data=/tempZone/home/shared/

#The port nibblonian should listen on
nibblonian.app.listen-port=31360

#REPL-related configuration
nibblonian.swank.enabled=
nibblonian.swank.port=
```

I use the following command to load it into Zookeeper:

```
clavin props --acl dep2-acls.properties --file dep-2/nibblonian.properties -a app -e dev -d dep-2
```

In Zookeeper that will create a the /app/dev/dep-2/nibblonian node. That node
will in turn contain nodes for each configuration setting in the properties
file. That means that Zookeeper will have a node created at
/app/dev/dep-2/nibblonian/nibblonian.irods.host and the value
"lolinternal.somethingorother.org" will be associated with it. Here's the
output from the dev Zookeeper cluster:

```
[zk: localhost:2181(CONNECTED) 2] get /app/dev/dep-2/nibblonian/nibblonian.irods.host
lolinternal.somethingorother.org
cZxid = 0x2000002fe
ctime = Tue Jan 17 16:09:07 MST 2012
mZxid = 0x200000300
mtime = Tue Jan 17 16:09:07 MST 2012
pZxid = 0x2000002fe
cversion = 0
dataVersion = 1
aclVersion = 1
ephemeralOwner = 0x0
dataLength = 31
numChildren = 0
```

The first line of output shows the value associated with the node.

*An important shortcut:* You can load a directory full of properties files
 like this:

```
clavin props --host 127.0.0.1 --acl <path-to-acls> --dir <path-to-prop-dir> -a <app> -e <env> -d <deployment>
```

All of the files in <path-to-prop-dir> will be processed and loaded into
Zookeeper.


Clavin command-line
-------------------

Running 'clavin' without any arguments on the command-line will list out the
supported sub-tasks:

```
[wregglej@example ~]$ clavin help
clavin props|hosts|help [options]
Each command has its own --help.
```

Currently, the two supported sub-tasks are 'props', which allows you to load
in either a directory or a single file full of configuration options, and
'hosts', which loads the layouts of the different environments.

Here's the --help of the 'props' sub-task:

```
[wregglej@example ~]$ clavin props --help
Usage:

 Switches               Default  Desc                                                            
 --------               -------  ----                                                            
 -h, --no-help, --help  false    Show help.                                                      
 --dir                           Read all of the configs from this directory.                    
 --file                          Read in a specific file.                                        
 --host                          The Zookeeper host to connection to.                            
 --port                 2181     The Zookeeper client port to connection to.                     
 --acl                           The file containing Zookeeper hostname ACLs.                    
 -a, --app                       The application the settings are for.                           
 -e, --env                       The environment that the options should be entered into.        
 -d, --deployment                The deployment inside the environment that is being configured. 
```

Here's the --help of the 'hosts' sub-task:

```
[wregglej@example ~]$ clavin hosts --help
Usage:

 Switches               Default  Desc                                         
 --------               -------  ----                                         
 -h, --no-help, --help  false    Shop help.                                   
 --acl                           The file containing Zookeeper hostname ACLs. 
 --host                          The Zookeeper host to connection to.         
 --port                 2181     The Zookeeper client port to connection to.  
```
