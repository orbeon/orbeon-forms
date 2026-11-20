/**
  * Copyright (C) 2026 Orbeon, Inc.
  *
  * This program is free software; you can redistribute it and/or modify it under the terms of the
  * GNU Lesser General Public License as published by the Free Software Foundation; either version
  * 2.1 of the License, or (at your option) any later version.
  *
  * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
  * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
  * See the GNU Lesser General Public License for more details.
  *
  * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
  */
package org.orbeon.xbl

import org.commonmark.Extension
import org.commonmark.ext.autolink.AutolinkExtension
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.ext.task.list.items.{TaskListItemMarker, TaskListItemsExtension}
import org.commonmark.node.{Link, Node}
import org.commonmark.parser.Parser
import org.commonmark.renderer.NodeRenderer
import org.commonmark.renderer.html.{AttributeProvider, HtmlNodeRendererContext, HtmlRenderer}
import org.orbeon.oxf.util.HtmlParsing
import org.orbeon.oxf.util.StringUtils.*

import java.util as ju
import scala.jdk.CollectionConverters.*


object MarkdownSupportJava {

  private val ParserExtensions: ju.List[Extension] =
    List[Extension](
      TablesExtension.create(),
      StrikethroughExtension.create(),
      AutolinkExtension.create(),
      TaskListItemsExtension.create()
    ).asJava

  private val RendererExtensions: ju.List[Extension] =
    List[Extension](
      TablesExtension.create(),
      StrikethroughExtension.create(),
      AutolinkExtension.create()
    ).asJava

  private val MarkdownParser: Parser =
    Parser.builder()
      .extensions(ParserExtensions)
      .build()

  private class TaskListSpanNodeRenderer(context: HtmlNodeRendererContext) extends NodeRenderer {
    private val html = context.getWriter

    override def getNodeTypes: ju.Set[Class[? <: Node]] =
      ju.Collections.singleton(classOf[TaskListItemMarker])

    override def render(node: Node): Unit = {
      val marker = node.asInstanceOf[TaskListItemMarker]
      val checked = marker.isChecked
      val attrs = new ju.LinkedHashMap[String, String]()
      attrs.put("class", if (checked) "fr-markdown-task-checkbox fr-markdown-task-checked" else "fr-markdown-task-checkbox")
      attrs.put("aria-hidden", "true")
      html.tag("span", attrs)
      html.text(if (checked) "\u2611 " else "\u2610 ")
      html.tag("/span")
    }
  }

  private val ExternalLinkAttributeProvider: AttributeProvider =
    (node: Node, tagName: String, attributes: ju.Map[String, String]) => {
      if (tagName == "a" && node.isInstanceOf[Link]) {
        val href = Option(attributes.get("href")).getOrElse("")
        if (href.startsWith("http://") || href.startsWith("https://")) {
          attributes.put("target", "_blank")
          attributes.put("rel", "noopener noreferrer")
        }
      }
    }

  private val MarkdownHtmlRenderer: HtmlRenderer =
    HtmlRenderer.builder()
      .extensions(RendererExtensions)
      .escapeHtml(true)
      .sanitizeUrls(true)
      .nodeRendererFactory(ctx => new TaskListSpanNodeRenderer(ctx))
      .attributeProviderFactory(_ => ExternalLinkAttributeProvider)
      .build()

  //@XPathFunction
  def markdownToSanitizedHtml(markdown: String): String =
    markdown.trimAllToOpt match {
      case None =>
        ""
      case Some(nonEmpty) =>
        val doc = MarkdownParser.parse(nonEmpty)
        val rawHtml = MarkdownHtmlRenderer.render(doc)
        HtmlParsing.sanitizeHtmlString(rawHtml)
    }
}
