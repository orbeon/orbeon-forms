package org.orbeon.oxf.util


object Mediatypes extends MediatypesTrait {

  protected def findMappings: Iterable[Mapping] =
    List(
      Mapping(extension = "bin", mediatype = "application/octet-stream", label = Some(value = "Binary")),
      Mapping(extension = "jpg", mediatype = "image/jpeg", label = Some(value = "JPEG")),
      Mapping(extension = "jpeg", mediatype = "image/jpeg", label = Some(value = "JPEG")),
      Mapping(extension = "jpe", mediatype = "image/jpeg", label = Some(value = "JPEG")),
      Mapping(extension = "png", mediatype = "image/png", label = Some(value = "PNG")),
      Mapping(extension = "svg", mediatype = "image/svg+xml", label = Some(value = "SVG")),
      Mapping(extension = "svgz", mediatype = "image/svg+xml", label = Some(value = "SVG")),
      Mapping(extension = "tiff", mediatype = "image/tiff", label = Some(value = "TIFF")),
      Mapping(extension = "tif", mediatype = "image/tiff", label = Some(value = "TIFF")),
      Mapping(extension = "bmp", mediatype = "image/bmp", label = Some(value = "BMP")),
      Mapping(extension = "gif", mediatype = "image/gif", label = Some(value = "GIF")),
      Mapping(extension = "heic", mediatype = "image/heic", label = Some(value = "HEIC")),
      Mapping(extension = "heif", mediatype = "image/heif", label = Some(value = "HEIF")),
      Mapping(extension = "webp", mediatype = "image/webp", label = Some(value = "WebP")),
      Mapping(extension = "ico", mediatype = "image/vnd.microsoft.icon", label = Some(value = "Icon")),
      Mapping(extension = "xhtml", mediatype = "application/xhtml+xml", label = Some(value = "XHTML")),
      Mapping(extension = "mpeg", mediatype = "video/mpeg", label = Some(value = "MPEG")),
      Mapping(extension = "mpg", mediatype = "video/mpeg", label = Some(value = "MPEG")),
      Mapping(extension = "mpe", mediatype = "video/mpeg", label = Some(value = "MPEG")),
      Mapping(extension = "webm", mediatype = "video/webm", label = Some(value = "WebM")),
      Mapping(extension = "avi", mediatype = "video/x-msvideo", label = Some(value = "AVI")),
      Mapping(extension = "ogv", mediatype = "video/ogg", label = Some(value = "Ogg")),
      Mapping(extension = "qt", mediatype = "video/quicktime", label = Some(value = "QuickTime")),
      Mapping(extension = "mov", mediatype = "video/quicktime", label = Some(value = "QuickTime")),
      Mapping(extension = "mp4", mediatype = "video/mp4", label = Some(value = "MP4")),
      Mapping(extension = "wmv", mediatype = "video/x-ms-wmv", label = Some(value = "WMV")),
      Mapping(extension = "aac", mediatype = "audio/aac", label = Some(value = "AAC")),
      Mapping(extension = "aac", mediatype = "audio/aac", label = Some(value = "AAC")),
      Mapping(extension = "wav", mediatype = "audio/wav", label = Some(value = "WAV")),
      Mapping(extension = "mid", mediatype = "audio/midi", label = Some(value = "MIDI")),
      Mapping(extension = "midi", mediatype = "audio/midi", label = Some(value = "MIDI")),
      Mapping(extension = "kar", mediatype = "audio/midi", label = Some(value = "MIDI")),
      Mapping(extension = "mp3", mediatype = "audio/mpeg", label = Some(value = "MP3")),
      Mapping(extension = "weba", mediatype = "audio/webm", label = Some(value = "WebM")),
      Mapping(extension = "opus", mediatype = "audio/opus", label = Some(value = "Opus")),
      Mapping(extension = "oga", mediatype = "audio/ogg", label = Some(value = "Ogg")),
      Mapping(extension = "ogg", mediatype = "audio/ogg", label = Some(value = "Ogg")),
      Mapping(extension = "html", mediatype = "text/html", label = Some(value = "HTML")),
      Mapping(extension = "htm", mediatype = "text/html", label = Some(value = "HTML")),
      Mapping(extension = "css", mediatype = "text/css", label = Some(value = "CSS")),
      Mapping(extension = "xml", mediatype = "application/xml", label = Some(value = "XML")),
      Mapping(extension = "xsl", mediatype = "application/xml", label = Some(value = "XML")),
      Mapping(extension = "json", mediatype = "application/json", label = Some(value = "JSON")),
      Mapping(
        extension = "ps",
        mediatype = "application/postscript",
        label = Some(value = "PostScript")
      ),
      Mapping(
        extension = "ai",
        mediatype = "application/postscript",
        label = Some(value = "PostScript")
      ),
      Mapping(
        extension = "eps",
        mediatype = "application/postscript",
        label = Some(value = "PostScript")
      ),
      Mapping(extension = "pdf", mediatype = "application/pdf", label = Some(value = "PDF")),
      Mapping(extension = "rtf", mediatype = "application/rtf", label = Some(value = "RTF")),
      Mapping(extension = "mml", mediatype = "application/mathml+xml", label = Some(value = "MathML")),
      Mapping(extension = "asc", mediatype = "text/plain", label = Some(value = "Text")),
      Mapping(extension = "txt", mediatype = "text/plain", label = Some(value = "Text")),
      Mapping(extension = "text", mediatype = "text/plain", label = Some(value = "Text")),
      Mapping(extension = "diff", mediatype = "text/plain", label = Some(value = "Text")),
      Mapping(extension = "js", mediatype = "text/javascript", label = Some(value = "JavaScript")),
      Mapping(extension = "mjs", mediatype = "text/javascript", label = Some(value = "JavaScript")),
      Mapping(extension = "zip", mediatype = "application/zip", label = Some(value = "ZIP")),
      Mapping(extension = "7z", mediatype = "application/x-7z-compressed", label = Some(value = "7z")),
      Mapping(extension = "tar", mediatype = "application/x-tar", label = Some(value = "TAR")),
      Mapping(extension = "csv", mediatype = "text/csv", label = Some(value = "CSV")),
      Mapping(extension = "jar", mediatype = "application/java-archive", label = Some(value = "Java")),
      Mapping(
        extension = "mpkg",
        mediatype = "application/vnd.apple.installer+xml",
        label = Some(value = "macOS Installer")
      ),
      Mapping(extension = "php", mediatype = "application/php", label = Some(value = "PHP Script")),
      Mapping(extension = "phtml", mediatype = "application/php", label = Some(value = "PHP Script")),
      Mapping(extension = "pht", mediatype = "application/php", label = Some(value = "PHP Script")),
      Mapping(extension = "gz", mediatype = "application/gzip", label = Some(value = "Gzip")),
      Mapping(extension = "rar", mediatype = "application/vnd.rar", label = Some(value = "RAR")),
      Mapping(extension = "bz", mediatype = "application/x-bzip", label = Some(value = "Bzip")),
      Mapping(extension = "bz2", mediatype = "application/x-bzip2", label = Some(value = "Bzip2")),
      Mapping(extension = "sh", mediatype = "application/x-sh", label = Some(value = "Shell Script")),
      Mapping(
        extension = "csh",
        mediatype = "application/x-csh",
        label = Some(value = "C Shell Script")
      ),
      Mapping(extension = "ics", mediatype = "text/calendar", label = Some(value = "iCalendar")),
      Mapping(
        extension = "swf",
        mediatype = "application/x-shockwave-flash",
        label = Some(value = "Flash")
      ),
      Mapping(extension = "otf", mediatype = "font/otf", label = Some(value = "OpenType")),
      Mapping(extension = "sfnt", mediatype = "font/sfnt", label = Some(value = "SFNT")),
      Mapping(extension = "ttf", mediatype = "font/ttf", label = Some(value = "TrueType")),
      Mapping(extension = "woff", mediatype = "application/woff", label = Some(value = "WOFF")),
      Mapping(extension = "woff2", mediatype = "font/woff2", label = Some(value = "WOFF2")),
      Mapping(
        extension = "eot",
        mediatype = "application/vnd.ms-fontobject",
        label = Some(value = "EOT")
      ),
      Mapping(
        extension = "azw",
        mediatype = "application/vnd.amazon.ebook",
        label = Some(value = "Kindle")
      ),
      Mapping(extension = "epub", mediatype = "application/epub+zip", label = Some(value = "EPUB")),
      Mapping(
        extension = "odp",
        mediatype = "application/vnd.oasis.opendocument.presentation",
        label = Some(value = "ODP")
      ),
      Mapping(
        extension = "ods",
        mediatype = "application/vnd.oasis.opendocument.spreadsheet",
        label = Some(value = "ODS")
      ),
      Mapping(
        extension = "odt",
        mediatype = "application/vnd.oasis.opendocument.text",
        label = Some(value = "ODT")
      ),
      Mapping(extension = "doc", mediatype = "application/msword", label = Some(value = "Word")),
      Mapping(extension = "dot", mediatype = "application/msword", label = Some(value = "Word")),
      Mapping(
        extension = "docx",
        mediatype = "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        label = Some(value = "Word")
      ),
      Mapping(
        extension = "dotx",
        mediatype = "application/vnd.openxmlformats-officedocument.wordprocessingml.template",
        label = Some(value = "Word")
      ),
      Mapping(
        extension = "docm",
        mediatype = "application/vnd.ms-word.document.macroenabled.12",
        label = Some(value = "Word Macro")
      ),
      Mapping(
        extension = "dotm",
        mediatype = "application/vnd.ms-word.template.macroenabled.12",
        label = Some(value = "Word Macro")
      ),
      Mapping(extension = "xls", mediatype = "application/vnd.ms-excel", label = Some(value = "Excel")),
      Mapping(extension = "xlt", mediatype = "application/vnd.ms-excel", label = Some(value = "Excel")),
      Mapping(extension = "xla", mediatype = "application/vnd.ms-excel", label = Some(value = "Excel")),
      Mapping(
        extension = "xlsx",
        mediatype = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        label = Some(value = "Excel")
      ),
      Mapping(
        extension = "xltx",
        mediatype = "application/vnd.openxmlformats-officedocument.spreadsheetml.template",
        label = Some(value = "Excel")
      ),
      Mapping(
        extension = "xlsm",
        mediatype = "application/vnd.ms-excel.sheet.macroenabled.12",
        label = Some(value = "Excel Macro")
      ),
      Mapping(
        extension = "xltm",
        mediatype = "application/vnd.ms-excel.template.macroenabled.12",
        label = Some(value = "Excel Macro")
      ),
      Mapping(
        extension = "xlam",
        mediatype = "application/vnd.ms-excel.addin.macroenabled.12",
        label = Some(value = "Excel Add-in")
      ),
      Mapping(
        extension = "xlsb",
        mediatype = "application/vnd.ms-excel.sheet.binary.macroenabled.12",
        label = Some(value = "Excel Binary")
      ),
      Mapping(
        extension = "ppt",
        mediatype = "application/vnd.ms-powerpoint",
        label = Some(value = "PowerPoint")
      ),
      Mapping(
        extension = "pot",
        mediatype = "application/vnd.ms-powerpoint",
        label = Some(value = "PowerPoint")
      ),
      Mapping(
        extension = "pps",
        mediatype = "application/vnd.ms-powerpoint",
        label = Some(value = "PowerPoint")
      ),
      Mapping(
        extension = "ppa",
        mediatype = "application/vnd.ms-powerpoint",
        label = Some(value = "PowerPoint")
      ),
      Mapping(
        extension = "pptx",
        mediatype = "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        label = Some(value = "PowerPoint")
      ),
      Mapping(
        extension = "potx",
        mediatype = "application/vnd.openxmlformats-officedocument.presentationml.template",
        label = Some(value = "PowerPoint")
      ),
      Mapping(
        extension = "ppsx",
        mediatype = "application/vnd.openxmlformats-officedocument.presentationml.slideshow",
        label = Some(value = "PowerPoint")
      ),
      Mapping(
        extension = "ppam",
        mediatype = "application/vnd.ms-powerpoint.addin.macroenabled.12",
        label = Some(value = "PowerPoint Add-in")
      ),
      Mapping(
        extension = "pptm",
        mediatype = "application/vnd.ms-powerpoint.presentation.macroenabled.12",
        label = Some(value = "PowerPoint Macro")
      ),
      Mapping(
        extension = "potm",
        mediatype = "application/vnd.ms-powerpoint.template.macroenabled.12",
        label = Some(value = "PowerPoint Macro")
      ),
      Mapping(
        extension = "ppsm",
        mediatype = "application/vnd.ms-powerpoint.slideshow.macroenabled.12",
        label = Some(value = "PowerPoint Macro")
      ),
      Mapping(
        extension = "msg",
        mediatype = "application/vnd.ms-outlook",
        label = Some(value = "Outlook Message")
      ),
      Mapping(extension = "htc", mediatype = "text/x-component", label = Some(value = "HTML")),
      Mapping(
        extension = "ez",
        mediatype = "application/andrew-inset",
        label = Some(value = "Andrew Inset")
      ),
      Mapping(extension = "csm", mediatype = "application/cu-seeme", label = Some(value = "CU-SeeMe")),
      Mapping(extension = "cu", mediatype = "application/cu-seeme", label = Some(value = "CU-SeeMe")),
      Mapping(extension = "tsp", mediatype = "application/dsptype", label = Some(value = "DSP Type")),
      Mapping(
        extension = "spl",
        mediatype = "application/futuresplash",
        label = Some(value = "FutureSplash")
      ),
      Mapping(
        extension = "cpt",
        mediatype = "application/mac-compactpro",
        label = Some(value = "Compact Pro")
      ),
      Mapping(extension = "hqx", mediatype = "application/mac-binhex40", label = Some(value = "BinHex")),
      Mapping(
        extension = "nb",
        mediatype = "application/mathematica",
        label = Some(value = "Mathematica Notebook")
      ),
      Mapping(
        extension = "mdb",
        mediatype = "application/msaccess",
        label = Some(value = "Access Database")
      ),
      Mapping(extension = "oda", mediatype = "application/oda", label = Some(value = "ODA")),
      Mapping(
        extension = "pgp",
        mediatype = "application/pgp-signature",
        label = Some(value = "PGP Signature")
      ),
      Mapping(extension = "smi", mediatype = "application/smil", label = Some(value = "SMIL")),
      Mapping(extension = "smil", mediatype = "application/smil", label = Some(value = "SMIL")),
      Mapping(
        extension = "sdw",
        mediatype = "application/vnd.stardivision.writer",
        label = Some(value = "StarWriter")
      ),
      Mapping(
        extension = "sgl",
        mediatype = "application/vnd.stardivision.writer-global",
        label = Some(value = "StarWriter Global")
      ),
      Mapping(
        extension = "vor",
        mediatype = "application/vnd.stardivision.writer",
        label = Some(value = "StarWriter")
      ),
      Mapping(
        extension = "sdc",
        mediatype = "application/vnd.stardivision.calc",
        label = Some(value = "StarCalc")
      ),
      Mapping(
        extension = "sda",
        mediatype = "application/vnd.stardivision.draw",
        label = Some(value = "StarDraw Drawing")
      ),
      Mapping(
        extension = "sdd",
        mediatype = "application/vnd.stardivision.impress",
        label = Some(value = "StarImpress")
      ),
      Mapping(
        extension = "sdp",
        mediatype = "application/vnd.stardivision.impress-packed",
        label = Some(value = "StarImpress Packed")
      ),
      Mapping(
        extension = "smf",
        mediatype = "application/vnd.stardivision.math",
        label = Some(value = "StarMath Formula")
      ),
      Mapping(
        extension = "sds",
        mediatype = "application/vnd.stardivision.chart",
        label = Some(value = "StarChart")
      ),
      Mapping(
        extension = "smd",
        mediatype = "application/vnd.stardivision.mail",
        label = Some(value = "StarMail")
      ),
      Mapping(
        extension = "wp5",
        mediatype = "application/wordperfect5.1",
        label = Some(value = "WordPerfect 5.1")
      ),
      Mapping(extension = "wk", mediatype = "application/x-123", label = Some(value = "Lotus 1-2-3")),
      Mapping(extension = "bcpio", mediatype = "application/x-bcpio", label = Some(value = "BCPIO")),
      Mapping(extension = "vcd", mediatype = "application/x-cdlink", label = Some(value = "VCD Link")),
      Mapping(
        extension = "pgn",
        mediatype = "application/x-chess-pgn",
        label = Some(value = "Chess PGN")
      ),
      Mapping(extension = "cpio", mediatype = "application/x-cpio", label = Some(value = "CPIO")),
      Mapping(
        extension = "csh",
        mediatype = "application/x-csh",
        label = Some(value = "C Shell Script")
      ),
      Mapping(
        extension = "deb",
        mediatype = "application/x-debian-package",
        label = Some(value = "Debian Package")
      ),
      Mapping(
        extension = "dcr",
        mediatype = "application/x-director",
        label = Some(value = "Director Movie")
      ),
      Mapping(
        extension = "dir",
        mediatype = "application/x-director",
        label = Some(value = "Director Movie")
      ),
      Mapping(
        extension = "dxr",
        mediatype = "application/x-director",
        label = Some(value = "Director Movie")
      ),
      Mapping(extension = "wad", mediatype = "application/x-doom", label = Some(value = "DOOM WAD")),
      Mapping(extension = "dms", mediatype = "application/x-dms", label = Some(value = "Amiga DMS")),
      Mapping(extension = "dvi", mediatype = "application/x-dvi", label = Some(value = "TeX DVI")),
      Mapping(extension = "pfa", mediatype = "application/x-font", label = Some(value = "Generic Font")),
      Mapping(extension = "pfb", mediatype = "application/x-font", label = Some(value = "Generic Font")),
      Mapping(extension = "gsf", mediatype = "application/x-font", label = Some(value = "Generic Font")),
      Mapping(extension = "pcf", mediatype = "application/x-font", label = Some(value = "Generic Font")),
      Mapping(extension = "z", mediatype = "application/x-font", label = Some(value = "Generic Font")),
      Mapping(
        extension = "spl",
        mediatype = "application/x-futuresplash",
        label = Some(value = "FutureSplash")
      ),
      Mapping(
        extension = "gnumeric",
        mediatype = "application/x-gnumeric",
        label = Some(value = "Gnumeric")
      ),
      Mapping(extension = "gtar", mediatype = "application/x-gtar", label = Some(value = "GNU Tar")),
      Mapping(extension = "tgz", mediatype = "application/x-gtar", label = Some(value = "GNU Tar")),
      Mapping(extension = "taz", mediatype = "application/x-gtar", label = Some(value = "GNU Tar")),
      Mapping(extension = "hdf", mediatype = "application/x-hdf", label = Some(value = "HDF Data")),
      Mapping(
        extension = "phps",
        mediatype = "application/x-httpd-php-source",
        label = Some(value = "PHP Source")
      ),
      Mapping(
        extension = "php3",
        mediatype = "application/x-httpd-php3",
        label = Some(value = "PHP3 Script")
      ),
      Mapping(
        extension = "php3p",
        mediatype = "application/x-httpd-php3-preprocessed",
        label = Some(value = "PHP3 Preprocessed")
      ),
      Mapping(
        extension = "php4",
        mediatype = "application/x-httpd-php4",
        label = Some(value = "PHP4 Script")
      ),
      Mapping(extension = "ica", mediatype = "application/x-ica", label = Some(value = "ICA")),
      Mapping(
        extension = "jnlp",
        mediatype = "application/x-java-jnlp-file",
        label = Some(value = "JNLP")
      ),
      Mapping(
        extension = "ser",
        mediatype = "application/x-java-serialized-object",
        label = Some(value = "Serialized Object")
      ),
      Mapping(
        extension = "class",
        mediatype = "application/x-java-vm",
        label = Some(value = "Java Class")
      ),
      Mapping(extension = "chrt", mediatype = "application/x-kchart", label = Some(value = "KChart")),
      Mapping(
        extension = "kil",
        mediatype = "application/x-killustrator",
        label = Some(value = "KIllustrator Drawing")
      ),
      Mapping(
        extension = "kpr",
        mediatype = "application/x-kpresenter",
        label = Some(value = "KPresenter")
      ),
      Mapping(
        extension = "kpt",
        mediatype = "application/x-kpresenter",
        label = Some(value = "KPresenter")
      ),
      Mapping(extension = "skp", mediatype = "application/x-koan", label = Some(value = "Koan Music")),
      Mapping(extension = "skd", mediatype = "application/x-koan", label = Some(value = "Koan Music")),
      Mapping(extension = "skt", mediatype = "application/x-koan", label = Some(value = "Koan Music")),
      Mapping(extension = "skm", mediatype = "application/x-koan", label = Some(value = "Koan Music")),
      Mapping(extension = "ksp", mediatype = "application/x-kspread", label = Some(value = "KSpread")),
      Mapping(extension = "kwd", mediatype = "application/x-kword", label = Some(value = "KWord")),
      Mapping(extension = "kwt", mediatype = "application/x-kword", label = Some(value = "KWord")),
      Mapping(extension = "latex", mediatype = "application/x-latex", label = Some(value = "LaTeX")),
      Mapping(extension = "lha", mediatype = "application/x-lha", label = Some(value = "LHA")),
      Mapping(extension = "lzh", mediatype = "application/x-lzh", label = Some(value = "LZH")),
      Mapping(extension = "lzx", mediatype = "application/x-lzx", label = Some(value = "LZX")),
      Mapping(extension = "frm", mediatype = "application/x-maker", label = Some(value = "FrameMaker")),
      Mapping(
        extension = "maker",
        mediatype = "application/x-maker",
        label = Some(value = "FrameMaker")
      ),
      Mapping(
        extension = "frame",
        mediatype = "application/x-maker",
        label = Some(value = "FrameMaker")
      ),
      Mapping(extension = "fm", mediatype = "application/x-maker", label = Some(value = "FrameMaker")),
      Mapping(extension = "fb", mediatype = "application/x-maker", label = Some(value = "FrameMaker")),
      Mapping(extension = "book", mediatype = "application/x-maker", label = Some(value = "FrameMaker")),
      Mapping(
        extension = "fbdoc",
        mediatype = "application/x-maker",
        label = Some(value = "FrameMaker")
      ),
      Mapping(
        extension = "mif",
        mediatype = "application/x-mif",
        label = Some(value = "FrameMaker MIF")
      ),
      Mapping(
        extension = "com",
        mediatype = "application/x-msdos-program",
        label = Some(value = "Windows Executable")
      ),
      Mapping(
        extension = "exe",
        mediatype = "application/x-msdos-program",
        label = Some(value = "Windows Executable")
      ),
      Mapping(
        extension = "bat",
        mediatype = "application/x-msdos-program",
        label = Some(value = "Windows Executable")
      ),
      Mapping(
        extension = "dll",
        mediatype = "application/x-msdos-program",
        label = Some(value = "Windows Executable")
      ),
      Mapping(
        extension = "msi",
        mediatype = "application/x-msi",
        label = Some(value = "Windows Installer")
      ),
      Mapping(extension = "nc", mediatype = "application/x-netcdf", label = Some(value = "NetCDF Data")),
      Mapping(
        extension = "cdf",
        mediatype = "application/x-netcdf",
        label = Some(value = "NetCDF Data")
      ),
      Mapping(
        extension = "pac",
        mediatype = "application/x-ns-proxy-autoconfig",
        label = Some(value = "Proxy Auto-Config")
      ),
      Mapping(extension = "o", mediatype = "application/x-object", label = Some(value = "Object")),
      Mapping(
        extension = "oza",
        mediatype = "application/x-oz-application",
        label = Some(value = "OZ Application")
      ),
      Mapping(extension = "pl", mediatype = "application/x-perl", label = Some(value = "Perl Script")),
      Mapping(extension = "pm", mediatype = "application/x-perl", label = Some(value = "Perl Script")),
      Mapping(
        extension = "crl",
        mediatype = "application/x-pkcs7-crl",
        label = Some(value = "Certificate Revocation List")
      ),
      Mapping(
        extension = "rpm",
        mediatype = "application/x-redhat-package-manager",
        label = Some(value = "RPM Package")
      ),
      Mapping(extension = "shar", mediatype = "application/x-shar", label = Some(value = "Shell")),
      Mapping(extension = "sit", mediatype = "application/x-stuffit", label = Some(value = "StuffIt")),
      Mapping(
        extension = "sv4cpio",
        mediatype = "application/x-sv4cpio",
        label = Some(value = "SV4 CPIO")
      ),
      Mapping(extension = "sv4crc", mediatype = "application/x-sv4crc", label = Some(value = "SV4 CRC")),
      Mapping(extension = "tcl", mediatype = "application/x-tcl", label = Some(value = "Tcl Script")),
      Mapping(extension = "tex", mediatype = "application/x-tex", label = Some(value = "TeX")),
      Mapping(extension = "gf", mediatype = "application/x-tex-gf", label = Some(value = "TeX GF")),
      Mapping(extension = "pk", mediatype = "application/x-tex-pk", label = Some(value = "TeX PK")),
      Mapping(
        extension = "texinfo",
        mediatype = "application/x-texinfo",
        label = Some(value = "Texinfo")
      ),
      Mapping(extension = "texi", mediatype = "application/x-texinfo", label = Some(value = "Texinfo")),
      Mapping(extension = "bak", mediatype = "application/x-trash", label = Some(value = "Backup")),
      Mapping(extension = "old", mediatype = "application/x-trash", label = Some(value = "Backup")),
      Mapping(extension = "sik", mediatype = "application/x-trash", label = Some(value = "Backup")),
      Mapping(extension = "t", mediatype = "application/x-troff", label = Some(value = "Troff")),
      Mapping(extension = "tr", mediatype = "application/x-troff", label = Some(value = "Troff")),
      Mapping(extension = "roff", mediatype = "application/x-troff", label = Some(value = "Troff")),
      Mapping(
        extension = "man",
        mediatype = "application/x-troff-man",
        label = Some(value = "Man Page")
      ),
      Mapping(extension = "me", mediatype = "application/x-troff-me", label = Some(value = "Troff ME")),
      Mapping(extension = "ms", mediatype = "application/x-troff-ms", label = Some(value = "Troff MS")),
      Mapping(extension = "ustar", mediatype = "application/x-ustar", label = Some(value = "USTAR")),
      Mapping(
        extension = "src",
        mediatype = "application/x-wais-source",
        label = Some(value = "WAIS Source")
      ),
      Mapping(extension = "wz", mediatype = "application/x-wingz", label = Some(value = "Wingz")),
      Mapping(
        extension = "crt",
        mediatype = "application/x-x509-ca-cert",
        label = Some(value = "CA Certificate")
      ),
      Mapping(extension = "fig", mediatype = "application/x-xfig", label = Some(value = "Xfig Drawing")),
      Mapping(extension = "au", mediatype = "audio/basic", label = Some(value = "AU")),
      Mapping(extension = "snd", mediatype = "audio/basic", label = Some(value = "AU")),
      Mapping(extension = "m3u", mediatype = "audio/mpegurl", label = Some(value = "M3U Playlist")),
      Mapping(extension = "aif", mediatype = "audio/x-aiff", label = Some(value = "AIFF")),
      Mapping(extension = "aiff", mediatype = "audio/x-aiff", label = Some(value = "AIFF")),
      Mapping(extension = "aifc", mediatype = "audio/x-aiff", label = Some(value = "AIFF")),
      Mapping(extension = "gsm", mediatype = "audio/x-gsm", label = Some(value = "GSM")),
      Mapping(extension = "m3u", mediatype = "audio/x-mpegurl", label = Some(value = "M3U Playlist")),
      Mapping(
        extension = "rpm",
        mediatype = "audio/x-pn-realaudio-plugin",
        label = Some(value = "RealAudio Plugin")
      ),
      Mapping(extension = "ra", mediatype = "audio/x-pn-realaudio", label = Some(value = "RealAudio")),
      Mapping(extension = "rm", mediatype = "audio/x-pn-realaudio", label = Some(value = "RealAudio")),
      Mapping(extension = "ram", mediatype = "audio/x-pn-realaudio", label = Some(value = "RealAudio")),
      Mapping(extension = "ra", mediatype = "audio/x-realaudio", label = Some(value = "RealAudio")),
      Mapping(extension = "pls", mediatype = "audio/x-scpls", label = Some(value = "PLS Playlist")),
      Mapping(extension = "pdb", mediatype = "chemical/x-pdb", label = Some(value = "PDB Chemical")),
      Mapping(extension = "xyz", mediatype = "chemical/x-xyz", label = Some(value = "XYZ Chemical")),
      Mapping(extension = "ief", mediatype = "image/ief", label = Some(value = "IEF")),
      Mapping(extension = "pcx", mediatype = "image/pcx", label = Some(value = "PCX")),
      Mapping(extension = "ras", mediatype = "image/x-cmu-raster", label = Some(value = "CMU Raster")),
      Mapping(extension = "cdr", mediatype = "image/x-coreldraw", label = Some(value = "CorelDRAW")),
      Mapping(
        extension = "pat",
        mediatype = "image/x-coreldrawpattern",
        label = Some(value = "Corel Pattern")
      ),
      Mapping(extension = "cdt", mediatype = "image/x-coreldrawtemplate", label = Some(value = "Corel")),
      Mapping(
        extension = "cpt",
        mediatype = "image/x-corelphotopaint",
        label = Some(value = "Corel Photo-Paint")
      ),
      Mapping(extension = "djvu", mediatype = "image/x-djvu", label = Some(value = "DjVu")),
      Mapping(extension = "djv", mediatype = "image/x-djvu", label = Some(value = "DjVu")),
      Mapping(extension = "jng", mediatype = "image/x-jng", label = Some(value = "JNG")),
      Mapping(extension = "bmp", mediatype = "image/x-ms-bmp", label = Some(value = "BMP")),
      Mapping(extension = "pnm", mediatype = "image/x-portable-anymap", label = Some(value = "PNM")),
      Mapping(extension = "pbm", mediatype = "image/x-portable-bitmap", label = Some(value = "PBM")),
      Mapping(extension = "pgm", mediatype = "image/x-portable-graymap", label = Some(value = "PGM")),
      Mapping(extension = "ppm", mediatype = "image/x-portable-pixmap", label = Some(value = "PPM")),
      Mapping(extension = "rgb", mediatype = "image/x-rgb", label = Some(value = "RGB")),
      Mapping(extension = "xbm", mediatype = "image/x-xbitmap", label = Some(value = "XBM")),
      Mapping(extension = "xpm", mediatype = "image/x-xpixmap", label = Some(value = "XPM")),
      Mapping(extension = "xwd", mediatype = "image/x-xwindowdump", label = Some(value = "XWD")),
      Mapping(extension = "igs", mediatype = "model/iges", label = Some(value = "IGES Model")),
      Mapping(extension = "iges", mediatype = "model/iges", label = Some(value = "IGES Model")),
      Mapping(extension = "msh", mediatype = "model/mesh", label = Some(value = "Mesh Model")),
      Mapping(extension = "mesh", mediatype = "model/mesh", label = Some(value = "Mesh Model")),
      Mapping(extension = "silo", mediatype = "model/mesh", label = Some(value = "Mesh Model")),
      Mapping(extension = "wrl", mediatype = "model/vrml", label = Some(value = "VRML Model")),
      Mapping(extension = "vrml", mediatype = "model/vrml", label = Some(value = "VRML Model")),
      Mapping(extension = "rtx", mediatype = "text/richtext", label = Some(value = "Rich Text")),
      Mapping(extension = "tsv", mediatype = "text/tab-separated-values", label = Some(value = "TSV")),
      Mapping(extension = "hdr", mediatype = "text/x-c", label = Some(value = "C Source")),
      Mapping(extension = "h", mediatype = "text/x-c", label = Some(value = "C Source")),
      Mapping(extension = "hpp", mediatype = "text/x-c", label = Some(value = "C Source")),
      Mapping(extension = "hxx", mediatype = "text/x-c", label = Some(value = "C Source")),
      Mapping(extension = "hh", mediatype = "text/x-c", label = Some(value = "C Source")),
      Mapping(extension = "src", mediatype = "text/x-c", label = Some(value = "C Source")),
      Mapping(extension = "c", mediatype = "text/x-c", label = Some(value = "C Source")),
      Mapping(extension = "cpp", mediatype = "text/x-c", label = Some(value = "C Source")),
      Mapping(extension = "cxx", mediatype = "text/x-c", label = Some(value = "C Source")),
      Mapping(extension = "cc", mediatype = "text/x-c", label = Some(value = "C Source")),
      Mapping(extension = "h", mediatype = "text/x-chdr", label = Some(value = "C Header")),
      Mapping(extension = "csh", mediatype = "text/x-csh", label = Some(value = "C Shell Script")),
      Mapping(extension = "c", mediatype = "text/x-csrc", label = Some(value = "C Source")),
      Mapping(extension = "java", mediatype = "text/x-java", label = Some(value = "Java Source")),
      Mapping(extension = "moc", mediatype = "text/x-moc", label = Some(value = "Qt Meta-Object")),
      Mapping(extension = "p", mediatype = "text/x-pascal", label = Some(value = "Pascal Source")),
      Mapping(extension = "pas", mediatype = "text/x-pascal", label = Some(value = "Pascal Source")),
      Mapping(extension = "etx", mediatype = "text/x-setext", label = Some(value = "Setext")),
      Mapping(extension = "sh", mediatype = "text/x-sh", label = Some(value = "Shell Script")),
      Mapping(extension = "tcl", mediatype = "text/x-tcl", label = Some(value = "Tcl Script")),
      Mapping(extension = "tk", mediatype = "text/x-tcl", label = Some(value = "Tcl Script")),
      Mapping(extension = "tex", mediatype = "text/x-tex", label = Some(value = "TeX")),
      Mapping(extension = "ltx", mediatype = "text/x-tex", label = Some(value = "TeX")),
      Mapping(extension = "sty", mediatype = "text/x-tex", label = Some(value = "TeX")),
      Mapping(extension = "cls", mediatype = "text/x-tex", label = Some(value = "TeX")),
      Mapping(extension = "vcs", mediatype = "text/x-vcalendar", label = Some(value = "vCalendar")),
      Mapping(extension = "vcf", mediatype = "text/x-vcard", label = Some(value = "vCard")),
      Mapping(extension = "dl", mediatype = "video/dl", label = Some(value = "DL")),
      Mapping(extension = "fli", mediatype = "video/fli", label = Some(value = "FLI")),
      Mapping(extension = "gl", mediatype = "video/gl", label = Some(value = "GL")),
      Mapping(extension = "mxu", mediatype = "video/vnd.mpegurl", label = Some(value = "MPEG URL")),
      Mapping(extension = "mng", mediatype = "video/x-mng", label = Some(value = "MNG")),
      Mapping(extension = "asf", mediatype = "video/x-ms-asf", label = Some(value = "ASF")),
      Mapping(extension = "asx", mediatype = "video/x-ms-asf", label = Some(value = "ASF")),
      Mapping(extension = "movie", mediatype = "video/x-sgi-movie", label = Some(value = "SGI Movie")),
      Mapping(
        extension = "ice",
        mediatype = "x-conference/x-cooltalk",
        label = Some(value = "CoolTalk Session")
      ),
      Mapping(extension = "vrm", mediatype = "x-world/x-vrml", label = Some(value = "VRML World")),
      Mapping(extension = "vrml", mediatype = "x-world/x-vrml", label = Some(value = "VRML World")),
      Mapping(extension = "wrl", mediatype = "x-world/x-vrml", label = Some(value = "VRML World")),
      Mapping(
        extension = "wbxml",
        mediatype = "application/vnd.wap.wbxml",
        label = Some(value = "WAP Binary XML")
      ),
      Mapping(
        extension = "wmlc",
        mediatype = "application/vnd.wap.wmlc",
        label = Some(value = "WAP WMLC")
      ),
      Mapping(
        extension = "wmlsc",
        mediatype = "application/vnd.wap.wmlscriptc",
        label = Some(value = "WAP WMLS")
      ),
      Mapping(extension = "wbmp", mediatype = "image/vnd.wap.wbmp", label = Some(value = "WAP Bitmap")),
      Mapping(extension = "wml", mediatype = "text/vnd.wap.wml", label = Some(value = "WAP WML")),
      Mapping(
        extension = "wmls",
        mediatype = "text/vnd.wap.wmlscript",
        label = Some(value = "WAP WMLS")
      )
    )
}
