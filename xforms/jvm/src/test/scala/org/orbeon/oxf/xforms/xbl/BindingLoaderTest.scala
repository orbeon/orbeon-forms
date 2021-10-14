/**
 * Copyright (C) 2015 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.xbl

import org.junit.Test
import org.orbeon.dom.Document
import org.orbeon.oxf.properties.PropertyStore
import org.orbeon.oxf.test.DocumentTestBase
import org.orbeon.oxf.xml.dom.Converter._
import org.scalatestplus.junit.AssertionsForJUnit
import org.xml.sax.helpers.AttributesImpl

import scala.collection.mutable

class BindingLoaderTest extends DocumentTestBase with AssertionsForJUnit {

  def newPropertySet = {
    val properties =
      <properties xmlns:xs="http://www.w3.org/2001/XMLSchema" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <property as="xs:string"  name="oxf.xforms.xbl.mapping.orbeon">
          http://orbeon.org/oxf/xml/form-runner
        </property>
        <property as="xs:string"  name="oxf.xforms.xbl.library">
          fr:foo fr:bar
        </property>
        <property as="xs:string"  name="oxf.xforms.resources.baseline">
          fr:bar fr:baz
        </property>
        <property as="xs:string"  name="oxf.xforms.assets.baseline">
          {{
            "css": [],
            "js": [],
            "xbl": []
          }}
        </property>
      </properties>.toDocument

    PropertyStore.parse(properties).getGlobalPropertySet
  }

  class TestBindingLoader extends BindingLoader {

    var propertySet = newPropertySet

    val FooXBL =
      <xbl:xbl xmlns:xbl="http://www.w3.org/ns/xbl" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <xbl:script src="/xbl/orbeon/foo/foo.js"/>
        <xbl:binding id="fr-foo" element="fr|foo, [appearance ~= foo]">
          <xbl:resources>
            <xbl:style src="/xbl/orbeon/foo/foo.css"/>
          </xbl:resources>
          <xbl:template/>
        </xbl:binding>
      </xbl:xbl>.toDocument

    val BarXBL =
        <xbl:xbl xmlns:xbl="http://www.w3.org/ns/xbl" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <xbl:script src="/xbl/orbeon/bar/bar.js"/>
        <xbl:binding id="fr-bar" element="fr|bar, fr|bar[appearance ~= bar]">
          <xbl:resources>
            <xbl:style src="/xbl/orbeon/bar/bar.css"/>
          </xbl:resources>
          <xbl:template/>
        </xbl:binding>
      </xbl:xbl>.toDocument

    val BazXBL =
      <xbl:xbl xmlns:xbl="http://www.w3.org/ns/xbl" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <xbl:script src="/xbl/orbeon/baz/baz.js"/>
        <xbl:binding id="fr-baz" element="fr|baz, fr|toto[mediatype = 'text/html']">
          <xbl:resources>
            <xbl:style src="/xbl/orbeon/baz/baz.css"/>
          </xbl:resources>
          <xbl:template/>
        </xbl:binding>
      </xbl:xbl>.toDocument

    val GagaXBL: Document =
      <xbl:xbl xmlns:xbl="http://www.w3.org/ns/xbl" xmlns:fr="http://orbeon.org/oxf/xml/form-runner">
        <xbl:script src="/xbl/orbeon/gaga/gaga.js"/>
        <xbl:binding id="fr-gaga" element="fr|gaga">
          <xbl:resources>
            <xbl:style src="/xbl/orbeon/gaga/gaga.css"/>
          </xbl:resources>
          <xbl:template/>
        </xbl:binding>
      </xbl:xbl>.toDocument

    val Docs = Map(
      "/xbl/orbeon/foo/foo.xbl"   -> FooXBL,
      "/xbl/orbeon/bar/bar.xbl"   -> BarXBL,
      "/xbl/orbeon/baz/baz.xbl"   -> BazXBL,
      "/xbl/orbeon/gaga/gaga.xbl" -> GagaXBL
    )

    def getPropertySet = propertySet

    def lastModifiedByPath(path: String) =
      lastModified

    def existsByPath(path: String) =
      Docs.contains(path)

    def contentAsOrbeonDom(path: String) = {
      contentRead += path
      Docs(path)
    }

    var lastModified = 123L
    val contentRead = mutable.Set[String]()
  }


  @Test def testLibraryLoad(): Unit = {

    val Loader = new TestBindingLoader

    val ExpectedCheckedPaths =
      Set("foo", "bar", "baz") map (Loader.bindingPathByName("orbeon", _))

    val ExpectedScripts =
      List("bar", "baz") map (Loader.bindingPathByName("orbeon", _)) map (_.replace(".xbl", ".js"))

    val ExpectedStyles =
      ExpectedScripts map (_.replace(".js", ".css"))

    var currentIndexOpt: Option[BindingIndex[IndexableBinding]] = None

    // Initial library load
    locally {
      val (newIndex, checkedPaths, scripts, styles) =
        Loader.getUpToDateLibraryAndBaseline(currentIndexOpt, checkUpToDate = true)
      currentIndexOpt = Some(newIndex)

      assert(ExpectedCheckedPaths.size === BindingIndex.distinctBindings(newIndex).size)

      assert(2 === newIndex.nameAndAttSelectors.size)
      assert(1 === newIndex.attOnlySelectors.size)
      assert(3 === newIndex.nameOnlySelectors.size)

      assert(ExpectedCheckedPaths === checkedPaths)
      assert(ExpectedScripts      === scripts)
      assert(ExpectedStyles       === styles)
    }

    // New load without touching last modification dates
    locally {
      Loader.contentRead.clear()
      val (newIndex, _, _, _) =
        Loader.getUpToDateLibraryAndBaseline(currentIndexOpt, checkUpToDate = true)

      // Index object not modified
      assert(currentIndexOpt exists (_ eq newIndex))

      // Nothing read
      assert(Loader.contentRead.isEmpty)
    }

    // New load with new last modification dates
    locally {
      Loader.lastModified += 1
      Loader.contentRead.clear()
      val (newIndex, _, _, _) =
        Loader.getUpToDateLibraryAndBaseline(currentIndexOpt, checkUpToDate = true)

      // Index modified
      assert(! (currentIndexOpt exists (_ eq newIndex)))
      currentIndexOpt = Some(newIndex)

      // All bindings reloaded
      assert(ExpectedCheckedPaths.size === Loader.contentRead.size)
    }

    // Property reload forces library reload
    locally {
      Loader.propertySet = newPropertySet
      Loader.contentRead.clear()
      val (newIndex, _, _, _) =
        Loader.getUpToDateLibraryAndBaseline(currentIndexOpt, checkUpToDate = true)

      // Index modified
      assert(! (currentIndexOpt exists (_ eq newIndex)))
      currentIndexOpt = Some(newIndex)

      // All bindings reloaded
      assert(ExpectedCheckedPaths.size === Loader.contentRead.size)
    }
  }

  @Test def testByNameLoad(): Unit = {

    val Loader = new TestBindingLoader

    val GagaPath = Loader.bindingPathByName("orbeon", "gaga")
    val TotoPath = Loader.bindingPathByName("orbeon", "toto")

    var currentIndex: BindingIndex[IndexableBinding] = GlobalBindingIndex.Empty
    var checkedPaths: Set[String] = Set.empty

    // Initial library load
    locally {
      val (newIndex, newCheckedPaths, _, _) =
        Loader.getUpToDateLibraryAndBaseline(None, checkUpToDate = true)

      currentIndex = newIndex
      checkedPaths = newCheckedPaths
    }

    // Load additional fr:gaga binding by name
    locally {

      Loader.contentRead.clear()

      val bindingsCountBefore = BindingIndex.distinctBindings(currentIndex).size

      def findFrGaga() = {
        val (newIndex, newCheckedPaths, newBinding) =
          Loader.findMostSpecificBinding(
            currentIndex,
            Some(checkedPaths),
            "http://orbeon.org/oxf/xml/form-runner",
            "gaga",
            new AttributesImpl
          )

        currentIndex = newIndex
        checkedPaths = newCheckedPaths

        newBinding
      }

      val newBinding = findFrGaga()

      // fr:gaga was loaded
      assert(Set(GagaPath) === Loader.contentRead)

      // One more binding
      assert(BindingIndex.distinctBindings(currentIndex).size === bindingsCountBefore + 1)
      assert(newBinding.isDefined)

      // Try again without touching last modification dates
      Loader.contentRead.clear()
      findFrGaga()
      assert(Loader.contentRead.isEmpty)

      // Try again with new last modification dates but not touching checkedPaths
      Loader.lastModified += 1
      Loader.contentRead.clear()
      findFrGaga()
      assert(Loader.contentRead.isEmpty)

      // Try again with new last modification dates but as if we hadn't ever seen the path to gaga.xbl
      Loader.contentRead.clear()
      checkedPaths -= GagaPath
      findFrGaga()
      assert(Set(GagaPath) === Loader.contentRead)

      // Binding number hasn't changed
      assert(BindingIndex.distinctBindings(currentIndex).size === bindingsCountBefore + 1)
    }

    // Missing binding
    locally {

      val (newIndex, newCheckedPaths, newBinding) =
        Loader.findMostSpecificBinding(
          currentIndex,
          Some(checkedPaths),
          "http://orbeon.org/oxf/xml/form-runner",
          "toto",
          new AttributesImpl
        )

      currentIndex = newIndex
      checkedPaths = newCheckedPaths

      // Path is still considered as checked
      assert(checkedPaths(TotoPath))

      // No new binding found
      assert(None === newBinding)
    }
  }
}
