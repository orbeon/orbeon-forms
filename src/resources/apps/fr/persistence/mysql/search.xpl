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
        xmlns:xs="http://www.w3.org/2001/XMLSchema"
        xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
        xmlns:saxon="http://saxon.sf.net/"
        xmlns:oxf="http://www.orbeon.com/oxf/processors"
        xmlns:xi="http://www.w3.org/2001/XInclude"
        xmlns:xpl="java:org.orbeon.oxf.pipeline.api.FunctionLibrary"
        xmlns:xf="http://www.w3.org/2002/xforms"
        xmlns:xxf="http://orbeon.org/oxf/xml/xforms"
        xmlns:ev="http://www.w3.org/2001/xml-events"
        xmlns:f="http//www.orbeon.com/function"
        xmlns:frf="java:org.orbeon.oxf.fr.FormRunner">

    <!--
        Search instance, e.g.:

        <search>
            <query>free text search</query>
            <query name="title" path="details/title" type="xs:string" control="input">title</query>
            <query name="author" path="details/author" type="xs:string" control="input">author</query>
            <query name="language" path="details/language" type="xs:string" control="select1">en</query>
            <page-size>5</page-size>
            <page-number>1</page-number>
            <lang>en</lang>
        </search>
    -->
    <p:param name="instance" type="input"/>

    <!--
        Search result, e.g.:

        <documents total="2" search-total="2" page-size="5" page-number="1" query="">
            <document created="2008-07-28T18:43:59.363-07:00" last-modified="2008-07-28T18:43:59.363-07:00" name="329737EA7637FAC1BC8FD6139BDFAE97" offline="">
                <details>
                    <detail>a</detail>
                    <detail>aa</detail>
                    <detail/>
                </details>
            </document>
            <document created="2008-07-25T17:31:19.016-07:00" last-modified="2008-07-25T17:34:33.503-07:00" name="C7AEA87DA2CC07680D97BC28A87EBAF5" offline="">
                <details>
                    <detail>ab</detail>
                    <detail>ba</detail>
                    <detail/>
                </details>
            </document>
        </documents>
    -->
    <p:param name="data" type="output"/>

    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../common-search.xpl"/>
        <p:input name="search" href="#instance"/>
        <p:output name="search" id="search-input"/>
    </p:processor>

    <p:processor name="oxf:request">
        <p:input name="config">
            <config stream-type="xs:anyURI">
                <include>/request/headers/header[name = 'orbeon-datasource']</include>
                <include>/request/headers/header[name = 'orbeon-username']</include>
                <include>/request/headers/header[name = 'orbeon-group']</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <p:processor name="oxf:xslt">
        <p:input name="data" href="#search-input"/>
        <p:input name="request" href="#request"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

                <!-- Annotate with username/group, so this info is available to SQL processor -->
                <xsl:template match="/search">
                    <xsl:variable name="headers" select="doc('input:request')/request/headers/header"/>
                    <xsl:copy>
                        <xsl:attribute name="orbeon-username" select="$headers[name = 'orbeon-username']/value/string()"/>
                        <xsl:attribute name="orbeon-group"    select="$headers[name = 'orbeon-group']/value/string()"/>
                        <xsl:apply-templates select="@* | node()"/>
                    </xsl:copy>
                </xsl:template>

                <!-- Hacky rewrite of the XPath to support both short (xh, xf) and long (xhtml, xforms) prefixes
                     See: https://github.com/orbeon/orbeon-forms/issues/598 -->
                <xsl:template match="query/@path">
                    <xsl:attribute name="path" select="
                        if (starts-with(., 'xh:')) then
                            concat('/*/', ., ' | /*/', replace(replace(., 'xh:', 'xhtml:'), 'xf:', 'xforms:'))
                        else if (starts-with(., 'xhtml:')) then
                            concat('/*/', ., ' | /*/', replace(replace(., 'xhtml:', 'xh:'), 'xforms:', 'xf:'))
                        else concat('/*/', .)"/>
                </xsl:template>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="search"/>
    </p:processor>

    <p:processor name="oxf:xforms-submission">
        <p:input name="request"><dummy/></p:input>
        <p:input name="submission" transform="oxf:xslt" href="#search-input">
            <xf:submission xsl:version="2.0" method="get" replace="instance"
                               resource="/fr/service/persistence/form/{encode-for-uri(/search/app)}/{encode-for-uri(/search/form)}"/>
        </p:input>
        <p:output name="response" id="form"/>
    </p:processor>

    <!-- Run query -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="form" href="#form"/>
        <p:input name="data" href="#search"/>
        <p:input name="request" href="#request"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:include href="../common-owner-group.xsl"/>

                <xsl:variable name="permissions"              select="doc('input:form')/forms/form/permissions"/>
                <xsl:variable name="search-operations"        select="('*', 'read', 'update', 'delete')"/>
                <xsl:variable name="search-permissions"       select="$permissions/permission[p:split(@operations)  = $search-operations]"/>
                <xsl:variable name="support-auto-save"        as="xs:boolean" select="xpl:property('oxf.fr.support-autosave')"/>
                <xsl:variable name="include-drafts"           as="xs:boolean" select="$support-auto-save     and (empty(/search/drafts) or /search/drafts = ('include', 'only')) and /search/@orbeon-username != ''"/>
                <xsl:variable name="include-non-drafts"       as="xs:boolean" select="not($support-auto-save) or (empty(/search/drafts) or /search/drafts = ('include', 'exclude'))"/>

                <!-- Are we authorized to see all the data based because of our role? -->
                <xsl:variable name="operations-from-role" select="frf:javaAuthorizedOperationsBasedOnRoles($permissions)"/>
                <xsl:variable name="authorized-based-on-role" select="$operations-from-role = $search-operations"/>

                <!-- Are we authorized to see data if we are the owner / group member? -->
                <xsl:variable name="authorized-if-owner" select="exists($search-permissions[owner])"/>
                <xsl:variable name="authorized-if-group-member" select="exists($search-permissions[group-member])"/>

                <xsl:template match="/">

                    <sql:config>
                        <documents>
                            <sql:connection>
                                <sql:datasource><xsl:value-of select="doc('input:request')/request/headers/header[name = 'orbeon-datasource']/value/string() treat as xs:string"/></sql:datasource>

                                <!-- Query that returns all the search results, which we will reuse in multiple places -->
                                <xsl:variable name="query">
                                    select
                                        created, <xsl:value-of select="$last-modified-time"/>, document_id
                                        <!-- Go over detail columns and extract data from XML -->
                                        <xsl:for-each select="/search/query[@path]">
                                            , extractValue(xml, '<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>') detail_<xsl:value-of select="position()"/>
                                        </xsl:for-each>
                                        <xsl:if test="$owner-group">, username, groupname</xsl:if>
                                        <xsl:if test="$support-auto-save">, draft</xsl:if>
                                    from
                                    (
                                        <xsl:if test="$include-non-drafts">
                                            (
                                                select data.*
                                                from orbeon_form_data data,
                                                    (
                                                        select max(<xsl:value-of select="$last-modified-time"/>) <xsl:value-of select="$last-modified-time"/>, app, form, document_id
                                                        from orbeon_form_data
                                                        where
                                                            app = <sql:param type="xs:string" select="/search/app"/>
                                                            and form = <sql:param type="xs:string" select="/search/form"/>
                                                            <xsl:if test="$support-auto-save"> and draft = 'N'</xsl:if>
                                                        group by app, form, document_id
                                                    ) latest
                                                where
                                                    <!-- Merge with 'latest', to make sure we only consider the document with the most recent last_date -->
                                                    data.<xsl:value-of select="$last-modified-time"/> = latest.<xsl:value-of select="$last-modified-time"/>
                                                    and data.app = latest.app
                                                    and data.form = latest.form
                                                    and data.document_id = latest.document_id
                                                    and data.deleted = 'N'
                                                    <xsl:if test="$support-auto-save"> and data.draft = 'N'</xsl:if>
                                                    <xsl:copy-of select="f:search-conditions(/)"/>
                                                    <xsl:copy-of select="f:owner-group-condition('data')"/>
                                            )
                                        </xsl:if>
                                        <xsl:if test="$include-non-drafts and $include-drafts">
                                            union all
                                        </xsl:if>
                                        <xsl:if test="$include-drafts">
                                            (
                                                select *
                                                from orbeon_form_data d1
                                                where
                                                    app = <sql:param type="xs:string" select="/search/app"/>
                                                    and form = <sql:param type="xs:string" select="/search/form"/>
                                                    and draft = 'Y'
                                                    <xsl:if test="exists(/search/drafts/@for-document-id)">
                                                        and document_id = <sql:param type="xs:string" select="/search/drafts/@for-document-id"/>
                                                    </xsl:if>
                                                    <xsl:if test="/search/drafts/@for-never-saved-document = 'true'">
                                                        and
                                                        (
                                                            select count(*)
                                                            from orbeon_form_data d2
                                                            where
                                                                d2.app = <sql:param type="xs:string" select="/search/app"/>
                                                                and d2.form = <sql:param type="xs:string" select="/search/form"/>
                                                                and d2.draft = 'N'
                                                                and d2.document_id = d1.document_id
                                                        ) = 0
                                                    </xsl:if>
                                                    <xsl:copy-of select="f:search-conditions(/)"/>
                                                    <xsl:copy-of select="f:owner-group-condition('d1')"/>
                                            )
                                        </xsl:if>
                                    ) mysql1
                                    order by created desc
                                </xsl:variable>

                                <!-- Get total number of document in collection for this app/form
                                     - the count includes drafts, whether returned or not, except for anonymous users, who can't use the draft functionality  -->
                                <sql:execute>
                                    <sql:query>
                                        select
                                            (
                                                select count(*) from orbeon_form_data data
                                                where
                                                    (
                                                        app, form, document_id,
                                                        <xsl:value-of select="$last-modified-time"/>
                                                        <xsl:if test="$support-auto-save">, draft</xsl:if>
                                                    )
                                                    in
                                                    (
                                                        select
                                                            app, form, document_id,
                                                            max(<xsl:value-of select="$last-modified-time"/>) <xsl:value-of select="$last-modified-time"/>
                                                            <xsl:if test="$support-auto-save">, draft</xsl:if>
                                                        from orbeon_form_data
                                                        where
                                                            app = <sql:param type="xs:string" select="/search/app"/>
                                                            and form = <sql:param type="xs:string" select="/search/form"/>
                                                            <xsl:if test="$support-auto-save and /search/@orbeon-username = ''"> and draft = 'N'</xsl:if>
                                                        group by app, form, document_id
                                                        <xsl:if test="$support-auto-save">, draft</xsl:if>
                                                    )
                                                    and deleted = 'N'
                                                    <xsl:copy-of select="f:owner-group-condition('data')"/>
                                            ) total,
                                            (
                                                select count(*) from (<xsl:copy-of select="$query"/>) a
                                            ) search_total
                                    </sql:query>
                                    <sql:result-set>
                                        <sql:row-iterator>
                                            <sql:get-columns format="xml"/>
                                        </sql:row-iterator>
                                    </sql:result-set>
                                </sql:execute>

                                <!-- Get details -->
                                <sql:execute>
                                    <sql:query>
                                        select *
                                          from ( select
                                          a.*, @rownum:=@rownum+1 rnum
                                              from (SELECT @rownum:=0) r, ( <xsl:copy-of select="$query"/> ) a
                                              LIMIT <xsl:value-of select="/search/page-number * /search/page-size"/> ) v
                                        where rnum  > <xsl:value-of select="(/search/page-number - 1) * /search/page-size"/>
                                    </sql:query>
                                    <sql:result-set>
                                        <sql:row-iterator>
                                            <document>
                                                <created><sql:get-column-value column="created"/></created>
                                                <last-modified><sql:get-column-value column="{$last-modified-time}"/></last-modified>
                                                <document-id><sql:get-column-value column="document_id"/></document-id>
                                                <username><sql:get-column-value column="username"/></username>
                                                <groupname><sql:get-column-value column="groupname"/></groupname>
                                                <xsl:if test="$support-auto-save"><draft><sql:get-column-value column="draft"/></draft></xsl:if>
                                                <xsl:for-each select="/search/query[@path]">
                                                    <detail><sql:get-column-value column="detail_{position()}"/></detail>
                                                </xsl:for-each>
                                            </document>
                                        </sql:row-iterator>
                                    </sql:result-set>
                                </sql:execute>
                            </sql:connection>
                        </documents>
                    </sql:config>
                </xsl:template>
                <xsl:function name="f:escape-lang">
                    <xsl:param name="text" as="xs:string"/>
                    <xsl:param name="lang" as="xs:string"/>
                    <xsl:value-of select="replace($text, '\[@xml:lang = \$fb-lang\]', concat('[@xml:lang = ''', f:escape-sql($lang), ''']'))"/>
                </xsl:function>
                <xsl:function name="f:escape-sql">
                    <xsl:param name="text" as="xs:string"/>
                    <xsl:value-of select="replace($text, '''', '''''')"/>
                </xsl:function>
                <xsl:function name="f:namespaces">
                    <xsl:param name="query" as="element(query)"/>
                    <xsl:for-each select="in-scope-prefixes($query)">
                        <xsl:text>xmlns:</xsl:text>
                        <xsl:value-of select="."/>
                        <xsl:text>="</xsl:text>
                        <xsl:value-of select="namespace-uri-for-prefix(., $query)"/>
                        <xsl:text>" </xsl:text>
                    </xsl:for-each>
                </xsl:function>
                <!-- Condition on owner / group -->
                <xsl:function name="f:owner-group-condition">
                    <xsl:param name="table" as="xs:string"/>
                    <xsl:if test="$owner-group and not($authorized-based-on-role)">
                        and (
                            <xsl:if test="$authorized-if-owner"><xsl:value-of select="$table"/>.username = <sql:param type="xs:string" select="/search/@orbeon-username"/></xsl:if>
                            <xsl:if test="$authorized-if-owner and $authorized-if-group-member"> or </xsl:if>
                            <xsl:if test="$authorized-if-group-member"><xsl:value-of select="$table"/>.groupname = <sql:param type="xs:string" select="/search/@orbeon-group"/></xsl:if>
                        )
                    </xsl:if>
                </xsl:function>
                <!-- Search conditions -->
                <xsl:function name="f:search-conditions">
                    <xsl:param name="search" as="document-node()"/>
                    <!-- Conditions on searchable columns -->
                    <xsl:for-each select="$search/search/query[@path and normalize-space() != '']">
                        <xsl:choose>
                            <xsl:when test="@match = 'exact'">
                                <!-- Exact match -->
                                and extractValue(data.xml, '<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>')
                                = '<xsl:value-of select="f:escape-sql(.)"/>'
                            </xsl:when>
                            <xsl:otherwise>
                                <!-- Substring, case-insensitive match -->
                                and lower(extractValue(data.xml, '<xsl:value-of select="f:escape-sql(f:escape-lang(@path, /*/lang))"/>'))
                                like '%<xsl:value-of select="lower-case(f:escape-sql(.))"/>%'
                            </xsl:otherwise>
                        </xsl:choose>
                    </xsl:for-each>
                    <!-- Condition for free text search -->
                    <xsl:if test="$search/search/query[empty(@path) and normalize-space() != '']">
                         and data.xml like <sql:param type="xs:string" select="concat('%', /search/query[not(@path)], '%')"/>
                    </xsl:if>
                </xsl:function>
            </xsl:stylesheet>
        </p:input>
        <p:output name="data" id="sql-config"/>
    </p:processor>
    <p:processor name="oxf:sql">
        <p:input name="data" href="#search"/>
        <p:input name="config" href="#sql-config"/>
        <p:output name="data" id="sql-output"/>
    </p:processor>

    <!-- Transform output from SQL processor into the XML form the caller expects -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#sql-output"/>
        <p:input name="form" href="#form"/>
        <p:input name="config">
            <xsl:stylesheet version="2.0">
                <xsl:import href="oxf:/oxf/xslt/utils/copy.xsl"/>

                <xsl:variable name="permissions" select="doc('input:form')/forms/form/permissions"/>
                <xsl:variable name="support-auto-save" as="xs:boolean" select="xpl:property('oxf.fr.support-autosave')"/>

                <!-- Move total and search-total as attribute -->
                <xsl:template match="documents">
                    <documents total="{total}" search-total="{search-total}">
                        <xsl:apply-templates select="document"/>
                    </documents>
                </xsl:template>

                <!-- Move created, last-modified, and name as attributes -->
                <!-- Add wrapping details element -->
                <xsl:template match="document">
                    <document created="{created}" last-modified="{last-modified}" name="{document-id}" operations="{frf:javaAllAuthorizedOperations($permissions, string(username), string(groupname))}">
                        <xsl:if test="$support-auto-save">
                            <xsl:attribute name="draft" select="draft"/>
                        </xsl:if>
                        <details>
                            <xsl:for-each select="detail">
                                <detail>
                                    <xsl:value-of select="."/>
                                </detail>
                            </xsl:for-each>
                        </details>
                    </document>
                </xsl:template>

            </xsl:stylesheet>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>


</p:config>
