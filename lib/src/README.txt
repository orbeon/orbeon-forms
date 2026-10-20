Source JAR files before jarjar transformation.

To build the resulting JAR files in the lib directory, run the ant jarjar target.

Upstream base versions:

- xerces-xercesImpl-2.12.2.jar: Apache Xerces-J 2.12.2 (xerces:xercesImpl:2.12.2 on Maven Central), which includes
  the fix for CVE-2022-23437. The previous jar was 2.12.0, in use from 2019-08-01 to 2026-10.
- xerces-serializer-2.12.jar: serializer.jar (Xalan serializer) from the Xerces-J 2.12 binary distribution.

To upgrade Xerces: replace the xercesImpl jar here, update its name in build.xml, run `ant jarjar`, check the
manifest of lib/runtime/xerces-2.12-orbeon.jar, and update XercesVersionTest.
