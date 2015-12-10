import ko from 'knockout';
import _ from 'underscore';
import * as authedAjax from 'modules/authed-ajax';
import {CONST} from 'modules/vars';
import alert from 'utils/alert';
import debounce from 'utils/debounce';
import mediator from 'utils/mediator';
import ColumnWidget from 'widgets/column-widget';

const bouncedSearch = Symbol();

export default class Package extends ColumnWidget {

    constructor(params, element) {
        super(params, element);
        this.allPackages;
        this.searchedPackages = ko.observableArray();
        this.searchTerm = ko.observable('');
        this.populateAllPackages(this.baseModel.state());
        this.subscribeOn(this.baseModel.state, this.populateAllPackages);
        this.subscribeOn(this.searchTerm, this.search);
        this.creatingPackage = ko.observable(false);
        this.displayName = ko.observable();

        this[bouncedSearch] = debounce(performSearch.bind(this), CONST.searchDebounceMs);
    };

    populateAllPackages(state) {
        this.allPackages = _.values(state.config.collections).map(collection => {
            return {
                displayName: collection.displayName,
                searchTerm: collection.displayName.toLowerCase()
            };
        });
    };

    search() {
        const searchTerm = this.searchTerm().toLowerCase().trim();
        if (searchTerm) {
            return this[bouncedSearch](searchTerm)
                .then(displayResuls.bind(this))
                .catch(() => { /* TODO what to todo? */});
        } else {
            return Promise.resolve([]);
        }
    }

    createPackage() {
        this.creatingPackage(!this.creatingPackage());
    }

    displayPackage(chosenPackage) {
        mediator.emit('find:package', chosenPackage.displayName);
    }

    savePackage() {
        return authedAjax.request({
            url: '/story-packages/create',
            type: 'post',
            data: JSON.stringify({
                name: this.displayName(),
                isHidden: this.baseModel.priority === 'training'
            })
        })
        .then(newPackage => {
            mediator.emit('find:package', newPackage.id);
        })
        .catch(response => {
            alert('Unable to create story package:\n' + response.responseText);
        })
        .then(() => {
            this.creatingPackage(false);
            this.displayName(null);
        });
    }
}

function performSearch(searchTerm) {
    return authedAjax.request({
        url: '/story-packages/search/' + encodeURIComponent(searchTerm)
    });
}

function displayResuls(results) {
    console.log(results);
}
