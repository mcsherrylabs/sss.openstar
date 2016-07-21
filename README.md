# sss.asado
Scala Based DLT 

[![Build Status](https://travis-ci.org/mcsherrylabs/sss.asado.svg?branch=master)](https://travis-ci.org/mcsherrylabs/sss.asado)   [![Coverage Status](https://coveralls.io/repos/github/mcsherrylabs/sss.asado/badge.svg?branch=master)](https://coveralls.io/github/mcsherrylabs/sss.asado?branch=master)
    
This codebase is a work in progress containing a modular blockchain, a proof of concept 'mail' service, a client for the mail service and some test rigs.
 
The blockchain is modular in that in currently supports 2 ledgers (a balance ledger and an Identity ledger) and to add new ledgers is very straightforward -> [sss.asado-ledgers](sss.asado-ledger) 
When new ledgers can be added or removed easily you have total control over whether the blockchain utilises a native crypto currency, is tied to bitcoin or any other currency.

The code base is designed to applicable to multiple scenarios and multiple global blockchains - although in this context 'global' does not mean 7 billion people using it simultaneously.  

## Concensus
There is none

##Overview
 An asado network consists of ledgers, blocks, distribution mechanisms, verification mechanisms and services. 

The blocks record streams of valid transactions in order.

The ledgers (described below) validate transactions allowing them to be either included in blocks or rejected. Being able to validate a transaction means knowing the global state of a ledger. ie if a ledger can validate a transaction containing a particular txOut then it must know the state of a particular txOut.
 
The distribution mechanisms distribute the transactions to other nodes, handle distribution retries.
     
The verification mechanisms - blocks are signed 
    

## The Identity ledger 
The Identity ledger is fundamental to network operation. It is a distributed ledger keeping track of the relationship between simple strings and public keys. Take an identity 'bob', to claim bob in the identity ledger a transaction attaching bob to a public key and tag is sent to the network (a tag simply differentiates public keys e.g. pKey1::pc, pKey2:phone)
   
If the identity has already been claimed, the transaction is rejected, if not, that public key (and tag) is registered to identity bob. This transaction is recorded in the ledger and broadcast to the rest of the network where it forms part of the blockchain. Bob may then add other keys to his identity by sending a new transaction linking his identity with a new key. Note that this transaction is only accepted because it is signed with the private key of the original public key Bob used to claim his identity. Assumming the transaction is valid it is broadcast across the network and then Bob may sign new transactions with the second key and these will also be accepted. He may continue to use his first key or he may decide to unlink his first key. 

This identity bootstrapping is quite a flexible way to provide a spectrum of security. The identity may have a single key associated with it and may live for a single block or may be a persons name, have several keys and be linked to another identity trusted to sign a new key into existence should they lose all their keys.                      
    
And all these decisions are recorded on the blockchain as signed transactions all the way back to the initial claim.

Having an identity is very usefule when you want osend a message ... 
   
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

(Currently a set of peers is formed manually in the config file however a 'trust based dynamic quorum' is coming soon.)

## Coinbase
Assuming the ledger is configured to support a reward for every block closed new currency comes into existence and is put in the wallet of the network leader closing the block. The amount and strategy is configurable.  
  
## Testing
There are automated tests for the non reactive code and the state machine at the heart of the state transitions.
 
## Thanks

Depends on -> https://github.com/ScorexProject/scrypto

Inspired by Scorex -> https://github.com/ScorexProject/Scorex
