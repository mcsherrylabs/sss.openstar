package sss.asado.storage

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/2/16.
  */
trait Storage[K, V] {
  private[storage] def entries: Set[V]
  def apply(k: K): V = get(k).get
  def get(k: K): Option[V]
  def write(k: K,v: V)
  def delete(k: K): Boolean
  def inTransaction(f: => Unit): Unit
}