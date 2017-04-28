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

    version="2.0">

    <xsl:variable name="data"         select="/*"                          as="element()"/>
    <xsl:variable name="xhtml"        select="doc('input:xhtml')/*"        as="element(xh:html)"/>
    <xsl:variable name="request"      select="doc('input:request')/*"      as="element(request)"/>
    <xsl:variable name="fr-resources" select="doc('input:fr-resources')/*" as="element(resources)"/>
    <xsl:variable name="attachments"  select="doc('input:attachments')/*"  as="element(attachments)"/>

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
                        for $raw-email in
                            tokenize(
                                $holder,
                                '(,|\s)\s*'
                            )
                        return
                            normalize-space($raw-email)[. != '']
                )
            "
        />
    </xsl:function>

    <xsl:template match="/">
        <message>

            <xsl:variable
                name="from-email-addresses"
                as="xs:string*"
                select="fr:find-emails('fr-email-sender', 'oxf.fr.email.from')[1]"/>

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
                        <!-- Append subject values to static subject, comma-separated -->
                        <xsl:value-of select="concat($fr-resources/resource[@xml:lang = $request-language]/email/subject, ' ', string-join($subject-values, ', '))"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <!-- Just put static subject -->
                        <xsl:value-of select="$fr-resources/resource[@xml:lang = $request-language]/email/subject"/>
                    </xsl:otherwise>
                </xsl:choose>
            </subject>
            <!-- Multipart body -->
            <body content-type="multipart/related">
                <!-- Email body -->
                <part name="text" content-type="text/plain">
                    <xsl:value-of select="$fr-resources/resource[@xml:lang = $request-language]/email/body"/>
                </part>
                <!-- XML attachment if needed -->
                <xsl:if test="p:property(string-join(('oxf.fr.email.attach-xml', $app, $form), '.'))">
                    <part name="form-xml" content-type="application/xml" content-disposition="attachment; filename=&quot;form.xml&quot;" src="input:form-xml"/>
                </xsl:if>
                <!-- PDF attachment if needed -->
                <xsl:if test="p:property(string-join(('oxf.fr.email.attach-pdf', $app, $form), '.'))">
                    <part name="form-pdf" content-type="application/pdf" content-disposition="attachment; filename=&quot;form.pdf&quot;" src="input:form-pdf"/>
                </xsl:if>
                <!-- TIFF attachment if needed -->
                <xsl:if test="p:property(string-join(('oxf.fr.email.attach-tiff', $app, $form), '.'))">
                    <part name="form-tiff" content-type="image/tiff" content-disposition="attachment; filename=&quot;form.tiff&quot;" src="input:form-tiff"/>
                </xsl:if>
                <!-- Other attachments if needed -->
                <xsl:for-each select="$attachments/attachment">
                    <part name="attachment-{position()}" content-type="{@mediatype}" content-disposition="attachment; filename=&quot;{@filename}&quot;" src="input:attachment-{position()}"/>
                </xsl:for-each>
            </body>
        </message>

    </xsl:template>
</xsl:stylesheet>
