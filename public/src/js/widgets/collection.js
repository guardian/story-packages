import _ from 'underscore';
import BaseWidget from 'widgets/base-widget';

export default class Collection extends BaseWidget {
    constructor(params, element) {
        super(params, element);

        this.collection = params.context.$data;

        this.group = _.find(this.collection.groups, group => group.name === params.group);

        if (params.group === 'included') {
            this.title = 'Included in ' + this.collection.configMeta.displayName();
        } else {
            this.title = 'Linking to ' + this.collection.configMeta.displayName();
        }

        this.collection.registerElement(element);
    }
}
