package com.github.hibou107.migration

sealed trait MutablePath

case class FieldPath(path: String) extends MutablePath

case class RecurFieldPath(path: String) extends MutablePath

sealed trait FieldCond extends MutablePath
case class HasField(path: String) extends FieldCond
case class AndField(left: HasField, right: HasField) extends FieldCond
case class OrField(left: HasField, right: HasField) extends FieldCond

case class RecurFieldCond(cond: FieldCond) extends MutablePath

object PathResolver {

  def checkCond(cond: FieldCond, value: JsValueWrapper): Boolean = (cond, value) match {
    case (HasField(path), v)        => v.map.contains(path)
    case (AndField(left, right), v) => checkCond(left, v) && checkCond(right, v)
    case (OrField(left, right), v)  => checkCond(left, v) || checkCond(right, v)
    case _                          => false
  }

  def searchOnePath(input: JsValueWrapper, path: MutablePath): List[JsValueWrapper] = {
    (input, path) match {
      case (JsObjectWrapper(value), FieldPath(p)) =>
        List(value(p))

      case (JsArrayWrapper(value), recursivePath @ RecurFieldPath(p)) =>
        value.flatMap(searchOnePath(_, recursivePath)).toList

      case (v @ JsObjectWrapper(value), recurFieldPath @ RecurFieldPath(p)) =>
        if (value.keySet contains p) List(v) else value.values.toList.flatMap(searchOnePath(_, recurFieldPath))

      case (v @ JsObjectWrapper(_), cond: FieldCond) => if (checkCond(cond, v)) List(v) else List.empty

      case (v @ JsObjectWrapper(value), p @ RecurFieldCond(cond)) =>
        val found = if (checkCond(cond, v)) List(v) else Nil
        found ++ value.values.toList.flatMap(searchOnePath(_, p))

      case (v @ JsArrayWrapper(value), p @ RecurFieldCond(_)) =>
        value.toList.flatMap(searchOnePath(_, p))

      case (_, _) => List.empty

    }
  }

  def searchMultiPath(input: JsValueWrapper, paths: List[MutablePath]): List[JsValueWrapper] = {
    if (paths.isEmpty)
      input :: Nil
    else {
      paths.tail.foldLeft(searchOnePath(input, paths.head)) { (current, path) =>
        current.flatMap(searchOnePath(_, path))
      }
    }
  }

  def migrate(input: JsValueWrapper, paths: List[MutablePath])(f: JsValueWrapper => Unit): Unit = {
    searchMultiPath(input, paths).foreach(f)
  }

}