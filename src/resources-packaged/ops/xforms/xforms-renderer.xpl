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
<!--
    This pipeline handles requests to process documents, forwarded from another web application.

    It handles:

    * XHTML+XForms (which is sent to the standard epilogue)
    * XHTML+XForms+Form Runner (which is sent to the standard epilogue after FR emulation)
    * XPL files with no output and no input
    * XPL files with one "data" output
    * Any other XML file (which is sent to the standard epilogue)

    Future enhancements:

    * Handle HTML tag soup input
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline" xmlns:oxf="http://www.orbeon.com/oxf/processors"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns:fr="http://orbeon.org/oxf/xml/form-runner" xmlns:xhtml="http://www.w3.org/1999/xhtml">

    <!-- Extract XForms document from request -->
    <p:processor name="oxf:scope-generator">
        <p:input name="config">
            <config>
                <key>oxf.xforms.renderer.document</key>
                <scope>request</scope>
            </config>
        </p:input>
        <p:output name="data" id="extracted-document"/>
    </p:processor>

    <p:choose href="#extracted-document">
        <p:when test="/p:config and /p:config/p:param[@type = 'output' and @name = 'data']">
            <!-- XPL -->
            <!-- Pipeline with a data output -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="#extracted-document"/>
                <p:output name="data" id="pipeline-result"/>
            </p:processor>
            <!-- Call up standard Orbeon Forms epilogue -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="/config/epilogue.xpl"/>
                <p:input name="data" href="#pipeline-result"/>
                <!-- There is no "current submission" -->
                <p:input name="instance">
                    <null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
                </p:input>
            </p:processor>
        </p:when>
        <p:when test="/p:config">
            <!-- XPL -->
            <!-- Pipeline without a data output -->
            <p:processor name="oxf:pipeline">
                <p:input name="config" href="#extracted-document"/>
            </p:processor>
        </p:when>
        <p:otherwise>
            <p:choose href="#extracted-document">
                <!-- Seems reasonable to assume that forms with a fr:view root want to be run by Form Runner... -->
                <p:when test="/xhtml:html/xhtml:body/fr:view">
                    <p:processor name="oxf:xslt">
                        <p:input name="data" href="#extracted-document"/>
                        <p:input name="config">
                            <parameters xsl:version="2.0" xmlns:xhtml="http://www.w3.org/1999/xhtml" xmlns:xforms="http://www.w3.org/2002/xforms">
                                <xsl:variable name="metadata"
                                    select="/xhtml:html/xhtml:head/xforms:model[1]/xforms:instance[@id='fr-form-metadata']/metadata"/>
                                <app>
                                    <xsl:value-of select="if ($metadata/application-name) then $metadata/application-name else 'no-name'"/>
                                </app>
                                <form>
                                    <xsl:value-of select="if ($metadata/form-name) then $metadata/form-name else 'no-name'"/>
                                </form>
                                <mode>Edit</mode>
                            </parameters>
                        </p:input>
                        <p:output name="data" id="parameters"/>
                    </p:processor>
                    <p:processor name="oxf:pipeline">
                        <p:input name="config" href="oxf:/apps/fr/unroll-form.xpl"/>
                        <p:input name="data" href="#extracted-document"/>
                        <p:input name="instance" href="#parameters"/>
                        <p:output name="data" id="converted-document"/>
                    </p:processor>
                    <!-- XForms -->
                    <!-- Call up standard Orbeon Forms epilogue with form parameters as the instance -->
                    <p:processor name="oxf:pipeline">
                        <p:input name="config" href="/config/epilogue.xpl"/>
                        <p:input name="data" href="#converted-document"/>
                        <!-- Pass submitted XML -->
                        <p:input name="instance" href="#parameters"/>
                    </p:processor>
                </p:when>
                <p:otherwise>
                    <!-- Run XInclude as that may be desirable (e.g. to include components) -->
                    <p:processor name="oxf:xinclude">
                        <p:input name="config" href="#extracted-document"/>
                        <p:output name="data" id="converted-document"/>
                    </p:processor>
                    <!-- XForms -->
                    <!-- Process XML submission if any -->
                    <!-- The idea is that if you submit XML to a servlet which forwards to Orbeon, it should be made available. -->
                    <p:processor name="oxf:pipeline">
                        <p:input name="config" href="/ops/pfc/xforms-xml-submission.xpl"/>
                        <!-- All those are empty (only the PFC uses them) -->
                        <p:input name="default-submission">
                            <null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
                        </p:input>
                        <p:input name="matcher-result">
                            <null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
                        </p:input>
                        <p:input name="setvalues">
                            <null xsi:nil="true" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"/>
                        </p:input>
                        <!-- Resulting submission if any -->
                        <p:output name="instance" id="submitted-xml"/>
                    </p:processor>
                    <!-- Call up standard Orbeon Forms epilogue -->
                    <p:processor name="oxf:pipeline">
                        <p:input name="config" href="/config/epilogue.xpl"/>
                        <p:input name="data" href="#converted-document"/>
                        <!-- Pass submitted XML -->
                        <p:input name="instance" href="#submitted-xml"/>
                    </p:processor>
                </p:otherwise>
            </p:choose>
        </p:otherwise>
    </p:choose>

</p:config>
