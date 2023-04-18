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
<xsl:stylesheet
    xmlns:p="http://www.orbeon.com/oxf/pipeline"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:xh="http://www.w3.org/1999/xhtml"
    xmlns:frf="java:org.orbeon.oxf.fr.FormRunner"
    xmlns:fr="http://orbeon.org/oxf/xml/form-runner"
    xmlns:metadata="java:org.orbeon.oxf.fr.FormRunnerMetadata"

    version="2.0">

    <xsl:variable name="data"         select="/*"                          as="element()"/>
    <xsl:variable name="xhtml"        select="doc('input:xhtml')/*"        as="element(xh:html)"/>
    <xsl:variable name="request"      select="doc('input:request')/*"      as="element(request)"/>
    <xsl:variable name="fr-resources" select="doc('input:fr-resources')/*" as="element(resources)"/>
    <xsl:variable name="attachments"  select="doc('input:attachments')/*"  as="element(attachments)"/>

    <xsl:variable name="metadata"     select="frf:metadataInstanceRootOpt($xhtml)" as="element(metadata)"/>

    <!-- Language requested -->
    <xsl:variable
        name="request-language"
        select="$request/parameters/parameter[name = 'fr-language']/value"
        as="xs:string"/>

    <!-- App and form -->
    <xsl:variable name="app"  select="doc('input:parameters')/*/app"  as="xs:string"/>
    <xsl:variable name="form" select="doc('input:parameters')/*/form" as="xs:string"/>

    <xsl:function name="fr:find-emails" as="xs:string*">

        <xsl:param name="class-name"    as="xs:string"/>
        <xsl:param name="property-name" as="xs:string"/>

        <xsl:sequence
            select="
                distinct-values(
                    for $holder in
                        (
                            frf:searchHoldersForClassTopLevelOnly       (                $xhtml/xh:body, $data, $class-name),
                            frf:searchHoldersForClassUseSectionTemplates($xhtml/xh:head, $xhtml/xh:body, $data, $class-name),
                            p:property(string-join(($property-name, $app, $form), '.'))
                        )
                    return
                        normalize-space($holder)[. != '']
                )
            "
        />
    </xsl:function>

    <xsl:function name="fr:build-message" as="xs:string">

        <xsl:param name="type"    as="xs:string"/>
        <xsl:param name="is-html" as="xs:boolean"/>

        <xsl:variable name="metadata-elem" select="$metadata/email/*[local-name() = $type]"/>
        <xsl:variable name="resource-elem" select="$fr-resources/resource[@xml:lang = $request-language]/email/*[local-name() = $type]"/>

        <xsl:choose>
            <xsl:when test="exists($metadata-elem/template) and empty($metadata-elem/fr:param)">
                <!-- Just a template -->
                <xsl:value-of select="$metadata-elem/template[@xml:lang = $request-language]"/>
            </xsl:when>
            <xsl:when test="exists($metadata-elem/fr:param)">

                <!-- Parameters to a template -->
                <xsl:variable
                    xmlns:map="http://www.w3.org/2005/xpath-functions/map"
                    xmlns:saxon="http://saxon.sf.net/"
                    name="string-value"
                    select="
                        p:process-template(
                            (
                                $metadata-elem/template[@xml:lang = $request-language],
                                $resource-elem
                            )[1],
                            'en',
                            map:merge(
                                for $p in $metadata-elem/fr:param
                                return
                                    map:entry(
                                        $p/fr:name,
                                        if ($p/@type = 'ExpressionParam') then
                                            (: NOTE: The Form Runner function library is not in scope in this XSLT transformation.
                                               Could we scope it? :)
                                            string(($data/saxon:evaluate(frf:replaceVarReferencesWithFunctionCalls($p/expr, $p/fr:expr, false(), (), ())))[1])
                                        else if ($p/@type = 'ControlValueParam') then
                                            (: 1. Just match by element name. This will also catch values in section templates if any. We
                                               could either use `fr:control-[string|typed]-value()`, or use `searchControlsTopLevelOnly`
                                               and `searchHoldersForClassUseSectionTemplates` which operate on the form definition and
                                               section templates without a live document. :)
                                            (: 2. Value is not formatted at all. Would need to be formatted properly like we should do
                                               with #3627. :)
                                            (: 3. It would be good to have a way to configure the values separator. :)
                                            string-join($data//*[empty(*) and name() = $p/fr:controlName]/string(), ', ')
                                        else if ($p/@type = 'AllControlValuesParam') then
                                            metadata:findAllControlsWithValues($is-html)
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
                                            frf:buildLinkBackToFormRunner($p/@type, if ($p/token = 'true') then 'read update' else '')
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

    <xsl:template match="/">
        <message>

            <xsl:variable
                name="from-email-addresses"
                as="xs:string*"
                select="fr:find-emails('fr-email-sender', 'oxf.fr.email.from')[1]"/>

            <xsl:variable
                name="reply-to-email-addresses"
                as="xs:string*"
                select="fr:find-emails('fr-email-reply-to', 'oxf.fr.email.reply-to')"/>

            <xsl:variable
                name="to-email-addresses"
                as="xs:string*"
                select="fr:find-emails('fr-email-recipient', 'oxf.fr.email.to')"/>

            <xsl:variable
                name="cc-email-addresses"
                as="xs:string*"
                select="fr:find-emails('fr-email-cc', 'oxf.fr.email.cc')"/>

            <xsl:variable
                name="bcc-email-addresses"
                as="xs:string*"
                select="fr:find-emails('fr-email-bcc', 'oxf.fr.email.bcc')"/>

            <!-- Find `fr-email-subject` at the top-level and in section templates -->
            <xsl:variable
                name="subject-values"
                as="xs:string*"
                select="
                    distinct-values(
                        for $holder in
                            (
                                frf:searchHoldersForClassTopLevelOnly       (                $xhtml/xh:body, $data, 'fr-email-subject'),
                                frf:searchHoldersForClassUseSectionTemplates($xhtml/xh:head, $xhtml/xh:body, $data, 'fr-email-subject')
                            )
                        return
                            normalize-space($holder)[. != '']
                    )
                "
            />

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
                <xsl:choose>
                    <xsl:when test="count($subject-values) > 0">
                        <!-- LEGACY, see https://github.com/orbeon/orbeon-forms/issues/2428 -->
                        <!-- Append subject values to static subject, comma-separated -->
                        <xsl:value-of select="concat($fr-resources/resource[@xml:lang = $request-language]/email/subject, ' ', string-join($subject-values, ', '))"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <!-- Just put static subject -->
                        <xsl:value-of select="fr:build-message('subject', false())"/>
                    </xsl:otherwise>
                </xsl:choose>
            </subject>
            <!-- Multipart body -->
            <body content-type="multipart/related">
                <!-- Email body -->

                <xsl:variable
                    name="is-html"
                    select="$metadata/email/body/template[@xml:lang = $request-language]/@mediatype = 'text/html'"/>

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
                                        fr:build-message('body', $is-html),
                                        '&lt;/body>',
                                        '&lt;/html>'
                                    ),
                                    ''
                                )"/>
                        </xsl:when>
                        <xsl:otherwise>
                            <xsl:value-of select="fr:build-message('body', $is-html)"/>
                        </xsl:otherwise>
                    </xsl:choose>
                </part>
                <!-- XML, PDF and TIFF attachments if needed -->
                <xsl:for-each select="'xml', 'pdf', 'tiff'">

                    <xsl:variable name="type" select="."/>

                    <xsl:if test="p:property(string-join((concat('oxf.fr.email.attach-', $type), $app, $form), '.'))">

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
                <xsl:for-each select="$attachments/attachment">
                    <part
                        name="attachment-{position()}"
                        content-type="{@mediatype}"
                        content-disposition="attachment; filename=&quot;{@filename}&quot;"
                        src="input:attachment-{position()}"/>
                </xsl:for-each>
            </body>
        </message>

    </xsl:template>
</xsl:stylesheet>
