import ko from 'knockout';
import BaseClass from 'models/base-class';
import populateObservables from 'utils/populate-observables';
import asObservableProps from 'utils/as-observable-props';
import humanTime from 'utils/human-time';

export default class StoryPackage extends BaseClass {
    constructor(opts = {}) {
        super();

        if (!opts.id) { return; }

        this.id = opts.id;

        this.editing = ko.observable(false);

        this.isHidden = opts.isHidden;

        this.savedDisplayName = opts.name;

        this.meta = asObservableProps([
            'name',
            'lastModify',
            'lastModifyBy',
            'lastModifyHuman',
            'lastModifyByName',
            'created',
            'createdHuman'
        ]);

        this.meta.lastModifyHuman(humanTime(new Date(opts.lastModify)));
        this.meta.createdHuman(humanTime(opts.created ? new Date(opts.created) : new Date('2016-04-04T17:00:00.000Z')));

        populateObservables(this.meta, opts);
    }
}
