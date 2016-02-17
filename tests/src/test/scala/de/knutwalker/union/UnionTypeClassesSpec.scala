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
      illTyped("implicitly[Float isPartOf IS]", "Float is not in .Int \\| String..")
    }

    "not infer ISB" >>> {
      illTyped("implicitly[Float isPartOf ISB]", "Float is not in .Int \\| String \\| Boolean..")
    }

    "not infer BIS" >>> {
      illTyped("implicitly[Float isPartOf BIS]", "Float is not in .Boolean \\| Int \\| String..")
    }

    "not infer ISBL" >>> {
      illTyped("implicitly[Float isPartOf ISBL]", "Float is not in .Int \\| String \\| Boolean \\| Long..")
    }

    "not infer LISB" >>> {
      illTyped("implicitly[Float isPartOf LISB]", "Float is not in .Long \\| Int \\| String \\| Boolean..")
    }

    "not infer BISL" >>> {
      illTyped("implicitly[Float isPartOf BISL]", "Float is not in .Boolean \\| Int \\| String \\| Long..")
    }

    "not infer LBIS" >>> {
      illTyped("implicitly[Float isPartOf LBIS]", "Float is not in .Long \\| Boolean \\| Int \\| String..")
    }
  }

  "contains some of (positive)" should {

    "infer IS" >>> {
      implicitly[(Int | String) isPartOf IS]
      implicitly[(String | Int) isPartOf IS]
    }

    "infer ISB" >>> {
      implicitly[(Int | String) isPartOf ISB]
      implicitly[(String | Int) isPartOf ISB]
      implicitly[(Int | Boolean) isPartOf ISB]
      implicitly[(Boolean | Int) isPartOf ISB]
      implicitly[(String | Boolean) isPartOf ISB]
      implicitly[(Boolean | String) isPartOf ISB]
      implicitly[(Int | String | Boolean) isPartOf ISB]
      implicitly[(Int | Boolean | String) isPartOf ISB]
      implicitly[(String | Int | Boolean) isPartOf ISB]
      implicitly[(String | Boolean | Int) isPartOf ISB]
      implicitly[(Boolean | Int | String) isPartOf ISB]
      implicitly[(Boolean | String | Int) isPartOf ISB]
      implicitly[(Int | (String | Boolean)) isPartOf ISB]
      implicitly[(Int | (Boolean | String)) isPartOf ISB]
      implicitly[(String | (Int | Boolean)) isPartOf ISB]
      implicitly[(String | (Boolean | Int)) isPartOf ISB]
      implicitly[(Boolean | (Int | String)) isPartOf ISB]
      implicitly[(Boolean | (String | Int)) isPartOf ISB]
    }

    "infer BIS" >>> {
      implicitly[(Int | String) isPartOf BIS]
      implicitly[(String | Int) isPartOf BIS]
      implicitly[(Int | Boolean) isPartOf BIS]
      implicitly[(Boolean | Int) isPartOf BIS]
      implicitly[(String | Boolean) isPartOf BIS]
      implicitly[(Boolean | String) isPartOf BIS]
      implicitly[(Int | String | Boolean) isPartOf BIS]
      implicitly[(Int | Boolean | String) isPartOf BIS]
      implicitly[(String | Int | Boolean) isPartOf BIS]
      implicitly[(String | Boolean | Int) isPartOf BIS]
      implicitly[(Boolean | Int | String) isPartOf BIS]
      implicitly[(Boolean | String | Int) isPartOf BIS]
      implicitly[(Int | (String | Boolean)) isPartOf BIS]
      implicitly[(Int | (Boolean | String)) isPartOf BIS]
      implicitly[(String | (Int | Boolean)) isPartOf BIS]
      implicitly[(String | (Boolean | Int)) isPartOf BIS]
      implicitly[(Boolean | (Int | String)) isPartOf BIS]
      implicitly[(Boolean | (String | Int)) isPartOf BIS]
    }

    "infer completely" >>> {
      implicitly[ISB isPartOf ISB]
      implicitly[BIS isPartOf ISB]
      implicitly[ISBL isPartOf LISB]
      implicitly[ISBL isPartOf BISL]
      implicitly[ISBL isPartOf LBIS]
      implicitly[LISB isPartOf ISBL]
      implicitly[LISB isPartOf BISL]
      implicitly[LISB isPartOf LBIS]
      implicitly[BISL isPartOf ISBL]
      implicitly[BISL isPartOf LISB]
      implicitly[BISL isPartOf LBIS]
      implicitly[LBIS isPartOf ISBL]
      implicitly[LBIS isPartOf LISB]
      implicitly[LBIS isPartOf BISL]
    }
  }

  "contains some of (negative)" should {

    "not infer unrelated type 1" >>> {
      illTyped("implicitly[(Float | String) isPartOf IS]", "Float is not in .Int \\| String..")
    }

    "not infer unrelated type 2" >>> {
      illTyped("implicitly[(String | Float) isPartOf IS]", "Float is not in .Int \\| String..")
    }

    "not infer unrelated type 3" >>> {
      illTyped("implicitly[(Float | Int) isPartOf IS]", "Float is not in .Int \\| String..")
    }

    "not infer unrelated type 4" >>> {
      illTyped("implicitly[(Int | Float) isPartOf IS]", "Float is not in .Int \\| String..")
    }
  }
}
