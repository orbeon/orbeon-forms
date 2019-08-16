YAHOO.xbl.fr.Tinymce.DefaultConfig = {
    "mode"              : "exact",
    "language"          : "en",
    "statusbar"         : false,
    "menubar"           : false,
    "plugins"           : "lists link",
    "toolbar"           : "bold italic | bullist numlist outdent indent | link",
    "gecko_spellcheck"  : true,
    "doctype"           : "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\">",
    "encoding"          : "xml",
    "entity_encoding"   : "raw",
    "forced_root_block" : "div",
    "verify_html"       : true,
    "visual_table_class": "fr-tinymce-table", // Override default TinyMCE class on tables, which adds borders. We can't leave this just empty, otherwise TinyMCE puts its own CSS class.
    "skin"              : false               // Disable skin (see https://github.com/orbeon/orbeon-forms/issues/3473)
};