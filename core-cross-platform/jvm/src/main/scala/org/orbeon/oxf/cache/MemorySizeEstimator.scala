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
package org.orbeon.oxf.cache

import org.apache.commons.pool.ObjectPool
import org.infinispan.commons.marshall.WrappedBytes
import org.orbeon.dom
import org.orbeon.dom.saxon.DocumentWrapper
import org.orbeon.oxf.xml.SAXStore
import org.orbeon.saxon.om.{NodeInfo, VirtualNode}
import org.orbeon.saxon.tinytree.{TinyDocumentImpl, TinyNodeImpl, TinyTree}

import scala.jdk.CollectionConverters.*
import scala.util.Try


/**
 * Fast, allocation-light structural memory estimator for cached objects in Orbeon Forms,
 * plus an optional JOL helper for unit test calibration and deep diagnostics.
 */
object MemorySizeEstimator {

  private val DefaultUnknownObjectBytes = 256L
  private val EstimatedXPathExprBytes   = 2048L

  def estimateBytes(value: Any): Long =
    value match {
      case null                        => 0L
      case s: SizedCacheable           => s.estimatedMemoryBytes
      case wb: WrappedBytes            => 32L + wb.getLength.toLong
      case bytes: Array[Byte]          => 16L + bytes.length.toLong
      case chars: Array[Char]          => 16L + chars.length.toLong * 2L
      case ints: Array[Int]            => 16L + ints.length.toLong * 4L
      case str: String                 => estimateStringBytes(str)
      case arr: Array[String]          => 16L + arr.length.toLong * 4L + arr.iterator.map(estimateStringBytes).sum
      case doc: dom.Document           => estimateOrbeonDomDocument(doc)
      case elem: dom.Element           => estimateOrbeonDomElement(elem, maxElements = 25000)
      case tinyDoc: TinyDocumentImpl   => estimateTinyTree(tinyDoc.getTree)
      case tinyNode: TinyNodeImpl      => Option(tinyNode.getDocumentRoot).collect { case d: TinyDocumentImpl => estimateTinyTree(d.getTree) }.getOrElse(DefaultUnknownObjectBytes)
      case wrapper: DocumentWrapper    =>
        wrapper.getUnderlyingNode match {
          case doc: dom.Document => 64L + estimateOrbeonDomDocument(doc)
          case _                 => DefaultUnknownObjectBytes
        }
      case vNode: VirtualNode          =>
        vNode.getUnderlyingNode match {
          case doc: dom.Document => 64L + estimateOrbeonDomDocument(doc)
          case elem: dom.Element => 64L + estimateOrbeonDomElement(elem, maxElements = 25000)
          case _                 => DefaultUnknownObjectBytes
        }
      case nodeInfo: NodeInfo          =>
        Option(nodeInfo.getDocumentRoot) match {
          case Some(d: TinyDocumentImpl) => estimateTinyTree(d.getTree)
          case Some(w: DocumentWrapper)  => estimateBytes(w)
          case _                         => DefaultUnknownObjectBytes
        }
      case pool: ObjectPool[?]         =>
        val count = math.max(0, pool.getNumIdle) + math.max(0, pool.getNumActive)
        128L + count.toLong * EstimatedXPathExprBytes
      case (a, b)                      => 24L + estimateBytes(a) + estimateBytes(b)
      case (a, b, c)                   => 32L + estimateBytes(a) + estimateBytes(b) + estimateBytes(c)
      case Some(v)                     => 16L + estimateBytes(v)
      case None                        => 0L
      case seq: scala.collection.Seq[?] if seq.isInstanceOf[scala.collection.immutable.ArraySeq.ofByte] =>
        24L + seq.size.toLong
      case _: java.lang.Long | _: java.lang.Integer | _: java.lang.Boolean | _: java.lang.Double =>
        24L
      case _                           =>
        estimateViaKnownFields(value)
    }

  def estimateStringBytes(s: String): Long =
    DomMemorySizeEstimator.estimateStringBytes(s)

  def estimateTinyTree(tree: TinyTree): Long =
    if (tree eq null) {
      0L
    } else {
      def byteArr(a: Array[Byte])   = if (a ne null) 16L + a.length.toLong      else 0L
      def shortArr(a: Array[Short]) = if (a ne null) 16L + a.length.toLong * 2L else 0L
      def intArr(a: Array[Int])     = if (a ne null) 16L + a.length.toLong * 4L else 0L
      def refArr(a: Array[?])       = if (a ne null) 16L + a.length.toLong * 4L else 0L

      val charBufBytes = Option(tree.getCharacterBuffer).map(_.length.toLong * 2L + 64L).getOrElse(0L)
      val commBufBytes = Option(tree.getCommentBuffer).map(_.length.toLong * 2L + 48L).getOrElse(0L)
      val attValBytes  = tree.getNumberOfAttributes.toLong * 44L

      256L +
        byteArr(tree.getNodeKindArray) +
        shortArr(tree.getNodeDepthArray) +
        intArr(tree.getNameCodeArray) +
        intArr(tree.getTypeCodeArray) +
        intArr(tree.getNextPointerArray) +
        intArr(tree.getAlphaArray) +
        intArr(tree.getBetaArray) +
        intArr(tree.getAttributeNameCodeArray) +
        intArr(tree.getAttributeTypeCodeArray) +
        intArr(tree.getAttributeParentArray) +
        refArr(tree.getAttributeValueArray) +
        intArr(tree.getNamespaceCodeArray) +
        intArr(tree.getNamespaceParentArray) +
        charBufBytes +
        commBufBytes +
        attValBytes
    }

  def estimateOrbeonDomDocument(doc: dom.Document, maxElements: Int = 25000): Long =
    DomMemorySizeEstimator.estimateOrbeonDomDocument(doc, maxElements)

  def estimateOrbeonDomElement(root: dom.Element, maxElements: Int): Long =
    DomMemorySizeEstimator.estimateOrbeonDomElement(root, maxElements)

  private def estimateViaKnownFields(value: Any): Long =
    value.getClass.getName match {
      case "org.orbeon.oxf.processor.transformer.xslt.XSLTTransformer$TemplatesInfo" =>
        // Compiled XSLT Templates ASTs are typically 32-128 KB
        65536L
      case name if name.endsWith("URIReferences") || name.endsWith("Stage1CacheableState") =>
        512L
      case _ =>
        DefaultUnknownObjectBytes
    }

  /**
   * Optional exact object-graph measurement using OpenJDK JOL, subtracting shared roots
   * (such as GlobalBindingIndex, Saxon GlobalConfiguration, or shared XFormsStaticState).
   * Guarded with `Try` as `Unsafe` field access can be restricted on newer JVMs without flags.
   */
  def measureExclusiveBytesWithJol(target: AnyRef, sharedRoots: Seq[AnyRef] = Nil): Option[(Long, String)] =
    Try {
      val targetLayout = org.openjdk.jol.info.GraphLayout.parseInstance(target)
      val exclusive    =
        if (sharedRoots.nonEmpty)
          targetLayout.subtract(org.openjdk.jol.info.GraphLayout.parseInstance(sharedRoots*))
        else
          targetLayout
      (exclusive.totalSize(), exclusive.toFootprint())
    }.toOption
}
