/**
 *  Copyright (C) 2004 Orbeon, Inc.
 *
 *  This program is free software; you can redistribute it and/or modify it under the terms of the
 *  GNU Lesser General Public License as published by the Free Software Foundation; either version
 *  2.1 of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Lesser General Public License for more details.
 *
 *  The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */
package org.orbeon.oxf.processor.pipeline.ast;

import org.apache.commons.collections.CollectionUtils;
import org.dom4j.Node;
import org.orbeon.oxf.common.ValidationException;
import org.orbeon.oxf.processor.pipeline.foreach.AbstractForEachProcessor;
import org.orbeon.oxf.xml.dom4j.LocationData;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;


public abstract class ASTNodeContainer {

    private Node node;
    private LocationData locationData;

    public Node getNode() {
        return node;
    }

    public void setNode(Node node) {
        this.node = node;
    }

    public void setLocationData(final LocationData locDat) {
        locationData = locDat;
    }

    /**
     * Use specified location if there is one.  Otherwise if a node has been provided use the
     * location of the node.  If no node and no location then return hull.
     */
    public LocationData getLocationData() {
        final LocationData ret;
        if (locationData != null) {
            ret = locationData;
        } else if (node == null) {
            ret = null;
        } else if (node.getNodeType() == org.dom4j.Node.ELEMENT_NODE) {
            ret = (LocationData) ((org.dom4j.Element) node).getData();
        } else if (node.getNodeType() == org.dom4j.Node.ATTRIBUTE_NODE) {
            ret = (LocationData) ((org.dom4j.Attribute) node).getData();
        } else {
            ret = null;
        }
        return ret;
    }

    public abstract void walk(ASTHandler handler);

    public void walkChildren(ASTHandler handler) {
    }

    protected void walk(List astNodes, ASTHandler handler) {
        for (Iterator i = astNodes.iterator(); i.hasNext();) {
            ASTNodeContainer astNode = (ASTNodeContainer) i.next();
            astNode.walk(handler);
        }
    }

