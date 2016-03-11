package sss.asado.storage

/**
  * Copyright Stepping Stone Software Ltd. 2016, all rights reserved. 
  * mcsherrylabs on 3/2/16.
  */
trait Storage[K, V] {
  def entries: Set[V]
  def apply(k: K): V = get(k).get
  def get(k: K): Option[V]
  def write(v: V)
}