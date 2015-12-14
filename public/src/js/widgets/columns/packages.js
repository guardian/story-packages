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
        mediator.emit('find:package', chosenPackage);
    }

    savePackage() {
        var name = this.displayName().trim();
        if (name.length < 3) {
            alert('Package name needs to include at least three characters');
        } else {
            this.searchInProgress(false);
            return authedAjax.request({
                url: '/story-packages/create',
                type: 'post',
                data: JSON.stringify({
                    name: name,
                    isHidden: this.baseModel.priority === 'training'
                })
            })
            .then(newPackage => {
                var packages = this.baseModel.latestPackages();
                packages.unshift(newPackage);
                this.baseModel.latestPackages(packages);
                this.search();
                mediator.emit('find:package', newPackage);
            })
            .catch(response => {
                alert('Unable to create story package:\n' + (response.message || response.responseText));
            })
            .then(() => {
                this.creatingPackage(false);
                this.displayName(null);
            });
        }
    }

    displayRemoveModal(deletedIndex, storyPackage) {
        var storyPackage = storyPackage;
        return modalDialog.confirm({
            name: 'confirm_package_delete',
            data: {
                packageName: storyPackage.name
            }
        })
        .then(() => {
            return removePackage(storyPackage.id)
            .then(() => {
                var newResults = this.searchResults();
                newResults.splice(deletedIndex, 1);
                this.searchResults(newResults);
                mediator.emit('delete:package', storyPackage.id);
            });
        })
        .catch(error => {
            alert('Unable to delete story package \'' + storyPackage.name + '\'\n' + (error.message || error.responseText));
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
