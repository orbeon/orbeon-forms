package org.orbeon.oxf.fb.mcp


private[mcp] object FormBuilderMcpTools {

  def registry(editor: FormBuilderEditor): FormBuilderMcpToolRegistry =
    new FormBuilderMcpToolRegistry(Seq(
      new tools.OpenTool(editor),
      new tools.AddControlTool(editor),
      new tools.SaveTool(editor)
    ))
}
