# The Network Module

The network module handles the comms between nodes at a tcp level, verifying connections, firing events when good connections are made, sending messages out to the network and propagating incoming messages to the listening actors.  

It uses Akka IO and is based on the [Scorex](https://github.com/input-output-hk/Scorex/blob/master/scorex-basics/src/main/scala/scorex/network/) code.  

## Initial Handshake
When negotiating an initial connection a random nonce is chosen and each side sends this as part of the handshake to the other. When the nonce is received each side signs the nonce with their private key and sends back the identity, signed nonce and the tag of the key used to sign. 
   
```scala
case class Handshake(applicationName: String,
                     applicationVersion: ApplicationVersion,
                     fromAddress: String,
                     nodeId: String,
                     tag: String,
                     fromPort: Int,
                     fromNonce: Long,
                     sig: Array[Byte],
                     time: Long
                    )
```   
Each side must verify the signature before accepting the connection. If either side cannot verify the connection, the connection is closed. 
Ideally the identity ledger is used to verify the identity of the connection, however in the case where the node is initialising it must add the public key of a known node to it's configuration in order to bootstrap itself. 
```
bob = {
  nodeId = "bob"
  tag = "defaultTag"
  publicKey = "AF3DFBF8hC86hAD2D6hA549h02DCh5F6h42D0AhC2AD4hF21FAEE15hhh0"
}
bootstrap = [${bob.nodeId}":::"${bob.publicKey} ] 
```
Note, this does _not_ create a secure connection.
    
##The Two Types of Connection
A node has a list of peers which form the core of the newtwork. Membership of that list will be dynamic for a public facing network but for smaller private facing networks the list is hardcoded in the configuration file. These are peer connections. A node needs to have over half of the connections in it's peer list connected before it can accept transactions.

This is the core of the network. If the number of connections drops to half or below the network will stop taking transactions - this is to prevent a ledger fork where 2 networks each consisting of half the core nodes run simultaneously accepting transactions. 
  
The second type of connection are ordinary connections which may come and go without affecting whether the network accepts transactions. 

##More Detail

###The State Controller
The state controller is provided to the NetworkController and in return is provided with Connection events.

```scala
case class PeerConnectionLost(conn: Connection, updatedSet: Set[Connection])
case class PeerConnectionGained(conn: Connection, updatedSet: Set[Connection])
case class ConnectionGained(conn: Connection, updatedSet: Set[Connection])
case class ConnectionLost(conn: Connection,updatedSet: Set[Connection])
```
As mentioned, an ordinary connection differs from a peer connection in how the systems determines the network is up.
 
###The Message Router
All valid messages from other nodes arriving at the connection handler are forwarded to the MessageRouter. The message router then checks a list of ActorRefs registered for that mesage type and forwards as appropriate. 

The implications are

- several actors may receive the same message
- an actor must register for network messages it is interested in
- actors may reply to a network message and expect to reach the remote client the netwrok message came from
- all messages out of the message router have a sender identity in the identity ledger
   
   
   
   
