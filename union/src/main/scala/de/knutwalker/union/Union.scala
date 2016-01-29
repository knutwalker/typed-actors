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

import de.knutwalker.union.Union.Completeness.{ Partial, Total }

import scala.language.experimental.macros


sealed trait Union
sealed trait |[+A, +B] extends Union

object Union {

  def apply[U]: UnionHelper[U, U, Partial, Partial] = new UnionHelper
  def partial[U]: UnionHelper[U, U, Partial, Partial] = new UnionHelper
  def total[U]: UnionHelper[U, U, Total, Partial] = new UnionHelper

  final class UnionHelper[U, SU, All <: Completeness, Sub <: Completeness] {
    def total[SU0]: UnionHelper[U, SU0, All, Total] = new UnionHelper

    def apply[T](msg: PartialFunction[Any, T]): PartialFunction[Any, T] = macro UnionMacros.checkPF[U, T, SU, All, Sub]
    def run[T](msg: PartialFunction[Any, T]): PartialFunction[Any, T] = macro UnionMacros.checkPF[U, T, SU, All, Sub]
  }

  sealed trait Completeness
  object Completeness {
    sealed trait Partial extends Completeness
    sealed trait Total extends Completeness
  }
}
