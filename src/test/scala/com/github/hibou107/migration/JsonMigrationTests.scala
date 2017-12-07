package com.github.hibou107.migration
import org.scalatest.{FlatSpec, Matchers}
import play.api.libs.json.Json

import scalaz.syntax.foldable._
import scalaz.std.list._


class JsonMigrationTests extends  FlatSpec with JsValueWrapperImplicits with Matchers {
  private val json = Json.parse(
    """
      |{
      |  "field1" : {
      |   "field11": 100
      |  },
      |  "field2": [
      |   {
      |     "sField": "Do it"
      |   },
      |   {
      |     "sField": "good",
      |     "s1": "fine"
      |   }
      |  ],
      |  "field3": {
      |   "sField": "nice"
      |  }
      |}
    """.stripMargin
  )

  // remove field field1/field11
  private val migrator1 = new JsonMigrator() {
    def migrate(x: JsValueWrapper): Unit = {
      x("field1").map.remove("field11")
      ()
    }
  }

  // add new field field1/field12
  private val migrator2 = new JsonMigrator {
    def migrate(input: JsValueWrapper): Unit =
      input("field1").map.update("field12", "myNewField")
  }

  //  Change all sFields to "hahaha"
  private val migrator3 = new JsonMigrator {
    def migrate(input: JsValueWrapper): Unit =
      PathResolver.migrate(input, List(RecurFieldCond(HasField("sField")))) { w =>
        w.map.update("sField", JsStringWrapper("hahaha"))
      }
  }

  // add field "field"

  it should "do the job" in {
    val jsonResult = Json.parse(
      """
        |{
        |  "field1" : {
        |   "field12": "myNewField"
        |  },
        |  "field2": [
        |   {
        |     "sField": "hahaha"
        |   },
        |   {
        |     "sField": "hahaha",
        |     "s1": "fine"
        |   }
        |  ],
        |  "field3": {
        |   "sField": "hahaha"
        |  }
        |}
      """.stripMargin
    )

    val allMigrator = List(migrator1, migrator2, migrator3).suml
    val x = JsValueWrapper.create(json)
    allMigrator.migrate(x)
    JsValueWrapper.toJson(x) shouldBe jsonResult
  }
}
