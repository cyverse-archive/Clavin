Description
-----------
Clavin is a basic command-line tool that can load ACL and service configuration information into a Zookeeper cluster.


Overview
--------

On an admin machine (admin machines are described below):

    clavin hosts --host 127.0.0.1 --acl myacls.properties -a app -e env -d deployment
    clavin props --host 127.0.0.1 --acl myacls.properties --file service-name.properties -a app -e env -d deployment


Creating an ACLs file
---------------------

An "ACL file" is simply a java properties file that lists the ip-addresses of machines that are in a particular environment (dev, qa, stage, prod, etc.). Each environment can have multiple deployments. For instance, dev has dep-1 and dep-2. Here's the ACLs file for the dep-1 deployment (you can list multiple deployments in one file).

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

The naming of the keys is significant. Any machine listed in the "admin" value will be given admin privileges in Zookeeper for the deployments included in the ACLs file.

The "app.dev.dep-1" key tells clavin the application name, environment, and deployment that is being described. In this case, "app" is the application name, "dev" is the environment, and "dep-1" is the deployment. These values correspond with the \--app (-a), \--env (-e), and \--deployment (-d) options that clavin uses when setting properties in Zookeeper. More on that below.

The values associated with each key in the ACLs file are comma separated lists of IP addresses.



High level guidelines for organizing ACL files
----------------------------------------------

* Don't reuse the same IP address in multiple deployments. 
* If an IP address appears in a deployment and as an admin machine, then that deployment and admin section should be in the same ACL file.
* If an IP address appears in multiple admin sections in different files, then consider merging those files.


Loading ACLs file into Zookeeper
--------------------------------

Once you've got the ACLs file put together, you load it into Zookeeper with the following command:

```
clavin hosts --host 127.0.0.1 --acl <path-to-acls-file>
```

--host tells clavin which Zookeeper node to run against. That will create entries in /hosts on Zookeeper for each machine and tell what deployments it's associated with (deployments are analogous to groups in this case).

For instance, since 192.168.1.5 is listed both as an admin machine and as a machine in the "app.dev.dep-2" deployment, Zookeeper will create the following nodes for it:

```
[zk: localhost:2181(CONNECTED) 2] ls /hosts/192.168.1.5

[app.dev.dep-2, admin]
```

In other words, the /hosts/192.168.1.5/app.dev.dep-2 and /hosts/192.168.1.5/admin nodes both exist. The ACLs on the "admin" node are as follows:


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

Translation: Every admin node is listed as having admin access (the "cdrwa" part) and the node itself has read-write access. This means that other machines can't accidentally or maliciously nuke the group/deployment memberships unless they are listed as an admin node in the ACLs file.


Loading properties into Zookeeper
---------------------------------

You will need to have the Java properties files for the services you're going to load and the ACLs file for the deployment you're working with. Then you can run the following command:

```
clavin props --host 127.0.0.1 --acl <path-to-acls-file> --file <path-to-properties-file> -a app -e dev -d dep-2
```

Remember: the -a is the app you're loading, the -e tells what enviroment you're loading it into, and the -d tells which deployment in the environment you're setting up.

The -a, -e, and -d options MUST correspond to a deployment listed in the ACLs file indicated with the --acl option. For instance, in the above example there must be a key of "app.dev.dep-2" with IP addresses associated with it in the --acl file. You will get a stack trace if it doesn't appear and nothing will get changed in Zookeeper.

The name of the properties file is significant. The name shouldn't contain any "."'s and should be of the form <service-name>.properties. The service name is extracted from the filename and is used to separate the settings for each service in Zookeeper.

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

In Zookeeper that will create a the /app/dev/dep-2/nibblonian node. That node will in turn contain nodes for each configuration setting in the properties file. That means that Zookeeper will have a node created at /app/dev/dep-2/nibblonian/nibblonian.irods.host and the value "lolinternal.somethingorother.org" will be associated with it. Here's the output from the dev Zookeeper cluster:

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

*An important shortcut:* You can load a directory full of properties files like this:

```
clavin props --host 127.0.0.1 --acl <path-to-acls> --dir <path-to-prop-dir> -a <app> -e <env> -d <deployment>
```

All of the files in <path-to-prop-dir> will be processed and loaded into Zookeeper.
