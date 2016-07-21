# The Node Module

This module can be used as a jar while developing clients or as an entry point when running service or core nodes.

## Overview of network node operation

A core node participates in keeping the ledger distributed and processing transactions. In an example of 5 core nodes each node participates in the election of a leader, this leader processes all transactions for the ledger. Should this leader become unavailable, a new leader is elected from the remaining nodes. Should the number of peer connected nodes in our example fall below 3, the network stops processing transactions. 
  
A core node who is not the leader still accepts transactions and forwards those to the leader, it returns the leaders response to it's client acting as a proxy. It also serves to confirm transactions for the leader such that when leader accepts a transaction into the ledger it sends that transaction to every connected node and forwards the confirmation to the client. In our example a client should expect to receive a 'tx accept' (or reject) followed by 2 'tx confirms'. At that point the network cannot refute the transaction. A node also signs blocks, the leader provides the first signature and when the block closes (a configurable timeout) each connected node signs that block and sends the signature to the leader which is then redistributed to every node such that each node signs the block and has a copy of every other nodes block signature.   
    
A services node includes all a core node does but also includes sundry services such as the Message service and the Claims servlet. A services node may or may not be configured to be part of the core network.
      
A client node connects to either a services or core node directly. This is known as it's 'home' node. From there it receives the blockchain and to there it sends transactions.
     
All nodes contain their own wallet associated with their identity. Each node has an identity as defined by the Identity ledger (see the ledger module)
     
##Docker

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
docker run --name="$TARGET" --entrypoint=${!KIND}  --net="host" -i -t $VOL sss-asado-node:0.2.11-SNAPSHOT $TARGET
```

Assuming bob is configured in the nodes.conf - call it for a service node named 'bob' as follows 
```bash
. run_nodes bob SERVICE
```
Assuming alice is configured in the nodes.conf - call it for a core node named 'alice' as follows 
```bash
. run_nodes alice CORE
```
  
     
   



