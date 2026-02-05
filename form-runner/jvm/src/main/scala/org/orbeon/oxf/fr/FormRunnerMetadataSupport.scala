package org.orbeon.oxf.fr

import org.orbeon.oxf.fr.XMLNames.{XF, XH}
import org.orbeon.oxf.util.Whitespace
import org.orbeon.oxf.xml.*
import org.orbeon.scaxon.SAXEvents.{Atts, StartElement}


object FormRunnerMetadataSupport {

  // See https://github.com/orbeon/orbeon-forms/issues/2385
  val MetadataElementsToKeep = Set(
    "metadata",
    "title",
    Names.Permissions,
    "available"
  )

  private object IdAtt {
    private val IdQName = JXQName("id")
    def unapply(atts: Atts): Option[String] = atts.atts collectFirst { case (IdQName, value) => value }
  }

  // NOTE: Tested that the pattern match works optimally: with form-with-metadata.xhtml, `JQName.unapply` is
  // called 17 times and `IdAtt.unapply` 2 times until the match is found, which is what is expected.
  def isMetadataElement(stack: List[StartElement]): Boolean =
    stack match {
      case
        StartElement(JXQName("", "metadata"), _)                             ::
        StartElement(JXQName(XF, "instance"), IdAtt(Names.MetadataInstance)) ::
        StartElement(JXQName(XF, "model"),    IdAtt(Names.FormModel))        ::
        StartElement(JXQName(XH, "head"), _)                                 ::
        StartElement(JXQName(XH, "html"), _)                                 ::
        Nil => true
      case _  => false
    }

  def isAttachmentsElement(stack: List[StartElement]): Boolean =
    stack match {
      case
        StartElement(JXQName("", "attachments"), _)                             ::
        StartElement(JXQName(XF, "instance"), IdAtt(Names.AttachmentsInstance)) ::
        StartElement(JXQName(XF, "model"),    IdAtt(Names.FormModel))           ::
        StartElement(JXQName(XH, "head"), _)                                    ::
        StartElement(JXQName(XH, "html"), _)                                    ::
        Nil => true
      case _  => false
    }

  def newInstanceFilter(
    metadataReceiver      : XMLReceiver,
    isStartElement        : List[StartElement] => Boolean,
    metadataElementsToKeep: String => Boolean
  ): FilterReceiver =
    // MAYBE: strip enclosing namespaces; truncate long titles
    new FilterReceiver(
      new WhitespaceXMLReceiver(
        new ElementFilterXMLReceiver(
          metadataReceiver,
          (level, uri, localname, _) => level != 1 || level == 1 && uri == "" && metadataElementsToKeep(localname)
        ),
        Whitespace.Policy.Normalize,
        (_, _, _, _) => Whitespace.Policy.Normalize
      ),
      isStartElement
    )

  def newBodyFilter(
    bodyReceiver: XMLReceiver
  ): FilterReceiver =
    new FilterReceiver(
      bodyReceiver,
      {
        case
          StartElement(JXQName(XH, "body"), _) ::
          StartElement(JXQName(XH, "html"), _) ::
          Nil => true
        case _ => false
      }
    )
}
