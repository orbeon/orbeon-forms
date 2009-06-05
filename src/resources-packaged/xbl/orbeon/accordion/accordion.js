YAHOO.namespace("xbl.fr");
YAHOO.xbl.fr.Accordion = {
    instances: {},

    init: function(target) {
        var container = YAHOO.util.Dom.getAncestorByClassName(target, "xbl-fr-accordion");
        if (! YAHOO.xbl.fr.Accordion.instances[container.id]) {
            console.log("Called");

            var fbAccordionMenuOptions = {
                dependent: false,
                openedIds: [],
                seconds: 0.2,
                easeOut: false,
                animation:true
            };
            var a = new AccordionMenu.setting('xf-7$gaga-dl', fbAccordionMenuOptions);
            console.log("a", a);

//            YAHOO.xbl.fr.Accordion.instances[container.id] = instance;
        }
    }
};
    