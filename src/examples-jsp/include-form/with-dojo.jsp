<%--
    Copyright (C) 2008 Orbeon, Inc.

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
--%>
<html xmlns="http://www.w3.org/1999/xhtml">
	<head>
        <title>XForms with Dojo</title>
            <script type="text/javascript" src="http://ajax.googleapis.com/ajax/libs/dojo/1.1.1/dojo/dojo.xd.js" djConfig="parseOnLoad: true"/>
		<script type="text/javascript">dojo.require("dojox.layout.ContentPane");</script>
        <link type="text/css" href="../config/theme/orbeon.css" rel="stylesheet"/>
        <link type="text/css" href="../config/theme/xforms.css" rel="stylesheet"/>
        <style type="text/css">
            .form-pane              { border: 2px solid #999; background-color: #FCF6D3; width: 400px; }
            .portal-header-footer   { border: 2px solid #999; background-color: #DDDDEE; width: 100px; text-align: center; margin: 1em 0 1em 150px; }
            .portal-note            { width: 400px; margin: 1em 0 1em 0;  }
            .orbeon-portlet-home    { display: none; }
        </style>
    </head>
	<body>

        <div class="portal-header-footer">Portal Header</div>

        <div class="portal-note">
            <p>
                <b>Note:</b> This example shows how you can include a form in a HTML page, and have
                <code>&lt;xforms:submission replace="all"></code> work in that example using Ajax to replace the
                embedded form by the target page. For this example to work, you need to add the following property to
                your <code>config/properties-local.xml</code>.
            </p>
            <p>
                <code>&lt;property as="xs:boolean" name="oxf.xforms.ajax-portlet" value="true"/></code>
            </p>
            <p>
                For more information, see the <a href="http://wiki.orbeon.com/forms/doc/developer-guide/configuration-properties#TOC-Ajax-Portlet">Ajax portlet documentation</a>.
            </p>
        </div>

        <!-- Dojo pane which contains the Orbeon form -->
        <div class="form-pane" dojoType="dojox.layout.ContentPane" executeScripts="true"  evalScripts="true" renderStyles="true" adjustPaths="true"
             href="../../xforms-sandbox/sample/submission-replace-all-1?orbeon-embeddable=true"/>

        <div class="portal-header-footer">Portal Footer</div>

        <script type="text/javascript">
            var javaScriptLoadingIntervalID = window.setInterval(function() {
                if (typeof ORBEON != "undefined") {
                    window.clearInterval(javaScriptLoadingIntervalID);
                    ORBEON.xforms.Init.document();
                    ORBEON.xforms.Globals.baseURL = "/orbeon";
                    ORBEON.xforms.Globals.xformsServerURL = "/orbeon/xforms-server";
                }
            }, 10);
        </script>
    </body>
</html>