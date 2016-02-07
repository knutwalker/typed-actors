/*
 * Copyright 2015 – 2016 Paul Horn
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.knutwalker.union

import de.knutwalker.union._
import de.knutwalker.TripleArrow

import org.specs2.mutable.Specification
import shapeless.test.illTyped


object UnionTypeClassesSpec extends Specification with TripleArrow {

  type IS = Int | String

  type ISB = IS | Boolean
  type BIS = Boolean | IS

  type ISBL = ISB | Long
  type LISB = Long | ISB
  type BISL = BIS | Long
  type LBIS = Long | BIS

  "is part of (positive)" should {

    "infer IS" >>> {
      implicitly[Int isPartOf IS]
      implicitly[String isPartOf IS]
    }

    "infer ISB" >>> {
      implicitly[Int isPartOf ISB]
      implicitly[String isPartOf ISB]
      implicitly[Boolean isPartOf ISB]
    }

    "infer BIS" >>> {
      implicitly[Int isPartOf BIS]
      implicitly[String isPartOf BIS]
      implicitly[Boolean isPartOf BIS]
    }

    "infer ISBL" >>> {
      implicitly[Int isPartOf ISBL]
      implicitly[String isPartOf ISBL]
      implicitly[Boolean isPartOf ISBL]
      implicitly[Long isPartOf ISBL]
    }

    "infer LISB" >>> {
      implicitly[Int isPartOf LISB]
      implicitly[String isPartOf LISB]
      implicitly[Boolean isPartOf LISB]
      implicitly[Long isPartOf LISB]
    }

    "infer BISL" >>> {
      implicitly[Int isPartOf BISL]
      implicitly[String isPartOf BISL]
      implicitly[Boolean isPartOf BISL]
      implicitly[Long isPartOf BISL]
    }

    "infer LBIS" >>> {
      implicitly[Int isPartOf LBIS]
      implicitly[String isPartOf LBIS]
      implicitly[Boolean isPartOf LBIS]
      implicitly[Long isPartOf LBIS]
    }
  }

  "is part of (negative)" should {

    "not infer IS" >>> {
      illTyped("implicitly[Float isPartOf IS]", "Float is not a member of .Int \\| String..")
    }

    "not infer ISB" >>> {
      illTyped("implicitly[Float isPartOf ISB]", "Float is not a member of .Int \\| String \\| Boolean..")
    }

    "not infer BIS" >>> {
      illTyped("implicitly[Float isPartOf BIS]", "Float is not a member of .Boolean \\| Int \\| String..")
    }

    "not infer ISBL" >>> {
      illTyped("implicitly[Float isPartOf ISBL]", "Float is not a member of .Int \\| String \\| Boolean \\| Long..")
    }

    "not infer LISB" >>> {
      illTyped("implicitly[Float isPartOf LISB]", "Float is not a member of .Long \\| Int \\| String \\| Boolean..")
    }

    "not infer BISL" >>> {
      illTyped("implicitly[Float isPartOf BISL]", "Float is not a member of .Boolean \\| Int \\| String \\| Long..")
    }

    "not infer LBIS" >>> {
      illTyped("implicitly[Float isPartOf LBIS]", "Float is not a member of .Long \\| Boolean \\| Int \\| String..")
    }
  }

  "contains some of (positive)" should {

    "infer IS" >>> {
      implicitly[(Int | String) containsSomeOf IS]
      implicitly[(String | Int) containsSomeOf IS]
    }

    "infer ISB" >>> {
      implicitly[(Int | String) containsSomeOf ISB]
      implicitly[(String | Int) containsSomeOf ISB]
      implicitly[(Int | Boolean) containsSomeOf ISB]
      implicitly[(Boolean | Int) containsSomeOf ISB]
      implicitly[(String | Boolean) containsSomeOf ISB]
      implicitly[(Boolean | String) containsSomeOf ISB]
      implicitly[(Int | String | Boolean) containsSomeOf ISB]
      implicitly[(Int | Boolean | String) containsSomeOf ISB]
      implicitly[(String | Int | Boolean) containsSomeOf ISB]
      implicitly[(String | Boolean | Int) containsSomeOf ISB]
      implicitly[(Boolean | Int | String) containsSomeOf ISB]
      implicitly[(Boolean | String | Int) containsSomeOf ISB]
      implicitly[(Int | (String | Boolean)) containsSomeOf ISB]
      implicitly[(Int | (Boolean | String)) containsSomeOf ISB]
      implicitly[(String | (Int | Boolean)) containsSomeOf ISB]
      implicitly[(String | (Boolean | Int)) containsSomeOf ISB]
      implicitly[(Boolean | (Int | String)) containsSomeOf ISB]
      implicitly[(Boolean | (String | Int)) containsSomeOf ISB]
    }

    "infer BIS" >>> {
      implicitly[(Int | String) containsSomeOf BIS]
      implicitly[(String | Int) containsSomeOf BIS]
      implicitly[(Int | Boolean) containsSomeOf BIS]
      implicitly[(Boolean | Int) containsSomeOf BIS]
      implicitly[(String | Boolean) containsSomeOf BIS]
      implicitly[(Boolean | String) containsSomeOf BIS]
      implicitly[(Int | String | Boolean) containsSomeOf BIS]
      implicitly[(Int | Boolean | String) containsSomeOf BIS]
      implicitly[(String | Int | Boolean) containsSomeOf BIS]
      implicitly[(String | Boolean | Int) containsSomeOf BIS]
      implicitly[(Boolean | Int | String) containsSomeOf BIS]
      implicitly[(Boolean | String | Int) containsSomeOf BIS]
      implicitly[(Int | (String | Boolean)) containsSomeOf BIS]
      implicitly[(Int | (Boolean | String)) containsSomeOf BIS]
      implicitly[(String | (Int | Boolean)) containsSomeOf BIS]
      implicitly[(String | (Boolean | Int)) containsSomeOf BIS]
      implicitly[(Boolean | (Int | String)) containsSomeOf BIS]
      implicitly[(Boolean | (String | Int)) containsSomeOf BIS]
    }
  }

  "contains some of (negative)" should {

    "not infer unrelated type 1" >>> {
      illTyped("implicitly[(Float | String) containsSomeOf IS]", "\nFloat is not in .Int \\| String..\n")
    }

    "not infer unrelated type 2" >>> {
      illTyped("implicitly[(String | Float) containsSomeOf IS]", "\nFloat is not in .Int \\| String..\n")
    }

    "not infer unrelated type 3" >>> {
      illTyped("implicitly[(Float | Int) containsSomeOf IS]", "\nFloat is not in .Int \\| String..\n")
    }

    "not infer unrelated type 4" >>> {
      illTyped("implicitly[(Int | Float) containsSomeOf IS]", "\nFloat is not in .Int \\| String..\n")
    }

    "not infer non union" >>> {
      illTyped("implicitly[Int containsSomeOf IS]", "Int does not contain some of de.knutwalker.union.UnionTypeClassesSpec.IS.")
      illTyped("implicitly[String containsSomeOf IS]", "String does not contain some of de.knutwalker.union.UnionTypeClassesSpec.IS.")
    }
  }

  "contains all of (positive)" should {

    "infer ISB" >>> {
      implicitly[ISB containsAllOf ISB]
      implicitly[BIS containsAllOf ISB]
      implicitly[(Int | (String | Boolean)) containsAllOf ISB]
      implicitly[(Int | (Boolean | String)) containsAllOf ISB]
      implicitly[(String | (Int | Boolean)) containsAllOf ISB]
      implicitly[(String | (Boolean | Int)) containsAllOf ISB]
      implicitly[(Boolean | (Int | String)) containsAllOf ISB]
      implicitly[(Boolean | (String | Int)) containsAllOf ISB]
      implicitly[((Int | String) | Boolean) containsAllOf ISB]
      implicitly[((Int | Boolean) | String) containsAllOf ISB]
      implicitly[((String | Int) | Boolean) containsAllOf ISB]
      implicitly[((String | Boolean) | Int) containsAllOf ISB]
      implicitly[((Boolean | Int) | String) containsAllOf ISB]
      implicitly[((Boolean | String) | Int) containsAllOf ISB]
    }
  }

  "contains all of (negative)" should {

    "not infer subunion 1" >>> {
      illTyped("implicitly[(Int | String) containsAllOf ISB]", "\nBoolean is not in .Int \\| String..\n")
    }

    "not infer subunion 2" >>> {
      illTyped("implicitly[(String | Int) containsAllOf ISB]", "\nBoolean is not in .String \\| Int..\n")
    }

    "not infer subunion 3" >>> {
      illTyped("implicitly[(Int | Boolean) containsAllOf ISB]", "\nString is not in .Int \\| Boolean..\n")
    }

    "not infer subunion 4" >>> {
      illTyped("implicitly[(Boolean | Int) containsAllOf ISB]", "\nString is not in .Boolean \\| Int..\n")
    }

    "not infer subunion 5" >>> {
      illTyped("implicitly[(Boolean | String) containsAllOf ISB]", "\nInt is not in .Boolean \\| String..\n")
    }

    "not infer subunion 6" >>> {
      illTyped("implicitly[(String | Boolean) containsAllOf ISB]", "\nInt is not in .String \\| Boolean..\n")
    }
  }

}
