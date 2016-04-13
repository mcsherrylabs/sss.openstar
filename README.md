# sss.asado
A Scala Based Distributed Ledger

[![Build Status](https://travis-ci.org/mcsherrylabs/sss.asado.svg?branch=master)](https://travis-ci.org/mcsherrylabs/sss.asado)   [![Coverage Status](https://coveralls.io/repos/github/mcsherrylabs/sss.asado/badge.svg?branch=master)](https://coveralls.io/github/mcsherrylabs/sss.asado?branch=master)

## A functioning blockchain with near instant confirmation.

Consensus? What consensus? This is a centralised but replicated network ledger with cooperating nodes. There is no spoon.

Nodes have peers, when over half the peers are connected, a leader is elected based on who has the latest transactions.When the other nodes in the cluster are synched transactions are accepted.

Each node (and a node does not have to be part of the cluster) can examine every transaction and verify it, as can each client.
Should the leader drop off, another leader is elected within seconds and the network continues to accept transactions.
When the node returns, it synchs and becomes part of the network.

Clients sending transactions get several 'confirmations'. The first indicates the transaction is saved on the leader node, and each one
after that indicates it is saved on the connected nodes. A tx with 2 or more confirms is irreversible. Tx confirmation times are measured in milliseconds.

The usual blockchain features apply - a chain of block headers, a merkle tree of all transactions in the block, a block containing all the txs. A block is closed after a set time.


## Genesis

To initialize the chain the genesis block and initial tx must be created, run this for every node in the cluster - 
 
    sbt "run-main sss.asado.tools.BlockChainTool <name-of-node-as-per-nodes.conf> init"

The the nodes wil start up and sync, run this for every node in the cluster - 

    sbt "run <name-of-node>"
 
 
### Test Client
 
    sbt "run-main sss.asado.tools.TxClient <name-of-client-as-per-nodes.conf> <TxId> <TxIndex> <amount-to-transfer>"
    
 ...so for example ...
    
    sbt "run-main sss.asado.tools.TxClient karl 47454E495345533147454E495345533147454E495345533147454E4953455331 0 10" 
 
 This client will continuously transfer 10 units to its own 'account' with each transfer being triggered by the previous transfers' confirmation.
 Running 3 nodes plus the client on a laptop with an i5 and 8G RAM gives approx. 60 tps. That's nice. 
    
## Coinbase tx
There is none, yet. 

## Testing
There are automated tests for the non reactive code and the state machine at the heart of the state transitions, but that's all so far.
 
## Thanks

Depends on -> https://github.com/ScorexProject/scrypto

Inspired by Scorex (although has nothing in common now) -> https://github.com/ScorexProject/Scorex
