<!--
    Copyright (C) 2004 Orbeon, Inc.
  
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
  
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
  
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xs="http://www.w3.org/2001/XMLSchema">

    <p:param type="input" name="data"/>
    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <p:choose href="../database/type.xml">
        <p:when test="/type = 'file'">

            <p:processor name="oxf:pipeline">
                <p:input name="config" href="../database/database.xpl"/>
                <p:output name="data" id="database"/>
            </p:processor>

            <p:processor name="oxf:xslt">
                <p:input name="data" href="aggregate('root', #instance, #database)"/>
                <p:input name="config">
                    <xsl:stylesheet version="2.0">
                        <xsl:import href="oxf:/petstore/pages/utils.xsl"/>
                        <xsl:template match="/">
                            <xsl:variable name="keyword" select="lower-case(/root/form/keywords)"/>
                            <xsl:variable name="_items">
                                <xsl:for-each select="/root/Populate/Catalog/Items/Item">
                                    <xsl:variable name="description" select="lower-case($description)"/>
                                    <xsl:if test="contains($description, $keyword)">
                                        <xsl:copy-of select="."/>
                                    </xsl:if>
                                </xsl:for-each>
                            </xsl:variable>
                            <xsl:variable name="items" select="$_items/*"/>
                            <items>
                                <xsl:choose>
                                    <xsl:when test="count($items) = 0">
                                        <title>No results were found for your search.</title>
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <title>Search Results</title>
                                        <xsl:variable name="database" select="/root/Populate"/>
                                        <xsl:for-each select="$items">
                                            <item>
                                                <id>
                                                    <xsl:value-of select="@id"/>
                                                </id>
                                                <description>
                                                    <xsl:value-of select="ItemDetails/Description"/>
                                                </description>
                                                <price>
                                                    <xsl:value-of select="ItemDetails/ListPrice"/>
                                                </price>
                                                <name>
                                                    <xsl:call-template name="item-name">
                                                        <xsl:with-param name="database" select="$database"/>
                                                        <xsl:with-param name="id" select="@id"/>
                                                    </xsl:call-template>
                                                </name>
                                            </item>
                                        </xsl:for-each>
                                    </xsl:otherwise>
                                </xsl:choose>
                            </items>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" ref="data"/>
            </p:processor>
        </p:when>

        <p:when test="/type='db'">
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="../database/locale.xpl"/>
                <p:output name="locale" id="locale"/>
            </p:processor>

            <p:processor name="oxf:sql">
                <p:input name="data" href="aggregate('root', #instance, #locale)"/>
                <p:input name="config">
                    <sql:config xmlns:sql="http://orbeon.org/oxf/xml/sql">
                        <sql:connection>
                            <sql:datasource>db</sql:datasource>
                            <items>
                                <sql:execute>
                                    <sql:query>
                                        select * from item, item_details, product_details
                                        where item.itemid = item_details.itemid
                                        and item_details.locale = <sql:parameter type="xs:string" select="/root/locale"/>
                                        and item.productid = product_details.productid
                                        and product_details.locale = item_details.locale
                                        and lower(item_details.descn) like lower('%<sql:parameter type="oxf:literalString" replace="true" select="/root/form/keywords"/>%')
                                    </sql:query>
                                    <sql:results>
                                        <sql:row-results>
                                            <item>
                                                <id>
                                                    <sql:get-column type="xs:string" column="itemid"/>
                                                </id>
                                                <description>
                                                    <sql:get-column type="xs:string" column="descn"/>
                                                </description>
                                                <price>
                                                    <sql:get-column type="xs:string" column="listprice"/>
                                                </price>
                                                <name>
                                                    <sql:get-column type="xs:string" column="attr1"/>&#160;
                                                    <sql:get-column type="xs:string" column="attr2"/>&#160;
                                                    <sql:get-column type="xs:string" column="attr3"/>&#160;
                                                    <sql:get-column type="xs:string" column="attr4"/>&#160;
                                                    <sql:get-column type="xs:string" column="attr5"/>&#160;
                                                    <sql:get-column type="xs:string" column="cname"/>
                                                </name>
                                            </item>
                                        </sql:row-results>
                                    </sql:results>
                                    <sql:no-results>
                                        <title>No results were found for your search.</title>
                                    </sql:no-results>
                                </sql:execute>
                            </items>
                        </sql:connection>
                    </sql:config>
                </p:input>
                <p:output name="data" ref="data"/>
            </p:processor>

        </p:when>
    </p:choose>
</p:config>
