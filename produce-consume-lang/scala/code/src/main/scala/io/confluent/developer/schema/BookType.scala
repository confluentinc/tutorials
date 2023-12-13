package io.confluent.developer.schema

import enumeratum._

sealed trait BookType extends EnumEntry

object BookType extends Enum[BookType] {

  case object Tech extends BookType
  case object Comic extends BookType
  case object Novel extends BookType
  case object Romance extends BookType
  case object Other extends BookType

  override def values: IndexedSeq[BookType] = Vector(Tech, Comic, Novel, Romance, Other)
}
