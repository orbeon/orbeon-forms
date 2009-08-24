This is an unstable branch of the datatable to rely more on XForms and XBL and less on XSLT.

The motivation for doing so is that XForms relies on instances while XSLT doesn't see them. 

The current implementation can't for instance generate a table which column list is the result of an 
xforms:repeat and that should be possible with this refactoring.

This branch will replace the current datatable once its development will be finished.

