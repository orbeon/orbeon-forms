/**
 * Copyright (C) 2016 Orbeon, Inc.
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
package org.orbeon.oxf.xforms.analysis

import org.junit.Assert.{assertFalse, assertTrue}
import org.junit.{Assume, Test}
import org.mockito.{Matchers, Mockito}
import org.orbeon.oxf.common.Version
import org.orbeon.oxf.processor.ProcessorUtils
import org.orbeon.oxf.test.ResourceManagerTestBase
import org.orbeon.oxf.xforms._
import org.orbeon.oxf.xforms.model.{XFormsInstance, XFormsModel}
import org.orbeon.xforms.Constants.{RepeatIndexSeparatorString, RepeatSeparatorString}
import org.orbeon.xforms.XFormsId
import org.scalatestplus.junit.AssertionsForJUnit


object XFormsStaticStateTest {

  def getStaticState(documentURL: String): XFormsStaticState =
    PartAnalysisBuilder.createFromDocument(ProcessorUtils.createDocumentFromURL(documentURL, null))._2

  def withRefresh[T](thunk: => T)(implicit dependencies: XPathDependencies): T = {
    dependencies.refreshStart()
    val result = thunk
    dependencies.refreshDone()
    result
  }
}

class XFormsStaticStateTest extends ResourceManagerTestBase with AssertionsForJUnit {

  import XFormsStaticStateTest._

  @Test def lhhaAnalysis(): Unit = {
    Assume.assumeTrue(Version.isPE)

    // TODO
    // val staticState = getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/lhha.xhtml")
  }

  private val EmptyNamespaces = Map("" -> "")

  @Test def bindAnalysis(): Unit = {
    Assume.assumeTrue(Version.isPE)

    val partAnalysis = getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/binds.xhtml").topLevelPart

    // TODO: test computedBindExpressionsInstances and validationBindInstances
    locally {
      val model1 = partAnalysis.getModel("model1")
      assertTrue(model1.figuredAllBindRefAnalysis)
      assertTrue(model1.bindInstances.contains("instance11"))
      assertTrue(model1.bindInstances.contains("instance12"))
      assertTrue(model1.bindInstances.contains("instance13"))
    }

    locally {
      val model2 = partAnalysis.getModel("model2")
      assertTrue(model2.figuredAllBindRefAnalysis)
      assertFalse(model2.bindInstances.contains("instance21"))
    }

    locally {
      val model3 = partAnalysis.getModel("model3")
      assertTrue(model3.figuredAllBindRefAnalysis)
      assertFalse(model3.bindInstances.contains("instance31"))
      assertTrue(model3.bindInstances.contains("instance32"))
    }

    locally {
      val model4 = partAnalysis.getModel("model4")
      assertTrue(model4.figuredAllBindRefAnalysis)
      assertTrue(model4.bindInstances.contains("instance41"))
      assertFalse(model4.bindInstances.contains("instance42"))
    }

    locally {
      val model5 = partAnalysis.getModel("model5")
      assertTrue(model5.figuredAllBindRefAnalysis)
      assertTrue(model5.validationBindInstances.contains("instance51"))
      assertFalse(model5.computedBindExpressionsInstances.contains("instance51"))
      assertFalse(model5.validationBindInstances.contains("instance52"))
      assertTrue(model5.computedBindExpressionsInstances.contains("instance52"))
    }
  }

  // Mock document with just what's required by PathMapXPathDependencies
  private def mockDocument(staticState: XFormsStaticState): XFormsContainingDocument = {
    val mockDocument = Mockito.mock(classOf[XFormsContainingDocument])
    val mockControls = Mockito.mock(classOf[XFormsControls])

    Mockito.when(mockDocument.indentedLogger).thenReturn(staticState.getIndentedLogger)

    val ops = new StaticStateGlobalOps(staticState.topLevelPart)

    Mockito.when(mockDocument.staticOps).thenReturn(ops)
    Mockito.when(mockDocument.controls).thenReturn(mockControls)
    Mockito.when(mockDocument.getIndentedLogger(Matchers.anyString)).thenReturn(staticState.getIndentedLogger)

    Mockito.when(mockControls.indentedLogger).thenReturn(staticState.getIndentedLogger)

    mockDocument
  }

  private def mockModel(effectiveId: String, sequenceNumber: Int) = {
    val mockModel = Mockito.mock(classOf[XFormsModel])
    Mockito.when(mockModel.getPrefixedId).thenReturn(XFormsId.getPrefixedId(effectiveId))
    Mockito.when(mockModel.effectiveId).thenReturn(effectiveId)
    Mockito.when(mockModel.getEffectiveId).thenReturn(effectiveId)
    Mockito.when(mockModel.sequenceNumber).thenReturn(sequenceNumber)
    mockModel
  }

  private def mockInstance(effectiveId: String, model: XFormsModel) = {
    val mockInstance = Mockito.mock(classOf[XFormsInstance])
    Mockito.when(mockInstance.getPrefixedId).thenReturn(XFormsId.getPrefixedId(effectiveId))
    Mockito.when(mockInstance.getEffectiveId).thenReturn(effectiveId)
    Mockito.when(mockInstance.model).thenReturn(model)
    mockInstance
  }

  private def requireBindingUpdate(effectiveId: String)(implicit dependencies: XPathDependencies, partAnalysis: PartAnalysis) =
    dependencies.requireBindingUpdate(partAnalysis.getControlAnalysis(XFormsId.getPrefixedId(effectiveId)), effectiveId)

  private def requireValueUpdate(effectiveId: String)(implicit dependencies: XPathDependencies, partAnalysis: PartAnalysis) =
    dependencies.requireValueUpdate(partAnalysis.getControlAnalysis(XFormsId.getPrefixedId(effectiveId)), effectiveId)

  @Test def xpathAnalysis(): Unit = {
    Assume.assumeTrue(Version.isPE)

    val staticState = getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/form.xhtml")

    implicit val dependencies = new PathMapXPathDependencies(mockDocument(staticState))
    implicit val partAnalysis = staticState.topLevelPart

    val model1 = mockModel("model1", 1)
    val instanceDefault = mockInstance("default", model1)

    val model2 = mockModel("model2", 2)
    val instance2 = mockInstance("instance2", model2)

    // == Value change to default ==================================================================================
    dependencies.markValueChangedTest(instanceDefault, EmptyNamespaces, "a")

    withRefresh {
      assertFalse(requireBindingUpdate("trigger1"))
      assertFalse(requireBindingUpdate("trigger2"))
      assertFalse(requireBindingUpdate("select1"))
      assertTrue(requireValueUpdate("select1"))
      assertTrue(requireBindingUpdate("group2"))
      assertTrue(requireBindingUpdate("select2"))
      assertTrue(requireValueUpdate("select2"))
      assertFalse(requireBindingUpdate("group3"))
      assertFalse(requireBindingUpdate("select3"))
      assertFalse(requireValueUpdate("select3"))
      assertFalse(requireBindingUpdate("group4"))
      assertFalse(requireBindingUpdate("select4"))
      assertFalse(requireValueUpdate("select4"))
    }

    // == Value change to default ==================================================================================
    dependencies.markValueChangedTest(instanceDefault, EmptyNamespaces, "b")

    withRefresh {
      assertFalse(requireBindingUpdate("trigger1"))
      assertFalse(requireBindingUpdate("trigger2"))
      assertFalse(requireBindingUpdate("select1"))
      assertFalse(requireValueUpdate("select1"))
      assertFalse(requireBindingUpdate("group2"))
      assertFalse(requireBindingUpdate("select2"))
      assertTrue(requireValueUpdate("select2"))
      assertFalse(requireBindingUpdate("group3"))
      assertFalse(requireBindingUpdate("select3"))
      assertFalse(requireValueUpdate("select3"))
      assertFalse(requireBindingUpdate("group4"))
      assertFalse(requireBindingUpdate("select4"))
      assertFalse(requireValueUpdate("select4"))
    }

    // == Value change to instance2 ================================================================================
    dependencies.markValueChangedTest(instance2, EmptyNamespaces, "a")

    withRefresh {
      assertFalse(requireBindingUpdate("trigger1"))
      assertFalse(requireBindingUpdate("trigger2"))
      assertFalse(requireBindingUpdate("select1"))
      assertFalse(requireValueUpdate("select1"))
      assertFalse(requireBindingUpdate("group2"))
      assertFalse(requireBindingUpdate("select2"))
      assertFalse(requireValueUpdate("select2"))
      assertFalse(requireBindingUpdate("group3"))
      assertFalse(requireBindingUpdate("select3"))
      assertTrue(requireValueUpdate("select3"))
      assertTrue(requireBindingUpdate("group4"))
      assertTrue(requireBindingUpdate("select4"))
      assertTrue(requireValueUpdate("select4"))
    }

    // == Value change to instance2 ================================================================================
    dependencies.markValueChangedTest(instance2, EmptyNamespaces, "b")

    withRefresh {
      assertFalse(requireBindingUpdate("trigger1"))
      assertFalse(requireBindingUpdate("trigger2"))
      assertFalse(requireBindingUpdate("select1"))
      assertFalse(requireValueUpdate("select1"))
      assertFalse(requireBindingUpdate("group2"))
      assertFalse(requireBindingUpdate("select2"))
      assertFalse(requireValueUpdate("select2"))
      assertFalse(requireBindingUpdate("group3"))
      assertFalse(requireBindingUpdate("select3"))
      assertFalse(requireValueUpdate("select3"))
      assertFalse(requireBindingUpdate("group4"))
      assertFalse(requireBindingUpdate("select4"))
      assertTrue(requireValueUpdate("select4"))
    }

    // == Structural change to model1 ==============================================================================
    dependencies.markStructuralChangeTest(model1)

    withRefresh {
      assertTrue(requireBindingUpdate("trigger1"))
      assertFalse(requireBindingUpdate("trigger2"))
      assertTrue(requireBindingUpdate("select1"))
      assertTrue(requireValueUpdate("select1"))
      assertTrue(requireBindingUpdate("group2"))
      assertTrue(requireBindingUpdate("select2"))
      assertTrue(requireValueUpdate("select2"))
      assertFalse(requireBindingUpdate("group3"))
      assertFalse(requireBindingUpdate("select3"))
      assertFalse(requireValueUpdate("select3"))
      assertFalse(requireBindingUpdate("group4"))
      assertFalse(requireBindingUpdate("select4"))
      assertFalse(requireValueUpdate("select4"))
    }

    // == Structural change to model2 ==============================================================================
    dependencies.markStructuralChangeTest(model2)

    withRefresh {
      assertFalse(requireBindingUpdate("trigger1"))
      assertTrue(requireBindingUpdate("trigger2"))
      assertFalse(requireBindingUpdate("select1"))
      assertFalse(requireValueUpdate("select1"))
      assertFalse(requireBindingUpdate("group2"))
      assertFalse(requireBindingUpdate("select2"))
      assertFalse(requireValueUpdate("select2"))
      assertTrue(requireBindingUpdate("group3"))
      assertTrue(requireBindingUpdate("select3"))
      assertTrue(requireValueUpdate("select3"))
      assertTrue(requireBindingUpdate("group4"))
      assertTrue(requireBindingUpdate("select4"))
      assertTrue(requireValueUpdate("select4"))
    }
  }

  @Test def variables(): Unit = {
    Assume.assumeTrue(Version.isPE)

    val staticState = getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/variables.xhtml")

    implicit val dependencies = new PathMapXPathDependencies(mockDocument(staticState))
    implicit val partAnalysis = staticState.topLevelPart

    val model1 = mockModel("model1", 1)
    val instanceDefault = mockInstance("default", model1)

    // == Value change to default ==================================================================================
    dependencies.markValueChangedTest(instanceDefault, EmptyNamespaces, "value")

    withRefresh {
      assertFalse(requireBindingUpdate("values"))
      assertTrue(requireValueUpdate("values"))
      assertFalse(requireBindingUpdate("repeat"))
      assertFalse(requireBindingUpdate("value"))
      assertTrue(requireValueUpdate("value"))
      assertFalse(requireBindingUpdate("input"))
      assertTrue(requireValueUpdate("input"))
    }
  }

  @Test def modelVariables(): Unit = {
    Assume.assumeTrue(Version.isPE)

    val staticState = getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/model-variables.xhtml")

    implicit val dependencies = new PathMapXPathDependencies(mockDocument(staticState))
    implicit val partAnalysis = staticState.topLevelPart

    val model1 = mockModel("model1", 1)
    val instance1 = mockInstance("instance1", model1)

    val model2 = mockModel("model2", 2)
    val instance2 = mockInstance("instance2", model2)

    // == Value change to instance1 ============================================================================

    dependencies.markValueChangedTest(instance1, EmptyNamespaces, "")

    withRefresh {
      // No binding update
      assertFalse(requireBindingUpdate("output1"))
      assertFalse(requireBindingUpdate("group2"))
      assertFalse(requireBindingUpdate("output2a"))
      assertFalse(requireBindingUpdate("output2b"))
      assertFalse(requireBindingUpdate("group3"))
      assertFalse(requireBindingUpdate("output3a"))
      assertFalse(requireBindingUpdate("output3b"))
      assertFalse(requireBindingUpdate("group4"))
      assertFalse(requireBindingUpdate("output4a"))
      assertFalse(requireBindingUpdate("output4b"))

      // $mv pointing to model1 must update their value
      assertTrue(requireValueUpdate("output1"))
      assertTrue(requireValueUpdate("output2b"))
      assertTrue(requireValueUpdate("output3a"))
      assertTrue(requireValueUpdate("output3b"))
      assertTrue(requireValueUpdate("output4a"))
      assertTrue(requireValueUpdate("output4b"))
      assertTrue(requireValueUpdate("output5a"))

      // $mv pointing to model2 must not update
      assertFalse(requireValueUpdate("output2a"))
      assertFalse(requireValueUpdate("output3c"))
      assertFalse(requireValueUpdate("output4c"))
      assertFalse(requireValueUpdate("output5b"))
    }

    // == Value change to instance2 ============================================================================

    dependencies.markValueChangedTest(instance2, EmptyNamespaces, "")

    withRefresh {
      // No binding update
      assertFalse(requireBindingUpdate("output1"))
      assertFalse(requireBindingUpdate("group2"))
      assertFalse(requireBindingUpdate("output2a"))
      assertFalse(requireBindingUpdate("output2b"))
      assertFalse(requireBindingUpdate("group3"))
      assertFalse(requireBindingUpdate("output3a"))
      assertFalse(requireBindingUpdate("output3b"))
      assertFalse(requireBindingUpdate("group4"))
      assertFalse(requireBindingUpdate("output4a"))
      assertFalse(requireBindingUpdate("output4b"))

      // $mv pointing to model1 must not update their value
      assertFalse(requireValueUpdate("output1"))
      assertFalse(requireValueUpdate("output2b"))
      assertFalse(requireValueUpdate("output3a"))
      assertFalse(requireValueUpdate("output3b"))
      assertFalse(requireValueUpdate("output4a"))
      assertFalse(requireValueUpdate("output4b"))
      assertFalse(requireValueUpdate("output5a"))

      // $mv pointing to model2 must update
      assertTrue(requireValueUpdate("output2a"))
      assertTrue(requireValueUpdate("output3c"))
      assertTrue(requireValueUpdate("output4c"))
      assertTrue(requireValueUpdate("output5b"))
    }
  }

  def buildEffectiveId(prefixedId: String, iterations: Iterable[Int]): String = {
    if (iterations.isEmpty)
      prefixedId
    else
      prefixedId + RepeatSeparatorString + (iterations mkString RepeatIndexSeparatorString)
  }

  @Test def repeatedModels(): Unit = {
    Assume.assumeTrue(Version.isPE)

    val staticState = getStaticState("oxf:/org/orbeon/oxf/xforms/analysis/repeated-models.xhtml")

    implicit val dependencies = new PathMapXPathDependencies(mockDocument(staticState))
    implicit val partAnalysis = staticState.topLevelPart

    object ModelSeq {
      private var seq = 0
      def nextSeq() = {
        seq += 1
        seq
      }
    }

    import ModelSeq._

    val topLevelModel    = mockModel("model", nextSeq())
    val topLevelInstance = mockInstance("instance", topLevelModel)

    val acmeFooModel1    = mockModel(buildEffectiveId("foo-1≡acme-foo-model", List(1)), nextSeq())
    val acmeFooInstance1 = mockInstance(buildEffectiveId("foo-1≡acme-foo-instance", List(1)), acmeFooModel1)

    val acmeFooModel2    = mockModel(buildEffectiveId("foo-1≡acme-foo-model", List(2)), nextSeq())
    val acmeFooInstance2 = mockInstance(buildEffectiveId("foo-1≡acme-foo-instance", List(2)), acmeFooModel2)

    def assertMatrix(expectedValues: List[Boolean]) =
      withRefresh {
        assert(8 === expectedValues.size)

        val results =
          for {
            prefixedId  <- List("foo-1≡acme-foo-input", "foo-1≡acme-foo-output")
            iteration   <- 1 to 2
            fn          <- List(requireBindingUpdate(_), requireValueUpdate(_))
            effectiveId = buildEffectiveId(prefixedId, List(iteration))
          } yield
            fn(effectiveId)

        assert(8 === results.size)

        for ((e, a) <- expectedValues.zip(results))
          assertTrue(e === a)
      }

    def assertNoUpdates() = {
      assertMatrix(List.fill(8)(false))
    }

    assertNoUpdates()

    // Value in model within iteration 1
    dependencies.markValueChangedTest(acmeFooInstance1, EmptyNamespaces, "")

    assertMatrix(List(false, true, false, false, false, true, false, false))
    assertNoUpdates()

    // Value in model within iteration 2
    dependencies.markValueChangedTest(acmeFooInstance2, EmptyNamespaces, "")

    assertMatrix(List(false, false, false, true, false, false, false, true))

    // Value in model within iteration 1 and 2
    dependencies.markValueChangedTest(acmeFooInstance1, EmptyNamespaces, "")
    dependencies.markValueChangedTest(acmeFooInstance2, EmptyNamespaces, "")

    assertMatrix(List(false, true, false, true, false, true, false, true))

    // Structural change in model within iteration 1
    dependencies.markStructuralChangeTest(acmeFooModel1)

    assertMatrix(List(true, true, false, false, true, true, false, false))

    // Structural change in model within iteration 2
    dependencies.markStructuralChangeTest(acmeFooModel2)

    assertMatrix(List(false, false, true, true, false, false, true, true))

    // Structural change in model within iteration 1 and 2
    dependencies.markStructuralChangeTest(acmeFooModel1)
    dependencies.markStructuralChangeTest(acmeFooModel2)

    assertMatrix(List(true, true, true, true, true, true, true, true))

    // Change to outer value
    dependencies.markValueChangedTest(topLevelInstance, EmptyNamespaces, "company/employee")

    assertMatrix(List(false, false, false, false, false, true, false, true))
  }
}