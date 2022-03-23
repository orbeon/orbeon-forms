package org.orbeon.oxf.fr


object FormRunner
  extends FormRunnerCommon
     with FormBuilderPermissionsOps
     with FormRunnerPDF
     with FormRunnerEmailBackend
     with FormRunnerSummary
     with FormRunnerHome
     with FormRunnerPublish
     with FormRunnerEncodeDecode
     with FormRunnerComponents