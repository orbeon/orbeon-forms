package org.orbeon.dom

import java.{util ⇒ ju}

trait ProcessingInstruction extends Node {
  def getTarget: String
  def setTarget(target: String): Unit
  def getText: String
  def getValue(name: String): String
  def getValues: ju.Map[String, String]
  def setValue(name: String, value: String): Unit
}
