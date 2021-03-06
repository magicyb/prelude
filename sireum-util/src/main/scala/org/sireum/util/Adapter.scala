/*
Copyright (c) 2011-2013 Robby, Kansas State University.        
All rights reserved. This program and the accompanying materials      
are made available under the terms of the Eclipse Public License v1.0 
which accompanies this distribution, and is available at              
http://www.eclipse.org/legal/epl-v10.html                             
*/

package org.sireum.util

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
trait Adapter[U, T] {
  def adapt(u : U) : T
}

/**
 * @author <a href="mailto:robby@k-state.edu">Robby</a>
 */
object Adapter {
  def usingF[U, T, R](u : U)(f : T => R)(implicit a : Adapter[U, T]) : R =
    f(a.adapt(u))

  def using[U, T](u : U)(f : T => Unit)(implicit a : Adapter[U, T]) : Unit =
    f(a.adapt(u))

  def foreach[U, T](u : U)(f : T => Unit)(implicit a : Adapter[U, T]) {
    f(a.adapt(u))
  }
}

