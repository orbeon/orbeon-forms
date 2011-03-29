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
    <!-- Path to the PDF -->
    <p:param type="output" name="data"/>

    <!-- Extract request parameters (app, form, document, and mode) from URL -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../request-parameters.xpl"/>
        <p:output name="data" id="parameters"/>
    </p:processor>

    <!-- Obtain PDF data -->
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="../detail/detail-model.xpl"/>
        <p:input name="instance"><null xsi:nil="true"/></p:input>
        <p:output name="data" id="xhtml"/>
    </p:processor>
    <p:processor name="oxf:pipeline">
        <p:input name="config" href="pdf-view.xpl"/>
        <p:input name="instance" href="#parameters"/>
        <p:input name="data" href="#xhtml"/>
        <p:output name="data" id="form-pdf"/>
    </p:processor>

    <!-- Store PDF data -->
    <p:processor name="oxf:file-serializer">
        <p:input name="config">
            <config>
                <scope>session</scope>
                <proxy-result>true</proxy-result>
                <content-type>application/pdf</content-type>
            </config>
        </p:input>
        <p:input name="data" href="#form-pdf"/>
        <p:output name="data" ref="data"/>
    </p:processor>

</p:config>