    public IdInfo getIdInfo() {
        class IdInfoASTHandler extends ASTSimpleHandler {

            final IdInfo idInfo = new IdInfo();

            /**
             * Returns the id collected after this walked went through an AST.
             */
            public IdInfo getIdInfo() {
                return idInfo;
            }

            public void hrefId(ASTHrefId hrefId) {
                idInfo.getInputRefs().add(hrefId.getId());
            }

            public void output(ASTOutput output) {
                if (output.getId() != null)
                    idInfo.getOutputIds().add(output.getId());
                if (output.getRef() != null) {
                    if (idInfo.getOutputRefs().contains(output.getRef()))
                        throw new ValidationException("Output id '" + output.getRef()
                                + "' can be referenced only once", output.getLocationData());
                    idInfo.getOutputRefs().add(output.getRef());
                }
            }

            public boolean startChoose(ASTChoose choose) {
                choose.getHref().walk(this);

                // Get idInfo for each branch
                final IdInfo[] whenIdInfos;
                {
                    List whens = choose.getWhen();
                    whenIdInfos = new IdInfo[whens.size()];
                    int count = 0;
                    for (Iterator i = whens.iterator(); i.hasNext();) {
                        ASTWhen astWhen = (ASTWhen) i.next();
                        IdInfoASTHandler idInfoASTHandler = new IdInfoASTHandler();
                        astWhen.walk(idInfoASTHandler);
                        whenIdInfos[count] = idInfoASTHandler.getIdInfo();
                        Collection intersection = CollectionUtils.intersection
                                (whenIdInfos[count].getInputRefs(), whenIdInfos[count].getOutputIds());
                        whenIdInfos[count].getInputRefs().removeAll(intersection);
                        whenIdInfos[count].getOutputIds().removeAll(intersection);
                        count++;
                    }
                }

                // Make sure the output ids and output refs are the same for every branch
                if (whenIdInfos.length > 1) {
                    for (int i = 1; i < whenIdInfos.length; i++) {
                        if (!CollectionUtils.isEqualCollection(whenIdInfos[0].getOutputIds(), whenIdInfos[i].getOutputIds()))
                            throw new ValidationException("ASTChoose branch number " + (i + 1) +
                                    " does not declare the same ids " + whenIdInfos[0].getOutputIds().toString() +
                                    " as the previous branches " + whenIdInfos[i].getOutputIds().toString(),
                                    choose.getLocationData());
                        if (!CollectionUtils.isEqualCollection(whenIdInfos[0].getOutputRefs(), whenIdInfos[i].getOutputRefs()))
                            throw new ValidationException("ASTChoose branch number " + (i + 1) +
                                    " does not declare the same ids " + whenIdInfos[0].getOutputRefs().toString() +
                                    " as the previous branches " + whenIdInfos[i].getOutputRefs().toString(),
                                    choose.getLocationData());
                    }
                }

                // Add ids from all the branches
                for (int i = 0; i < whenIdInfos.length; i++)
                    idInfo.getInputRefs().addAll(whenIdInfos[i].getInputRefs());

                // Add output ids and output refs from first branch (they are the same for each branch)
                idInfo.getOutputIds().addAll(whenIdInfos[0].getOutputIds());
                idInfo.getOutputRefs().addAll(whenIdInfos[0].getOutputRefs());

                return false;
            }

            public boolean startForEach(ASTForEach forEach) {
                // Add contribution from <p:for-each> attributes
                forEach.getHref().walk(this);
                if (forEach.getRef() != null)
                    idInfo.getOutputRefs().add(forEach.getRef());
                if (forEach.getId() != null)
                    idInfo.getOutputIds().add(forEach.getId());
                forEach.getHref().walk(this);

                // Collect idInfo for all the statements
                final IdInfo statementsIdInfo;
                {
                    IdInfoASTHandler statementsIdInfoASTHandler = new IdInfoASTHandler();
                    statementsIdInfoASTHandler.getIdInfo().getOutputIds().add(AbstractForEachProcessor.FOR_EACH_CURRENT_INPUT);
                    for (Iterator i = forEach.getStatements().iterator(); i.hasNext();) {
                        ASTStatement statement = (ASTStatement) i.next();
                        statement.walk(statementsIdInfoASTHandler);
                    }
                    statementsIdInfo = statementsIdInfoASTHandler.getIdInfo();
                    Collection intersection = CollectionUtils.intersection
                            (statementsIdInfo.getInputRefs(), statementsIdInfo.getOutputIds());
                    statementsIdInfo.getInputRefs().removeAll(intersection);
                    statementsIdInfo.getOutputIds().removeAll(intersection);
                }

                // Check the refs
                if (forEach.getId() == null && forEach.getRef() == null) {
                    if (statementsIdInfo.getOutputRefs().size() != 0)
                        throw new ValidationException("The statements in a <for-each> cannot have output ref; they reference "
                                + statementsIdInfo.getOutputRefs().toString(), forEach.getLocationData());
                } else if (statementsIdInfo.getOutputRefs().size() != 1) {
                    throw new ValidationException("The statements in a <for-each> must have exactly one output ref; "
                            + (statementsIdInfo.getOutputRefs().isEmpty() ? "this <for-each> has none" :
                            "this <for-each> defined: " + statementsIdInfo.getOutputRefs().toString()),
                            forEach.getLocationData());
                } else {
                    String statementsRef = (String) statementsIdInfo.getOutputRefs().iterator().next();
                    if (forEach.getId() != null) {
                        if (!forEach.getId().equals(statementsRef))
                            throw new ValidationException("The statements in a <for-each> referenced the id '"
                                    + statementsRef + "' but the id declared on the <for-each> is '"
                                    + forEach.getId() + "'", forEach.getLocationData());
                    } else if (!forEach.getRef().equals(statementsRef)) {
                        throw new ValidationException("The statements in a <for-each> referenced the id '"
                                + statementsRef + "' but the ref declared on the <for-each> is '"
                                + forEach.getRef() + "'", forEach.getLocationData());
                    }
                }

                // Add ids referenced inside <for-each>
                idInfo.getInputRefs().addAll(statementsIdInfo.getInputRefs());

                return false;
            }
        }

        // Run the handler declared above on this node
        IdInfoASTHandler idInfoASTHandler = new IdInfoASTHandler();
        walk(idInfoASTHandler);
        return idInfoASTHandler.getIdInfo();
    }
}
