package sss.openstar.message

import java.util.Date

import org.joda.time.LocalDateTime
import sss.ancillary.Logging
import sss.openstar.UniqueNodeIdentifier
import sss.openstar.ledger._
import sss.db._

import scala.util.{Failure, Success, Try}



object MessageInBox {
  private val idCol = "id"
  private val fromCol = "from_col"
  private val toCol = "to_col"
  private val statusCol = "status_col"
  private val txCol = "tx_col"
  private val messageCol = "message"
  private val createdAtCol = "created_at"

  private val statusNew = 0
  private val statusArchived = 1
  private val statusSentConfirmed = 2
  private val statusDeleted = 3
  private val statusJunk = 4

  private val messageTableNamePrefix = "message_"

  def apply(identity: UniqueNodeIdentifier)(implicit db:Db): MessageInBox = new MessageInBox(identity)


  class MessagePage[M](page: Page, f: Row => M) {
    lazy val hasNext: Boolean = page.hasNext
    lazy val hasPrev: Boolean = page.hasPrev
    val messages: Seq[M] = page.rows map (f)
    lazy val next: MessagePage[M] = new MessagePage[M](page.next, f)
    lazy val prev: MessagePage[M] = new MessagePage[M](page.prev, f)
  }
}

class MessageInBox(id: UniqueNodeIdentifier)(implicit val db: Db) extends Logging {


  import MessageInBox._

  private val tableName = s"${messageTableNamePrefix}${id}"
  private val sentTableName =  s"${tableName}_sent"

  db.executeSql (s"CREATE TABLE IF NOT EXISTS $sentTableName (" +
    s"$idCol BIGINT GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1), " +
    s"$toCol VARCHAR(100), " +
    s"$statusCol INT, " +
    s"$txCol BLOB, " +
    s"$messageCol BLOB, " +
    s"$createdAtCol BIGINT, " +
    s"PRIMARY KEY($idCol));")

  db.executeSql (s"CREATE TABLE IF NOT EXISTS $tableName (" +
    s"$idCol BIGINT GENERATED BY DEFAULT AS IDENTITY (START WITH 1, INCREMENT BY 1), " +
    s"$fromCol VARCHAR(100), " +
    s"$statusCol INT, " +
    s"$txCol BLOB, " +
    s"$messageCol BLOB, " +
    s"$createdAtCol BIGINT, " +
    s"PRIMARY KEY($idCol));")

  lazy private val table = db.table(tableName)
  lazy private val sentTable = db.table(sentTableName)


  private def toMsg(r:Row): Message = Message(
    id,
    r[String](fromCol),
    r[Array[Byte]](messageCol).toMessagePayload,
    r[Array[Byte]](txCol),
    r[Long](idCol),
    new LocalDateTime(r[Long](createdAtCol)))

  private def toAddressedMsg(r:Row): AddressedMessage = AddressedMessage(
    id,
    r[Array[Byte]](txCol).toLedgerItem,
    r[Array[Byte]](messageCol).toMessagePayload)

  private def toSavedAddressedMsg(r:Row): SavedAddressedMessage = SavedAddressedMessage(
    r[String](toCol),
    r[Long](idCol),
    new LocalDateTime(r[Long](createdAtCol)),
    toAddressedMsg(r))

  def addNew(msg: Message): Message = add(msg, statusNew)
  def addJunk(msg: Message): Message = add(msg, statusJunk)

  private def add(msg: Message, status: Int): Message = table.tx {

    val bs = msg.msgPayload.toBytes

    val mp = Map(
      idCol -> msg.index,
      fromCol -> msg.from,
      statusCol -> status,
      messageCol -> bs,
      txCol -> msg.tx,
      createdAtCol -> msg.createdAt.toDate.getTime)

    Try(table.insert(mp)) match {
      case Failure(e) =>
        // assume another session is also writing to the db
        log.warn(s"Couldn't insert message index ${msg.index} into mailbox for ${id}")
        log.warn(e.toString)
        val r = table(msg.index)
        require(msg.createdAt.toDate.getTime == r[Long](createdAtCol),
          s"2 messages have the same index but different dates(${msg.index})")
        toMsg(r)
      case Success(r) => toMsg(r)
    }
  }

  def addSent(to: UniqueNodeIdentifier, msgPayload: MessagePayload, txBytes: Array[Byte]): SavedAddressedMessage = {
    toSavedAddressedMsg(sentTable.insert(Map(
      toCol -> to,
      statusCol -> statusNew,
      messageCol -> msgPayload.toBytes,
      txCol -> txBytes,
      createdAtCol -> new Date().getTime)))
  }

  def pageSent(lastReadindex: Long, pageSize: Int): Seq[AddressedMessage] = {

    sentTable.filter(

      where(s"$idCol > ? AND $statusCol = ?)", lastReadindex, statusNew)
        .orderBy(OrderAsc(idCol))
          .limit(pageSize)

    ).map(toAddressedMsg)
  }

  def sentPager(pageSize: Int) =
    new MessagePage(PagedView(sentTable, pageSize, where (statusCol -> statusNew)).lastPage, toSavedAddressedMsg)

  def inBoxPager(pageSize: Int) =
    new MessagePage(PagedView(table, pageSize, where(statusCol -> statusNew)).lastPage, toMsg)

  def archivedPager(pageSize: Int) =
    new MessagePage(PagedView(table, pageSize, where(statusCol -> statusArchived)).lastPage, toMsg)

  def junkPager(pageSize: Int) =
    new MessagePage(PagedView(table, pageSize, where(statusCol -> statusJunk)).lastPage, toMsg)

  def archive(index: Long) = table.update(Map(idCol ->  index, statusCol -> statusArchived))

  def deleteSent(index: Long): Boolean = sentTable.delete(where(s"$idCol = ?") using (index)) == 1

  def delete(index: Long) = table.update(Map(idCol ->  index, statusCol -> statusDeleted, messageCol -> None))

  def maxInIndex = table.maxId
}