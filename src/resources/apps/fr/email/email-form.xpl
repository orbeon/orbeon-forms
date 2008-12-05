<?xml version="1.0" encoding="UTF-8"?>
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
          xmlns:oxf="http://www.orbeon.com/oxf/processors"
          xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
          xmlns:xs="http://www.w3.org/2001/XMLSchema"
          xmlns:xhtml="http://www.w3.org/1999/xhtml"
          xmlns:xforms="http://www.w3.org/2002/xforms"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xmlns:saxon="http://saxon.sf.net/"
          xmlns:pipeline="java:org.orbeon.oxf.processor.pipeline.PipelineFunctionLibrary">

    <!-- fr-form-instance -->
    <p:param type="input" name="instance"/>

    <p:param type="output" name="data"/>

    <!-- Extract page detail (app, form, document, and mode) from URL -->
    <p:processor name="oxf:request">
        <p:input name="config">
            <config>
                <include>/request/parameters/parameter</include>
            </config>
        </p:input>
        <p:output name="data" id="request"/>
    </p:processor>

    <!-- Extract request parameters (app, form, document, and mode) from URL -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../request-parameters.xpl"/>
        <p:output name="data" id="parameters"/>
    </p:processor>

    <!-- Obtain the form definition -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../detail/read-form.xpl"/>
        <p:input name="instance" href="#parameters"/>
        <p:output name="data" id="xhtml-fr-xforms"/>
    </p:processor>

    <!-- Retrieve Form Runner resources -->
    <p:processor name="oxf:url-generator">
        <p:input name="config" transform="oxf:unsafe-xslt" href="#parameters">
            <config xsl:version="2.0">
                <url>
                    <xsl:value-of select="pipeline:rewriteResourceURI(concat('/fr/service/i18n/fr-resources/', /*/app, '/', /*/form), true())"/>
                </url>
                <!-- Forward the same headers that the XForms engine forwards -->
                <forward-headers><xsl:value-of select="pipeline:property('oxf.xforms.forward-submission-headers')"/></forward-headers>
            </config>
        </p:input>
        <p:output name="data" id="fr-resources"/>
    </p:processor>

    <!-- Build email message -->
    <p:processor name="oxf:unsafe-xslt">
        <p:input name="data" href="#instance"/>
        <p:input name="xhtml" href="#xhtml-fr-xforms"/>
        <p:input name="request" href="#request"/>
        <p:input name="parameters" href="#parameters"/>
        <p:input name="fr-resources" href="#fr-resources"/>
        <p:input name="config">
            <message xsl:version="2.0">

                <xsl:variable name="data" select="/*" as="element()"/>
                <xsl:variable name="xhtml" select="doc('input:xhtml')/*" as="element(xhtml:html)"/>
                <xsl:variable name="request" select="doc('input:request')/*" as="element(request)"/>
                <xsl:variable name="fr-resources" select="doc('input:fr-resources')/*" as="element(resources)"/>

                <!-- Language requested -->
                <xsl:variable name="request-language" select="$request/parameters/parameter[name = 'fr-language']/value" as="xs:string"/>

                <!-- App and form -->
                <xsl:variable name="app" select="doc('input:parameters')/*/app" as="xs:string"/>
                <xsl:variable name="form" select="doc('input:parameters')/*/form" as="xs:string"/>

                <!-- Find fr-email-recipient controls and binds -->

                <xsl:variable name="controls" as="element()*"
                              select="$xhtml/xhtml:body//xforms:*[@class and tokenize(@class, ' ') = 'fr-email-recipient']"/>
                <xsl:variable name="binds" as="element(xforms:bind)*"
                              select="for $control in $controls return $xhtml/xhtml:head/xforms:model//xforms:bind[@id = $control/@bind]"/>

                <xsl:variable name="paths" as="xs:string*"
                              select="for $bind in $binds return string-join(($bind/ancestor-or-self::xforms:bind/@nodeset)[position() gt 1], '/')"/>

                <!-- Extract email addresses from form if any -->
                <xsl:variable name="email-addresses" as="xs:string*"
                              select="for $path in $paths return $data/saxon:evaluate($path)"/>

                <!-- SMTP outgoing server settings -->
                <smtp-host>
                    <xsl:value-of select="pipeline:property(string-join(('oxf.fr.email.smtp.host', $app, $form), '.'))"/>
                </smtp-host>
                <credentials>
                    <username>
                        <xsl:value-of select="pipeline:property(string-join(('oxf.fr.email.smtp.username', $app, $form), '.'))"/>
                    </username>
                    <password>
                        <xsl:value-of select="pipeline:property(string-join(('oxf.fr.email.smtp.credentials', $app, $form), '.'))"/>
                    </password>
                </credentials>

                <!-- Sender -->
                <from>
                    <email>
                        <xsl:value-of select="pipeline:property(string-join(('oxf.fr.email.from', $app, $form), '.'))"/>
                    </email>
                </from>
                <!-- Recipients -->
                <xsl:for-each select="$email-addresses">
                    <to>
                        <email>
                            <xsl:value-of select="."/>
                        </email>
                    </to>
                </xsl:for-each>
                <to>
                    <email>
                        <xsl:value-of select="pipeline:property(string-join(('oxf.fr.email.to', $app, $form), '.'))"/>
                    </email>
                </to>
                <!-- Message details -->
                <subject>
                    <xsl:value-of select="$fr-resources/resource[@xml:lang = $request-language]/email/subject"/>
                </subject>
                <body content-type="multipart/related">
                    <part name="text" content-type="text/plain">
                        <xsl:value-of select="$fr-resources/resource[@xml:lang = $request-language]/email/body"/>
                    </part>
                    <xsl:if test="pipeline:property(string-join(('oxf.fr.email.attach-xml', $app, $form), '.'))">
                        <part name="form-xml" content-type="application/xml" content-disposition="inline; filename=&quot;form.xml&quot;" src="input:form-xml"/>
                    </xsl:if>
                    <xsl:if test="pipeline:property(string-join(('oxf.fr.email.attach-pdf', $app, $form), '.'))">
                        <part name="form-pdf" content-type="application/pdf" content-disposition="inline; filename=&quot;form.pdf&quot;" src="input:form-pdf"/>
                    </xsl:if>
                </body>
            </message>
        </p:input>
        <p:output name="data" id="message"/>
    </p:processor>

    <!-- Obtain PDF data for attachment -->
    <!-- TODO: add option to get PDF from temp file -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../detail/detail-model.xpl"/>
        <p:input name="instance"><null xsi:nil="true"/></p:input>
        <p:output name="data" id="xhtml"/>
    </p:processor>
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../print/pdf-view.xpl"/>
        <p:input name="instance" href="#parameters"/>
        <p:input name="data" href="#xhtml"/>
        <p:output name="data" id="form-pdf"/>
    </p:processor>

    <!-- Convert form data for attachment -->
    <p:processor name="oxf:xml-converter">
        <p:input name="data" href="#instance"/>
        <p:input name="config"><config/></p:input>
        <p:output name="data" id="form-xml"/>
    </p:processor>

    <!-- Send the email -->
    <p:processor name="oxf:email">
        <!-- The instance contains the email message -->
        <p:input name="data" href="#message"/>
        <!-- The attachment contains the XML document -->
        <p:input name="form-xml" href="#form-xml"/>
        <!-- The attachment contains the pdf document -->
        <p:input name="form-pdf" href="#form-pdf"/>
    </p:processor>

    <!-- If something goes wrong, an exception will be thrown before we get here -->
    <p:processor name="oxf:identity">
        <p:input name="data">
            <status>success</status>
        </p:input>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
