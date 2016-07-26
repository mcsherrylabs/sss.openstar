# WIP 

## The Node Module

This module can be used as a jar while developing clients or as an entry point when running service or core nodes.

###Docker

To create a docker image ...

```bash
sbt docker:publishLocal
```
This script helps run nodes ... 

```bash
TARGET=$1
CORE="/opt/docker/bin/core_node"
SERVICE="/opt/docker/bin/service_node"
KIND=$2

EXT_VOL="/extra"
VOL="-v $EXT_VOL/memento:/opt/docker/memento"
VOL="$VOL -v $EXT_VOL/conf:/opt/docker/conf"
VOL="$VOL -v $EXT_VOL/data:/opt/docker/data"
docker run --name="$TARGET" --entrypoint=${!KIND}  --net="host" -d $VOL sss-asado-node:0.2.11-SNAPSHOT $TARGET
```

Assuming bob is configured in the nodes.conf - call it for a service node named 'bob' as follows 
```bash
. run_nodes bob SERVICE
```
Assuming alice is configured in the nodes.conf - call it for a core node named 'alice' as follows 
```bash
. run_nodes alice CORE
```
  
Extra Volume growth rate - 500M/day
Using -i -t causes memory growth, where to enter the password?
Will run in about 1G RAM

 
   



