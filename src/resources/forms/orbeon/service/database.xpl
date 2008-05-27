<?xml version="1.0" encoding="utf-8"?>
<!--
    Copyright (C) 2008 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline"
        xmlns:oxf="http://www.orbeon.com/oxf/processors">

    <p:param type="input" name="instance"/>
    <p:param type="output" name="data"/>

    <p:processor name="oxf:sql">
        <p:input name="data"><null xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true"/></p:input>
        <p:input name="config" href="#instance" transform="oxf:xslt">
            <sql:config xmlns:sql="http://orbeon.org/oxf/xml/sql" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:xs="http://www.w3.org/2001/XMLSchema" xsl:version="2.0">
                <response>
                    <sql:connection>
                        <!-- "instance" input is a sql:config with sql:datasource and sql:query -->
                        <sql:datasource><xsl:value-of select="/*/sql:datasource"/></sql:datasource>
                        <sql:execute>
                            <xsl:copy-of select="/*/sql:query"/>
                            <!-- Format output in a generic way -->
                            <sql:result-set>
                                <sql:row-iterator>
                                    <row><sql:get-columns format="xml"/></row>
                                </sql:row-iterator>
                            </sql:result-set>
                        </sql:execute>
                    </sql:connection>
                </response>
            </sql:config>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
