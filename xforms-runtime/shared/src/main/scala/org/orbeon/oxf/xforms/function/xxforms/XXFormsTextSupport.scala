package org.orbeon.oxf.xforms.function.xxforms

import org.orbeon.oxf.util.HtmlParsing
import org.orbeon.oxf.xforms.model.InstanceData
import org.orbeon.oxf.xml.ElemFilter
import org.orbeon.saxon.om
import org.orbeon.xforms.XFormsNames


object XXFormsTextSupport {

  def innerText(item: om.Item): String =
    item match {
      case nodeInfo: om.NodeInfo if isHTMLFragment(nodeInfo) =>
        HtmlParsing.sanitizeHtmlString(
          value           = nodeInfo.getStringValue,
          extraElemFilter = _ => ElemFilter.Remove
        )
      case _ =>
        item.getStringValue
    }

  private def isHTMLFragment(nodeInfo: om.NodeInfo): Boolean =
    Option(InstanceData.getType(nodeInfo)).exists { nodeType =>
      nodeType.namespace.uri == XFormsNames.XFORMS_NAMESPACE_URI &&
        nodeType.localName == "HTMLFragment"
    }
}
