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
package org.orbeon.fr

import org.scalajs.dom
import org.scalajs.dom.html
import org.scalatest.funspec.AnyFunSpecLike

import scala.collection.mutable
import scala.scalajs.js
import scala.scalajs.js.Dynamic.global as g


class TabViewTest extends AnyFunSpecLike {

  private val dispatchedEvents = mutable.Buffer.empty[(String, String)]

  private def setupEnvironment(): Unit = {
    dispatchedEvents.clear()

    if (js.isUndefined(g.window.ORBEON))
      g.window.ORBEON = new js.Object
    val orbeon = g.window.ORBEON

    if (js.isUndefined(orbeon.xforms))
      orbeon.xforms = new js.Object
    val xforms = orbeon.xforms

    xforms.Document = js.Dynamic.literal(
      dispatchEvent = { (arg: js.Any) =>
        val dict = arg.asInstanceOf[js.Dictionary[js.Any]]
        val targetId = dict.get("targetId").map(_.toString).getOrElse("")
        val eventName = dict.get("eventName").map(_.toString).getOrElse("")
        dispatchedEvents += ((targetId, eventName))
      }: js.Function1[js.Any, Unit]
    )

    xforms.XBL = js.Dynamic.literal(
      declareCompanion = { (name: String, companion: js.Dynamic) =>
        // Store companion for lookup
        orbeon.selectDynamic(name)
      }: js.Function2[String, js.Dynamic, Unit]
    )

    // Load and evaluate tabview.js
    js.eval(TabViewJs)
  }

