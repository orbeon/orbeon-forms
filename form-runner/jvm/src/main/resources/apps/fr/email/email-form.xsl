<!--
    Copyright (C) 2022 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<xsl:stylesheet
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:metadata="java:org.orbeon.oxf.fr.FormRunnerMetadata"

    version="2.0">

    <xsl:variable name="data"          select="/*"                          as="element()"/>
    <xsl:variable name="xhtml"         select="doc('input:xhtml')/*"        as="element(xh:html)"/>
    <xsl:variable name="request"       select="doc('input:request')/*"      as="element(request)"/>
    <xsl:variable name="fr-resources"  select="doc('input:fr-resources')/*" as="element(resources)"/>

    <xsl:variable name="metadata-elem" select="frf:metadataInstanceRootOpt($xhtml)" as="element(metadata)"/>

    <!-- Language and template requested -->
    <xsl:variable
        name="request-language"
        select="$request/parameters/parameter[name = 'fr-language']/value"
        as="xs:string"/>
    <xsl:variable
        name="request-template"
        select="$request/parameters/parameter[name = 'fr-template']/value"/>
    <xsl:variable
        name="request-match"
        select="$request/parameters/parameter[name = 'fr-match']/value"/>
    <xsl:variable
        name="template-elems-all"
        select="$metadata-elem/email/templates/template[
            (p:is-blank(@xml:lang)    or @xml:lang = $request-language) and
            (empty($request-template) or @name = $request-template)
        ]"/>
    <xsl:variable
        name="template-elems"
        select="if ($request-match = 'all') then $template-elems-all else $template-elems-all[1]"/>

    <!-- App and form -->
    <xsl:variable name="app"  select="doc('input:parameters')/*/app"  as="xs:string"/>
    <xsl:variable name="form" select="doc('input:parameters')/*/form" as="xs:string"/>

    <!--
        Given a header name (e.g. `to`), and the corresponding property name (e.g. `oxf.fr.email.to`), tries to find
        the header values from:

        - Control values in the data: this is done using the form definition, and this job is delegated to the Scala
          function `headerValues()`. Depending on the form metadata, the header values can also come from expressions or
          static texts.
        - The value of the given property.
    -->
    <xsl:function name="fr:header-values" as="xs:string*">

        <xsl:param name="template-elem" as="element()"/>
        <xsl:param name="header-name"   as="xs:string"/>
        <xsl:param name="property-name" as="xs:string"/>

        <xsl:variable
            name="result"
            select="
                distinct-values(
                    for $holder in
                        (
                            frf:headerValues($xhtml, $template-elem, $data, $header-name),
                            p:property(string-join(($property-name, $app, $form), '.'))
                        )
                    return
                        normalize-space($holder)[. != '']
                )
            "
        />
        <xsl:sequence select="$result"/>

    </xsl:function>

    <xsl:function name="fr:build-message" as="xs:string">

        <xsl:param name="template-elem" as="element()"/>
        <xsl:param name="type"          as="xs:string"/>
        <xsl:param name="is-html"       as="xs:boolean"/>

        <xsl:variable
            name="template-text-elem"
            select="$template-elem/*[local-name() = $type]"/>
        <xsl:variable
            name="resource-elem"
            select="$fr-resources
                /resource[@xml:lang = $request-language]
                /email/*[local-name() = $type]
            "/>

        <xsl:choose>
            <xsl:when test="exists($template-text-elem)">

                <!-- Parameters to a template -->
                <xsl:variable
                    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
                    xmlns:saxon="http://saxon.sf.net/"
                    name="string-value"
                    select="
                        p:process-template(
                            $template-text-elem,
                            'en',
                            map:merge(
                                for $p in $metadata-elem/email/parameters/param[p:non-blank(name) and p:non-blank(@type)]
                                return
                                    map:entry(
                                        $p/name,
                                        if ($p/@type = 'ExpressionParam') then
                                            (: NOTE: The Form Runner function library is not in scope in this XSLT transformation.
                                               Could we scope it? :)
                                            string(($data/saxon:evaluate(frf:replaceVarReferencesWithFunctionCalls($p/expr, $p/expr, false(), (), ())))[1])
                                        else if ($p/@type = 'ControlValueParam') then
                                            (: 1. Just match by element name. This will also catch values in section templates if any. We
                                               could either use `fr:control-[string|typed]-value()`, or use `searchControlsTopLevelOnly`
                                               and `searchHoldersForClassUseSectionTemplates` which operate on the form definition and
                                               section templates without a live document. :)
                                            (: 2. Value is not formatted at all. Would need to be formatted properly like we should do
                                               with #3627. :)
                                            (: 3. It would be good to have a way to configure the values separator. :)
                                            string-join($data//*[empty(*) and name() = $p/controlName]/string(), ', ')
                                        else if ($p/@type = 'AllControlValuesParam') then
                                            metadata:findAllControlsWithValues($is-html, $template-elem/exclude-from-all-control-values/control)
                                        else if (
                                            $p/@type = (
                                                'LinkToEditPageParam',
                                                'LinkToViewPageParam',
                                                'LinkToNewPageParam',
                                                'LinkToSummaryPageParam',
                                                'LinkToHomePageParam',
                                                'LinkToFormsPageParam',
                                                'LinkToAdminPageParam',
                                                'LinkToPdfParam'
                                            )
                                        ) then
                                            frf:buildLinkBackToFormRunner($p/@type, $p/token = 'true')
                                        else
                                            error()
                                    )
                            )
                        )
                    "/>

                <xsl:value-of select="$string-value"/>

            </xsl:when>
            <xsl:otherwise>
                <!-- Use the value from resources or properties -->
                <xsl:value-of select="$resource-elem"/>
            </xsl:otherwise>
        </xsl:choose>
    </xsl:function>

    <xsl:template match="template">
        <xsl:variable
            name="enable-if-true"
            as="xs:boolean"
            select="
            (
                for $expression in enable-if-true
                return frf:evaluatedExpressionAsBoolean($xhtml, frf:replaceVarReferencesWithFunctionCalls($expression, $expression, false(), (), ())),
                true()
            )[1]
            "/>

        <xsl:choose>
            <xsl:when test="$enable-if-true">
                <message>
                    <xsl:variable name="template-elem" select="."/>

                    <xsl:variable
                        name="from-email-addresses"
                        as="xs:string*"
                        select="fr:header-values($template-elem, 'from', 'oxf.fr.email.from')[1]"/>

                    <xsl:variable
                        name="reply-to-email-addresses"
                        as="xs:string*"
                        select="fr:header-values($template-elem, 'reply-to', 'oxf.fr.email.reply-to')"/>

                    <xsl:variable
                        name="to-email-addresses"
                        as="xs:string*"
                        select="fr:header-values($template-elem, 'to', 'oxf.fr.email.to')"/>

                    <xsl:variable
                        name="cc-email-addresses"
                        as="xs:string*"
                        select="fr:header-values($template-elem, 'cc', 'oxf.fr.email.cc')"/>

                    <xsl:variable
                        name="bcc-email-addresses"
                        as="xs:string*"
                        select="fr:header-values($template-elem, 'bcc', 'oxf.fr.email.bcc')"/>

                    <!-- SMTP outgoing server settings -->
                    <smtp-host>
                        <xsl:value-of select="p:property(string-join(('oxf.fr.email.smtp.host', $app, $form), '.'))"/>
                    </smtp-host>
                    <xsl:variable name="port" select="p:property(string-join(('oxf.fr.email.smtp.port', $app, $form), '.'))"/>
                    <xsl:if test="normalize-space($port)">
                        <smtp-port><xsl:value-of select="$port"/></smtp-port>
                    </xsl:if>
                    <encryption>
                        <xsl:value-of select="p:property(string-join(('oxf.fr.email.smtp.encryption', $app, $form), '.'))"/>
                    </encryption>
                    <credentials>
                        <username>
                            <xsl:value-of select="p:property(string-join(('oxf.fr.email.smtp.username', $app, $form), '.'))"/>
                        </username>
                        <password>
                            <xsl:value-of select="p:property(string-join(('oxf.fr.email.smtp.credentials', $app, $form), '.'))"/>
                        </password>
                    </credentials>

                    <!-- Sender -->
                    <!-- Take the first one only, see https://serverfault.com/questions/554520 -->
                    <xsl:for-each select="$from-email-addresses[1]">
                        <from>
                            <email>
                                <xsl:value-of select="."/>
                            </email>
                        </from>
                    </xsl:for-each>
                    <!-- Reply-To -->
                    <xsl:for-each select="$reply-to-email-addresses">
                        <reply-to>
                            <email>
                                <xsl:value-of select="."/>
                            </email>
                        </reply-to>
                    </xsl:for-each>
                    <!-- Recipients -->
                    <xsl:for-each select="$to-email-addresses">
                        <to>
                            <email>
                                <xsl:value-of select="."/>
                            </email>
                        </to>
                    </xsl:for-each>
                    <xsl:for-each select="$cc-email-addresses">
                        <cc>
                            <email>
                                <xsl:value-of select="."/>
                            </email>
                        </cc>
                    </xsl:for-each>
                    <xsl:for-each select="$bcc-email-addresses">
                        <bcc>
                            <email>
                                <xsl:value-of select="."/>
                            </email>
                        </bcc>
                    </xsl:for-each>
                    <!-- Subject -->
                    <subject>
                        <xsl:value-of select="fr:build-message($template-elem, 'subject', false())"/>
                    </subject>
                    <!-- Custom headers -->
                    <xsl:for-each select="frf:customHeaderNames($xhtml, $template-elem)">
                        <header>
                            <name>
                                <xsl:value-of select="."/>
                            </name>
                            <value>
                                <xsl:value-of select="frf:headerValues($xhtml, $template-elem, $data, .)[1]"/>
                            </value>
                        </header>
                    </xsl:for-each>
                    <!-- Multipart body -->
                    <body content-type="multipart/related">
                        <!-- Email body -->

                        <xsl:variable
                            name="is-html"
                            select="$template-elem/body/@mediatype = 'text/html'"/>

                        <part name="text" content-type="{if ($is-html) then 'text/html' else 'text/plain'}">
                            <xsl:choose>
                                <xsl:when test="$is-html">

                                    <xsl:variable
                                        name="style"
                                        select="p:property(string-join(('oxf.fr.email.css.custom.inline', $app, $form), '.'))"/>

                                    <xsl:variable
                                        name="style-seq"
                                        select="
                                            if (p:non-blank($style)) then
                                                (
                                                    '&lt;head>',
                                                    '&lt;style type=&quot;text/css&quot;>',
                                                    $style,
                                                    '&lt;/style>',
                                                    '&lt;/head>'
                                                )
                                            else
                                                ()"/>

                                    <xsl:value-of
                                        select="
                                        string-join(
                                            (
                                                '&lt;html>',
                                                $style-seq,
                                                '&lt;body>',
                                                fr:build-message($template-elem, 'body', $is-html),
                                                '&lt;/body>',
                                                '&lt;/html>'
                                            ),
                                            ''
                                        )"/>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:value-of select="fr:build-message($template-elem, 'body', $is-html)"/>
                                </xsl:otherwise>
                            </xsl:choose>
                        </part>
                        <!-- XML, PDF and TIFF attachments if needed -->
                        <xsl:for-each select="'xml', 'pdf', 'tiff'">

                            <xsl:variable name="type" select="."/>
                            <xsl:variable name="property-prefix" select="concat('oxf.fr.email.attach-', $type)"/>
                            <xsl:variable
                                name="do-attach"
                                select="
                                    (
                                        for $attribute in $template-elem/attach/@*[local-name() = $type] return $attribute = 'true',
                                        p:property(string-join(($property-prefix, $app, $form), '.')),
                                        false()
                                    )[1]"
                                as="xs:boolean"/>

                            <xsl:if test="$do-attach">

                                <xsl:variable
                                    name="filename"
                                    select="(frf:emailAttachmentFilename($data, $type, $app, $form), concat('form.', $type))[1]"/>

                                <part
                                    name="form-{$type}"
                                    content-type="{if ($type = 'tiff') then 'image/tiff' else concat('application/', $type)}"
                                    content-disposition="attachment; filename=&quot;{$filename}&quot;"
                                    src="input:form-{$type}"/>
                            </xsl:if>
                        </xsl:for-each>

                        <!-- Other attachments if needed -->
                        <xsl:variable
                            name="attach-files"
                            select="
                                (
                                    $template-elem/attach/@files/string(),
                                    p:property(string-join(('oxf.fr.email.attach-files', $app, $form), '.')),
                                    'all'
                                )[1]"
                            as="xs:string"/>
                        <xsl:variable
                            name="attachment-holders"
                            as="element()*"
                            select="
                                if ($attach-files = 'all') then
                                    (
                                        frf:searchHoldersForClassTopLevelOnly       ($xhtml/xh:body,                 $data, 'fr-attachment'),
                                        frf:searchHoldersForClassUseSectionTemplates($xhtml/xh:head, $xhtml/xh:body, $data, 'fr-attachment')
                                    )
                                else if ($attach-files = 'selected') then
                                    frf:attachments($xhtml, $template-elem, $data)
                                else
                                    ()"/>
                        <xsl:for-each
                            select="
                                $attachment-holders/(
                                    self::*[@filename and normalize-space() != ''], (: single attachment    :)
                                    _[@filename and normalize-space() != '']        (: multiple attachments :)
                                )">
                            <!-- URL may be absolute or already point to persistence layer -->
                            <part
                                name="attachment-{position()}"
                                content-type="{@mediatype}"
                                content-disposition="attachment; filename=&quot;{@filename}&quot;"
                                src="input:attachment-{position()}"
                                fr:uri="{p:rewrite-service-uri(normalize-space(), true())}"/>
                        </xsl:for-each>

                    </body>
                </message>
            </xsl:when>
            <xsl:otherwise/>
        </xsl:choose>

    </xsl:template>

    <xsl:template match="/">
        <messages>
            <xsl:for-each select="$template-elems">
                <xsl:apply-templates select="." />
            </xsl:for-each>
        </messages>
    </xsl:template>
</xsl:stylesheet>
