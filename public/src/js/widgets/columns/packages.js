import ko from 'knockout';
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
        this.searchInProgress = ko.observable(false);
        this.subscribeOn(this.searchTerm, this.search);
        this.creatingPackage = ko.observable(false);
        this.displayName = ko.observable();
        this.searchResults = ko.observableArray();
        this.searchedPackages = ko.observable();

        this[bouncedSearch] = debounce(performSearch.bind(this), CONST.searchDebounceMs);
    };

    search() {
        const searchTerm = this.searchTerm().toLowerCase().trim();
        if (searchTerm) {
            if (searchTerm.length > 2) {
                this.searchInProgress(true);
                return this[bouncedSearch](searchTerm)
                    .then(displayResuls.bind(this))
                    .catch(() => {
                        this.searchInProgress(false);
                    });
            } else {
                console.log('no search');
                this.searchedPackages(false);
                this.searchResults([]);
            }
        } else {
            this.searchInProgress(false);
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
            var packages = this.baseModel.latestPackages();
            packages.unshift(newPackage);
            this.baseModel.latestPackages(packages);
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
        url: '/story-packages/search/' + encodeURI(searchTerm),
        data: {
            isHidden: this.baseModel.priority === 'training'
        }
    });
}

function displayResuls({results} = {}) {
    this.searchResults(results || []);
    this.searchInProgress(false);
    this.searchedPackages(true);
}