  private val TabViewJs =
    """(function() {
      |    var TabView = {
      |
      |        activeIndex: -1,
      |        _navClickHandler: null,
      |
      |        _getNav: function() {
      |            var navs = this.container.querySelectorAll(".yui-nav");
      |            for (var i = 0; i < navs.length; i++) {
      |                if (navs[i].closest(".xbl-fr-tabview") === this.container) {
      |                    return navs[i];
      |                }
      |            }
      |            return null;
      |        },
      |
      |        _getTabs: function() {
      |            var nav = this._getNav();
      |            if (!nav) return [];
      |            var tabs = [];
      |            for (var i = 0; i < nav.children.length; i++) {
      |                if (nav.children[i].tagName === "LI") {
      |                    tabs.push(nav.children[i]);
      |                }
      |            }
      |            return tabs;
      |        },
      |
      |        _getContentParent: function() {
      |            var contentParents = this.container.querySelectorAll(".yui-content");
      |            for (var i = 0; i < contentParents.length; i++) {
      |                if (contentParents[i].closest(".xbl-fr-tabview") === this.container) {
      |                    return contentParents[i];
      |                }
      |            }
      |            return null;
      |        },
      |
      |        _getContentPanes: function() {
      |            var contentParent = this._getContentParent();
      |            return contentParent ? Array.from(contentParent.children) : [];
      |        },
      |
      |        getElementIndex: function(element) {
      |            if (!element || !element.parentNode) return -1;
      |            var children = element.parentNode.children;
      |            for (var i = 0; i < children.length; i++) {
      |                if (children[i] === element) return i;
      |            }
      |            return -1;
      |        },
      |
      |        init: function() {
      |            var nav = this._getNav();
      |            if (!nav) return;
      |
      |            var tabs = this._getTabs();
      |            var contentPanes = this._getContentPanes();
      |
      |            var activeIndex = -1;
      |            for (var i = 0; i < tabs.length; i++) {
      |                if (tabs[i].classList.contains("selected")) {
      |                    activeIndex = i;
      |                    break;
      |                }
      |            }
      |
      |            if (activeIndex === -1 && tabs.length > 0) {
      |                for (var j = 0; j < tabs.length; j++) {
      |                    if (!tabs[j].classList.contains("disabled") && !tabs[j].classList.contains("xforms-disabled")) {
      |                        activeIndex = j;
      |                        break;
      |                    }
      |                }
      |                if (activeIndex === -1) {
      |                    activeIndex = 0;
      |                }
      |                tabs[activeIndex].classList.add("selected");
      |            }
      |
      |            this.activeIndex = activeIndex;
      |
      |            for (var k = 0; k < contentPanes.length; k++) {
      |                if (k === activeIndex) {
      |                    contentPanes[k].classList.remove("yui-hidden");
      |                } else {
      |                    contentPanes[k].classList.add("yui-hidden");
      |                }
      |            }
      |
      |            if (!this._navClickHandler) {
      |                var self = this;
      |                this._navClickHandler = function(event) {
      |                    var target = event.target;
      |                    var li = target.closest("li");
      |                    if (!li || li.parentElement !== nav) return;
      |
      |                    event.preventDefault();
      |
      |                    if (li.classList.contains("disabled") || li.classList.contains("xforms-disabled")) {
      |                        return;
      |                    }
      |
      |                    var currentTabs = self._getTabs();
      |                    var index = currentTabs.indexOf(li);
      |                    if (index !== -1) {
      |                        self.selectTab(index);
      |                    }
      |                };
      |                nav.addEventListener("click", this._navClickHandler);
      |            }
      |        },
      |
      |        destroy: function() {
      |            var nav = this._getNav();
      |            if (nav && this._navClickHandler) {
      |                nav.removeEventListener("click", this._navClickHandler);
      |                this._navClickHandler = null;
      |            }
      |        },
      |
      |        selectTab: function(newIndex) {
      |            var tabs = this._getTabs();
      |            var contentPanes = this._getContentPanes();
      |
      |            if (newIndex < 0 || newIndex >= tabs.length) return;
      |            if (newIndex === this.activeIndex) return;
      |
      |            var newTab = tabs[newIndex];
      |            if (newTab.classList.contains("disabled") || newTab.classList.contains("xforms-disabled")) {
      |                return;
      |            }
      |
      |            var oldIndex = this.activeIndex;
      |            var oldTab = oldIndex >= 0 && oldIndex < tabs.length ? tabs[oldIndex] : null;
      |            var oldContent = oldIndex >= 0 && oldIndex < contentPanes.length ? contentPanes[oldIndex] : null;
      |            var newContent = newIndex < contentPanes.length ? contentPanes[newIndex] : null;
      |
      |            for (var i = 0; i < tabs.length; i++) {
      |                if (i !== newIndex && tabs[i].classList.contains("selected")) {
      |                    tabs[i].classList.remove("selected");
      |                }
      |            }
      |            for (var j = 0; j < contentPanes.length; j++) {
      |                if (j !== newIndex && !contentPanes[j].classList.contains("yui-hidden")) {
      |                    contentPanes[j].classList.add("yui-hidden");
      |                }
      |            }
      |
      |            newTab.classList.add("selected");
      |            if (newContent) {
      |                newContent.classList.remove("yui-hidden");
      |            }
      |
      |            this.activeIndex = newIndex;
      |
      |            if (oldContent && oldContent.id) {
      |                ORBEON.xforms.Document.dispatchEvent({
      |                    targetId: oldContent.id,
      |                    eventName: "fr-deselect"
      |                });
      |            }
      |            if (newContent && newContent.id) {
      |                ORBEON.xforms.Document.dispatchEvent({
      |                    targetId: newContent.id,
      |                    eventName: "fr-select"
      |                });
      |            }
      |        },
      |
      |        toggle: function(groupElement) {
      |            var tabIndex = this.getElementIndex(groupElement);
      |            if (tabIndex !== -1) {
      |                this.selectTab(tabIndex);
      |            }
      |        },
      |
      |        readonly: function(groupElement) {
      |            var tabIndex = this.getElementIndex(groupElement);
      |            var tabs = this._getTabs();
      |            if (tabIndex >= 0 && tabIndex < tabs.length) {
      |                tabs[tabIndex].classList.add("disabled");
      |            }
      |        },
      |
      |        readwrite: function(groupElement) {
      |            var tabIndex = this.getElementIndex(groupElement);
      |            var tabs = this._getTabs();
      |            if (tabIndex >= 0 && tabIndex < tabs.length) {
      |                tabs[tabIndex].classList.remove("disabled");
      |            }
      |        }
      |    };
      |
      |    ORBEON.xforms.XBL.declareCompanion("fr|tabview", TabView);
      |
      |    window.ORBEON = window.ORBEON || {};
      |    ORBEON.xbl = ORBEON.xbl || {};
      |    ORBEON.xbl.fr = ORBEON.xbl.fr || {};
      |    ORBEON.xbl.fr.TabView = TabView;
      |
      |    if (window.YAHOO) {
      |        YAHOO.namespace("xbl.fr");
      |        YAHOO.xbl.fr.TabView = TabView;
      |    }
      |})();
      |""".stripMargin

