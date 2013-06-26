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
        xmlns:sql="http://orbeon.org/oxf/xml/sql"
        xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary">

    <p:param type="input" name="instance"/>

    <!-- NOTE: It's disappointing that we have to use oxf:request/oxf:regexp rather than using the page flow
         directly, but because we want to support the PUT and POST methods, this is currently the only solution. -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/request-path</include>
                <include>/request/content-type</include>
                <include>/request/method</include>
                <include>/request/headers/header[name = 'orbeon-username' or name = 'orbeon-roles' or name = 'orbeon-datasource' or name = 'orbeon-group']</include>
                <include>/request/body</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>
    <p:processor name="oxf:regexp">
        <p:input name="config"><config>/fr/service/mysql/crud/([^/]+)/([^/]+)/((form)/([^/]+)|(data)/(([^/]+)/([^/]+))?)</config></p:input>
        <p:input name="data" href="#request#xpointer(/request/request-path)"/>
        <p:output name="data" id="matcher-groups"/>
    </p:processor>

    <!-- Build a document with a description of the request -->
    <p:processor name="oxf:xslt">
        <p:input name="matcher-groups" href="#matcher-groups"/>
        <p:input name="instance" href="#instance"/>
        <p:input name="request" href="#request"/>
        <p:input name="data"><dummy/></p:input>
        <p:input name="config">
            <request xsl:version="2.0">
                <xsl:variable name="matcher-groups" as="element(group)+" select="doc('input:matcher-groups')/result/group"/>
                <xsl:variable name="request" as="element(request)" select="doc('input:request')/request"/>
                <content-type><xsl:value-of select="$request/content-type"/></content-type>
                <document><xsl:copy-of select="doc('input:instance')"/></document>
                <timestamp><xsl:value-of select="current-dateTime()"/></timestamp>
                <username><xsl:value-of select="$request/headers/header[name = 'orbeon-username']/value"/></username>
                <groupname><xsl:value-of select="$request/headers/header[name = 'orbeon-group']/value"/></groupname>
                <roles><xsl:value-of select="$request/headers/header[name = 'orbeon-roles']/value"/></roles>
                <app><xsl:value-of select="$matcher-groups[1]"/></app>
                <form><xsl:value-of select="$matcher-groups[2]"/></form>
                <xsl:variable name="type" as="xs:string" select="if ($matcher-groups[4] = 'form') then 'form' else 'data'"/>
                <type><xsl:value-of select="$type"/></type>
                <xsl:if test="$type = 'data' and $matcher-groups[8] != ''">                             <!-- Document id is only used for data; might be missing for operations over a collection -->
                    <document-id><xsl:value-of select="$matcher-groups[8]"/></document-id>
                </xsl:if>
                <xsl:if test="$type = 'form' or $matcher-groups[9] != ''">                              <!-- Filename isn't present for operations over an app/form collection -->
                    <filename><xsl:value-of select="if ($type = 'data') then $matcher-groups[9] else $matcher-groups[5]"/></filename>
                </xsl:if>
                <sql:datasource><xsl:value-of select="$request/headers/header[name = 'orbeon-datasource']/value/string() treat as xs:string"/></sql:datasource>
                <xsl:copy-of select="$request/body"/>
            </request>
        </p:input>
        <p:output name="data" id="request-description"/>
    </p:processor>

    <p:processor name="oxf:null-serializer">
        <p:input name="data" href="#request-description"/>
    </p:processor>

    <!-- Discriminate based on the HTTP method and content type -->
    <p:choose href="#request">
        <!-- Handle binary and XML GET -->
        <p:when test="/*/method = 'GET'">

            <p:processor name="oxf:unsafe-xslt">
                <p:input name="data" href="#request-description"/>
                <p:input name="config">
                    <xsl:stylesheet version="2.0">
                        <xsl:include href="../common-owner-group.xsl"/>
                        <xsl:template match="/">
                            <sql:config>
                                <sql-out>
                                    <sql:connection>
                                        <xsl:copy-of select="/request/sql:datasource"/>
                                        <xsl:variable name="is-data" as="xs:boolean" select="/request/type = 'data'"/>
                                        <xsl:variable name="is-attachment" as="xs:boolean" select="not(ends-with(/request/filename, '.xml') or ends-with(/request/filename, '.xhtml'))"/>
                                        <xsl:variable name="table-name" as="xs:string" select="concat(
                                            if ($is-data) then 'orbeon_form_data' else 'orbeon_form_definition',
                                            if ($is-attachment) then '_attach' else '')"/>

                                        <sql:execute>
                                            <sql:query>
                                                select
                                                    <xsl:value-of select="$last-modified-time"/>,
                                                    <xsl:if test="not($is-attachment)">t.xml xml</xsl:if>
                                                    <xsl:if test="$is-attachment">file_content</xsl:if>
                                                from <xsl:value-of select="$table-name"/> t
                                                    where app = <sql:param type="xs:string" select="/request/app"/>
                                                    and form = <sql:param type="xs:string" select="/request/form"/>
                                                    <xsl:if test="$is-data">and document_id = <sql:param type="xs:string" select="/request/document-id"/></xsl:if>
                                                    <xsl:if test="$is-attachment">and file_name = <sql:param type="xs:string" select="/request/filename"/></xsl:if>
                                                    and <xsl:value-of select="$last-modified-time"/> = (
                                                        select max(<xsl:value-of select="$last-modified-time"/>) from <xsl:value-of select="$table-name"/>
                                                        where app = <sql:param type="xs:string" select="/request/app"/>
                                                        and form = <sql:param type="xs:string" select="/request/form"/>
                                                        <xsl:if test="$is-data">and document_id = <sql:param type="xs:string" select="/request/document-id"/></xsl:if>
                                                        <xsl:if test="$is-attachment">and file_name = <sql:param type="xs:string" select="/request/filename"/></xsl:if>
                                                    )
                                                    <!-- This will prevent request for document that have been deleted to return a delete doc -->
                                                    and deleted = 'N'
                                            </sql:query>
                                            <sql:result-set>
                                                <sql:row-iterator>
                                                    <last-modified>
                                                        <sql:get-column-value column="{$last-modified-time}" type="xs:dateTime"/>
                                                    </last-modified>
                                                    <data>
                                                        <xsl:choose>
                                                            <xsl:when test="$is-attachment">
                                                                <sql:get-column-value column="file_content" type="xs:base64Binary"/>
                                                            </xsl:when>
                                                            <xsl:otherwise>
                                                                <sql:get-column-value column="xml" type="odt:xmlFragment"/>
                                                            </xsl:otherwise>
                                                        </xsl:choose>
                                                    </data>
                                                </sql:row-iterator>
                                            </sql:result-set>
                                        </sql:execute>
                                    </sql:connection>
                                </sql-out>
                            </sql:config>
                        </xsl:template>
                    </xsl:stylesheet>
                </p:input>
                <p:output name="data" id="sql-config"/>
            </p:processor>

            <p:processor name="oxf:sql">
                <p:input name="data" href="#request-description"/>
                <p:input name="config" href="#sql-config"/>
                <p:output name="data" id="sql-out"/>
            </p:processor>

            <p:choose href="aggregate('root', #request-description, #sql-out)">
                <!-- Test whether we got data as well as the extension type before trying to parse as XML -->
                <p:when test="/*/sql-out/data and (ends-with(/*/request/filename, '.xml') or ends-with(/*/request/filename, '.xhtml'))">
                    <!-- Convert and serialize to XML -->
                    <p:processor name="oxf:xml-converter">
                        <p:input name="config">
                            <config>
                                <indent>false</indent>
                                <encoding>utf-8</encoding>
                            </config>
                        </p:input>
                        <p:input name="data" transform="oxf:unsafe-xslt" href="#sql-out">
                            <!-- We don't use XPointer here because XPointer doesn't deal correctly with namespaces -->
                            <xsl:stylesheet version="2.0">
                                <xsl:template match="/">
                                    <xsl:copy-of select="/sql-out/data/*"/>
                                </xsl:template>
                            </xsl:stylesheet>
                        </p:input>
                        <p:output name="data" id="converted"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <!-- Add the xsi:type in a separate step, as the SQL Processor doesn't pass namespace declaration for xs even if the namespace is declared on the element -->
                    <p:processor name="oxf:xslt">
                        <p:input name="data" href="#sql-out"/>
                        <p:input name="config">
                            <document xsi:type="xs:base64Binary" xsl:version="2.0">
                                <xsl:value-of select="/sql-out/data"/>
                            </document>
                        </p:input>
                        <p:output name="data" id="converted"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>

            <!-- Serialize out as is -->
            <p:processor name="oxf:http-serializer">
                <p:input name="config" transform="oxf:unsafe-xslt" href="#sql-out">
                    <config xsl:version="2.0">
                        <cache-control>
                            <use-local-cache>false</use-local-cache>
                        </cache-control>
                        <header>
                            <name>Last-Modified</name>
                            <value>
                                <!-- Format the date -->
                                <xsl:value-of select="format-dateTime(xs:dateTime(/sql-out/last-modified), '[FNn,*-3], [D] [MNn,*-3] [Y] [H01]:[m01]:[s01] GMT', 'en', (), ()) "/>
                            </value>
                        </header>
                    </config>
                </p:input>
                <p:input name="data" href="#converted"/>
            </p:processor>

        </p:when>
        <p:otherwise>
            <!-- Discriminate based on the HTTP method and content type -->
            <p:choose href="#request">

                <!-- DELETE -->
                <p:when test="/*/method = 'DELETE'">

                    <p:processor name="oxf:sql">
                        <p:input name="data" href="#request-description"/>
                        <p:input name="config" transform="oxf:unsafe-xslt" href="#request-description">
                            <xsl:stylesheet version="2.0">
                                <xsl:include href="../common-owner-group.xsl"/>
                                <xsl:template match="/">
                                    <sql:config xsl:version="2.0">
                                        <result>
                                            <sql:connection>
                                                <xsl:copy-of select="/request/sql:datasource"/>
                                                <sql:execute>
                                                    <sql:update>
                                                        <xsl:variable name="is-data" as="xs:boolean" select="/request/type = 'data'"/>
                                                        <xsl:variable name="has-document-id" as="xs:boolean" select="exists(/request/document-id)"/>
                                                        <xsl:variable name="table-name" as="xs:string" select="if ($is-data) then 'orbeon_form_data' else 'orbeon_form_definition'"/>

                                                        insert into <xsl:value-of select="$table-name"/>
                                                            (
                                                                <!-- TODO: This list of columns only works for the data (not form definition) table -->
                                                                created, <xsl:value-of select="$last-modified-time"/>, <xsl:value-of select="$last-modified-by"/>,
                                                                app, form, <xsl:if test="$is-data">document_id,</xsl:if> deleted, xml
                                                                <xsl:if test="$owner-group">, username, groupname</xsl:if>
                                                            )
                                                        select
                                                            d.created,
                                                            <sql:param type="xs:dateTime" select="/request/timestamp"/>,
                                                            <sql:param type="xs:string" select="/request/username"/>,
                                                            d.app, d.form, <xsl:if test="$is-data">d.document_id,</xsl:if>
                                                            'Y', d.xml
                                                            <xsl:if test="$owner-group">, d.username, d.groupname</xsl:if>
                                                        from
                                                            orbeon_form_data d,
                                                            (
                                                                select
                                                                    app, form, document_id, max(<xsl:value-of select="$last-modified-time"/>) <xsl:value-of select="$last-modified-time"/>
                                                                from orbeon_form_data
                                                                where
                                                                    app =  <sql:param type="xs:string" select="/request/app"/>
                                                                    and form =  <sql:param type="xs:string" select="/request/form"/>
                                                                    <xsl:if test="$is-data and $has-document-id">and document_id = <sql:param type="xs:string" select="/request/document-id"/></xsl:if>
                                                                group by
                                                                    app, form, document_id
                                                            ) m
                                                        where
                                                            d.document_id = m.document_id
                                                            and d.<xsl:value-of select="$last-modified-time"/> = m.<xsl:value-of select="$last-modified-time"/>
                                                            and d.deleted = 'N'
                                                    </sql:update>
                                                </sql:execute>
                                            </sql:connection>
                                        </result>
                                    </sql:config>
                                </xsl:template>
                            </xsl:stylesheet>
                        </p:input>
                    </p:processor>

                </p:when>

                <!-- PUT -->
                <p:when test="/*/method = 'PUT'">

                    <p:processor name="oxf:sql">
                        <p:input name="data" href="#request-description"/>
                        <p:input name="config" transform="oxf:unsafe-xslt" href="#request-description">
                            <xsl:stylesheet version="2.0">
                                <xsl:include href="../common-owner-group.xsl"/>
                                <xsl:template match="/">
                                    <sql:config xsl:version="2.0">
                                        <result>
                                            <sql:connection>
                                                <xsl:copy-of select="/request/sql:datasource"/>
                                                <sql:execute>
                                                    <sql:update>
                                                        <xsl:variable name="is-data" as="xs:boolean" select="/request/type = 'data'"/>
                                                        <xsl:variable name="is-attachment" as="xs:boolean" select="not(/request/content-type = ('application/xml', 'text/xml') or ends-with(/request/content-type, '+xml'))"/>
                                                        <xsl:variable name="table-name" as="xs:string" select="concat(
                                                            if ($is-data) then 'orbeon_form_data' else 'orbeon_form_definition',
                                                            if ($is-attachment) then '_attach' else '')"/>
                                                            insert
                                                                into <xsl:value-of select="$table-name"/>
                                                                (
                                                                    created, <xsl:value-of select="$last-modified-time"/>, <xsl:value-of select="$last-modified-by"/>, app, form, <xsl:if test="$is-data">document_id,</xsl:if> deleted,
                                                                    <xsl:if test="$is-attachment">file_name, file_content</xsl:if>
                                                                    <xsl:if test="not($is-attachment)">xml</xsl:if>
                                                                    <xsl:if test="$owner-group">, username, groupname</xsl:if>
                                                                )
                                                                select
                                                                    case when <xsl:value-of select="$last-modified-time"/> is null
                                                                        then <sql:param type="xs:dateTime" select="/request/timestamp"/>
                                                                        else created end as created,
        	                                                        <sql:param type="xs:dateTime" select="/request/timestamp"/>,
                                                                    <sql:param type="xs:string" select="/request/username"/>,
                                                                    <sql:param type="xs:string" select="/request/app"/>,
                                                                    <sql:param type="xs:string" select="/request/form"/>,
                                                                    <xsl:if test="$is-data">
                                                                        <sql:param type="xs:string" select="/request/document-id"/>,
                                                                    </xsl:if> 'N',
                                                                    <xsl:if test="$is-attachment">
                                                                        <sql:param type="xs:string" select="/request/filename"/>,
                                                                        <sql:param type="xs:anyURI" sql-type="blob" select="/request/body" />
                                                                    </xsl:if>
                                                                    <xsl:if test="not($is-attachment)">
        																<sql:param type="odt:xmlFragment" sql-type="clob" select="/request/document/*" />
                                                                    </xsl:if>
                                                                    <xsl:if test="$owner-group">
                                                                        , case when <xsl:value-of select="$last-modified-time"/> is null
                                                                            then <sql:param type="xs:string" select="/request/username"/>
                                                                            else username end as username
                                                                        , case when <xsl:value-of select="$last-modified-time"/> is null
                                                                            then <sql:param type="xs:string" select="/request/groupname"/>
                                                                            else groupname end as groupname
                                                                    </xsl:if>
                                                                from
                                                                (
                                                                    select
                                                                        max(<xsl:value-of select="$last-modified-time"/>) <xsl:value-of select="$last-modified-time"/>, created
                                                                        <xsl:if test="$owner-group">, username , groupname</xsl:if>
                                                                    from
                                                                    (
                                                                        (
                                                                            select
                                                                                t.<xsl:value-of select="$last-modified-time"/>, t.created
                                                                                <xsl:if test="$owner-group">, t.username , t.groupname</xsl:if>
                                                                            from
                                                                                <xsl:value-of select="$table-name"/> t,
                                                                                (
                                                                                    select max(<xsl:value-of select="$last-modified-time"/>) <xsl:value-of select="$last-modified-time"/>
                                                                                    from <xsl:value-of select="$table-name"/>
                                                                                    where
                                                                                        app = <sql:param type="xs:string" select="/request/app"/>
                                                                                        and form = <sql:param type="xs:string" select="/request/form"/>
                                                                                        <xsl:if test="$is-data"> and document_id = <sql:param type="xs:string" select="/request/document-id"/></xsl:if>
                                                                                        <xsl:if test="$is-attachment"> and file_name = <sql:param type="xs:string" select="/request/filename"/></xsl:if>
                                                                                ) l
                                                                            where
                                                                                t.<xsl:value-of select="$last-modified-time"/> = l.<xsl:value-of select="$last-modified-time"/>
                                                                                and t.app = <sql:param type="xs:string" select="/request/app"/>
                                                                                and t.form = <sql:param type="xs:string" select="/request/form"/>
                                                                                <xsl:if test="$is-data"> and t.document_id = <sql:param type="xs:string" select="/request/document-id"/></xsl:if>
                                                                                <xsl:if test="$is-attachment"> and t.file_name = <sql:param type="xs:string" select="/request/filename"/></xsl:if>
                                                                                and t.<xsl:value-of select="$last-modified-time"/> = l.<xsl:value-of select="$last-modified-time"/>
                                                                                and t.deleted = 'N'
                                                                        )
                                                                        union all
                                                                        (
                                                                            select
                                                                                null <xsl:value-of select="$last-modified-time"/>, null created
                                                                                <xsl:if test="$owner-group">, null username, null groupname</xsl:if>
                                                                            from dual
                                                                        )
                                                                    ) t2
                                                                ) t3;
                                                    </sql:update>
                                                </sql:execute>
                                            </sql:connection>
                                        </result>
                                    </sql:config>
                                </xsl:template>
                            </xsl:stylesheet>
                        </p:input>
                    </p:processor>

                </p:when>
            </p:choose>

            <p:processor name="oxf:xml-converter">
                <p:input name="config">
                    <config>
                        <indent>false</indent>
                        <encoding>utf-8</encoding>
                    </config>
                </p:input>
                <p:input name="data"><dummy/></p:input>
                <p:output name="data" id="converted"/>
            </p:processor>
            <p:processor name="oxf:http-serializer">
                <p:input name="config">
                    <config>
                        <cache-control>
                            <use-local-cache>false</use-local-cache>
                        </cache-control>
                    </config>
                </p:input>
                <p:input name="data" href="#converted"/>
            </p:processor>
        </p:otherwise>

    </p:choose>

</p:config>