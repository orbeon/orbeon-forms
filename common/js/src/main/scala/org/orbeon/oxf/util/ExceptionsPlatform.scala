package org.orbeon.oxf.util


trait ExceptionsPlatform extends ExceptionsTrait{

  Exceptions =>

  def findNestedThrowable(t: Throwable): Option[Throwable] = Option(t.getCause)
}
