package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.xforms.function.XFormsFunction
import org.orbeon.saxon.expr.XPathContext
import org.orbeon.saxon.om.SequenceIterator
import org.orbeon.saxon.{om, value}
import org.orbeon.scaxon.Implicits._


class XXFormsDocumentId extends XFormsFunction {
  override def evaluateItem(xpathContext: XPathContext): value.StringValue =
    XFormsFunction.getContainingDocument(xpathContext).uuid
}

class XXFormsSetDocumentAttribute extends XFormsFunction {
  override def iterate(xpathContext: XPathContext): SequenceIterator = {
    implicit val ctx = xpathContext
    XFormsFunction.getContainingDocument.setAttribute(
      stringArgument(0),
      stringArgument(1),
      new value.SequenceExtent(itemsArgument(2))
    )
    om.EmptyIterator.getInstance
  }
}

class XXFormsGetDocumentAttribute extends XFormsFunction {
  override def iterate(xpathContext: XPathContext): SequenceIterator = {
    implicit val ctx = xpathContext
    XFormsFunction.getContainingDocument.getAttribute(
      stringArgument(0),
      stringArgument(1)
    ) match {
      case Some(value: value.SequenceExtent) => value.iterate()
      case _                                 => om.EmptyIterator.getInstance
    }
  }
}

class XXFormsRemoveDocumentAttribute extends XFormsFunction {
  override def iterate(xpathContext: XPathContext): SequenceIterator = {
    implicit val ctx = xpathContext
    XFormsFunction.getContainingDocument.removeAttribute(
      stringArgument(0),
      stringArgument(1)
    )
    om.EmptyIterator.getInstance
  }
}

class XXFormsRemoveDocumentAttributes extends XFormsFunction {
  override def iterate(xpathContext: XPathContext): SequenceIterator = {
    implicit val ctx = xpathContext
    XFormsFunction.getContainingDocument.removeAttributes(stringArgument(0))
    om.EmptyIterator.getInstance
  }
}
