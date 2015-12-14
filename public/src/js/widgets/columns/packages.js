import ko from 'knockout';
import * as authedAjax from 'modules/authed-ajax';
import modalDialog from 'modules/modal-dialog';
import {CONST} from 'modules/vars';
import alert from 'utils/alert';
import debounce from 'utils/debounce';
import humanTime from 'utils/human-time';
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
                this.searchedPackages(false);
                this.searchResults.removeAll();
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
        mediator.emit('find:package', chosenPackage.id);
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
            alert('Unable to create story package:\n' + (response.message || response.responseText));
        })
        .then(() => {
            this.creatingPackage(false);
            this.displayName(null);
        });
    }

    displayRemoveModal(storyPackage) {
        return modalDialog.confirm({
            name: 'confirm_package_delete',
            data: {
                packageName: storyPackage.name
            }
        })
        .then(() => {
            return removePackage(storyPackage.id)
            .then(() => {
                // TODO maybe splice from the list?
                // TODO what if it's open in the fronts column?
            })
            .catch(error => {
                alert('Unable to delete story package \'' + storyPackage.name + '\'\n' + (error.message || error.responseText));
            });
        })
        .catch(() => {});
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
    this.searchResults((results || []).map(result => {
        return Object.assign({
            lastModifyHuman: humanTime(new Date(result.lastModify))
        }, result);
    }));
    this.searchInProgress(false);
    this.searchedPackages(true);
}

function removePackage(storyPackageId) {
    return authedAjax.request({
        url: '/story-package/' + storyPackageId,
        type: 'delete'
    });
}
