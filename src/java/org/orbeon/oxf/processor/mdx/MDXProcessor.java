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
package org.orbeon.oxf.processor.mdx;

import mondrian.olap.*;
import mondrian.rolap.RolapSchema;
import org.apache.log4j.Logger;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.Node;
import org.orbeon.oxf.common.OXFException;
import org.orbeon.oxf.processor.*;
import org.orbeon.oxf.util.LoggerFactory;
import org.orbeon.oxf.xml.ContentHandlerHelper;
import org.orbeon.oxf.xml.XMLUtils;
import org.orbeon.oxf.xml.dom4j.Dom4jUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.util.*;
import java.util.Set;

public class MDXProcessor extends ProcessorImpl {

    static private Logger logger = LoggerFactory.createLogger(MDXProcessor.class);
    public static String INPUT_OPEN = "open";
    public static String INPUT_MDX = "mdx";
    public static String INPUT_SCHEMA = "schema";

    public MDXProcessor() {
        addInputInfo(new ProcessorInputOutputInfo(INPUT_CONFIG));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_MDX));
        addInputInfo(new ProcessorInputOutputInfo(INPUT_OPEN));
        addOutputInfo(new ProcessorInputOutputInfo(OUTPUT_DATA));
    }

    public ProcessorOutput createOutput(String name) {
        ProcessorOutput output = new ProcessorImpl.CacheableTransformerOutputImpl(getClass(), name) {
            public void readImpl(org.orbeon.oxf.pipeline.api.PipelineContext context, ContentHandler contentHandler) {
                try {
                    // Read schema: get hierarchies with members
                    Schema schema = (Schema) readCacheInputAsObject(context, getInputByName(MDXProcessor.INPUT_CONFIG),
                            new CacheableInputReader() {
                                public Object read(org.orbeon.oxf.pipeline.api.PipelineContext context, ProcessorInput input) {

                                    Schema schema = new Schema();
                                    Element configElement = readInputAsDOM4J(context, input).getRootElement();
                                    schema.datasource = configElement.element("datasource").getStringValue();
                                    schema.cube = configElement.element("cube").getStringValue();
                                    schema.schemaXML = Dom4jUtils.domToString(configElement.element("Schema"));

                                    // Get hierarchies with more than one level
                                    for (Iterator i = configElement.selectNodes
                                            ("/config/Schema/Cube/Dimension").iterator(); i.hasNext();) {
                                        Element dimensionElement = (Element) i.next();
                                        Hierarchy hierarchy = new Hierarchy();
                                        schema.hierarchies.add(hierarchy);
                                        hierarchy.name = dimensionElement.attributeValue("name");

                                        // Add "All" member
                                        if ("true".equals(dimensionElement.selectObject("string(Hierarchy/@hasAll)"))) {
                                            HierarchyMember member = new HierarchyMember();
                                            String allMemberName = (String) dimensionElement.selectObject("string(Hierarchy/@allMemberName)");
                                            if (allMemberName.equals(""))
                                                allMemberName = "All " + hierarchy.name + "s";
                                            member.name = "[" + hierarchy.name + "].[" + allMemberName + "]";
                                            hierarchy.members.add(member);
                                            buildHierarchyMembers(schema.cube, schema.datasource, schema.schemaXML,
                                                    null, member.name, member.children);
                                        } else {
                                            buildHierarchyMembers(schema.cube, schema.datasource, schema.schemaXML,
                                                    "[" + hierarchy.name + "]", null, hierarchy.members);
                                        }
                                    }
                                    return schema;
                                }
                            });

                    // Get open elements and populate set
                    Element openElement = readCacheInputAsDOM4J(context, INPUT_OPEN).getRootElement();
                    Set openMembers = new HashSet();
                    for (Iterator i = openElement.elements("member").iterator(); i.hasNext();) {
                        Element memberElement = (Element) i.next();
                        openMembers.add(memberElement.getStringValue());
                    }

                    // Read MDX query and create copy (we don't want to modify the original)
                    Element mdxElement = ((Document) readCacheInputAsDOM4J(context, INPUT_MDX).clone()).getRootElement();

                    // Replace <hierarchy name="..."> elements in query
                    List openableMemberNames = new ArrayList();
                    for (Iterator i = mdxElement.selectNodes("//hierarchy").iterator(); i.hasNext();) {
                        Element hierarchyElement = (Element) i.next();
                        String hierarchyName = hierarchyElement.attributeValue("name");

                        // Find hierarchy
                        Hierarchy hierarchy = null;
                        for (Iterator j = schema.hierarchies.iterator(); j.hasNext();) {
                            Hierarchy candidateHierarchy = (Hierarchy) j.next();
                            if (candidateHierarchy.name.equals(hierarchyName)) {
                                hierarchy = candidateHierarchy;
                                break;
                            }
                        }
                        if (hierarchy == null)
                            throw new OXFException("Cannot find hierachy '" + hierarchyName + "' in schema");

                        // Create member list
                        StringBuffer memberSet = new StringBuffer("{ ");
                        buildMemberSet(openMembers, true, hierarchy.members, memberSet);
                        memberSet.append("}");

                        // Populate list of openable member names
                        buildOpenableMemberNames(hierarchy.members, openableMemberNames);

                        // Replace element with computed string
                        List before = new ArrayList();
                        List after = new ArrayList();
                        boolean foundElement = false;
                        Element parentElement = hierarchyElement.getParent();
                        for (Iterator j = parentElement.content().iterator(); j.hasNext();) {
                            Node node = (Node) j.next();
                            if (foundElement) {
                                after.add(node);
                            } else {
                                if (node == hierarchyElement) {
                                    foundElement = true;
                                } else {
                                    before.add(node);
                                }
                            }
                        }
                        parentElement.clearContent();
                        for (Iterator j = before.iterator(); j.hasNext();) {
                            Node node = (Node) j.next();
                            parentElement.add(node);
                        }
                        parentElement.addText(memberSet.toString());
                        for (Iterator j = after.iterator(); j.hasNext();) {
                            Node node = (Node) j.next();
                            parentElement.add(node);
                        }
                    }

                    // Execute query
                    Result result = executeQuery(schema.datasource, schema.schemaXML, mdxElement.getStringValue());

                    ContentHandlerHelper saxHelper = new ContentHandlerHelper(contentHandler);
                    saxHelper.startDocument();
                    saxHelper.startElement("table");

                    // Get axes information
                    Axis[] axes = result.getAxes();
                    if (axes.length != 2)
                        throw new OXFException("Only cubes with two dimensions are supported");
                    if (axes[0].positions.length == 0)
                        throw new OXFException("No tuples in column dimension");
                    if (axes[1].positions.length == 0)
                        throw new OXFException("No tuples in row dimension");
                    AxisCell[][][] axisCells = new AxisCell[2][][];
                    for (int axisIndex = 0; axisIndex < axes.length; axisIndex++) {
                        Axis axis = axes[axisIndex];
                        axisCells[axisIndex] = new AxisCell[axis.positions.length][];
                        for (int tupleIndex = 0; tupleIndex < axis.positions.length; tupleIndex++) {
                            Position position = axis.positions[tupleIndex];
                            axisCells[axisIndex][tupleIndex] = new AxisCell[position.members.length];
                            for (int memberIndex = 0; memberIndex < position.members.length; memberIndex++) {
                                axisCells[axisIndex][tupleIndex][memberIndex] =
                                        createAxisCell(openMembers, openableMemberNames, axis.positions, tupleIndex, memberIndex);
                            }
                        }
                    }

                    // Output top left cell
                    saxHelper.startElement("tr");
                    saxHelper.startElement("th", new String[]{
                        "colspan", Integer.toString(axisCells[1][0].length),
                        "rowspan", Integer.toString(axisCells[0][0].length)});
                    saxHelper.endElement();

                    // Output column headers
                    for (int line = 0; line < axisCells[0][0].length; line++) {
                        if (line > 0)
                            saxHelper.startElement("tr");
                        for (int column = 0; column < axisCells[0].length; column++) {
                            AxisCell axisCell = axisCells[0][column][line];
                            if (axisCell != null) {
                                AttributesImpl attributesImpl = saxHelper.getAttributesImpl();
                                attributesImpl.addAttribute("", "colspan", "colspan", "CDATA", Integer.toString(axisCell.span));
                                attributesImpl.addAttribute("", "depth", "depth", "CDATA", Integer.toString(axisCell.depth));
                                if (axisCell.member != null) {
                                    attributesImpl.addAttribute("", "open", "open", "CDATA",
                                            axisCell.open ? "true" : "false");
                                    attributesImpl.addAttribute("", "member", "member", "CDATA", axisCell.member);
                                }
                                saxHelper.startElement("th", attributesImpl);
                                saxHelper.text(axisCell.caption);
                                saxHelper.endElement();
                            }
                        }
                        saxHelper.endElement();
                    }

                    // Output lines
                    int[] position = new int[2];
                    for (int line = 0; line < axisCells[1].length; line++) {
                        saxHelper.startElement("tr");

                        // Output line header
                        for (int column = 0; column < axisCells[1][line].length; column++) {
                            AxisCell axisCell = axisCells[1][line][column];
                            if (axisCell != null) {
                                AttributesImpl attributesImpl = saxHelper.getAttributesImpl();
                                attributesImpl.addAttribute("", "rowspan", "rowspan", "CDATA", Integer.toString(axisCell.span));
                                attributesImpl.addAttribute("", "depth", "depth", "CDATA", Integer.toString(axisCell.depth));
                                if (axisCell.member != null) {
                                    attributesImpl.addAttribute("", "open", "open", "CDATA",
                                            axisCell.open ? "true" : "false");
                                    attributesImpl.addAttribute("", "member", "member", "CDATA", axisCell.member);
                                }
                                saxHelper.startElement("th", attributesImpl);
                                saxHelper.text(axisCell.caption);
                                saxHelper.endElement();
                            }
                        }

                        // Output cells on this line
                        for (int column = 0; column < axisCells[0].length; column++) {
                            position[0] = column;
                            position[1] = line;
                            saxHelper.startElement("td");
                            XMLUtils.parseDocumentFragment(result.getCell(position).getFormattedValue(), contentHandler);
                            saxHelper.endElement();
                        }
                        saxHelper.endElement();
                    }

                    saxHelper.endElement();
                    saxHelper.endDocument();
                } catch (SAXException e) {
                    throw new OXFException(e);
                }
            }
        };
        addOutput(name, output);
        return output;
    }

    /**
     * Recursively go through all children of current member
     */
    private void buildHierarchyMembers(String cube, String datasource, String schemaXML, String hierarchyName, String memberName, List children) {
        Result result = executeQuery(datasource, schemaXML, hierarchyName != null
                ? " with member [Measures].[Empty] as '0'"
                + " select " + hierarchyName + ".defaultmember.siblings on columns"
                + " from [" + cube + "]"
                + " where [Measures].[Empty]"
                : " with member [Measures].[Empty] as '0'"
                + " select " + memberName + ".children on columns"
                + " from [" + cube + "]"
                + " where [Measures].[Empty]");
        Position[] positions = result.getAxes()[0].positions;
        for (int i = 0; i < positions.length; i++) {
            HierarchyMember childMember = new HierarchyMember();
            children.add(childMember);
            childMember.name = (hierarchyName != null ? hierarchyName : memberName)
                    + ".[" + positions[i].members[0].getCaption() + "]";
            buildHierarchyMembers(cube, datasource, schemaXML, null, childMember.name, childMember.children);
        }
    }

    public void buildOpenableMemberNames(List members, List openableMemberNames) {
        for (Iterator i = members.iterator(); i.hasNext();) {
            HierarchyMember member = (HierarchyMember) i.next();
            if (member.children.size() > 0) {
                openableMemberNames.add(member.name);
                buildOpenableMemberNames(member.children, openableMemberNames);
            }
        }
    }

    /**
     * Recursively go through hierarchy and find which member must be included
     */
    private void buildMemberSet(Set openMembers, boolean isFirst, List members, StringBuffer memberSet) {

        for (Iterator i = members.iterator(); i.hasNext();) {
            HierarchyMember member = (HierarchyMember) i.next();

            // Add comma
            if (!isFirst) {
                memberSet.append(", ");
            } else {
                isFirst = false;
            }

            // Add this member
            memberSet.append(member.name);

            // Add children
            if (openMembers.contains(member.name)) {
                buildMemberSet(openMembers, isFirst, member.children, memberSet);
            }
        }

    }

    /**
     * Output a member with his parents
     */
    private AxisCell createAxisCell(Set openMembers, List openableMemberNames,
                                    Position[] tuples, int tuplePosition, int memberPosition) {

        // Is current member different from previous member?
        boolean isFirst;
        if (tuplePosition == 0) {
            isFirst = true;
        } else {
            if (!tuples[tuplePosition].members[memberPosition].getCaption().equals
                    (tuples[tuplePosition - 1].members[memberPosition].getCaption())) {
                isFirst = true;
            } else {
                // If any of the previous members are "first", then mark this one as
                // first as well
                if (memberPosition > 0) {
                    boolean foundDifferent = false;
                    for (int i = 0; i < memberPosition; i++) {
                        if (!tuples[tuplePosition].members[i].getCaption().equals
                                (tuples[tuplePosition - 1].members[i].getCaption())) {
                            foundDifferent = true;
                            break;
                        }
                    }
                    isFirst = foundDifferent;
                } else {
                    isFirst = false;
                }
            }
        }

        if (isFirst) {
            AxisCell axisCell = new AxisCell();
            Member member = tuples[tuplePosition].members[memberPosition];
            axisCell.caption = member.getCaption();

            // Compute number of following members having the same caption
            int cardinality = 1;
            int currentTuple = tuplePosition + 1;
            while (currentTuple < tuples.length) {
                boolean foundDifferent = false;
                for (int i = 0; i <= memberPosition; i++) {
                    if (!tuples[currentTuple].members[i].getCaption().equals
                            (tuples[tuplePosition].members[i].getCaption())) {
                        foundDifferent = true;
                        break;
                    }
                }
                if (foundDifferent)
                    break;
                cardinality++;
                currentTuple++;
            }
            axisCell.span = cardinality;

            // Compute depth of this member
            int minDepth = Integer.MAX_VALUE;
            for (int i = 0; i < tuples.length; i++) {
                Member sibling = tuples[i].members[memberPosition];
                int depth = memberDepth(sibling);
                if (depth < minDepth)
                    minDepth = depth;
            }
            axisCell.depth = memberDepth(member) - minDepth;

            // Is this member open/closed?
            if (openableMemberNames.contains(member.getUniqueName())) {
                axisCell.open = openMembers.contains(member.getUniqueName());
                axisCell.member = member.getUniqueName();
            }

            return axisCell;
        } else {
            return null;
        }
    }

    /**
     * Compute depth of a member relative to its parents (top level depth is 0)
     */
    private int memberDepth(Member member) {
        int depth = -1;
        for (Member currentMember = member; currentMember != null; currentMember = currentMember.getParentMember()) {
            depth++;
        }
        return depth;
    }

    private Result executeQuery(String datasource, String schema, String queryString) {
        Connection connection = null;
        try {
            if (logger.isDebugEnabled())
                logger.debug("Executing MDX query:\n" + queryString);
            //MondrianProperties.instance().setProperty("mondrian.trace.level", "1");
            //CachePool.instance().flush();

            Util.PropertyList propertyList = new Util.PropertyList();
            propertyList.put("Provider", "mondrian");
            propertyList.put("Schema", schema);
            propertyList.put("DataSource", "java:comp/env/jdbc/" + datasource);

            RolapSchema.flushSchema(propertyList.get("Catalog"), propertyList.get("Jdbc"), propertyList.get("JdbcUser"), "forecast");
            connection = DriverManager.getConnection(propertyList, false);
            Query query = connection.parseQuery(queryString);
            return connection.execute(query);

        } finally {
            if (connection != null)
                connection.close();
        }

    }

    private static class HierarchyMember {
        String name;
        List children = new ArrayList();
    }

    private static class Hierarchy {
        String name;
        List members = new ArrayList();
    }

    private static class Schema {
        String cube;
        String datasource;
        String schemaXML;
        List hierarchies = new ArrayList();
    }

    private static class AxisCell {
        int span;
        String caption;
        boolean open;
        String member;
        int depth;
    }

}
