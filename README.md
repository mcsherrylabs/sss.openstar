# sss.asado
Scala Based DLT 

[![Build Status](https://travis-ci.org/mcsherrylabs/sss.asado.svg?branch=master)](https://travis-ci.org/mcsherrylabs/sss.asado)   [![Coverage Status](https://coveralls.io/repos/github/mcsherrylabs/sss.asado/badge.svg?branch=master)](https://coveralls.io/github/mcsherrylabs/sss.asado?branch=master)
    
This codebase is a work in progress containing a modular blockchain, a proof of concept 'mail' service, a client for the mail service and some test rigs.
 
The blockchain is modular in that in currently supports 2 ledgers (a balance ledger and an Identity ledger) and to add new ledgers is very straightforward -> [sss.asado-ledgers](sss.asado-ledgers) 
When new ledgers can be added or removed easily you have total control over whether the blockchain utilises a crypto currency, is tied to bitcoin or any other currency.
    
## Identity 
To connect to the network a node must have an 'identity', this is a simple string used to identity a set of public keys. Node which do not have an entry in the identity ledger or cannot prove they hold a private key paired with one of the identities public keys cannot connect to the network. 
 
## The Message Service
As a proof of concept a messaging service has been implemented in the node moodule. As described elsewhere every node on the network has an identity, the 'nobu' allows messages to be sent from identity A to B. The service node charges a configurable amount 
              
## The 'Blockchain' in brief...   

Nodes have peers, when over half the peers are connected, a leader is elected based on who has the latest transactions. When the other nodes in the cluster are synched transactions are accepted.

Each node (and a node does not have to be part of the cluster) can examine every transaction and verify it, as can each client.
Should the leader drop off, another leader is elected within seconds and the network continues to accept transactions.
When the node returns, it synchs and becomes part of the network.

Clients sending transactions get several 'confirmations'. The first indicates the transaction is saved on the leader node, and each one
after that indicates it is saved on the connected nodes. A tx with 2 or more confirms is irreversible. Tx confirmation times are measured in milliseconds.

The usual blockchain features apply - a chain of block headers, a merkle tree of all transactions in the block, a block containing all the txs. A block is closed after a set time.
 
## Coinbase
Assuming the ledger is configured to support a reward for every block closed new currency comes into existence and is put in the wallet of the network leader closing the block. The amount and strategy is configurable.  
  
## Testing
There are automated tests for the non reactive code and the state machine at the heart of the state transitions.
 
## Thanks

Depends on -> https://github.com/ScorexProject/scrypto

Inspired by Scorex -> https://github.com/ScorexProject/Scorex
