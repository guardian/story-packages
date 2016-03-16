import ko from 'knockout';
import * as authedAjax from 'modules/authed-ajax';
import modalDialog from 'modules/modal-dialog';
import {CONST} from 'modules/vars';
import alert from 'utils/alert';
import debounce from 'utils/debounce';
import mediator from 'utils/mediator';
import ColumnWidget from 'widgets/column-widget';
import StoryPackage from 'models/story-packages/story-package';

const bouncedSearch = Symbol();

export default class Package extends ColumnWidget {

    constructor(params, element) {
        super(params, element);
        this.allPackages;
        this.searchTerm = ko.observable('');
        this.searchInProgress = ko.observable(false);
        this.subscribeOn(this.searchTerm, this.search);
        this.creatingPackage = ko.observable(params.column.config() === 'create');
        this.displayName = ko.observable();
        this.searchResults = ko.observableArray();
        this.searchedPackages = ko.observable();
        this.editingPackage = ko.observable(false);
        this.newPackageName = ko.observable();

        this[bouncedSearch] = debounce(performSearch.bind(this), CONST.searchDebounceMs);

        this.listenOn(mediator, 'package:edit', this.editPackage);

    };

    search() {
        if (this.editingPackage()) {
            this.editingPackage(false);
            this.searchedPackages(false);
        }

        const searchTerm = this.searchTerm().toLowerCase().trim();
        if (searchTerm) {
            if (searchTerm.length > 2) {
                this.searchInProgress(true);
                return this[bouncedSearch](searchTerm)
                    .then(displayResults.bind(this))
                    .catch(() => {
                        this.searchInProgress(false);
                    });
            } else {
                this.searchInProgress(false);
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
        const name = this.newPackageName().trim();
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
            .then(response => {
                this.newPackageName('');
                mediator.emit('find:package', response);
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
                this.editingPackage(false);

                var newResults = this.searchResults();
                newResults.splice(deletedIndex, 1);
                this.searchResults(newResults);
                mediator.emit('delete:package', storyPackage.id);
            });
        })
        .catch(error => {
            alert('Unable to delete story package \'' + storyPackage.name + '\'\n' + (error.message || error.responseText));
        });
    }

    savePackageEdits(index, storyPackage) {
        const name = storyPackage.meta.name().trim();
        if (name.length < 3) {
            alert('Package name needs to include at least three characters');
            return;
        }

        return authedAjax.request({
            url: '/story-packages/edit/' + storyPackage.id,
            type: 'post',
            data: JSON.stringify({
                name: name,
                isHidden: this.baseModel.priority === 'training'
            })
        })
        .then((response) => {

            var storyPackage = new StoryPackage(response);
            var results = this.searchResults();
            results[index] = storyPackage;
            this.searchResults(results);
            mediator.emit('update:package', response);
            storyPackage.editing(false);
        })
        .catch(error => {
            alert('Unable to edit story package \'' + storyPackage.name + '\'\n' + (error.message || error.responseText));
        });
    }

    closePackageEdit(storyPackage) {
        this.meta.name(this.savedDisplayName);
        storyPackage.editing(false);
        return;
    }

    openEditor(storyPackage) {
        if (!storyPackage.editing()) {
            storyPackage.editing(true);
        }
    }

    editPackage(packageId) {

        return authedAjax.request({
            url: '/story-package/' + packageId
        })
        .then(response => {

            this.searchInProgress(false);
            this.searchTerm('');

            this.editingPackage(true);
            this.searchResults([new StoryPackage(response)]);
        })
        .catch(error => {
            alert('Unable to edit story package' + '\n' + (error.message || error.responseText));
        });
    }

    displaySearchResults() {
        return (this.searchTerm() && this.searchTerm().length > 2 && !this.searchInProgress()) || this.editingPackage();
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

function displayResults(results = {}) {
    if (this.searchInProgress()) {
        this.searchResults((results || []).map(result => {
            var storyPackage = new StoryPackage(result);
            return storyPackage;
        }));
        this.searchInProgress(false);
        this.searchedPackages(true);
    }
}

function removePackage(storyPackageId) {
    return authedAjax.request({
        url: '/story-package/' + storyPackageId,
        type: 'delete'
    });
}
