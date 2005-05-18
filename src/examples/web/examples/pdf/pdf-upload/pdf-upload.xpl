<!--
    Copyright (C) 2005 TAO Consulting Pte Ltd

    This program is free software; you can redistribute it and/or modify it under the terms of the
    GNU Lesser General Public License as published by the Free Software Foundation; either version
    2.1 of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
    without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    See the GNU Lesser General Public License for more details.

    The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
-->
<p:config xmlns:p="http://www.orbeon.com/oxf/pipeline" xmlns:oxf="http://www.orbeon.com/oxf/processors" xmlns:tao="http://www.taoconsulting.biz/ops/processors" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
	<p:param name="instance" type="input"/>
	<p:param name="data" type="output"/>
	<!-- Dereference the xs:anyURI obtained from the instance -->
	<p:processor name="oxf:pipeline">
				<p:input name="config" href="read-uri.xpl"/>
				<p:input name="uri" href="aggregate('uri', #instance#xpointer(string(/*/file)))"/>
				<p:output name="data" id="file"/>
	</p:processor>
	<!-- Now convert the PDF into XML -->
	<p:processor name="tao:from-pdf-converter">
		<p:input name="config" href="aggregate('action', #instance#xpointer(string(/*/action)))"/>
		<p:input name="data" href="#file"/>
		<p:output name="data" ref="data"/>
	</p:processor>
</p:config>
