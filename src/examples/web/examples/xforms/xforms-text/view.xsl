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
<xhtml:html xmlns:xforms="http://www.w3.org/2002/xforms"
    xmlns:f="http://orbeon.org/oxf/xml/formatting" 
    xmlns:xhtml="http://www.w3.org/1999/xhtml"
    xmlns:xxforms="http://orbeon.org/oxf/xml/xforms"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:ev="http://www.w3.org/2001/xml-events"
    xsl:version="2.0">
    
    <xhtml:head>
        <xhtml:title>XForms Text Controls</xhtml:title>
        <xforms:model xmlns:xforms="http://www.w3.org/2002/xforms"
                xmlns:xs="http://www.w3.org/2001/XMLSchema">
            <xforms:instance id="instance">
                <form>
                    <text>35</text>
                    <secret>42</secret>
                    <textarea>The world is but a canvas for the imagination.</textarea>
                    <label>Hello, World!</label>
                    <date/>
                    <date2/>
                    <time/>     
                    <gaga>
                        <b>Hey</b>
                    </gaga>
                </form>
            </xforms:instance>
            <xforms:instance id="formatted">
                <formated-instance>
                    &lt;span>
                    &lt;/span>
                </formated-instance>
            </xforms:instance>                
            <xforms:bind nodeset="/form/text" constraint="number(.) >= 0"/>
            <xforms:bind nodeset="/form/secret" constraint="string(.) = '42'"/>
            <xforms:bind nodeset="/form/textarea" constraint="string-length(.) > 10"/>
            <xforms:bind nodeset="/form/date" type="xs:date"/>
            <xforms:bind nodeset="/form/date2" type="xs:date"/>
            <xforms:bind nodeset="/form/time" type="xs:time"/>
            <xforms:bind nodeset="instance('formatted')" 
                calculate="saxon:serialize(xxforms:call-xpl
                    ('oxf:/oxf-theme/format.xpl', 'data', instance('instance'), 'data')/*, 'html')"/>
        </xforms:model>
    </xhtml:head>
    <xhtml:body>
        <xforms:group ref="form">
            <p>
                <xforms:input ref="text" xhtml:class="xforms-valid">
                    <xforms:label xhtml:class="fixed-width">Age:</xforms:label>
                    <xforms:hint>Positive number</xforms:hint>
                    <xforms:alert>The age must be a positive number</xforms:alert>
                </xforms:input>
            </p>
            <p>
                <xforms:secret ref="secret">
                    <xforms:label xhtml:class="fixed-width">Password:</xforms:label>
                    <xforms:hint>The password is 42 ;)</xforms:hint>
                    <xforms:alert>Invalid password</xforms:alert>
                </xforms:secret>
            </p>
            <p>
                <xforms:textarea ref="textarea">
                    <xforms:label xhtml:class="fixed-width">Text area:</xforms:label>
                    <xforms:alert>Content of text area has less than 10 characters</xforms:alert>
                </xforms:textarea>
            </p>
            <p>
                <xforms:input ref="date">
                    <xforms:label xhtml:class="fixed-width">Birth date:</xforms:label>
                    <xforms:help>This date must be in the past.</xforms:help>
                </xforms:input>
            </p>
            <p>
                <xforms:input ref="date2">
                    <xforms:label xhtml:class="fixed-width">Second date:</xforms:label>
                </xforms:input>
            </p>
            <p>
                <xforms:input ref="time">
                    <xforms:label xhtml:class="fixed-width">Time:</xforms:label>
                </xforms:input>
            </p>
            <p>
                <xforms:submit>
                    <xforms:label>Submit</xforms:label>
                </xforms:submit>
            </p>
            <p style="margin-top: 2em">
                <xforms:group>
                    <xforms:label>XForms instance</xforms:label>
                    <xforms:output ref="instance('formatted')" xhtml:class="xforms-xhtml"/>
                </xforms:group>
            </p>
        </xforms:group>
    </xhtml:body>
</xhtml:html>
