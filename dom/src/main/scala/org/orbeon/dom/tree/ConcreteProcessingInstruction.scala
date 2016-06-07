package org.orbeon.dom.tree

import java.{lang ⇒ jl, util ⇒ ju}

import org.orbeon.dom.{Node, ProcessingInstruction, Visitor}

class ConcreteProcessingInstruction(var target: String, var text: String, var values: ju.Map[String, String])
  extends AbstractNode with ProcessingInstruction with WithParent {

  override def getNodeType: Short = Node.PROCESSING_INSTRUCTION_NODE

  def this(target: String, text: String) =
    this(target, text, ConcreteProcessingInstruction.parseValues(text))

  override def getName: String = getTarget
  override def setName(name: String): Unit = setTarget(name)

  def getTarget: String         = target
  def setTarget(target: String) = this.target = target

  override def getText = text
  override def setText(text: String): Unit = {
    this.text = text
    this.values = ConcreteProcessingInstruction.parseValues(text)
  }

  def getValue(name: String): String = {
    val answer = values.get(name)
    if (answer eq null)
      ""
    else
      answer
  }

  def setValue(name: String, value: String): Unit =
    values.put(name, value)

  def getValues: ju.Map[String, String] = ju.Collections.unmodifiableMap(values)

  def accept(visitor: Visitor): Unit = visitor.visit(this)

  override def toString: String = {
    super.toString + " [ProcessingInstruction: &" + getName + ";]"
  }
}

// ORBEON: Not sure we need this custom parsing of PIs. If not, could remove it.
object ConcreteProcessingInstruction {

  /**
   * Parses the raw data of PI as a `Map`.
   */
  def parseValues(text: String): ju.Map[String, String] = {
    val data = new ju.HashMap[String, String]()
    val s = new ju.StringTokenizer(text, " =\'\"", true)
    while (s.hasMoreTokens) {
      val name = getName(s)
      if (s.hasMoreTokens) {
        val value = getValue(s)
        data.put(name, value)
      }
    }
    data
  }

  private def getName(tokenizer: ju.StringTokenizer): String = {
    var token = tokenizer.nextToken()
    val name = new jl.StringBuilder(token)
    while (tokenizer.hasMoreTokens) {
      token = tokenizer.nextToken()
      if (token != "=") {
        name.append(token)
      } else {
        return name.toString.trim
      }
    }
    name.toString.trim
  }

  private def getValue(tokenizer: ju.StringTokenizer): String = {
    var token = tokenizer.nextToken()
    val value = new jl.StringBuilder
    while (tokenizer.hasMoreTokens && token != "\'" && token != "\"") {
      token = tokenizer.nextToken()
    }
    val quote = token
    while (tokenizer.hasMoreTokens) {
      token = tokenizer.nextToken()
      if (quote != token) {
        value.append(token)
      } else {
        return value.toString
      }
    }
    value.toString
  }
}
