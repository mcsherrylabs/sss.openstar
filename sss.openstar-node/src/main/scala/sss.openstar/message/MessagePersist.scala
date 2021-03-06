package sss.openstar.message

import java.util.Date

import com.twitter.util.SynchronizedLruMap
import org.joda.time.LocalDateTime
import sss.openstar.UniqueNodeIdentifier
import sss.db._

/**
  * Created by alan on 6/6/16.
  */
object MessagePersist {

  private val idCol = "id"
  private val metaInfoCol = "meta_info"
  private val fromCol = "from_col"
  private val statusCol = "status_col"
  private val messageCol = "message"
  private val txCol = "tx_col"
  private val createdAtCol = "created_at"

  private val statusPending  = 0
  private val statusAccepted = 1
  private val statusRejected = 2

  private val messageTableNamePrefix = "message_"
  private def makeMessageTableName(identity: UniqueNodeIdentifier): String = messageTableNamePrefix + identity.toLowerCase
  private lazy val tableCache = new SynchronizedLruMap[UniqueNodeIdentifier, MessagePersist](500)


  def apply(identity: UniqueNodeIdentifier)(implicit db:Db): MessagePersist = {
    fromName(identity)
  }

  private def fromName(identity: UniqueNodeIdentifier)(implicit db:Db): MessagePersist =
    tableCache.getOrElseUpdate(identity, new MessagePersist(identity))


}

class MessagePersist(identity: UniqueNodeIdentifier)(implicit val db: Db) {

  import MessagePersist._

  val tableName = makeMessageTableName(identity)

  db.executeSql (s"CREATE TABLE IF NOT EXISTS $tableName (" +
    s"$idCol BIGINT GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1), " +
    s"$fromCol VARCHAR(100), " +
    s"$statusCol INT, " +
    s"$metaInfoCol VARCHAR(100), " +
    s"$messageCol BLOB, " +
    s"$txCol BLOB, " +
    s"$createdAtCol BIGINT, " +
    s"PRIMARY KEY($idCol));")

  lazy private val table = db.table(tableName)


  private def toMsg(r:Row): Message = Message(
    identity,
    r[String](fromCol),
     r[Array[Byte]](messageCol).toMessagePayload,
    r[Array[Byte]](txCol),
    r[Long](idCol),
    new LocalDateTime(r[Long](createdAtCol)))


  def pending(from: String, msgPayload: MessagePayload, tx: Array[Byte]): Long = {
    val row = table.insert(Map(
      metaInfoCol -> None,
      fromCol -> from,
      messageCol -> msgPayload.toBytes,
      statusCol -> statusPending,
      txCol -> tx,
      createdAtCol -> new Date().getTime))
    row[Long](idCol)
  }

  def accept(index: Long): Unit = {
    table.update(Map(statusCol -> statusAccepted, idCol -> index))
  }

  def reject(index: Long): Boolean = table.delete(where(s"$idCol = ?") using (index)) == 1

  def page(lastReadindex: Long, pageSize: Int): Seq[Message] = {
    table.filter(
      where(s"$idCol > ? AND $statusCol = $statusAccepted ORDER BY $idCol ASC LIMIT $pageSize")
        using(lastReadindex)).map(toMsg)
  }

  def delete(index: Long): Boolean = table.delete(where(s"$idCol = ?") using(index)) == 1

  def maxIndex: Long = table.maxId

}