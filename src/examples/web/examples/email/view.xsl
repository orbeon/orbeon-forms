<!--
    Copyright (C) 2004 Orbeon, Inc.
  
    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.
  
    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.
  
    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<html xmlns="http://www.w3.org/1999/xhtml"
      xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
      xmlns:xforms="http://www.w3.org/2002/xforms"
      xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
      xmlns:f="http://orbeon.org/oxf/xml/formatting"
      xmlns:xhtml="http://www.w3.org/1999/xhtml"
      xsl:version="2.0">
    <head>
        <title>Email</title>
    </head>
    <body>
        <xforms:group>
            <p>
                <xsl:choose>
                    <xsl:when test="status = 'success'">
                        <span style="color: green">Email sent!</span>
                    </xsl:when>
                    <xsl:when test="status = 'failure'">
                        <span style="color: red">Please correct the validation errors before submitting the form.</span>
                    </xsl:when>
                </xsl:choose>
            </p>
            <p>
                <span style="color: red">NOTE: You need to configure access to a SQL database for
                this example to work.</span>
            </p>
            <p>
                Please fill-out the mandatory fields below and submit the form. An email providing
                alternative text and HTML versions of the body specified will be sent, with several
                attachments:
            </p>
            <ul>
                <li>A static JPEG image (related to the HTML document)</li>
                <li>A PDF file produced dynamically from a SQL database</li>
                <li>A chart in PNG format produced dynamically from a configuration file (related to the HTML document)</li>
            </ul>
            <p>
                This example also illustrates XForms model features such as:
            </p>
            <ul>
                <li><b>constraints:</b> enforce validation constraints on certain form elements</li>
                <li><b>calculate:</b> dynamically calculate form values based on other form values</li>
            </ul>
            <table class="gridtable">
                <tr>
                    <th>SMTP Host *</th>
                    <td colspan="3">
                        <xforms:input ref="message/smtp-host"/>
                    </td>
                </tr>
                <tr>
                    <th>From Email *</th>
                    <td>
                        <xforms:input ref="message/from/email"/>
                    </td>
                    <th>From Name</th>
                    <td>
                        <xforms:input ref="message/from/name"/>
                    </td>
                </tr>
                <tr>
                    <th>To Email *</th>
                    <td>
                        <xforms:input ref="message/to/email"/>
                    </td>
                    <th>To Name</th>
                    <td>
                        <xforms:input ref="message/to/name"/>
                    </td>
                </tr>
                <tr>
                    <th>Subject *</th>
                    <td colspan="3">
                        <xforms:input ref="message/subject" xhtml:style="width: 100%"/>
                    </td>
                </tr>
                <tr>
                    <th>Body *</th>
                    <td colspan="3">
                        <xforms:textarea ref="message/body/part[2]/part/html/body/p[1]" xhtml:style="width: 100%" xhtml:rows="20"/>
                    </td>
                </tr>
            </table>
            <p>
                <xforms:submit xxforms:appearance="button">
                    <xforms:label>Send an Email!</xforms:label>
                </xforms:submit>
            </p>
        </xforms:group>
    </body>
</html>
