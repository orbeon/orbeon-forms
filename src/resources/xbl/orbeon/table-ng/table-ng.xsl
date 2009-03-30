<?xml version="1.0" encoding="UTF-8"?>
<!--
    Copyright (C) 2009 Orbeon, Inc.
    
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
    
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
    
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<xsl:transform xmlns:xforms="http://www.w3.org/2002/xforms" xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms" xmlns:ev="http://www.w3.org/2001/xml-events" xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xbl="http://www.w3.org/ns/xbl" xmlns:xxbl="http://orbeon.org/oxf/xml/xbl" xmlns:widget="http://orbeon.org/oxf/xml/widget"
    xmlns:oxf="http://www.orbeon.com/oxf/processors" xmlns:exf="http://www.exforms.org/exf/1-0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="2.0">

    <xsl:variable name="parameters">
        <!-- These optional attributes are used as parameters -->
        <parameter>appearance</parameter>
        <parameter>scrollable</parameter>
        <parameter>width</parameter>
        <parameter>height</parameter>
        <parameter>paginated</parameter>
        <parameter>rowsPerPage</parameter>
        <parameter>sortedByKey</parameter>
        <parameter>sortedByDir</parameter>
    </xsl:variable>

    <xsl:template match="@*|node()" mode="#all">
        <!-- Default template == identity -->
        <xsl:copy>
            <xsl:apply-templates select="@*|node()" mode="#current"/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="widget:table-ng">
        <!-- Matches the bound element -->
        <xhtml:div class="table-ng yui-dt yui-dt-scrollable">

            <xsl:variable name="pass1">
                <!-- 
                This pass generates the XHTML structure .
                and uses the default mode.
            -->
                <xhtml:table>
                    <!-- Copy attributes that are not parameters! -->
                    <xsl:apply-templates select="@*[not(name() = $parameters/*)]"/>
                    <xsl:if test="not(xhtml:thead)">
                        <!-- If there is no thead element, add one -->
                        <xhtml:thead>
                            <xhtml:tr>
                                <xsl:for-each select="(xhtml:tbody/xhtml:tr|xhtml:tr)[1]/xhtml:td">
                                    <xhtml:th>
                                        <xsl:copy-of select="@widget:*"/>
                                        <xsl:value-of select="xforms:*/xforms:label"/>
                                    </xhtml:th>
                                </xsl:for-each>
                            </xhtml:tr>
                        </xhtml:thead>
                    </xsl:if>
                    <xsl:choose>
                        <xsl:when test="xhtml:tbody">
                            <xsl:apply-templates select="node()"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <!-- If there is no tbody, add one -->
                            <xsl:apply-templates select="xhtml:thead"/>
                            <xhtml:tbody>
                                <xsl:apply-templates select="*[not(self::xhtml:thead)]"/>
                            </xhtml:tbody>
                        </xsl:otherwise>
                    </xsl:choose>
                </xhtml:table>
            </xsl:variable>

            <xforms:model id="datatable-ng">
                <xforms:instance id="sort">
                    <sort currentId="-1" currentOrder="" xmlns="">
                        <xsl:for-each select="$pass1/xhtml:table/xhtml:thead/xhtml:tr/xhtml:th">
                            <key type="{if (@widget:sort-type = 'number') then 'number' else 'text'}">
                                <xsl:variable name="position" select="count(preceding-sibling::xhtml:th) + 1"/>
                                <xsl:if test="@widget:sortable='true'">
                                    <xsl:apply-templates
                                        select="$pass1/xhtml:table/xhtml:tbody/xforms:repeat/xhtml:tr/xhtml:td[position() = $position]" mode="sortKey"
                                    />
                                </xsl:if>
                            </key>
                        </xsl:for-each>
                    </sort>
                </xforms:instance>
                <xforms:bind nodeset="@currentId" type="xs:integer"/>
            </xforms:model>

            <xhtml:div>
                <xxforms:variable name="sort" model="datatable-ng" instance="sort" select="."/>

                <!-- <xforms:group ref="$sort">
                    <xhtml:p>
                        <xforms:output ref="@currentId">
                            <xforms:label>@currentId: </xforms:label>
                        </xforms:output>
                    </xhtml:p>
                    <xhtml:p>
                        <xforms:output ref="@currentOrder">
                            <xforms:label>@currentOrder: </xforms:label>
                        </xforms:output>
                    </xhtml:p>
                    </xforms:group>-->

            </xhtml:div>

            <!-- 
            sortKey mode builds a sort key from a cell content 
        -->

            <!-- 
            
            This second pass adds the YUI decorations
            and uses the "YUI" mode 
            
        -->
            <xhtml:div class="yui-dt-hd" style="background-color: rgb(242, 242, 242); display: table;">
                <!-- See http://snippets.dzone.com/posts/show/216... -->
                <xsl:apply-templates select="$pass1" mode="YUI"/>
            </xhtml:div>

        </xhtml:div>
        <!-- End of template on the bound element -->
    </xsl:template>


    <!-- 
        
        Default mode (pass1)
        
    -->

    <xsl:template match="xhtml:tr/@repeat-nodeset">
        <!-- Remove  repeat-nodeset attributes -->
    </xsl:template>

    <xsl:template match="xhtml:tr[@repeat-nodeset]">
        <!-- Generate xforms:repeat for  repeat-nodeset attributes-->
        <xforms:repeat nodeset="{@repeat-nodeset}">
            <xhtml:tr>
                <xsl:apply-templates select="@*|node()"/>
            </xhtml:tr>
        </xforms:repeat>
    </xsl:template>

    <xsl:template match="xhtml:td/xforms:output/xforms:label">
        <!-- Remove xforms:label since they are used as headers -->
    </xsl:template>

    <!-- 
        
        YUI mode (YUI decorations)
        
    -->

    <xsl:template match="xhtml:thead/xhtml:tr" mode="YUI">
        <xhtml:tr class="yui-dt-first yui-dt-last {@class}">
            <xsl:apply-templates select="@*[name() != 'class']|node()" mode="YUI"/>
        </xhtml:tr>
    </xsl:template>

    <xsl:template name="yui-dt-liner">
        <xsl:param name="position"/>
        <xhtml:div class="yui-dt-liner">
            <xhtml:span class="yui-dt-label">
                <xsl:choose>
                    <xsl:when test="@widget:sortable = 'true'">
                        <xforms:group model="datatable-ng" instance="sort">
                            <xforms:group ref=".[$nextSortOrder = 'ascending']">
                                <xforms:trigger appearance="minimal">
                                    <xforms:label>
                                        <xsl:apply-templates select="node()" mode="YUI"/>
                                    </xforms:label>
                                    <xforms:hint>Click to sort <xforms:output value="$nextSortOrder"/></xforms:hint>
                                    <xforms:action ev:event="DOMActivate">
                                        <xforms:setvalue ref="@currentOrder" value="$nextSortOrder"/>
                                        <xforms:setvalue ref="@currentId">
                                            <xsl:value-of select="$position"/>
                                        </xforms:setvalue>
                                    </xforms:action>
                                </xforms:trigger>
                            </xforms:group>
                            <xforms:group ref=".[$nextSortOrder != 'ascending']">
                                <xforms:trigger appearance="minimal">
                                    <xforms:label>
                                        <xsl:apply-templates select="node()" mode="YUI"/>
                                    </xforms:label>
                                    <xforms:hint>Click to sort <xforms:output value="$nextSortOrder"/></xforms:hint>
                                    <xforms:action ev:event="DOMActivate">
                                        <xforms:setvalue ref="@currentOrder" value="$nextSortOrder"/>
                                        <xforms:setvalue ref="@currentId">
                                            <xsl:value-of select="$position"/>
                                        </xforms:setvalue>
                                    </xforms:action>
                                </xforms:trigger>
                            </xforms:group>
                        </xforms:group>
                    </xsl:when>
                    <xsl:otherwise>
                        <xsl:apply-templates select="node()" mode="YUI"/>
                    </xsl:otherwise>
                </xsl:choose>
            </xhtml:span>
        </xhtml:div>
    </xsl:template>

    <xsl:template match="xhtml:thead/xhtml:tr/xhtml:th" mode="YUI">
        <xsl:variable name="position" select="count(preceding-sibling::xhtml:th) + 1"/>
        <xxforms:variable name="nextSortOrder" model="datatable-ng" instance="sort"
            select=" if (@currentId = {$position} and @currentOrder = 'ascending') then 'descending' else 'ascending' "/>
        <xxforms:variable name="currentId" model="datatable-ng" instance="sort" select="@currentId"/>
        <xxforms:variable name="currentOrder" model="datatable-ng" instance="sort" select="@currentOrder"/>
        <xhtml:th
            class="
            {if (@widget:sortable = 'true') then 'yui-dt-sortable' else ''} 
            {if (@widget:resizeable = 'true') then 'yui-dt-resizeable' else ''} 
            {{if ({$position} = $currentId) 
                then  if($currentOrder = 'descending') then 'yui-dt-desc' else 'yui-dt-asc'
                else ''}}
            {@class}
            ">
            <xsl:apply-templates select="@*[name() != 'class']" mode="YUI"/>
            <xsl:choose>
                <xsl:when test="@widget:resizeable = 'true'">
                    <xhtml:div class="yui-dt-resizerliner">
                        <xsl:call-template name="yui-dt-liner">
                            <xsl:with-param name="position" select="$position"/>
                        </xsl:call-template>
                        <xhtml:div id="{generate-id()}" class="yui-dt-resizer" style="left: auto; right: 0pt; top: auto; bottom: 0pt; height: 100%;"/>
                    </xhtml:div>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:call-template name="yui-dt-liner">
                        <xsl:with-param name="position" select="$position"/>
                    </xsl:call-template>
                </xsl:otherwise>
            </xsl:choose>
        </xhtml:th>
    </xsl:template>

    <xsl:template match="xhtml:tbody" mode="YUI">
        <xhtml:tbody class="yui-dt-data {@class}">
            <xsl:apply-templates select="@*[name() != 'class']|node()" mode="YUI"/>
        </xhtml:tbody>
    </xsl:template>

    <xsl:template match="xforms:repeat" mode="YUI">
        <xxforms:variable name="sort" model="datatable-ng" instance="sort" select="."/>
        <xxforms:variable name="key" model="datatable-ng" instance="sort" select="key[position() = $sort/@currentId]"/>
        <xforms:repeat>
            <xsl:attribute name="nodeset">
                <xsl:text>if ($sort/@currentId = -1 or $sort/@currentOrder = '') then </xsl:text>
                <xsl:value-of select="@nodeset"/>
                <xsl:text> else exf:sort(</xsl:text>
                <xsl:value-of select="@nodeset"/>
                <xsl:text>, $key, $key/@type, $sort/@currentOrder)</xsl:text>
            </xsl:attribute>
            <xsl:apply-templates select="@*[not(name()='nodeset')]|node()" mode="YUI"/>
        </xforms:repeat>
    </xsl:template>

    <xsl:template match="xhtml:tr" mode="YUI">
        <xhtml:tr
            class="
            {{if (position() = 1) then 'yui-dt-first' else '' }}
            {{if (position() = last()) then 'yui-dt-last' else '' }}
            {{if (position() mod 2 = 0) then 'yui-dt-odd' else 'yui-dt-even' }}
            {{if (xxforms:index() = position()) then 'yui-dt-selected' else ''}}
            {@class}">
            <xsl:apply-templates select="@*[name() != 'class']|node()" mode="YUI"/>
        </xhtml:tr>
    </xsl:template>

    <xsl:template match="xforms:repeat/xhtml:tr/xhtml:td" mode="YUI">
        <xsl:variable name="position" select="count(preceding-sibling::xhtml:td) + 1"/>
        <xxforms:variable name="currentId" model="datatable-ng" instance="sort" select="@currentId"/>
        <xxforms:variable name="currentOrder" model="datatable-ng" instance="sort" select="@currentOrder"/>
        <xhtml:td
            class="
            {if (@widget:sortable = 'true') then 'yui-dt-sortable' else ''} 
            {{if ({$position} = $currentId) 
                then  if($currentOrder = 'descending') then 'yui-dt-desc' else 'yui-dt-asc'
                else ''}}
            {@class}
            ">

            <xsl:apply-templates select="@*[name() != 'class']" mode="YUI"/>
            <xhtml:div class="yui-dt-liner">
                <xsl:apply-templates select="node()" mode="YUI"/>
            </xhtml:div>
        </xhtml:td>
    </xsl:template>

    <xsl:template match="@widget:*" mode="YUI"/>

    <!-- 
        sortKey mode builds a sort key from a cell content 
    -->

    <xsl:template match="xhtml:td" mode="sortKey">
        <xsl:value-of select="xforms:output/(@ref|@value)"/>
    </xsl:template>

</xsl:transform>
