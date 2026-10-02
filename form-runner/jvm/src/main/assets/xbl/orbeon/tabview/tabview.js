/**
 * Copyright (C) 2010-2026 Orbeon, Inc.
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

(function() {
    var TabView = {

        activeIndex: -1,
        _navClickHandler: null,

        _getNav: function() {
            var navs = this.container.querySelectorAll(".yui-nav");
            for (var i = 0; i < navs.length; i++) {
                if (navs[i].closest(".xbl-fr-tabview") === this.container) {
                    return navs[i];
                }
            }
            return null;
        },

        _getTabs: function() {
            var nav = this._getNav();
            if (!nav) return [];
            var tabs = [];
            for (var i = 0; i < nav.children.length; i++) {
                if (nav.children[i].tagName === "LI") {
                    tabs.push(nav.children[i]);
                }
            }
            return tabs;
        },

        _getContentParent: function() {
            var contentParents = this.container.querySelectorAll(".yui-content");
            for (var i = 0; i < contentParents.length; i++) {
                if (contentParents[i].closest(".xbl-fr-tabview") === this.container) {
                    return contentParents[i];
                }
            }
            return null;
        },

        _getContentPanes: function() {
            var contentParent = this._getContentParent();
            return contentParent ? Array.from(contentParent.children) : [];
        },

        /**
         * Find position of this element amongst its sibling elements
         */
        getElementIndex: function(element) {
            if (!element || !element.parentNode) return -1;
            var children = element.parentNode.children;
            for (var i = 0; i < children.length; i++) {
                if (children[i] === element) return i;
            }
            return -1;
        },

        /**
         * Constructor
         */
        init: function() {
            var nav = this._getNav();
            if (!nav) return;

            var tabs = this._getTabs();
            var contentPanes = this._getContentPanes();

            // Find active tab index: look for tab with class 'selected'
            var activeIndex = -1;
            for (var i = 0; i < tabs.length; i++) {
                if (tabs[i].classList.contains("selected")) {
                    activeIndex = i;
                    break;
                }
            }

            // Fallback to first non-disabled tab or first tab
            if (activeIndex === -1 && tabs.length > 0) {
                for (var j = 0; j < tabs.length; j++) {
                    if (!tabs[j].classList.contains("disabled") && !tabs[j].classList.contains("xforms-disabled")) {
                        activeIndex = j;
                        break;
                    }
                }
                if (activeIndex === -1) {
                    activeIndex = 0;
                }
                tabs[activeIndex].classList.add("selected");
            }

            this.activeIndex = activeIndex;

            // Update content panes visibility
            for (var k = 0; k < contentPanes.length; k++) {
                if (k === activeIndex) {
                    contentPanes[k].classList.remove("yui-hidden");
                } else {
                    contentPanes[k].classList.add("yui-hidden");
                }
            }

            // Attach click handler on nav container once
            if (!this._navClickHandler) {
                var self = this;
                this._navClickHandler = function(event) {
                    var target = event.target;
                    var li = target.closest("li");
                    if (!li || li.parentElement !== nav) return;

                    event.preventDefault();

                    if (li.classList.contains("disabled") || li.classList.contains("xforms-disabled")) {
                        return;
                    }

                    var currentTabs = self._getTabs();
                    var index = currentTabs.indexOf(li);
                    if (index !== -1) {
                        self.selectTab(index);
                    }
                };
                nav.addEventListener("click", this._navClickHandler);
            }
        },

        destroy: function() {
            var nav = this._getNav();
            if (nav && this._navClickHandler) {
                nav.removeEventListener("click", this._navClickHandler);
                this._navClickHandler = null;
            }
        },

        /**
         * Switch to tab at given index
         */
        selectTab: function(newIndex) {
            var tabs = this._getTabs();
            var contentPanes = this._getContentPanes();

            if (newIndex < 0 || newIndex >= tabs.length) return;
            if (newIndex === this.activeIndex) return;

            var newTab = tabs[newIndex];
            if (newTab.classList.contains("disabled") || newTab.classList.contains("xforms-disabled")) {
                return;
            }

            var oldIndex = this.activeIndex;
            var oldTab = oldIndex >= 0 && oldIndex < tabs.length ? tabs[oldIndex] : null;
            var oldContent = oldIndex >= 0 && oldIndex < contentPanes.length ? contentPanes[oldIndex] : null;
            var newContent = newIndex < contentPanes.length ? contentPanes[newIndex] : null;

            // Remove selected from any previously selected tab
            for (var i = 0; i < tabs.length; i++) {
                if (i !== newIndex && tabs[i].classList.contains("selected")) {
                    tabs[i].classList.remove("selected");
                }
            }
            // Hide all other content panes
            for (var j = 0; j < contentPanes.length; j++) {
                if (j !== newIndex && !contentPanes[j].classList.contains("yui-hidden")) {
                    contentPanes[j].classList.add("yui-hidden");
                }
            }

            newTab.classList.add("selected");
            if (newContent) {
                newContent.classList.remove("yui-hidden");
            }

            this.activeIndex = newIndex;

            // Dispatch fr-deselect to previous tab and fr-select to newly selected tab
            if (oldContent && oldContent.id) {
                ORBEON.xforms.Document.dispatchEvent({
                    targetId: oldContent.id,
                    eventName: "fr-deselect"
                });
            }
            if (newContent && newContent.id) {
                ORBEON.xforms.Document.dispatchEvent({
                    targetId: newContent.id,
                    eventName: "fr-select"
                });
            }
        },

        /**
         * Respond to fr-toggle event.
         */
        toggle: function(groupElement) {
            var tabIndex = this.getElementIndex(groupElement);
            if (tabIndex !== -1) {
                this.selectTab(tabIndex);
            }
        },

        readonly: function(groupElement) {
            var tabIndex = this.getElementIndex(groupElement);
            var tabs = this._getTabs();
            if (tabIndex >= 0 && tabIndex < tabs.length) {
                tabs[tabIndex].classList.add("disabled");
            }
        },

        readwrite: function(groupElement) {
            var tabIndex = this.getElementIndex(groupElement);
            var tabs = this._getTabs();
            if (tabIndex >= 0 && tabIndex < tabs.length) {
                tabs[tabIndex].classList.remove("disabled");
            }
        }
    };

    ORBEON.xforms.XBL.declareCompanion("fr|tabview", TabView);

    window.ORBEON = window.ORBEON || {};
    ORBEON.xbl = ORBEON.xbl || {};
    ORBEON.xbl.fr = ORBEON.xbl.fr || {};
    ORBEON.xbl.fr.TabView = TabView;

    if (window.YAHOO) {
        YAHOO.namespace("xbl.fr");
        YAHOO.xbl.fr.TabView = TabView;
    }
})();