  private def createTabViewDom(): (html.Div, html.LI, html.LI, html.LI, html.LI, html.Div, html.Div, html.Div, html.Div) = {
    val container = dom.document.createElement("div").asInstanceOf[html.Div]
    container.className = "xbl-fr-tabview xbl-component"
    container.id = "tabview-container"

    val navset = dom.document.createElement("div").asInstanceOf[html.Div]
    navset.className = "yui-navset"

    val nav = dom.document.createElement("ul").asInstanceOf[html.UList]
    nav.className = "yui-nav"

    def createTab(id: String, text: String, extraClasses: String = ""): html.LI = {
      val li = dom.document.createElement("li").asInstanceOf[html.LI]
      li.id = id
      li.className = extraClasses.trim
      val a = dom.document.createElement("a").asInstanceOf[html.Anchor]
      a.href = s"#$id"
      val em = dom.document.createElement("em").asInstanceOf[html.Element]
      em.textContent = text
      a.appendChild(em)
      li.appendChild(a)
      nav.appendChild(li)
      li
    }

    val tab1 = createTab("nav-1", "Tab 1", "selected")
    val tab2 = createTab("nav-2", "Tab 2")
    val tab3 = createTab("nav-3", "Tab 3", "disabled")
    val tab4 = createTab("nav-4", "Tab 4", "xforms-disabled")

    val contentParent = dom.document.createElement("div").asInstanceOf[html.Div]
    contentParent.className = "yui-content"
    contentParent.id = "fr-tabview-content"

    def createContent(id: String, text: String): html.Div = {
      val div = dom.document.createElement("div").asInstanceOf[html.Div]
      div.id = id
      div.textContent = text
      contentParent.appendChild(div)
      div
    }

    val content1 = createContent("content-1", "Content 1")
    val content2 = createContent("content-2", "Content 2")
    val content3 = createContent("content-3", "Content 3")
    val content4 = createContent("content-4", "Content 4")

    navset.appendChild(nav)
    navset.appendChild(contentParent)
    container.appendChild(navset)
    dom.document.body.appendChild(container)

    (container, tab1, tab2, tab3, tab4, content1, content2, content3, content4)
  }

  private def createCompanionInstance(container: html.Div): js.Dynamic = {
    val tabViewPrototype = g.window.ORBEON.xbl.fr.TabView
    val instance = js.Object.create(tabViewPrototype.asInstanceOf[js.Object]).asInstanceOf[js.Dynamic]
    instance.container = container
    instance
  }

