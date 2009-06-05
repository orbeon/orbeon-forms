YAHOO.namespace("xbl.fr");
YAHOO.xbl.fr.Accordion = {
    instances: {},

    init: function(target) {
        var container = YAHOO.util.Dom.getAncestorByClassName(target, "xbl-fr-accordion");
        if (! YAHOO.xbl.fr.Accordion.instances[container.id]) {
            var dlElement = YAHOO.util.Dom.getElementsByClassName("xbl-fr-accordion-dl", null, container)[0];
            var firstDtElement = YAHOO.util.Dom.getFirstChild(dlElement);
            YAHOO.util.Dom.generateId(firstDtElement);
            console.log("firstDtElement.id", firstDtElement.id);
            new AccordionMenu.setting(dlElement, {
                dependent: false,
                openedIds: [ firstDtElement ],
                seconds: 0.1,
                easeOut: false,
                animation:true
            });
            YAHOO.xbl.fr.Accordion.instances[container.id] = true;
        }
    }
};
