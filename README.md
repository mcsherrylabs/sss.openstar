# sss.asado
Scala Based DLT 

[![Build Status](https://travis-ci.org/mcsherrylabs/sss.asado.svg?branch=master)](https://travis-ci.org/mcsherrylabs/sss.asado)   [![Coverage Status](https://coveralls.io/repos/github/mcsherrylabs/sss.asado/badge.svg?branch=master)](https://coveralls.io/github/mcsherrylabs/sss.asado?branch=master)

## TL;DR 

Nodes have peers, when over half the peers are connected, a leader is elected based on who has the latest transactions.When the other nodes in the cluster are synched transactions are accepted.

Each node (and a node does not have to be part of the cluster) can examine every transaction and verify it, as can each client.
Should the leader drop off, another leader is elected within seconds and the network continues to accept transactions.
When the node returns, it synchs and becomes part of the network.

Clients sending transactions get several 'confirmations'. The first indicates the transaction is saved on the leader node, and each one
after that indicates it is saved on the connected nodes. A tx with 2 or more confirms is irreversible. Tx confirmation times are measured in milliseconds.

The usual blockchain features apply - a chain of block headers, a merkle tree of all transactions in the block, a block containing all the txs. A block is closed after a set time.

## Testing
There are automated tests for the non reactive code and the state machine at the heart of the state transitions.
 
## Thanks

Depends on -> https://github.com/ScorexProject/scrypto

Inspired by Scorex -> https://github.com/ScorexProject/Scorex
