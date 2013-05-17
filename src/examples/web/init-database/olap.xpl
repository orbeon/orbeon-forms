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
          xmlns:sql="http://orbeon.org/oxf/xml/sql"
          xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:processor name="oxf:xslt">
        <p:input name="data" href="olap.xml"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
                <xsl:template match="/">
                    <sql:config>
                        <sql:connection>
                            <sql:datasource>db</sql:datasource>
                            <xsl:variable name="statements" select="tokenize(string(.), ';')"/>

                            <!-- Create tables, and insert members -->
                            <xsl:for-each select="$statements[position() &lt; count($statements)]">
                                <sql:execute>
                                    <sql:update>
                                        <xsl:value-of select="."/>
                                    </sql:update>
                                </sql:execute>
                            </xsl:for-each>

                            <!-- Get number of products, regions, times -->
                            <measure-count>
                                <sql:execute>
                                    <sql:query>
                                        select count(*) product_count from olap_product
                                    </sql:query>
                                    <sql:results>
                                        <sql:row-results>
                                            <sql:get-columns format="xml"/>
                                        </sql:row-results>
                                    </sql:results>
                                </sql:execute>
                                <sql:execute>
                                    <sql:query>
                                        select count(*) region_count from olap_region
                                    </sql:query>
                                    <sql:results>
                                        <sql:row-results>
                                            <sql:get-columns format="xml"/>
                                        </sql:row-results>
                                    </sql:results>
                                </sql:execute>
                                <sql:execute>
                                    <sql:query>
                                        select count(*) time_count from olap_time
                                    </sql:query>
                                    <sql:results>
                                        <sql:row-results>
                                            <sql:get-columns format="xml"/>
                                        </sql:row-results>
                                    </sql:results>
                                </sql:execute>
                            </measure-count>
                        </sql:connection>
                    </sql:config>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="sql"/>
    </p:processor>

    <!-- Execute table creating and member insertion -->
    <p:processor name="oxf:sql">
        <p:input name="data"><dummy/></p:input>
        <p:input name="config" href="#sql"/>
        <p:output name="output" id="measure-count"/>
    </p:processor>

    <!-- Create SQL statements to insert random measures -->
    <p:processor name="oxf:xslt">
        <p:input name="data" href="#measure-count"/>
        <p:input name="config">
            <xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:math="java:java.lang.Math">
                <xsl:template match="/">
                    <sql:config>
                        <sql:connection>
                            <sql:datasource>db</sql:datasource>
                            <xsl:variable name="measure-count" select="/measure-count"/>
                            <xsl:for-each select="1 to $measure-count/product-count">
                                <xsl:variable name="product-id" select="."/>
                                <xsl:for-each select="1 to $measure-count/region-count">
                                    <xsl:variable name="region-id" select="."/>
                                    <xsl:for-each select="1 to $measure-count/time-count">
                                        <xsl:variable name="time-id" select="."/>
                                        <sql:execute>
                                            <sql:update>
                                                <xsl:text>insert into olap_sales values (</xsl:text>
                                                <xsl:value-of select="$product-id"/>
                                                <xsl:text>, </xsl:text>
                                                <xsl:value-of select="$region-id"/>
                                                <xsl:text>, </xsl:text>
                                                <xsl:value-of select="$time-id"/>
                                                <xsl:text>, </xsl:text>
                                                <xsl:value-of select="round(math:random() * 1000) * 1000"/>
                                                <xsl:text>)</xsl:text>
                                            </sql:update>
                                        </sql:execute>
                                    </xsl:for-each>
                                </xsl:for-each>
                            </xsl:for-each>
                        </sql:connection>
                    </sql:config>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="random-values-sql"/>
    </p:processor>

    <!-- Execute table creating and member insertion -->
    <p:processor name="oxf:sql">
        <p:input name="data"><dummy/></p:input>
        <p:input name="config" href="#random-values-sql"/>
    </p:processor>

</p:config>