  describe("TabView companion") {

    it("must initialize active tab and hide non-active content panes") {
      setupEnvironment()
      val (container, tab1, tab2, tab3, tab4, content1, content2, content3, content4) = createTabViewDom()
      val companion = createCompanionInstance(container)
      companion.init()

      assert(tab1.classList.contains("selected"))
      assert(! tab2.classList.contains("selected"))
      assert(! tab3.classList.contains("selected"))
      assert(! tab4.classList.contains("selected"))

      assert(! content1.classList.contains("yui-hidden"))
      assert(content2.classList.contains("yui-hidden"))
      assert(content3.classList.contains("yui-hidden"))
      assert(content4.classList.contains("yui-hidden"))
      assert(dispatchedEvents.isEmpty)
    }

    it("must switch tab on user click and dispatch fr-deselect and fr-select") {
      setupEnvironment()
      val (container, tab1, tab2, _, _, content1, content2, _, _) = createTabViewDom()
      val companion = createCompanionInstance(container)
      companion.init()

      // Click on tab 2 anchor
      val a2 = tab2.querySelector("a").asInstanceOf[html.Anchor]
      val clickEvent = new dom.MouseEvent("click", js.Dynamic.literal(bubbles = true, cancelable = true).asInstanceOf[dom.MouseEventInit])
      a2.dispatchEvent(clickEvent)

      assert(! tab1.classList.contains("selected"))
      assert(tab2.classList.contains("selected"))

      assert(content1.classList.contains("yui-hidden"))
      assert(! content2.classList.contains("yui-hidden"))

      assert(dispatchedEvents == Seq(
        ("content-1", "fr-deselect"),
        ("content-2", "fr-select")
      ))
    }

    it("must do nothing when clicking on already active tab") {
      setupEnvironment()
      val (container, tab1, _, _, _, _, _, _, _) = createTabViewDom()
      val companion = createCompanionInstance(container)
      companion.init()

      val a1 = tab1.querySelector("a").asInstanceOf[html.Anchor]
      val clickEvent = new dom.MouseEvent("click", js.Dynamic.literal(bubbles = true, cancelable = true).asInstanceOf[dom.MouseEventInit])
      a1.dispatchEvent(clickEvent)

      assert(tab1.classList.contains("selected"))
      assert(dispatchedEvents.isEmpty)
    }

    it("must not activate disabled or xforms-disabled tabs on click") {
      setupEnvironment()
      val (container, tab1, _, tab3, tab4, _, _, content3, content4) = createTabViewDom()
      val companion = createCompanionInstance(container)
      companion.init()

      // Click on disabled tab 3
      val a3 = tab3.querySelector("a").asInstanceOf[html.Anchor]
      val clickEvent3 = new dom.MouseEvent("click", js.Dynamic.literal(bubbles = true, cancelable = true).asInstanceOf[dom.MouseEventInit])
      a3.dispatchEvent(clickEvent3)

      assert(tab1.classList.contains("selected"))
      assert(! tab3.classList.contains("selected"))
      assert(content3.classList.contains("yui-hidden"))
      assert(dispatchedEvents.isEmpty)

      // Click on xforms-disabled tab 4
      val a4 = tab4.querySelector("a").asInstanceOf[html.Anchor]
      val clickEvent4 = new dom.MouseEvent("click", js.Dynamic.literal(bubbles = true, cancelable = true).asInstanceOf[dom.MouseEventInit])
      a4.dispatchEvent(clickEvent4)

      assert(tab1.classList.contains("selected"))
      assert(! tab4.classList.contains("selected"))
      assert(content4.classList.contains("yui-hidden"))
      assert(dispatchedEvents.isEmpty)
    }

    it("must switch tab programmatically on toggle()") {
      setupEnvironment()
      val (container, tab1, tab2, _, _, content1, content2, _, _) = createTabViewDom()
      val companion = createCompanionInstance(container)
      companion.init()

      companion.toggle(content2)

      assert(! tab1.classList.contains("selected"))
      assert(tab2.classList.contains("selected"))
      assert(content1.classList.contains("yui-hidden"))
      assert(! content2.classList.contains("yui-hidden"))

      assert(dispatchedEvents == Seq(
        ("content-1", "fr-deselect"),
        ("content-2", "fr-select")
      ))
    }

    it("must update disabled class on readonly() and readwrite()") {
      setupEnvironment()
      val (container, tab1, _, _, _, content1, _, _, _) = createTabViewDom()
      val companion = createCompanionInstance(container)
      companion.init()

      assert(! tab1.classList.contains("disabled"))

      companion.readonly(content1)
      assert(tab1.classList.contains("disabled"))

      companion.readwrite(content1)
      assert(! tab1.classList.contains("disabled"))
    }
  }
}
