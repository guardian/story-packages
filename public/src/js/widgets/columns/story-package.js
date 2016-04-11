import ko from 'knockout';
import _ from 'underscore';
import Collection from 'models/collections/collection';
import * as authedAjax from 'modules/authed-ajax';
import {CONST} from 'modules/vars';
import alert from 'utils/alert';
import mediator from 'utils/mediator';
import ColumnWidget from 'widgets/column-widget';

export default class StoryPackage extends ColumnWidget {
    constructor(params, element) {
        super(params, element);

        const packageId = params.column.config();
        this.front = ko.observable(packageId);
        this.previousFront = packageId;
        this.collection = ko.observable();
        this.mode = ko.observable('live');
        this.maxArticlesInHistory = 5;
        this.controlsVisible = ko.observable(false);

        this.subscribeOn(this.front, this.onFrontChange);

        this.setFront = id => this.front(id);

        this.isControlsVisible = ko.observable(true);

        this.uiOpenArticle = ko.observable();

        this.listenOn(mediator, 'ui:open', (element, article, front) => {
            if (front !== this) {
                return;
            }
            this.uiOpenArticle(article);
        });

        this.listenOn(mediator, 'delete:package', function(storyPackageId) {
            var existingPackages = this.baseModel.latestPackages();
            if (this.front() === storyPackageId) {
                this.front(null);
                this.collection(null);
            }
            var index = _.findIndex(existingPackages, existingPackage => existingPackage.id === storyPackageId);
            if (index !== -1) {
                existingPackages.splice(index, 1);
                this.baseModel.latestPackages(existingPackages);
            }
        });

        this.listenOn(mediator, 'update:package', function(storyPackage) {
            var existingPackages = this.baseModel.latestPackages();
            const index = _.findIndex(existingPackages, existingPackage => existingPackage.id === storyPackage.id);
            if (index !== -1) {
                existingPackages[index] = storyPackage;
            } else {
                existingPackages.push(storyPackage);
            }
            this.baseModel.latestPackages(existingPackages);
        });

        this.listenOn(mediator, 'find:package', function(storyPackage) {

            var existingPackages = this.baseModel.latestPackages();

            if (_.every(existingPackages, existingPackage => {
                return existingPackage.id !== storyPackage.id;
            })) {
                var packageDate = new Date(storyPackage.meta.lastModify());
                var packageIndex = _.findIndex(existingPackages, existingPackage => new Date(existingPackage.lastModify) < packageDate);

                var newPackage = {
                    id: storyPackage.id,
                    name: storyPackage.meta.name(),
                };

                if (packageIndex > -1) {
                    existingPackages.splice(packageIndex, 0, newPackage);
                } else {
                    existingPackages.push(newPackage);
                }
            }
            this.baseModel.latestPackages(existingPackages);
            this.onFrontChange(storyPackage.id);
        });

        this.subscribeOn(this.column.config, newConfig => {
            if (newConfig !== this.front()) {
                this.front(newConfig);
            }
        });

        this.setIntervals = [];
        this.setTimeouts = [];
        this.refreshCollections(CONST.collectionsPollMs || 60000);
        this.refreshRelativeTimes(CONST.pubTimeRefreshMs || 60000);

        this.load(packageId);
    }

    load(id) {
        if (id !== this.front()) {
            this.front(id);
        }
        if (!id) {
            this.loaded = Promise.resolve();
            return;
        }

        this.loaded = authedAjax.request({
            url: '/story-package/' + id
        })
        .then(response => {
            const newCollection = new Collection({
                id: id,
                front: this,
                displayName: response.name,
                lastUpdated: response.lastModify,
                updatedBy: response.lastModifyByName,
                updatedEmail: response.lastModifyBy,
                groups: ['linked', 'included']
            });
            const oldCollection = this.collection();
            this.collection(newCollection);
            const latestPackages = this.baseModel.latestPackages();
            if (!_.find(latestPackages, latestPackage => latestPackage.id === newCollection.id )) {
                latestPackages.unshift(response);
                this.baseModel.latestPackages(latestPackages);
                this.front(newCollection.id);
            }

            return newCollection.loaded.then(() => {
                if (oldCollection) {
                    oldCollection.dispose();
                }
            });
        })
        .then(() => mediator.emit('front:loaded', this))
        .catch(response => {
            alert('Failed loading story package ' + id + '\n' + response.responseText || response.message);
        });
    }

    refreshCollections(period) {
        this.setIntervals.push(setInterval(() => {
            if (this.collection()) {
                this.collection().refresh();
            }
        }, period));
    }

    refreshRelativeTimes(period) {
        this.setIntervals.push(setInterval(() => {
            if (this.collection()) {
                this.collection().refreshRelativeTimes();
            }
        }, period));
    }

    onFrontChange(front) {
        if (front === this.previousFront) {
            // This happens when the page is loaded and the select is bound
            return;
        }
        this.previousFront = front;
        this.column.setConfig(front);

        this.load(front);
    }

    getCollectionList(list) {
        return list.live || [];
    }

    newItemValidator(item) {
        if (item.meta.snapType()) {
            return 'You cannot add snaps to packages';
        }

        const groupArticlesCount = {};
        const {includedCap, linkingCap} = this.baseModel.state().defaults;
        this.collection().groups.forEach(group => {
            groupArticlesCount[group.name] = group.items().length;
        });

        if (groupArticlesCount.included > includedCap) {
            return 'You can have maximum of ' + includedCap + ' articles in a story package. Remove an article from the package before adding a new one.';
        } else if (groupArticlesCount.linked > linkingCap) {
            return 'You can link up to ' + linkingCap + ' articles to a package. Remove an article from the package before adding a new one.';
        }
    }

    dispose() {
        super.dispose();
        _.each(this.setIntervals, clearInterval);
        _.each(this.setTimeouts, clearTimeout);
    }

    managePackage() {
        mediator.emit('package:edit', this.front());
    }

}
