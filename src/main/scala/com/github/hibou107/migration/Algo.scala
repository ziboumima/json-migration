package com.github.hibou107.migration

import play.api.libs.json._

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scalaz.Monoid
import scala.language.implicitConversions

sealed trait JsValueWrapper

case class JsObjectWrapper(value: collection.mutable.Map[String, JsValueWrapper]) extends JsValueWrapper
case class JsStringWrapper(value: String) extends JsValueWrapper
case class JsArrayWrapper(value: ArrayBuffer[JsValueWrapper]) extends JsValueWrapper
case class JsBooleanWrapper(value: Boolean) extends JsValueWrapper
case class JsNumberWrapper(value: BigDecimal) extends JsValueWrapper
case object JsNUllWrapper extends JsValueWrapper

trait JsValueWrapperImplicits {
  implicit def fromInt(input: Int): JsValueWrapper = JsNumberWrapper(input)
  implicit def fromLong(input: Long): JsValueWrapper = JsNumberWrapper(input)
  implicit def fromDouble(input: Double): JsValueWrapper = JsNumberWrapper(input)
  implicit def fromString(input: String): JsValueWrapper = JsStringWrapper(input)
  implicit def fromBoolean(input: Boolean): JsValueWrapper = JsBooleanWrapper(input)
  implicit def fromJson(input: JsValue): JsValueWrapper = JsValueWrapper.create(input)
  implicit class fromJsonWrapper(input: JsValue) {
    def wrapped: JsValueWrapper = JsValueWrapper.create(input)
  }
}

object JsValueWrapperImplicits extends JsValueWrapperImplicits

object JsValueWrapper {

  implicit class JsObjectWrapperConverter(input: JsValueWrapper) {
    def apply(field: String): JsValueWrapper = input.asInstanceOf[JsObjectWrapper].value(field)
    def number: BigDecimal = input.asInstanceOf[JsNumberWrapper].value
    def map: mutable.Map[String, JsValueWrapper] = input.asInstanceOf[JsObjectWrapper].value
    def setDefault(field: String, value: JsValueWrapper): Unit = {
      if (!has(field))
        input.asInstanceOf[JsObjectWrapper].map.update(field, value)
    }
    def has(field: String): Boolean = input.asInstanceOf[JsObjectWrapper].value.keySet.contains(field)
    def remove(field: String): Option[JsValueWrapper] = {
      input.asInstanceOf[JsObjectWrapper].map.remove(field)
    }
    def updatedMap(field: String, value: JsValueWrapper) = JsObjectWrapper(input.asInstanceOf[JsObjectWrapper].value.updated(field, value))
    def string: String = input.asInstanceOf[JsStringWrapper].value
    def array: ArrayBuffer[JsValueWrapper] = input.asInstanceOf[JsArrayWrapper].value
    def toJson: JsValue = JsValueWrapper.toJson(input)

    def keyValues(): Map[JsValueWrapper, JsValueWrapper] = input.array.map { obj =>
      val key = obj.map("key")
      val value = obj.map("value")
      (key, value)
    }.toMap
  }

  implicit def create(input: JsValue): JsValueWrapper =
    input match {
      case x: JsObject    => JsObjectWrapper(collection.mutable.Map(x.value.mapValues(create).toSeq: _*))
      case x: JsArray     => JsArrayWrapper(ArrayBuffer(x.value: _*).map(create))
      case x: JsString    => JsStringWrapper(x.value)
      case x: JsBoolean   => JsBooleanWrapper(x.value)
      case x: JsNumber    => JsNumberWrapper(x.value)
      case JsNull         => JsNUllWrapper
    }

  implicit def toJson(input: JsValueWrapper): JsValue = {
    input match {
      case x: JsObjectWrapper    => JsObject(x.value.map { case (name, value) => (name, toJson(value)) }.toSeq)
      case x: JsArrayWrapper     => JsArray(x.value.map(toJson))
      case x: JsStringWrapper    => JsString(x.value)
      case x: JsBooleanWrapper   => JsBoolean(x.value)
      case x: JsNumberWrapper    => JsNumber(x.value)
      case JsNUllWrapper         => JsNull
    }
  }
}

trait JsonMigrator {
  def migrate(input: JsValueWrapper): Unit
  def transform(input: JsValueWrapper): JsValueWrapper = {
    migrate(input)
    input
  }
}

object JsonMigrator {
  def apply(f: JsValueWrapper => Unit): JsonMigrator = (input: JsValueWrapper) => f(input)

  implicit val monoid: Monoid[JsonMigrator] {
    def zero: JsonMigrator

    def append(f1: JsonMigrator, f2: => JsonMigrator): JsonMigrator
  } = new Monoid[JsonMigrator] {

    def zero: JsonMigrator = (_: JsValueWrapper) => ()

    def append(f1: JsonMigrator, f2: => JsonMigrator): JsonMigrator = {
      (input: JsValueWrapper) => {
        f1.migrate(input)
        f2.migrate(input)
      }
    }
  }
}
