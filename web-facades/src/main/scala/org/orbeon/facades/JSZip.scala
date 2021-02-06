package org.orbeon.facades

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal
import scala.scalajs.js.typedarray.{ArrayBuffer, Uint8Array}


// Simple facade for JSZip. See https://stuk.github.io/jszip/

@js.native
@JSGlobal
class ZipObject extends js.Object {

  val name: String = js.native
  val dir : Boolean = js.native

  // date	date	the last modification date
  // comment	string	the comment for this file
  // unixPermissions	16 bits number	The UNIX permissions of the file, if any.
  // dosPermissions	6 bits number	The DOS permissions of the file, if any.
  // options	object	the options of the file. The available options are :
  // options.compression	compression	see file(name, data [,options])

  // base64 : the result will be a string, the binary in a base64 form.
  // text (or string): the result will be an unicode string.
  // binarystring: the result will be a string in “binary” form, using 1 byte per char (2 bytes).
  // array: the result will be an Array of bytes (numbers between 0 and 255).
  // uint8array : the result will be a Uint8Array. This requires a compatible browser.
  // arraybuffer : the result will be a ArrayBuffer. This requires a compatible browser.
  // blob : the result will be a Blob. This requires a compatible browser.
  // nodebuffer : the result will be a nodejs Buffer. This requires nodejs.

  // TODO: `onUpdate` optional 2nd param
  def async(`type`: String): js.Promise[Uint8Array] = js.native
}

@js.native
@JSGlobal
class JSZip extends js.Object {
  def file(name: String): ZipObject = js.native
  def file(regex: js.RegExp): js.Array[ZipObject] = js.native
  def forEach(callback: js.Function2[String, ZipObject, Unit]): Unit = js.native
  val version: String = js.native
}

@js.native
@JSGlobal
object JSZip extends js.Object {
  def loadAsync(buf: ArrayBuffer): js.Promise[JSZip] = js.native
}