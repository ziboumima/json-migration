# json-migration
This project tries to help building a json migration framework in the same way of a database migration tool.

The `json` libary used in this example is `play-json`

The aim of this project is to make the migration script easy to write by people who does not have much experience in
functional programming. Even experienced programmers can stuck with the `Coast to coast design` 
[Coast to coast design  ](https://www.playframework.com/documentation/2.6.x/ScalaJsonTransformers)

This comes with a cost because it's not type safe: if the user wants to update a field which is a string but it's an object
in realty, then an `Exception` is throw. Remember, exception can be throwed anywhere inside the migration script.

In a real world project, the users should backup their databases before applying any migrations

For the moment, I did not make it available to import from `sbt`. If you want to integrate into your project, just copy 
the files and add the dependencies.

```
libraryDependencies += "org.scalaz" %% "scalaz-core" % "7.2.17"
libraryDependencies += "com.typesafe.play" %% "play-json" % "2.6.7"
libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.4" % "test"
``` 

If you've found it useful, please let me know

# How to use

Suppose we want have a json value:

```json
    
    {"field1" : {
         "field11": 100
        },
        "field2": [
         {
           "sField": "Do it"
         },
         {
           "sField": "good",
           "s1": "fine"
         }
        ],
        "field3": {
         "sField": "nice"
        }
      }
```

We want to apply a list of transformation to this json value:

    1. Remove the `field1` / `field11`
    2. Add the field `field1` / 'field12' with the value `myNewField`
    3. For every field `sField`, replace all the values by `hahaha`. The field `sField` is in multiple place: 
    inside an array of `field2` and inside `field3`
    
The result we want to see is:
```json
{
  "field1" : {
   "field12": "myNewField"
  },
  "field2": [
   {
     "sField": "hahaha"
   },
   {
     "sField": "hahaha",
     "s1": "fine"
   }
  ],
  "field3": {
   "sField": "hahaha"
  }
}
```


## Create a list of migrators

The library provides a nice way to describe easily:

Extends the trait `JsValueWrapperImplicits` to have automatic conversion between Json and the mutable wrapper

Create a new `JsonMigrator` that defines a function `migrate`. This function takes a `JsValueWrapper` which is
a mutable value

### first migration

```scala
private val migrator1 = new JsonMigrator() {
    def migrate(x: JsValueWrapper): Unit = {
      x("field1").map.remove("field11")
      ()
    }
  }
``` 

    * `x("field1")` means I know x is a JsObject and I access the field `field1`
    * `y.map.remove("field11")` means I know y is a JsObject and I remove the field `field11`
    
### second migration

```scala
  // add new field field1/field12
private val migrator2 = new JsonMigrator {
def migrate(input: JsValueWrapper): Unit =
  input("field1").map.update("field12", "myNewField")
}
```

### third migration
```scala
  //  Change all sFields to "hahaha"
private val migrator3 = new JsonMigrator {
def migrate(input: JsValueWrapper): Unit =
  PathResolver.migrate(input, List(RecurFieldCond(HasField("sField")))) { w =>
    w.map.update("sField", JsStringWrapper("hahaha"))
  }
}
```

This migration is a little more tricky. Inside the function `migrate` we use a helper `PathResolver.migrate` that
takes the first input as `JsValueWrapper` and a list of `FieldCond`. `RecurFieldCond(HasField("sField"))` means find all
the `Jsvalue` that is an `JsObject` and has the field `sField`. The second argument is a function that makes the 
change in that value. Note that the `w` object is the found object and not the the value of the field `sField`

Once we have the list of migrators, we can combine these to make a global json migrator

```scala
import scalaz.syntax.foldable._
import scalaz.std.list._
val globalMigrator = List(migrator1, migrator2, migrator3).suml
```

Note that you need to import `scalaz` syntax in order to use the `suml` utilities


## Migration

Now when the global `migrator` is created, the only thing to do is to migrate the original json:

```scala
val x = JsValueWrapper.create(json) // first we need to create a mutable version of the original json
allMigrator.migrate(x) // then mutate it by applying the global migration
JsValueWrapper.toJson(x) shouldBe jsonResult // the result must be identical to the desired result
```

