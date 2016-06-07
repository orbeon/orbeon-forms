package org.orbeon.dom.tree
import org.orbeon.dom._

trait WithCharacterData extends AbstractNode with CharacterData with WithParent {
  def appendText(text: String) = setText(getText + text)
}
