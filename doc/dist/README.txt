=====================================================

= = = =    Orbeon PresentationServer Read Me    = = =

=====================================================

Copyright 1999-2005 (C) Orbeon, Inc. All rights reserved.

This README.TXT file covers the following topics:

    1. About Orbeon PresentationServer
    2. Licenses
    3. New Features
    4. Software Prerequisites
    5. Installing PresentationServer
    6. Compiling PresentationServer
    7. More Information
    8. Known Issues
    9. Third-Party Software

For more information, visit:

  http://www.orbeon.com/


****************************************
1. About Orbeon PresentationServer
****************************************

Orbeon PresentationServer (OPS) is an open source J2EE-based platform
for XML-centric web applications. OPS is built around XHTML, XForms,
XSLT, XML pipelines, and Web Services, which makes it ideal for
applications that capture, process and present XML data.

Unlike other popular web application frameworks like Struts or WebWork
that are based on Java objects and JSP, OPS is based on XML documents
and XML technologies. This leads to an architecture better suited for
the tasks of capturing, processing, and presenting information in XML
format, and often does not require writing any Java code at all to
implement your presentation layer.

OPS is built around Orbeon's optimized XPL engine, a mature,
high-performance XML pipeline engine for processing XML data.


****************************************
2. Licenses
****************************************

The source code is distributed under the terms of the GNU Lesser
General Public License (LGPL). The full text of the license is
available at http://www.gnu.org/copyleft/lesser.html.

Some examples are distributed under the terms of the Apache License,
Version 2.0. The full text of the license is available at:
http://www.apache.org/licenses/LICENSE-2.0.

Please refer to file headers to identify which license governs the
distribution of a particular file.

This software is OSI Certified Open Source Software. OSI Certified is
a certification mark of the Open Source Initiative.

The documentation is subject to the following terms:

    * Conversion to other formats is allowed, but the actual content
      may not be altered or edited in any way.

    * You may create printed copies for your own personal use.

    * For all other uses, such as selling printed copies or using
      (parts of) the manual in another publication, prior written
      agreement from Orbeon, Inc. is required.

See section 9 for more details about the licenses of included
third-party software.


****************************************
3. New Features
****************************************

A complete list of changes can be found online at:

  http://www.orbeon.com/ops/doc/home-changes-30

or in this distribution under:

  doc/home-changes-30.html


****************************************
4. Software Prerequisites
****************************************

OPS is supported on the following application servers:

    * Apache Tomcat 4.1.31 with JDK 1.4.2
    * Apache Tomcat 5.0.25 alpha with JDK 1.4.2
    * Apache Tomcat 5.5.4 with JDK 1.4.2 and JDK 1.5.0
    * BEA WebLogic Server 8.1
    * IBM WebSphere 5.1


****************************************
5. Installing OPS
****************************************

    * Download the OPS zip or tgz archive from
      http://forge.objectweb.org/projects/ops

    * Extract the archive in a directory that we call below:
      OPS_HOME/

After extraction, you will find the following files and directories
under the root directory OPS_HOME/:

    * README.txt: this document.

    * orbeon.war: the Web Archive where the software, required
      resources, and demo samples reside.

    * doc: this directory contains the OPS documentation as static
      HTML files, as well as the tutorial in PDF format
      ("doc/OPS Tutorial.pdf").

    * cli-examples: this directory contains command line examples.

    * licenses: this directory contains third-party software licenses.

You then need to deploy orbeon.war file to your servlet container or
application server of choice. In the OPS documentation, you will find
detailed installation instructions for several servlet containers /
applications servers.

More detailed information on installing OPS can be found in the
documentation under OPS_HOME/doc/ in the "Installing" section, or
directly online:

  http://www.orbeon.com/ops/doc/intro-install


****************************************
6. Compiling OPS
****************************************

For information about compiling OPS, please visit:

  http://www.orbeon.com/community/getting-involved

****************************************
7. More Information
****************************************

Here are some pointers to useful information related to this release.

    * For more information and updates, go to:

        http://www.orbeon.com/software/

    * If you have questions, comments, or difficulties with OPS,
      please subscribe to the ops-users mailing-list at ObjectWeb:

        http://www.objectweb.org/wws/info/ops-users

    * For commercial support and licensing alternatives, please contact:

        info@orbeon.com


****************************************
8. Known Issues
****************************************

For a list of known issues, consult the ObjectWeb issue tracking system:

  http://forge.objectweb.org/tracker/?atid=350207&group_id=168&func=browse


****************************************
9. Third-Party Software
****************************************

This product includes software developed by the Apache Software Foundation
(http://www.apache.org/):

    * Axis (http://ws.apache.org/axis/)
    * Batik (http://xml.apache.org/batik/)
    * FOP (http://xml.apache.org/fop/)
    * Forrest (http://xml.apache.org/forrest/)
    * Jakarta Commons (http://jakarta.apache.org/commons/)
    * log4j (http://jakarta.apache.org/log4j/docs/)
    * ORO (http://jakarta.apache.org/oro/)
    * POI (http://jakarta.apache.org/poi/)
    * Taglibs (http://jakarta.apache.org/taglibs/)
    * Xalan (http://xml.apache.org/xalan-j/)
    * Xerces (http://xml.apache.org/xerces2-j/)

In addition, this product includes the following software:

    * dom4j (http://dom4j.org/)
    * eXist (http://exist.sourceforge.net/)
    * hsqldb (http://hsqldb.sourceforge.net/)
    * Jaxen (http://jaxen.org/)
    * JFreeChart (http://www.jfree.org/jfreechart/)
    * Jing (http://www.thaiopensource.com/relaxng/jing.html)
    * Joost (http://joost.sourceforge.net/)
    * JTidy (http://sourceforge.net/projects/jtidy/)
    * JUnit (http://www.junit.org/)
    * Kawa (http://www.gnu.org/software/kawa/)
    * Mondrian (http://mondrian.sourceforge.net/)
    * Saxon (http://saxon.sourceforge.net/)
    * SAXPath (http://sourceforge.net/projects/saxpath/)
    * Sun Multi-Schema XML Validator
      (http://wwws.sun.com/software/xml/developers/multischema/)

This software makes use of a schema for XSLT 2.0 provided under W3C
Software License. The schema is available at the following location:

    jar:orbeon.jar!/org/orbeon/oxf/xml/schemas/xslt-2_0.xsd

Please consult the third-party-licenses directory for more information
about individual licenses.

Last updated: July 2005

Copyright 1999-2005 (C) Orbeon, Inc. All rights reserved.

--
