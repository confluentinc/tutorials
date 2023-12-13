package io.confluent.developer

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.sksamuel.avro4s.AvroSchema
import kantan.csv.RowDecoder
import kantan.csv.enumeratum._
import kantan.csv.java8._
import org.apache.avro.Schema
import upickle.default
import upickle.default.{ReadWriter => JsonDecoder, macroRW => JsonMacro, _}

package object schema {

  implicit lazy val BookTypeAvroSchema: Schema = AvroSchema[BookType]

  implicit lazy val BookTypeJsonDecoder: JsonDecoder[BookType] = JsonMacro

  implicit lazy val BookAvroSchema: Schema = AvroSchema[Book]

  implicit lazy val BookJsonDecoder: JsonDecoder[Book] = JsonMacro

  implicit lazy val CsvDecoder: RowDecoder[Book] = RowDecoder.ordered(
    (author: String, title: String, `type`: BookType, pages: Int, releaseDate: java.time.LocalDate) =>
      Book(noLineBreak(author), noLineBreak(title), `type`, pages, releaseDate)
  )

  implicit val LocalDateJson: default.ReadWriter[LocalDate] = readwriter[ujson.Value].bimap[LocalDate](
    date => date.format(DateTimeFormatter.BASIC_ISO_DATE),
    json => LocalDate.parse(json.str)
  )

  def noLineBreak(line: String): String = line.replace(util.Properties.lineSeparator, " ")
}
