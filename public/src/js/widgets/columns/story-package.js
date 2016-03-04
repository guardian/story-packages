import ko from 'knockout';
import _ from 'underscore';
import Collection from 'models/collections/collection';
import * as authedAjax from 'modules/authed-ajax';
import {CONST} from 'modules/vars';
import alert from 'utils/alert';
import mediator from 'utils/mediator';
import ColumnWidget from 'widgets/column-widget';

export default class Front extends ColumnWidget {
    constructor(params, element) {
        super(params, element);

        var frontId = params.column.config();
        this.front = ko.observable(frontId);
        this.previousFront = frontId;
        this.collection = ko.observable();
        this.mode = ko.observable('live');
        this.maxArticlesInHistory = 5;
        this.controlsVisible = ko.observable(false);
        this.authorized = ko.observable(isAuthorized(this.baseModel, frontId));

        this.subscribeOn(this.front, this.onFrontChange);
        this.subscribeOn(this.mode, this.onModeChange);
        this.subscribeOn(this.baseModel.permissions, () => {
            this.authorized(isAuthorized(this.baseModel, this.front()));
        });

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

        this.listenOn(mediator, 'find:package', function(storyPackage) {
            var existingPackages = this.baseModel.latestPackages();
            if (_.every(existingPackages, existingPackage => {
                return existingPackage.id !== storyPackage.id;
            })) {
                var packageDate = new Date(storyPackage.lastModify);
                var packageIndex = _.findIndex(existingPackages, existingPackage => new Date(existingPackage.lastModify) < packageDate);

                if (packageIndex > -1) {
                    existingPackages.splice(packageIndex, 0, storyPackage);
                } else {
                    existingPackages.push(storyPackage);
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

        this.load(frontId);
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
            this.collection(newCollection);
            var latestPackages = this.baseModel.latestPackages();
            if (!_.find(latestPackages, latestPackage => latestPackage.id === newCollection.id )) {
                latestPackages.unshift(response);
                this.baseModel.latestPackages(latestPackages);
                this.front(newCollection.id);
            }

            return newCollection.loaded;
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

    onModeChange() {
        var collection = this.collection();
        if (collection) {
            collection.closeAllArticles();
            collection.populate();
        }
    }

    getCollectionList(list) {
        var sublist;
        if (this.mode() === 'treats') {
            sublist = list.treats;
        } else if (this.mode() === 'live') {
            sublist = list.live;
        } else {
            sublist = list.draft || list.live;
        }
        return sublist || [];
    }

    newItemValidator(item) {
        if (item.meta.snapType()) {
            return 'You cannot add snaps to packages';
        }

        var numberOfArticles = _.reduce(this.collection().groups, (articleNumber, group) => {
            return group.items().length + articleNumber;
        }, 0);

        var collectionCap = this.baseModel.state().defaults.collectionCap;
        if (numberOfArticles > collectionCap) {
            return 'You can have maximum of ' + collectionCap + ' articles in a strory package. You must delete an article from the package before adding a new one';
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

function isAuthorized (baseModel, frontId) {
    var permissions = baseModel.permissions() || {};
    return (permissions.fronts || {})[frontId] !== false;
}
