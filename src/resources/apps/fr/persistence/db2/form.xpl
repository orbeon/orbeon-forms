<!--
  Copyright (C) 2011 Orbeon, Inc.

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
        xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:f="http//www.orbeon.com/function"
        xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
        xmlns:xh="http://www.w3.org/1999/xhtml"
        xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary">

    <p:param name="instance" type="input"/>
    <p:param name="data" type="output"/>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/request-path</include>
                <include>/request/headers/header[name = 'orbeon-datasource']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="instance" href="#instance"/>
        <p:input name="request" href="#request"/>
        <p:input name="data"><dummy/></p:input>
        <p:input name="config">
            <request xsl:version="2.0">
                <xsl:variable name="request" as="element(request)" select="doc('input:request')/request"/>
                <sql:datasource><xsl:value-of select="$request/headers/header[name = 'orbeon-datasource']/value/string() treat as xs:string"/></sql:datasource>
                <xsl:copy-of select="doc('input:instance')/request/*"/>
            </request>
        </p:input>
        <p:output name="data" id="request-description"/>
    </p:processor>

    <!-- Get the metadata section of each form -->
    <p:processor name="oxf:sql">
        <p:input name="data" href="#request-description"/>
        <p:input name="config" transform="oxf:unsafe-xslt" href="#request-description">
            <sql:config xsl:version="2.0">
                <sql-out>
                    <sql:connection>
                        <xsl:copy-of select="/request/sql:datasource"/>
                        <sql:execute>
                            <sql:query>
                                select  d.xml xml, d.last_modified_time last_modified_time
                                from    orbeon_form_definition d,
                                        (
                                            select      app, form, max(last_modified_time) last_modified_time
                                            from        orbeon_form_definition
                                            <xsl:if test="/request/app != ''">
                                                where
                                                    <xsl:if test="/request/app != ''">
                                                        app = <sql:param type="xs:string" select="/request/app"/>
                                                    </xsl:if>
                                                    <xsl:if test="/request/form != ''">
                                                        and form = <sql:param type="xs:string" select="/request/form"/>
                                                    </xsl:if>
                                            </xsl:if>
                                            group by    app, form
                                        ) m
                                where   d.app = m.app
                                        and d.form = m.form
                                        and d.last_modified_time = m.last_modified_time
                                        and d.deleted = 'N'
                            </sql:query>
                            <sql:result-set>
                                <sql:row-iterator>
                                    <sql:get-column-value column="xml" type="odt:xmlFragment"/>
                                </sql:row-iterator>
                            </sql:result-set>
                        </sql:execute>
                    </sql:connection>
                </sql-out>
            </sql:config>
        </p:input>
        <p:output name="data" id="sql-out"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#sql-out"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0" exclude-result-prefixes="#all">
                <xsl:template match="/">
                    <forms>
                        <xsl:for-each select="/sql-out/xh:html/xh:head/xf:model/xf:instance[@id = 'fr-form-metadata']/metadata">
                            <form>
                                <xsl:copy-of select="*" copy-namespaces="no"/>
                            </form>
                        </xsl:for-each>
                    </forms>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
