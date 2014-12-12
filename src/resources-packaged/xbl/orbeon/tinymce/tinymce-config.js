YAHOO.xbl.fr.Tinymce.DefaultConfig = {
    mode:		                            "exact",
    language:                               "en",
    theme:		                            "advanced",
    skin:                                   "thebigreason",
    plugins:                                "spellchecker,style,table,save,iespell,paste,visualchars,nonbreaking,xhtmlxtras,template",
    theme_advanced_buttons1:                "bold,italic,|,bullist,numlist,|,outdent,indent,link",
    theme_advanced_buttons2:                "",
    theme_advanced_buttons3:                "",
    theme_advanced_buttons4:                "",
    theme_advanced_toolbar_location:        "top",
    theme_advanced_toolbar_align:           "left",
    theme_advanced_resizing:                true,
    theme_advanced_blockformats:            "p,h1,h2,h3",
    gecko_spellcheck:                       true,
    doctype:                                '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">',
    encoding:                               "xml",
    entity_encoding:                        "raw",
    forced_root_block:                      'div',
    remove_redundant_brs:                   true,
    verify_html:                            true,
    editor_css:                             "",      // don't let the editor load UI CSS because that fails in portlets
    theme_advanced_statusbar_location:      "none",
    theme_advanced_path:                    false,
    // Override default TinyMCE class on tables, which adds borders. We can't leave this just empty, otherwise
    // TinyMCE puts its own CSS class.
    visual_table_class:                     "fr-tinymce-table"
};