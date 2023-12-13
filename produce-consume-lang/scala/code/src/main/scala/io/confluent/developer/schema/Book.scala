package io.confluent.developer.schema

case class Book(author: String, title: String, `type`: BookType, pages: Int, releaseDate: java.time.LocalDate)
