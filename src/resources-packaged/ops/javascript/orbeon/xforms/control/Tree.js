/**
 * Copyright (C) 2010 Orbeon, Inc.
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

    var OD = ORBEON.util.Dom;
    var Control = ORBEON.xforms.control.Control;
    var Page = ORBEON.xforms.Page;
    var YD = YAHOO.util.Dom;

    /**
     * Tree, corresponding to <xforms:select appearance="xxforms:tree"> or <xforms:select1 appearance="xxforms:tree">.
     *
     * @constructor
     * @extends {ORBEON.xforms.control.Control}
     */
    ORBEON.xforms.control.Tree = function() {};
    var Tree = ORBEON.xforms.control.Tree;
    Tree.prototype = new Control();

    /** @private @type {boolean} */
    Tree.prototype.itemsetHasOpenAnnotation = false;

    /**
     * @override
     * @param {HTMLElement}     container
     */
    Tree.prototype.init = function(container) {
        Control.prototype.init.call(this, container);
        var controlId = container.id;
        var allowMultipleSelection = YD.hasClass(container, "xforms-select");
        var showToolTip = YD.hasClass(container, "xforms-show-tooltip");
        if (ORBEON.util.Utils.isNewXHTMLLayout())
            container = container.getElementsByTagName("div")[0];
        // Save in the control if it allows multiple selection
        container.xformsAllowMultipleSelection = allowMultipleSelection;
        // Parse data put by the server in the div
        var treeString = OD.getStringValue(container);
        var treeArray = ORBEON.util.String.eval(treeString);
        OD.setStringValue(container, "");
        container.value = "";
        // Create YUI tree and save a copy
        var yuiTree = new YAHOO.widget.TreeView(container.id);
        ORBEON.xforms.Globals.treeYui[controlId] = yuiTree;
        // Build the tree if there is something to build (JSON is not an empty string)
        if (! YAHOO.lang.isUndefined(treeArray))
            this.initTreeDivFromArray(container, yuiTree, treeArray);
        // Save value in tree
        ORBEON.xforms.ServerValueStore.set(controlId, container.value);
        // Register event handler for click on label
        yuiTree.subscribe("labelClick", ORBEON.xforms.Events.treeLabelClick);
        yuiTree.subscribe("enterKeyPressed", ORBEON.xforms.Events.treeLabelClick);
        // By default clicking on an item expends the tree; we just want that to select the item,
        // so here we return false to disable the default behavior
        yuiTree.subscribe("clickEvent", function(object) { return false; });
        if (showToolTip) {
            function addTreeToolTip() {
                var nodes = yuiTree.getNodesByProperty();
                // Nodes can be null when the tree is empty
                if (nodes != null) {
                    for (var nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
                        var node = nodes[nodeIndex];
                        if (node.children.length == 0) continue;
                        var contentEl = OD.get(node.contentElId);
                        if (contentEl == null) continue; // This node isn't visible on page yet
                        var iconEl = contentEl.previousSibling;
                        new YAHOO.widget.Tooltip(contentEl.id + "-orbeon-tree-tooltip", {
                            context: iconEl.id,
                            text: (node.expanded? "Collapse " : "Expand ") + node.label,
                            showDelay: 100
                        });
                     }
                }
            }

            // Add initial tooltips
            addTreeToolTip();
            // When nodes are expanded or collapse, reset tooltips as they might have changed (new nodes, expand switched with collapse)
            yuiTree.subscribe("expandComplete", function() { addTreeToolTip(); });
            yuiTree.subscribe("collapseComplete", function() { addTreeToolTip(); });
        }
        // Show the tree now that it has been built
        YD.removeClass(ORBEON.util.Utils.isNewXHTMLLayout() ? container.parentNode : container, "xforms-initially-hidden");
    };

    Tree.prototype.addToTree = function (treeDiv, nodeInfoArray, treeNode) {
        for (var nodeIndex = 0; nodeIndex < nodeInfoArray.length; nodeIndex++) {
            var nodeInfo = nodeInfoArray[nodeIndex];

            // Normalize nodeInfo
            if (YAHOO.lang.isUndefined(nodeInfo.attributes)) nodeInfo.attributes = {};
            if (YAHOO.lang.isUndefined(nodeInfo.selected)) nodeInfo.selected = false;
            if (YAHOO.lang.isUndefined(nodeInfo.children)) nodeInfo.children = [];

            // Create node and add to tree
            var nodeInformation = {
                label: nodeInfo.label,
                value: nodeInfo.value,
                labelStyle: "ygtvlabel" + (nodeInfo.attributes["class"] ? " " + nodeInfo.attributes["class"] : ""),
                renderHidden: true
            };
            // Remember we have seen information about open nodes
            if (! YAHOO.lang.isUndefined(nodeInfo.attributes["xxforms-open"])) this.itemsetHasOpenAnnotation = true;
            var expanded = nodeInfo.attributes["xxforms-open"] == "true";
            /** @type {YAHOO.widget.Node} */ var childNode;
            if (treeDiv.xformsAllowMultipleSelection) {
                childNode = new YAHOO.widget.TaskNode(nodeInformation, treeNode, expanded);
                childNode.onCheckClick = ORBEON.xforms.Events.treeCheckClick;
            } else {
                childNode = new YAHOO.widget.TextNode(nodeInformation, treeNode, expanded);
            }
            this.addToTree(treeDiv, nodeInfo.children, childNode);
            // Add this value to the list if selected
            if (nodeInfo.selected) {
                if (treeDiv.value != "") treeDiv.value += " ";
                treeDiv.value += nodeInfo.value;
            }
        }
    };

    Tree.prototype.initTreeDivFromArray = function(treeDiv, yuiTree, treeArray) {
        // Populate the tree
        var treeRoot = yuiTree.getRoot();
        this.addToTree(treeDiv, treeArray, treeRoot);
        // For select tree, check the node that are selected
        if (treeDiv.xformsAllowMultipleSelection) {
            var values = treeDiv.value.split(" ");
            var nodes = yuiTree.getNodesByProperty();
            // nodes can be null when the tree is empty
            if (nodes != null) {
                for (var nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
                    var node = nodes[nodeIndex];
                    var currentValue = node.data.value;
                    for (var valueIndex = 0; valueIndex < values.length; valueIndex++) {
                        if (currentValue == values[valueIndex]) {
                            node.check();
                            break;
                        }
                    }
                }
            }
        }
        // Make selected nodes visible if xxforms:open isn't used
        if (! this.itemsetHasOpenAnnotation) {
            values = treeDiv.xformsAllowMultipleSelection ? treeDiv.value.split(" ") : [ treeDiv.value ];
            ORBEON.xforms.Controls.treeOpenSelectedVisible(yuiTree, values);
        }
        // Draw the tree the first time
        yuiTree.draw();
        // Show the currently selected value
        if (!treeDiv.xformsAllowMultipleSelection) {
            var selectedNode = yuiTree.getNodeByProperty("value", treeDiv.value);
            // Handle cases where the current value is not in the tree. In most cases this is because the value is
            // empty string; no value has been selected yet.
            if (selectedNode != null)
                YD.addClass(selectedNode.getLabelEl(), "xforms-tree-label-selected");
        }
    };

    /**
     * @override
     */
    Tree.prototype.getValue = function() {
        var oneValue = YAHOO.util.Dom.hasClass(this.container, "xforms-select1-appearance-xxforms-tree");
        var yuiTree = ORBEON.xforms.Globals.treeYui[this.container.id];
        var result = "";
        for (var nodeIndex in yuiTree._nodes) {
            var node = yuiTree._nodes[nodeIndex];
            if (oneValue) {
                // Select1
                if (YAHOO.util.Dom.hasClass(node.getLabelEl(), "xforms-tree-label-selected"))
                    return node.data.value;
            } else {
                // Select
                if (node.checkState == 2) {
                    if (result != "") result += " ";
                    result += node.data.value;
                }
            }
        }
        return result;
    };


    /**
     * @override
     */
    Tree.prototype.setValue = function(value) {
        var yuiTree = ORBEON.xforms.Globals.treeYui[this.container.id];
        if (YD.hasClass(this.container, "xforms-select-appearance-xxforms-tree")) {
            // Select tree
            var values = value.split(" ");
            for (var nodeIndex in yuiTree._nodes) {
                var node = yuiTree._nodes[nodeIndex];
                if (node.children.length == 0) {
                    var checked = xformsArrayContains(values, node.data.value);
                    if (checked) node.check(); else node.uncheck();
                }
            }
        } else {
            // Select1 tree
            // Make sure the tree is open enough so the node with the new value is visible
            if (! this.itemsetHasOpenAnnotation)
                ORBEON.xforms.Controls.treeOpenSelectedVisible(yuiTree, [value]);
            // Deselect old value, select new value
            var currentValue = ORBEON.xforms.Controls.getCurrentValue(this.container);
            var oldNode = yuiTree.getNodeByProperty("value", currentValue);
            var newNode = yuiTree.getNodeByProperty("value", value);
            if (oldNode != null)
                YAHOO.util.Dom.removeClass(oldNode.getLabelEl(), "xforms-tree-label-selected");
            if (newNode != null)
                YAHOO.util.Dom.addClass(newNode.getLabelEl(), "xforms-tree-label-selected");
        }
    };

    /**
     * @override
     */
    Tree.prototype.setItemset = function(itemset) {

        // Case of a tree
        var yuiTree = ORBEON.xforms.Globals.treeYui[this.container.id];

        // Remember the values for the expanded nodes
        var expandedValues = [];
        var nodes = yuiTree.getNodesByProperty();
        if (! YAHOO.lang.isNull(nodes)) {
            for (var nodeIndex = 0; nodeIndex < nodes.length; nodeIndex++) {
                var node = nodes[nodeIndex];
                if (node.expanded) expandedValues.push(node.data.value);
            }
        }

        // Remove markup for current tree
        var yuiRoot = yuiTree.getRoot();
        yuiTree.removeChildren(yuiRoot);
        // Expand root; if we don't the tree with checkboxes does not show
        yuiRoot.expand();

        // Re-populate the tree
        var treeDiv = this.container;
        if (ORBEON.util.Utils.isNewXHTMLLayout())
            treeDiv = treeDiv.getElementsByTagName("div")[0];
        this.initTreeDivFromArray(treeDiv, yuiTree, itemset);

        // Expand nodes corresponding to values that were previously expanded
        for (var expandedValueIndex = 0; expandedValueIndex < expandedValues.length; expandedValueIndex++) {
            var expandedValue = expandedValues[expandedValueIndex];
            var nodeToExpand = yuiTree.getNodeByProperty("value", expandedValue);
            if (nodeToExpand != null) nodeToExpand.expand();
        }
    };

    Page.registerControlConstructor(Tree,  function(container) {
        return YD.hasClass(container, "xforms-select1-appearance-xxforms-tree")
            || YD.hasClass(container, "xforms-select-appearance-xxforms-tree");
    });

})();

