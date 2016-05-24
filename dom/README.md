About this module
=================

## What is this?

This module implements an XML DOM in Scala, based on DOM4J.

## How is this different from DOM4J?

- the original DOM4J 1.6.1 Java code got converted to Scala via Scalagen and manual changes
- parts of DOM4j not needed for Orbeon Forms purposes were removed
- the class hierarchy was simplified
- unneeded abstractions were removed
- and a lot of further clean-up got done

## Why do it?

The reason for doing this are:

- a lot of code in Orbeon Forms uses DOM4J and we don't want to change it all to another DOM implementation
- we want to be able to compile all our code to JavaScript via Scala.js, and this requires Scala sources
- DOM4J is unmaintained (the last release dates back to 2005), so there is not point staying in sync with the original releases
- we have some DOM4j changes which were previously done via clumsy inheritance which we can now simply include

## Requirements

- be compatible with our original use of DOM4j (reasonable refactoring allowed)
- be written in Scala
- compile to both JVM and with Scala.js

## Non-requirements

- be a general-purpose DOM for others to use (although it probably could be)
- built-in support for DTDs, XPath, XSLT, etc. (XPath support *is* available from Saxon)

## What it supports

- basic DOM types: document, element, etc.
- creation from SAX events
- serialization to SAX events

## License and original authors

This code still derives from the original DOM4j so it is mostly released under the DOM4j license (a BSD-style license).

The original authors and contributors of DOM4j, based on @author annotations in the original source, are:

- James Strachan
- Dave White
- Maarten Coene
- Brett Finnell
- Joseph Bowbeer
