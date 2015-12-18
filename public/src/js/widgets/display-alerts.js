import ko from 'knockout';
import mediator from 'utils/mediator';
import Extension from 'models/extension';

export default class extends Extension {
    constructor(baseModel) {
        super(baseModel);

        this.alert = ko.observable(false);

        this.listenOn(mediator, 'packages:alert', function(message){
            this.alert(message);
        });
    }

    clearAlerts() {
        this.alert(false);
        mediator.emit('alert:dismiss');
    }
}
