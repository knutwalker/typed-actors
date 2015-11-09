/*
 * Copyright 2015 Paul Horn
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

package de.knutwalker.akka.typed

import org.specs2.execute._
import org.specs2.execute.Typecheck._
import org.specs2.matcher.TypecheckMatchers._
import org.specs2.mutable.Specification


object UnionTypeSpec extends Specification {

  type IS = Int | String

  type ISB = IS | Boolean
  type BIS = Boolean | IS

  type ISBL = ISB | Long
  type LISB = Long | ISB
  type BISL = BIS | Long
  type LBIS = Long | BIS

  "is part of (positive)" should {

    "infer IS" >> typecheck {
      """
      implicitly[Int isPartOf IS]
      implicitly[String isPartOf IS]
      """
    }

    "infer ISB" >> typecheck {
      """
      implicitly[Int isPartOf ISB]
      implicitly[String isPartOf ISB]
      implicitly[Boolean isPartOf ISB]
      """
    }

    "infer BIS" >> typecheck {
      """
      implicitly[Int isPartOf BIS]
      implicitly[String isPartOf BIS]
      implicitly[Boolean isPartOf BIS]
      """
    }

    "infer ISBL" >> typecheck {
      """
      implicitly[Int isPartOf ISBL]
      implicitly[String isPartOf ISBL]
      implicitly[Boolean isPartOf ISBL]
      implicitly[Long isPartOf ISBL]
      """
    }

    "infer LISB" >> typecheck {
      """
      implicitly[Int isPartOf LISB]
      implicitly[String isPartOf LISB]
      implicitly[Boolean isPartOf LISB]
      implicitly[Long isPartOf LISB]
      """
    }

    "infer BISL" >> typecheck {
      """
      implicitly[Int isPartOf BISL]
      implicitly[String isPartOf BISL]
      implicitly[Boolean isPartOf BISL]
      implicitly[Long isPartOf BISL]
      """
    }

    "infer LBIS" >> typecheck {
      """
      implicitly[Int isPartOf LBIS]
      implicitly[String isPartOf LBIS]
      implicitly[Boolean isPartOf LBIS]
      implicitly[Long isPartOf LBIS]
      """
    }
  }

  "is part of (negative)" should {

    "not infer IS" >> {
      typecheck {
        """
        implicitly[Float isPartOf IS]
        """
      } must not succeed
    }

    "not infer ISB" >> {
      typecheck {
        """
        implicitly[Float isPartOf ISB]
        """
      } must not succeed
    }

    "not infer BIS" >> {
      typecheck {
        """
        implicitly[Float isPartOf BIS]
        """
      } must not succeed
    }

    "not infer ISBL" >> {
      typecheck {
        """
        implicitly[Float isPartOf ISBL]
        """
      } must not succeed
    }

    "not infer LISB" >> {
      typecheck {
        """
        implicitly[Float isPartOf LISB]
        """
      } must not succeed
    }

    "not infer BISL" >> {
      typecheck {
        """
        implicitly[Float isPartOf BISL]
        """
      } must not succeed
    }

    "not infer LBIS" >> {
      typecheck {
        """
        implicitly[Float isPartOf LBIS]
        """
      } must not succeed
    }
  }

  "contains some of (positive)" should {

    "infer IS" >> typecheck {
      """
      implicitly[(Int | String) containsSomeOf IS]
      implicitly[(String | Int) containsSomeOf IS]
      """
    }

    "infer ISB" >> typecheck {
      """
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
      """
    }

    "infer BIS" >> typecheck {
      """
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
      """
    }
  }

  "contains some of (negative)" should {

    "not infer unrelated type 1" >> {
      typecheck {
        """
        implicitly[(Float | String) containsSomeOf IS]
        """
      } must not succeed
    }

    "not infer unrelated type 2" >> {
      typecheck {
        """
        implicitly[(String | Float) containsSomeOf IS]
        """
      } must not succeed
    }

    "not infer unrelated type 3" >> {
      typecheck {
        """
        implicitly[(Float | Int) containsSomeOf IS]
        """
      } must not succeed
    }

    "not infer unrelated type 4" >> {
      typecheck {
        """
        implicitly[(Int | Float) containsSomeOf IS]
        """
      } must not succeed
    }

    "not infer non union 1" >> {
      typecheck {
        """
        implicitly[Int containsSomeOf IS]
        implicitly[String containsSomeOf IS]
        """
      } must not succeed
    }

    "not infer non union 2" >> {
      typecheck {
        """
        implicitly[Int containsSomeOf IS]
        implicitly[String containsSomeOf IS]
        """
      } must not succeed
    }
  }

  "contains all of (positive)" should {

    "infer ISB" >> typecheck {
      """
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
      """
    }
  }

  "contains all of (negative)" should {

    "not infer subunion 1" >> {
      typecheck {
        """
        implicitly[(Int | String) containsAllOf ISB]
        """
      } must not succeed
    }

    "not infer subunion 2" >> {
      typecheck {
        """
        implicitly[(String | Int) containsAllOf ISB]
        """
      } must not succeed
    }

    "not infer subunion 3" >> {
      typecheck {
        """
        implicitly[(Int | Boolean) containsAllOf ISB]
        """
      } must not succeed
    }

    "not infer subunion 4" >> {
      typecheck {
        """
        implicitly[(Boolean | Int) containsAllOf ISB]
        """
      } must not succeed
    }

    "not infer subunion 5" >> {
      typecheck {
        """
        implicitly[(Boolean | String) containsAllOf ISB]
        """
      } must not succeed
    }

    "not infer subunion 6" >> {
      typecheck {
        """
        implicitly[(String | Boolean) containsAllOf ISB]
        """
      } must not succeed
    }
  }

}
