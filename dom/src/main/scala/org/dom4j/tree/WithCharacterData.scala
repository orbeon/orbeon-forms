package org.dom4j.tree

import org.dom4j.CharacterData

trait WithCharacterData extends AbstractNode with CharacterData with WithParent {
  def appendText(text: String) = setText(getText + text)
}
