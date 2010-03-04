/**
 * Copyright (C) 2009 Orbeon, Inc.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU Lesser General Public License as published by the Free Software Foundation; either version
 * 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU Lesser General Public License for more details.
 *
 * The full text of the license is available at http://www.gnu.org/copyleft/lesser.html
 */


var testCase = {

    name: "datatable: loading indicator feature",

    testNoscroll: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'noscroll', function() {

            ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                ORBEON.xforms.Document.setValue("loading", "true");
            }, function() {

                var table = YAHOO.util.Dom.get('my-accordion$table-noscroll$table-noscroll-table');
                var loading = thiss.getLoadingIndicator(table);
                thiss.checkVisibility(loading, true);

                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    ORBEON.xforms.Document.setValue("loading", "false");
                }, function() {
                    thiss.checkVisibility(loading, false);

                    thiss.closeAccordionCase(thiss, 'noscroll');
                });

            });
        });

    },


    testScrollH: function() {
         var thiss = this;
         thiss.openAccordionCase(thiss, 'scrollH', function() {
             ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                 ORBEON.xforms.Document.setValue("loading", "true");
             }, function() {
                 var table = YAHOO.util.Dom.get('my-accordion$table-scrollH$table-scrollH-table');
                 var loading = thiss.getLoadingIndicator(table);
                 thiss.checkVisibility(loading, true);
                 var region = YAHOO.util.Dom.getRegion(loading.parentNode);
                 var widthLoading = region.right - region.left;

                 ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                     ORBEON.xforms.Document.setValue("loading", "false");
                 }, function() {
                     thiss.checkVisibility(loading, false);

                     region = YAHOO.util.Dom.getRegion(loading.parentNode);
                     var widthNotLoading = region.right - region.left;

                     YAHOO.util.Assert.areEqual(widthNotLoading, widthLoading, 'Loading indicator should have the same width than the table, ie ' + widthNotLoading + ' rather than ' +widthLoading);
                     thiss.closeAccordionCase(thiss, 'scrollH');
                 });
             });

         });

     },

    testScrollHThin: function() {
         var thiss = this;
         thiss.openAccordionCase(thiss, 'scrollH-thin', function() {
             ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                 ORBEON.xforms.Document.setValue("loading", "true");
             }, function() {
                 var table = YAHOO.util.Dom.get('my-accordion$table-scrollH-thin$table-scrollH-thin-table');
                 var loading = thiss.getLoadingIndicator(table);
                 thiss.checkVisibility(loading, true);
                 var region = YAHOO.util.Dom.getRegion(loading.parentNode);
                 var widthLoading = region.right - region.left;

                 ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                     ORBEON.xforms.Document.setValue("loading", "false");
                 }, function() {
                     thiss.checkVisibility(loading, false);

                     region = YAHOO.util.Dom.getRegion(loading.parentNode);
                     var widthNotLoading = region.right - region.left;

                     YAHOO.util.Assert.areEqual(widthNotLoading, widthLoading, 'Loading indicator should have the same width than the table, ie ' + widthNotLoading + ' rather than ' +widthLoading);
                     thiss.closeAccordionCase(thiss, 'scrollH-thin');
                 });
             });

         });

     },


    testScrollV: function() {
         var thiss = this;
         thiss.openAccordionCase(thiss, 'scrollV', function() {
             ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                 ORBEON.xforms.Document.setValue("loading", "true");
             }, function() {
                 var table = YAHOO.util.Dom.get('my-accordion$table-scrollV$table-scrollV-table');
                 var loading = thiss.getLoadingIndicator(table);
                 thiss.checkVisibility(loading, true);
                 var region = YAHOO.util.Dom.getRegion(loading.parentNode);
                 var heightLoading = region.bottom - region.top;

                 ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                     ORBEON.xforms.Document.setValue("loading", "false");
                 }, function() {
                     thiss.checkVisibility(loading, false);

                     region = YAHOO.util.Dom.getRegion(loading.parentNode);
                     var heightNotLoading = region.bottom - region.top;

                     YAHOO.util.Assert.areEqual(heightNotLoading, heightLoading, 'Loading indicator should have the same height than the table, ie ' + heightNotLoading + ' rather than ' +heightLoading);
                     thiss.closeAccordionCase(thiss, 'scrollV');
                 });
             });

         });

     },

    testScrollVH: function() {
         var thiss = this;
         thiss.openAccordionCase(thiss, 'scrollVH', function() {
             ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                 ORBEON.xforms.Document.setValue("loading", "true");
             }, function() {
                 var table = YAHOO.util.Dom.get('my-accordion$table-scrollVH$table-scrollVH-table');
                 var loading = thiss.getLoadingIndicator(table);
                 thiss.checkVisibility(loading, true);
                 var region = YAHOO.util.Dom.getRegion(loading.parentNode);
                 var widthLoading = region.right - region.left;
                 var heightLoading = region.bottom - region.top;

                 ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                     ORBEON.xforms.Document.setValue("loading", "false");
                 }, function() {
                     thiss.checkVisibility(loading, false);

                     region = YAHOO.util.Dom.getRegion(loading.parentNode);
                     var widthNotLoading = region.right - region.left;
                     var heightNotLoading = region.bottom - region.top;

                     YAHOO.util.Assert.areEqual(widthNotLoading, widthLoading, 'Loading indicator should have the same width than the table, ie ' + widthNotLoading + ' rather than ' +widthLoading);
                     YAHOO.util.Assert.areEqual(heightNotLoading, heightLoading, 'Loading indicator should have the same height than the table, ie ' + heightNotLoading + ' rather than ' +heightLoading);
                     thiss.closeAccordionCase(thiss, 'scrollVH');
                 });
             });

         });

     },

    testNoscrollDyn: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'noscroll-dyn', function() {

            ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                ORBEON.xforms.Document.setValue("loading", "true");
            }, function() {

                var table = YAHOO.util.Dom.get('my-accordion$table-noscroll-dyn$table-noscroll-dyn-table');
                var loading = thiss.getLoadingIndicator(table);
                thiss.checkVisibility(loading, true);

                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    ORBEON.xforms.Document.setValue("loading", "false");
                }, function() {
                    thiss.checkVisibility(loading, false);

                    thiss.closeAccordionCase(thiss, 'noscroll-dyn');
                });

            });
        });

    },

    testNoscrollDyn2: function() {
        var thiss = this;
        thiss.openAccordionCase(thiss, 'noscroll-dyn2', function() {

            ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                ORBEON.xforms.Document.setValue("loading", "true");
            }, function() {

                var table = YAHOO.util.Dom.get('my-accordion$noscroll-dyn2-table$noscroll-dyn2-table-table');
                var loading = thiss.getLoadingIndicator(table);
                thiss.checkVisibility(loading, true);
                var table = loading.getElementsByTagName('table')[0];
                var region = YAHOO.util.Dom.getRegion(table.parentNode);
                var widthLoading = region.right - region.left;
                YAHOO.util.Assert.isTrue(widthLoading > 50, 'The indicator width should be > 50 (' + widthLoading +' found)')


                ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                    ORBEON.xforms.Document.setValue("loading", "false");
                }, function() {
                    thiss.checkVisibility(loading, false);

                    thiss.closeAccordionCase(thiss, 'noscroll-dyn2');
                });

            });
        });

    },

    testScrollHDyn: function() {
         var thiss = this;
         thiss.openAccordionCase(thiss, 'scrollH-dyn', function() {
             ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                 ORBEON.xforms.Document.setValue("loading", "true");
             }, function() {
                 var table = YAHOO.util.Dom.get('my-accordion$table-scrollH-dyn$table-scrollH-dyn-table');
                 var loading = thiss.getLoadingIndicator(table);
                 thiss.checkVisibility(loading, true);
                 var region = YAHOO.util.Dom.getRegion(loading.parentNode);
                 var widthLoading = region.right - region.left;

                 ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                     ORBEON.xforms.Document.setValue("loading", "false");
                 }, function() {
                     thiss.checkVisibility(loading, false);

                     region = YAHOO.util.Dom.getRegion(loading.parentNode);
                     var widthNotLoading = region.right - region.left;

                     YAHOO.util.Assert.areEqual(widthNotLoading, widthLoading, 'Loading indicator should have the same width than the table, ie ' + widthNotLoading + ' rather than ' +widthLoading);
                     thiss.closeAccordionCase(thiss, 'scrollH-dyn');
                 });
             });

         });

     },

    testScrollVDyn: function() {
          var thiss = this;
          thiss.openAccordionCase(thiss, 'scrollV-dyn', function() {
              ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                  ORBEON.xforms.Document.setValue("loading", "true");
              }, function() {
                  var table = YAHOO.util.Dom.get('my-accordion$table-scrollV-dyn$table-scrollV-dyn-table');
                  var loading = thiss.getLoadingIndicator(table);
                  thiss.checkVisibility(loading, true);
                  var region = YAHOO.util.Dom.getRegion(loading.parentNode);
                  var heightLoading = region.bottom - region.top;

                  ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                      ORBEON.xforms.Document.setValue("loading", "false");
                  }, function() {
                      thiss.checkVisibility(loading, false);

                      region = YAHOO.util.Dom.getRegion(loading.parentNode);
                      var heightNotLoading = region.bottom - region.top;

                      YAHOO.util.Assert.areEqual(heightNotLoading, heightLoading, 'Loading indicator should have the same height than the table, ie ' + heightNotLoading + ' rather than ' +heightLoading);
                      thiss.closeAccordionCase(thiss, 'scrollV-dyn');
                  });
              });

          });

      },

    testScrollVHDyn: function() {
         var thiss = this;
         thiss.openAccordionCase(thiss, 'scrollVH-dyn', function() {
             ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                 ORBEON.xforms.Document.setValue("loading", "true");
             }, function() {
                 var table = YAHOO.util.Dom.get('my-accordion$table-scrollVH-dyn$table-scrollVH-dyn-table');
                 var loading = thiss.getLoadingIndicator(table);
                 thiss.checkVisibility(loading, true);
                 var region = YAHOO.util.Dom.getRegion(loading.parentNode);
                 var widthLoading = region.right - region.left;
                 var heightLoading = region.bottom - region.top;

                 ORBEON.util.Test.executeCausingAjaxRequest(thiss, function() {
                     ORBEON.xforms.Document.setValue("loading", "false");
                 }, function() {
                     thiss.checkVisibility(loading, false);

                     region = YAHOO.util.Dom.getRegion(loading.parentNode);
                     var widthNotLoading = region.right - region.left;
                     var heightNotLoading = region.bottom - region.top;

                     YAHOO.util.Assert.areEqual(widthNotLoading, widthLoading, 'Loading indicator should have the same width than the table, ie ' + widthNotLoading + ' rather than ' +widthLoading);
                     YAHOO.util.Assert.areEqual(heightNotLoading, heightLoading, 'Loading indicator should have the same height than the table, ie ' + heightNotLoading + ' rather than ' +heightLoading);
                     thiss.closeAccordionCase(thiss, 'scrollVH-dyn');
                 });
             });

         });

     },



    EOS: ""
}


ORBEON.xforms.Events.orbeonLoadedEvent.subscribe(function() {
    for (var property in YAHOO.xbl.fr.Datatable.unittests_lib) {
        testCase[property] = YAHOO.xbl.fr.Datatable.unittests_lib[property];
    }
    YAHOO.tool.TestRunner.add(new YAHOO.tool.TestCase(testCase));
    AccordionMenu.setting('my-accordion$dl', {animation: true, seconds: 0.001, openedIds: [], dependent: false, easeOut: false});
    if (parent && parent.TestManager) {
        parent.TestManager.load();
    } else {
        new YAHOO.tool.TestLogger();
        YAHOO.tool.TestRunner.run();
    }
});
