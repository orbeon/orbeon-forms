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
        xmlns:sql="http://orbeon.org/oxf/xml/sql"
        xmlns:odt="http://orbeon.org/oxf/xml/datatypes"
        xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary"
        xmlns:form-runner="java:org.orbeon.oxf.fr.FormRunner">

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
        <p:input name="config"><config>/fr/service/db2/crud/([^/]+)/([^/]+)/((form)/([^/]+)|(data|draft)/(([^/]+)/([^/]+))?)</config></p:input>
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
                <xsl:variable name="type" as="xs:string" select="concat($matcher-groups[4], $matcher-groups[6])"/>
                <type><xsl:value-of select="$type"/></type>
                <xsl:if test="$type = ('data', 'draft') and $matcher-groups[8] != ''">                             <!-- Document id is only used for data; might be missing for operations over a collection -->
                    <document-id><xsl:value-of select="$matcher-groups[8]"/></document-id>
                </xsl:if>
                <xsl:if test="$type = 'form' or $matcher-groups[9] != ''">                              <!-- Filename isn't present for operations over an app/form collection -->
                    <filename><xsl:value-of select="if ($type = ('data', 'draft')) then $matcher-groups[9] else $matcher-groups[5]"/></filename>
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
                    <sql:config xsl:version="2.0">
                        <sql-out>
                            <sql:connection>
                                <xsl:copy-of select="/request/sql:datasource"/>
                                        <xsl:variable name="support-auto-save" as="xs:boolean" select="xpl:property('oxf.fr.support-autosave')"/>
                                        <xsl:variable name="is-data-draft" as="xs:boolean" select="/request/type = ('data', 'draft')"/>
                                        <xsl:variable name="draft" as="xs:string" select="if (/request/type = 'draft') then 'Y' else 'N'"/>
                                <xsl:variable name="is-attachment" as="xs:boolean" select="not(ends-with(/request/filename, '.xml') or ends-with(/request/filename, '.xhtml'))"/>
                                <xsl:variable name="table-name" as="xs:string" select="concat(
                                            if ($is-data-draft) then 'orbeon_form_data' else 'orbeon_form_definition',
                                    if ($is-attachment) then '_attach' else '')"/>

                                <sql:execute>
                                    <sql:query>
                                        select
                                            last_modified_time,
                                            <xsl:if test="not($is-attachment)">t.xml xml</xsl:if>
                                            <xsl:if test="$is-attachment">file_content</xsl:if>
                                            <xsl:if test="$is-data-draft">, username, groupname</xsl:if>
                                        from <xsl:value-of select="$table-name"/> t
                                            where app = <sql:param type="xs:string" select="/request/app"/>
                                            and form = <sql:param type="xs:string" select="/request/form"/>
                                            <xsl:if test="$is-data-draft">
                                                and document_id = <sql:param type="xs:string" select="/request/document-id"/>
                                                <xsl:if test="$support-auto-save">and draft = '<xsl:value-of select="$draft"/>'</xsl:if>
                                            </xsl:if>
                                            <xsl:if test="$is-attachment">and file_name = <sql:param type="xs:string" select="/request/filename"/></xsl:if>
                                            and last_modified_time = (
                                                select max(last_modified_time) from <xsl:value-of select="$table-name"/>
                                                where app = <sql:param type="xs:string" select="/request/app"/>
                                                and form = <sql:param type="xs:string" select="/request/form"/>
                                                <xsl:if test="$is-data-draft">
                                                    and document_id = <sql:param type="xs:string" select="/request/document-id"/>
                                                    <xsl:if test="$support-auto-save">and draft = '<xsl:value-of select="$draft"/>'</xsl:if>
                                                </xsl:if>
                                                <xsl:if test="$is-attachment">and file_name = <sql:param type="xs:string" select="/request/filename"/></xsl:if>
                                            )
                                            and deleted = 'N'
                                    </sql:query>
                                    <sql:result-set>
                                        <sql:row-iterator>
                                            <last-modified><sql:get-column-value column="last_modified_time" type="xs:dateTime"/></last-modified>
                                            <xsl:if test="$is-data-draft">
                                                <username><sql:get-column-value column="username" type="xs:string"/></username>
                                                <groupname><sql:get-column-value column="groupname" type="xs:string"/></groupname>
                                            </xsl:if>
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
                </p:input>
                <p:output name="data" id="sql-config"/>
            </p:processor>

            <p:processor name="oxf:sql">
                <p:input name="data" href="#request-description"/>
                <p:input name="config" href="#sql-config"/>
                <p:output name="data" id="sql-out"/>
            </p:processor>

            <!-- Set Orbeon-Operations header -->
            <p:processor name="oxf:xforms-submission">
                <p:input name="request" href="#request-description"/>
                <p:input name="submission">
                    <xf:submission xsl:version="2.0" method="get" replace="instance" serialization="none"
                                       resource="/fr/service/persistence/form/{encode-for-uri(/request/app)}/{encode-for-uri(/request/form)}"/>
                </p:input>
                <p:output name="response" id="form"/>
            </p:processor>

            <p:processor name="oxf:unsafe-xslt">
                <p:input name="data"><dummy/></p:input>
                <p:input name="form" href="#form"/>
                <p:input name="sql-out" href="#sql-out"/>
                <p:input name="request-description" href="#request-description"/>
                <p:input name="config">
                    <operations xsl:version="2.0">
                        <xsl:variable name="permissions" select="doc('input:form')/forms/form/permissions"/>
                        <xsl:variable name="sql-out" select="doc('input:sql-out')/sql-out"/>
                        <xsl:value-of select="form-runner:setAllAuthorizedOperationsHeader($permissions, $sql-out/username/string(), $sql-out/groupname/string())"/>
                    </operations>
                </p:input>
                <p:output name="data" id="send-operations"/>
            </p:processor>

            <p:processor name="oxf:null-serializer">
                <p:input name="data" href="#send-operations"/>
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
                            <sql:config xsl:version="2.0">
                                <result>
                                    <sql:connection>
                                        <xsl:copy-of select="/request/sql:datasource"/>
                                        <xsl:variable name="support-auto-save" as="xs:boolean" select="xpl:property('oxf.fr.support-autosave')"/>
                                        <xsl:variable name="is-data-draft" as="xs:boolean" select="/request/type = ('data', 'draft')"/>
                                        <xsl:variable name="has-document-id" as="xs:boolean" select="exists(/request/document-id)"/>
                                        <xsl:variable name="table-name" as="xs:string" select="if ($is-data-draft) then 'orbeon_form_data' else 'orbeon_form_definition'"/>

                                        <!-- Normal delete for data and form definitions (the latter to be implemented) -->
                                        <xsl:if test="/request/type != 'draft'">
                                            <sql:execute>
                                                <sql:update>
                                                    insert into <xsl:value-of select="$table-name"/>
                                                        (
                                                            <!-- TODO: This list of columns only works for the data (not form definition) table -->
                                                            created, last_modified_time, last_modified_by,
                                                            app, form,
                                                            <xsl:if test="$is-data-draft">document_id,</xsl:if>
                                                            deleted,
                                                            <xsl:if test="$is-data-draft and $support-auto-save">draft, </xsl:if>
                                                            xml
                                                            <xsl:if test="$is-data-draft">, username, groupname</xsl:if>
                                                        )
                                                    select
                                                        d.created,
                                                        <sql:param type="xs:dateTime" select="/request/timestamp"/>,
                                                        <sql:param type="xs:string" select="/request/username"/>,
                                                        d.app, d.form,
                                                        <xsl:if test="$is-data-draft">d.document_id,</xsl:if>
                                                        'Y',
                                                        <xsl:if test="$is-data-draft and $support-auto-save">'N', </xsl:if>
                                                        d.xml
                                                        <xsl:if test="$is-data-draft">, d.username, d.groupname</xsl:if>
                                                    from
                                                        orbeon_form_data d,
                                                        (
                                                            select
                                                                app, form, document_id, max(last_modified_time) last_modified_time
                                                            from orbeon_form_data
                                                            where
                                                                app =  <sql:param type="xs:string" select="/request/app"/>
                                                                and form =  <sql:param type="xs:string" select="/request/form"/>
                                                                <xsl:if test="$is-data-draft and $has-document-id">and document_id = <sql:param type="xs:string" select="/request/document-id"/></xsl:if>
                                                            group by
                                                                app, form, document_id
                                                        ) m
                                                    where
                                                        d.document_id = m.document_id
                                                        and d.last_modified_time = m.last_modified_time
                                                        and d.deleted = 'N'
                                                </sql:update>
                                            </sql:execute>
                                        </xsl:if>

                                        <!-- Delete drafts in the following two cases:
                                             - data is deleted, in which case we don't want to keep corresponding drafts
                                             - a draft is explicitly deleted, which can be done from the summary page -->
                                        <xsl:if test="$support-auto-save and /request/type = ('data', 'draft')">
                                            <xsl:variable name="delete-where">
                                                where
                                                    app = <sql:param type="xs:string" select="/request/app"/>
                                                    and form = <sql:param type="xs:string" select="/request/form"/>
                                                    and document_id = <sql:param type="xs:string" select="/request/document-id"/>
                                                    and draft = 'Y'
                                            </xsl:variable>
                                            <sql:execute><sql:update>delete from orbeon_form_data        <xsl:copy-of select="$delete-where"/></sql:update></sql:execute>
                                            <sql:execute><sql:update>delete from orbeon_form_data_attach <xsl:copy-of select="$delete-where"/></sql:update></sql:execute>
                                        </xsl:if>

                                    </sql:connection>
                                </result>
                            </sql:config>
                        </p:input>
                    </p:processor>

                </p:when>

                <!-- PUT -->
                <p:when test="/*/method = 'PUT'">

                    <!-- Query getting information about existing data for which we're creating a new row -->
                    <p:processor name="oxf:sql">
                        <p:input name="data" href="#request-description"/>
                        <p:input name="config" transform="oxf:unsafe-xslt" href="#request-description">
                            <sql:config xsl:version="2.0">
                                <existing>
                                    <sql:connection>
                                        <xsl:copy-of select="/request/sql:datasource"/>
                                        <xsl:variable name="is-data-draft" as="xs:boolean" select="/request/type = ('data', 'draft')"/>
                                        <xsl:variable name="is-attachment" as="xs:boolean" select="not(/request/content-type = ('application/xml', 'text/xml') or ends-with(/request/content-type, '+xml'))"/>
                                        <xsl:variable name="table-name" as="xs:string" select="concat(
                                            if ($is-data-draft) then 'orbeon_form_data' else 'orbeon_form_definition',
                                            if ($is-attachment) then '_attach' else '')"/>
                                        <xsl:variable name="columns-seq" select="('app', 'form',
                                            if ($is-data-draft) then 'document_id' else (),
                                            if ($is-attachment) then 'file_name' else ())"/>
                                        <xsl:variable name="columns" select="string-join($columns-seq, ', ')"/>
                                        <sql:execute>
                                            <sql:query>
                                                select created <xsl:if test="$is-data-draft">, username , groupname</xsl:if>
                                                from <xsl:value-of select="$table-name"/>
                                                where
                                                    (last_modified_time, <xsl:value-of select="$columns"/>)
                                                    in
                                                    (
                                                        select max(last_modified_time) last_modified_time, <xsl:value-of select="$columns"/>
                                                        from <xsl:value-of select="$table-name"/>
                                                        where
                                                            app = <sql:param type="xs:string" select="/request/app"/>
                                                            and form = <sql:param type="xs:string" select="/request/form"/>
                                                            <xsl:if test="$is-data-draft">and document_id = <sql:param type="xs:string" select="/request/document-id"/></xsl:if>
                                                            <xsl:if test="$is-attachment"> and file_name = <sql:param type="xs:string" select="/request/filename"/></xsl:if>
                                                        group by <xsl:value-of select="$columns"/>
                                                    )
                                                    and deleted = 'N'
                                            </sql:query>
                                            <sql:result-set>
                                                <sql:row-iterator>
                                                    <sql:get-columns format="xml"/>
                                                </sql:row-iterator>
                                            </sql:result-set>
                                        </sql:execute>
                                    </sql:connection>
                                </existing>
                            </sql:config>
                        </p:input>
                        <p:output name="data" id="existing-data"/>
                    </p:processor>

                    <p:processor name="oxf:sql">
                        <p:input name="data" href="aggregate('root', #request-description, #existing-data)"/>
                        <p:input name="config" transform="oxf:unsafe-xslt" href="aggregate('root', #request-description, #existing-data)">
                            <sql:config xsl:version="2.0">
                                <result>
                                    <sql:connection>
                                        <xsl:copy-of select="/root/request/sql:datasource"/>
                                        <xsl:variable name="support-auto-save" as="xs:boolean" select="xpl:property('oxf.fr.support-autosave')"/>
                                        <xsl:variable name="is-data-draft" as="xs:boolean" select="/root/request/type = ('data', 'draft')"/>
                                        <xsl:variable name="draft" as="xs:string" select="if (/root/request/type = 'draft') then 'Y' else 'N'"/>
                                        <xsl:variable name="is-attachment" as="xs:boolean" select="not(/root/request/content-type = ('application/xml', 'text/xml') or ends-with(/root/request/content-type, '+xml'))"/>
                                        <xsl:variable name="table-name" as="xs:string" select="concat(
                                            if ($is-data-draft) then 'orbeon_form_data' else 'orbeon_form_definition',
                                            if ($is-attachment) then '_attach' else '')"/>
                                        <sql:execute>
                                            <sql:update>
                                                insert into <xsl:value-of select="$table-name"/>
                                                (
                                                    created,
                                                    last_modified_time,
                                                    last_modified_by,
                                                    app, form,
                                                    <xsl:if test="$is-data-draft">document_id,</xsl:if>
                                                    deleted
                                                    <xsl:if test="$is-data-draft and $support-auto-save">, draft</xsl:if>
                                                    <xsl:if test="$is-attachment">, file_name, file_content</xsl:if>
                                                    <xsl:if test="not($is-attachment)">, xml</xsl:if>
                                                    <xsl:if test="$is-data-draft">, username, groupname</xsl:if>
                                                )
                                                values
                                                (
                                                    <sql:param type="xs:dateTime" select="{if (exists(/root/existing/created)) then '/root/existing/created' else '/root/request/timestamp'}"/>,
                                                    <sql:param type="xs:dateTime" select="/root/request/timestamp"/>,
                                                    <sql:param type="xs:string" select="/root/request/username"/>,
                                                    <sql:param type="xs:string" select="/root/request/app"/>,
                                                    <sql:param type="xs:string" select="/root/request/form"/>,
                                                    <xsl:if test="$is-data-draft"><sql:param type="xs:string" select="/root/request/document-id"/>,</xsl:if>
                                                    'N'
                                                    <xsl:if test="$is-data-draft and $support-auto-save">
                                                        , '<xsl:value-of select="$draft"/>'
                                                    </xsl:if>
                                                    <xsl:if test="$is-attachment">
                                                        , <sql:param type="xs:string" select="/root/request/filename"/>
                                                        , <sql:param type="xs:anyURI" sql-type="blob" select="/root/request/body" />
                                                    </xsl:if>
                                                    <xsl:if test="not($is-attachment)">
                                                        , <sql:param type="odt:xmlFragment" sql-type="clob" select="/root/request/document/*" />
                                                    </xsl:if>
                                                    <xsl:if test="$is-data-draft">
                                                        , <sql:param type="xs:string" select="{if (exists(/root/existing/username))  then '/root/existing/username'  else '/root/request/username'}"/>
                                                        , <sql:param type="xs:string" select="{if (exists(/root/existing/groupname)) then '/root/existing/groupname' else '/root/request/groupname'}"/>
                                                    </xsl:if>
                                                )
                                            </sql:update>
                                        </sql:execute>
                                        <xsl:if test="$support-auto-save and /root/request/type = 'data' and not($is-attachment)">
                                            <!--If we saved a "normal" document (not a draft), delete any draft document and draft attachments -->
                                            <xsl:variable name="delete-where">
                                                where
                                                    app = <sql:param type="xs:string" select="/root/request/app"/>
                                                    and form = <sql:param type="xs:string" select="/root/request/form"/>
                                                    and document_id = <sql:param type="xs:string" select="/root/request/document-id"/>
                                                    and draft = 'Y'
                                            </xsl:variable>
                                            <sql:execute><sql:update>delete from orbeon_form_data        <xsl:copy-of select="$delete-where"/></sql:update></sql:execute>
                                            <sql:execute><sql:update>delete from orbeon_form_data_attach <xsl:copy-of select="$delete-where"/></sql:update></sql:execute>
                                        </xsl:if>
                                        <xsl:if test="$support-auto-save and /root/request/type = 'draft'">
                                            <!-- If we just saved a draft, older drafts (if any) for the same app/form/document-id/file-name -->
                                            <sql:execute>
                                                <sql:update>
                                                    delete from <xsl:value-of select="$table-name"/>
                                                    where
                                                        app = <sql:param type="xs:string" select="/root/request/app"/>
                                                        and form = <sql:param type="xs:string" select="/root/request/form"/>
                                                        and document_id = <sql:param type="xs:string" select="/root/request/document-id"/>
                                                        <xsl:if test="$is-attachment"> and file_name = <sql:param type="xs:string" select="/root/request/filename"/></xsl:if>
                                                        and draft = 'Y'
                                                        and last_modified_time !=
                                                            (
                                                                <!-- Additional level of nesting required on MySQL http://stackoverflow.com/a/45498/5295 -->
                                                                select last_modified_time from
                                                                (
                                                                    select max(last_modified_time) last_modified_time
                                                                    from <xsl:value-of select="$table-name"/>
                                                                    where
                                                                        app = <sql:param type="xs:string" select="/root/request/app"/>
                                                                        and form = <sql:param type="xs:string" select="/root/request/form"/>
                                                                        and document_id = <sql:param type="xs:string" select="/root/request/document-id"/>
                                                                        <xsl:if test="$is-attachment"> and file_name = <sql:param type="xs:string" select="/root/request/filename"/></xsl:if>
                                                                        and draft = 'Y'
                                                                ) t
                                                            )
                                                </sql:update>
                                            </sql:execute>
                                        </xsl:if>
                                    </sql:connection>
                                </result>
                            </sql:config>
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
