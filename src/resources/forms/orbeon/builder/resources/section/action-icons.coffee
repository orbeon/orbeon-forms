OD = ORBEON.xforms.Document
FSM = ORBEON.util.FiniteStateMachine

$ ->
    sectionEditor = $ '.fb-section-editor'
    currentSection = null
    waiting = $.Callbacks()

    FSM.create
        transitions : [
            { events: [ 'enterSection'                ], to: 'over',           actions: [ 'showEditor' ]   }
            { events: [ 'enterEditor'                 ], to: 'over'                                      }
            { events: [ 'leaveSection', 'leaveEditor' ], to: 'initial',        actions: [ 'wait' ]         }
            { events: [ 'waited'                      ], from: [ 'initial' ],  actions: [ 'hideEditor' ]   }
        ]
        events:
            enterSection: (f) -> ($ document).on 'mouseenter', '.xbl-fr-section',    () -> f $ @
            leaveSection: (f) -> ($ document).on 'mouseleave', '.xbl-fr-section',    () -> f $ @
            enterEditor:  (f) -> ($ document).on 'mouseenter', '.fb-section-editor', () -> f $ @
            leaveEditor:  (f) -> ($ document).on 'mouseleave', '.fb-section-editor', () -> f $ @
            waited:       (f) -> waiting.add f
        actions:
            showEditor: (section) ->
                currentSection = section
                offset = currentSection.offset()
                sectionEditor.show()
                offset.left -= sectionEditor.outerWidth()
                sectionEditor.offset offset
            hideEditor: -> sectionEditor.hide()
            wait: -> _.delay (-> waiting.fire()), 100

    ($ document).on 'click', '.fb-section-editor', () ->
        # Find id of the .xbl-fr-section for the current section
        OD.dispatchEvent 'fb$section-1-section', 'fb-set-current-section'
