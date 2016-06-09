package org.orbeon.dom

trait CharacterData extends Node {
  def appendText(text: String): Unit
}

trait Comment extends CharacterData
trait Text    extends CharacterData
