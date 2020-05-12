package org.orbeon.oxf.fr
import process.{SimpleProcess => NewSimpleProcess}

// Trampoline for backward compatibility. See also:
// https://github.com/orbeon/orbeon-forms/issues/1095
object SimpleProcess {
  def runProcessByName(scope: String, name: String) =
    NewSimpleProcess.runProcessByName(scope, name)

  def runProcess(scope: String, process: String) =
    NewSimpleProcess.runProcess(scope, process)
}
