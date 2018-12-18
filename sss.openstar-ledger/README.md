# The Ledgers Module

While all the rest of the code concerns itself with keeping the ledgers in sync across a network and going forward, this module contains the ledgers themselves. 
  
  The currently supported ledgers are the balance ledger and an identity ledger. 
  
  In order for a ledger to be supported it must conform to the Ledger interface as described in the 'common' module.
  
```scala
  trait Ledger {
      @throws[LedgerException]
      def apply(ledgerItem: LedgerItem, blockHeight: Long)
      def coinbase(nodeIdentity: NodeIdentity, blockId: BlockId, ledgerId: Byte): Option[LedgerItem] = None
    }
    
```
 
   For example the IdentityLedger looks like ... 
   
```scala
   class IdentityLedger(ledgerId: Byte, idLedgerStorage: IdentityService) extends  Ledger with Logging {
   
     override def apply(ledgerItem: LedgerItem, blockHeight: Long): Unit = {
       require(ledgerItem.ledgerId == ledgerId, s"The ledger id for this (Identity) ledger is $ledgerId but " +
         s"the ledgerItem passed has an id of ${ledgerItem.ledgerId}")
   
       val ste = ledgerItem.txEntryBytes.toSignedTxEntry
       ...
```

From the point of view of the rest of the system all a ledger has to do is accept a LedgerItem via `apply` and optionally provide a coinbase LedgerItem for each block. If the ledger rejects the LedgerItem it throws an `Exception`.
 
 A LedgerItem itself requires a transaction id and the identifier of the Ledger itself (Balance Ledger is 1 and Identity Ledger is 2)
 It also has an Array of bytes which should mean something to the ledger itself but nothing to the rest of the system.
 
```scala
case class LedgerItem(ledgerId: Byte, txId : TxId, txEntryBytes: Array[Byte])
```

These LedgerItems represent transactions and end up stored as bytes in Blocks once after they have been applied to the ledger itself.
 
## The BalanceLedger
 
The balance ledger is similar to the bitcoin ledger. A pool of transaction outputs exists. A new transaction must have as it's inputs some of these previous outputs. In order to use these outputs successfully the transaction must provide the necessary 'proofs' to unlock the TxOut, otherwise the tx is rejected. Whereas in Bitocin the mechanism for locking outputs and unlocking inputs is the bitcoin scripting language or [script](https://en.bitcoin.it/wiki/Script), in openstar the mechanisms are written in JVM code.  

In the simplest case a TxOut might be locked to a particular public key, an entity trying to use that TxOut must provide proof that they hold the private key paired with the public key. So the txOut is encumbered with a simple contract and to 'decumber' or use the output, proof of holding the private key must be provided. 
     
The balance ledger also has a pool of TxOuts. Each of these is encumbered with a contract. In order to use the txOut to fund another transaction the txOut must be 'decumbered' successfully.

Thus a valid transaction is a set of valid inputs and a set of valid outputs. The TxId identifying the transaction is a hash of all the inputs and outputs. 
   
```scala
trait Tx {
    val ins: Seq[TxInput]
    val outs: Seq[TxOutput]
    val txId: TxId
  }
```  

The set of valid outputs become the inputs of the next tx. To uniquely address the new input from the output of a previous tx we need to know the TxId and the index of the output.

```scala
case class TxIndex(txId: TxId, index: Int) 
``` 
 
A valid input contains an address (TxIndex) and the correct 'decumbrance' for that new input...
 (note currently the amount in the new tx must match the amount in the output of the previous tx)
  
```scala
case class TxInput(txIndex: TxIndex, amount: Int, sig: Decumbrance)
```
An output must contain an amount and a new encumbrance for that output. 
```scala
case class TxOutput(amount: Int, encumbrance: Encumbrance)
```
The total amount in all outputs must equal the total of all the inputs.
 
###Supported Encumbrances
The encumbrances and decumbrances are written in scala code. On the plus side it extremely easy to create new encumbrances, however every new encumbrance requires every node in the network wishing to validate the blockchain must support every new encumbrance. Encumbrances are serialised and stored in the ledger and when they are used in a transaction they are stored in the block. Thus an encumbrance once supported must always be supported. (This inertia might be seen as a good thing (tm)). 
  
```scala
case class SinglePrivateKey(pKey: PublicKey, minBlockHeight: Long = 0) extends Encumbrance
```

This encumbrance decumbers by providing a signature of the current transaction hash. That signature must have been made with the private key  matching the public key provided in the encumbrance. Further the encumbrance cannot be decumbered if the current block height is lower than the minBlockHeight provided in the encumbrance. This block height param allows a balance to be entrusted to a key but prevents them from using it until some block height in the future. 

```scala
case class SingleIdentityEnc(identity: String, minBlockHeight: Long = 0) extends Encumbrance
```
The single identity encumbrance is similar to the single key encumbrance except it uses an identity instead of a key. An identity is a string unique in the identity ledger. It is associated with at least one and potentially several key pairs, such that if the owner of an identity loses a private key, another key associated with the same identity can be used to decumber the output.

```scala
 case class SaleOrReturnSecretEnc(
                    returnIdentity: String,
                    claimant: String,
                    hashOfSecret: HashedSecret,
                    returnBlockHeight: Long
                  ) extends Encumbrance 
```                                          
The 'sale or return' encumbrance enables the MessageInBox functionality described in the 'node' module. The claimant may claim the output if they can provide a secret who's hash has been embedded in the encumbrance. Should a 'returnBlockHeight' be reached the returnIdentity may then claim the output. The idea is to allow the claimant some time to take the money if they provide the secret you have embedded in your message to them.               

###Coinbase
Ledgers may optionally provide a coinbase tx. The balance ledger allows the node to claim a coinbase tx *if* they are the first signer of the block they are claiming the coinbase tx in. The coinbase tx is special in that its inputs are not in the txOut set. This can be turned off.  
 

## The IdentityLedger
Public keys are not an intuitive way to identify a person, group or organisation. And locking value to a single private key puts great pressure on that key. The identity ledger attaches a string value to a key pair and then allows several key pairs to be associated with that identity through the magic of distributed ledgers. 
   
At first an identity is 'claimed', as an example take 'bob', there are many ways to control the 'claim' entry point, but take a simple servlet as a starting point. To claim the identity 'bob', bob must not already be in the identity ledger and a public key must be provided. That key and the identifier are inserted into the ledger and distributed across the nodes. Bob may then sign a tx with the first private key adding a second public key to his identity.
 
```scala
case class Link(identity: String, pKey: PublicKey, tag: String) extends IdentityLedgerMessage(identity) 
 ```
 
These are differentiated by a tag (e.g. 'pc', 'mobile') and bob may then use encumbrances based on his identity, which can be proven by either of the 2 keys. Should Bob lose the first key he may add a third key using the second key. Bob may also create a 'rescuer', this is a second identity linked to Bob's identity entrusted to link a public key to Bob's identity.     

A node may not connect to the network without a valid identity. The mechanics of this are detailed in the 'network' module. The implication is that it is very important to have an up to date blockchain containing the latest identities and keys.   