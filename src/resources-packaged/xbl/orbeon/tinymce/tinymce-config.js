/*
 * TinyMCE default configuration
 * for config options, see http://tinymce.moxiecode.com/wiki.php/Configuration
 */
YAHOO.xbl.fr.Tinymce.DefaultConfig = {
		
	mode:		'exact',
	
	// currrently: da|de|en|fi|fr|nl|pl|ru; see http://tinymce.moxiecode.com/wiki.php/Configuration:language
	// check js files in jscripts/tiny_mce/langs directory as well as theme- and plugin-specifid subdirs
	// TODO: Make language selection dynamic - using OXF preferences?? Form Builder/Runner integration??
    language : "en",
    
    theme:		'advanced',
	plugins : "spellchecker,style,table,save,iespell,preview,media,searchreplace,print,contextmenu,paste,visualchars,nonbreaking,xhtmlxtras,template",
	// plugins : "spellchecker,pagebreak,style,layer,table,save,advhr,advimage,advlink,emotions,iespell,inlinepopups,insertdatetime,preview,media,searchreplace,print,contextmenu,paste,directionality,fullscreen,noneditable,visualchars,nonbreaking,xhtmlxtras,template",
	// Theme options
    theme_advanced_buttons1 : "save,newdocument,|,bold,italic,underline,strikethrough,|,justifyleft,justifycenter,justifyright,justifyfull,|,styleselect,formatselect,fontselect,fontsizeselect",
    // theme_advanced_buttons2 : "cut,copy,paste,pastetext,pasteword,|,search,replace,|,bullist,numlist,|,outdent,indent,blockquote,|,undo,redo,|,link,unlink,anchor,image,cleanup,help,code,|,insertdate,inserttime,preview,|,forecolor,backcolor",
    // theme_advanced_buttons3 : "tablecontrols,|,hr,removeformat,visualaid,|,sub,sup,|,charmap,emotions,iespell,media,advhr,|,print,|,ltr,rtl,|,fullscreen",
    // theme_advanced_buttons4 : "insertlayer,moveforward,movebackward,absolute,|,styleprops,spellchecker,|,cite,abbr,acronym,del,ins,attribs,|,visualchars,nonbreaking,template,blockquote,pagebreak,|,insertfile,insertimage",
    theme_advanced_toolbar_location : "top",
    theme_advanced_toolbar_align : "left",
    theme_advanced_statusbar_location : "bottom",
    theme_advanced_resizing : true, // doesn't work when not in dialog???
    
    // see http://tinymce.moxiecode.com/wiki.php/Configuration:gecko_spellcheck
    gecko_spellcheck : true, 
    
    // see http://tinymce.moxiecode.com/wiki.php/Configuration:doctype
    doctype : '<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">',
    
    // see http://tinymce.moxiecode.com/wiki.php/Configuration:encoding
    encoding : "xml",
    
    // see http://tinymce.moxiecode.com/wiki.php/Configuration:entity_encoding
    entity_encoding : "raw",
    
    // see http://tinymce.moxiecode.com/wiki.php/Configuration:forced_root_block
    forced_root_block : 'div',
    
    // see http://tinymce.moxiecode.com/wiki.php/Configuration:remove_redundant_brs
    remove_redundant_brs : true,
    
    // see http://tinymce.moxiecode.com/wiki.php/Configuration:verify_html
    verify_html : true,
    
    // Skin options
	skin : "o2k7",
	skin_variant : "silver"
	   
	/*
	 * Other important config options:
	 * http://tinymce.moxiecode.com/wiki.php/Configuration:formats
	 * 
	 */
};

