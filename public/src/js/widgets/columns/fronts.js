import ko from 'knockout';
import Promise from 'Promise';
import _ from 'underscore';
import Collection from 'models/collections/collection';
import {CONST} from 'modules/vars';
import mediator from 'utils/mediator';
import ColumnWidget from 'widgets/column-widget';

export default class Front extends ColumnWidget {
    constructor(params, element) {
        super(params, element);

        var frontId = params.column.config();
        this.front = ko.observable(frontId);
        this.previousFront = frontId;
        this.frontAge = ko.observable();
        this.collections = ko.observableArray();
        this.mode = ko.observable('live');
        this.flattenGroups = ko.observable(params.mode === 'treats');
        this.maxArticlesInHistory = 5;
        this.controlsVisible = ko.observable(false);
        this.authorized = ko.observable(isAuthorized(this.baseModel, frontId));

        this.subscribeOn(this.front, this.onFrontChange);
        this.subscribeOn(this.mode, this.onModeChange);
        this.subscribeOn(this.baseModel.permissions, () => {
            this.authorized(isAuthorized(this.baseModel, this.front()));
        });

        this.setFront = id => this.front(id);
        this.setModeLive = () => this.mode('live');
        this.setModeDraft = () => this.mode('draft');

        this.frontMode = ko.pureComputed(() => {
            var classes = [this.mode() + '-mode'];
            return classes.join(' ');
        });

        this.previewUrl = ko.pureComputed(() => {
            var path = this.mode() === 'live' ? 'http://' + CONST.mainDomain : CONST.previewBase;

            return CONST.previewBase + '/responsive-viewer/' + path + '/' + this.front();
        });

        this.isControlsVisible = ko.observable(false);
        this.controlsText = ko.pureComputed(() => '');

        this.uiOpenArticle = ko.observable();

        this.allExpanded = ko.observable(true);

        this.listenOn(mediator, 'ui:open', (element, article, front) => {
            if (front !== this) {
                return;
            }
            var openArticle = this.uiOpenArticle();
            if (openArticle && openArticle.group &&
                openArticle.group.parentType === 'Article' &&
                openArticle !== article) {
                openArticle.close();
            }
            this.uiOpenArticle(article);
        });

        this.listenOn(mediator, 'alert:dismiss', () => this.alertFrontIsStale(false));
        this.listenOn(mediator, 'collection:collapse', this.onCollectionCollapse);
        this.listenOn(mediator, 'find:package', this.onFrontChange);

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

    load(frontId) {
        if (frontId !== this.front()) {
            this.front(frontId);
        }
        var allCollections = this.baseModel.state().config.collections;

        this.allExpanded(true);
        this.collections(
            ((this.baseModel.frontsMap()[frontId] || {}).collections || [])
            .filter(id => allCollections[id] && !allCollections[id].uneditable)
            .map(id => new Collection(
                _.extend(
                    allCollections[id],
                    {
                        id: id,
                        front: this
                    }
                )
            ))
        );

        this.loaded = Promise.all(
            this.collections().map(collection => collection.loaded)
        ).then(() => mediator.emit('front:loaded', this));
    }

    toggleAll() {
        var state = !this.allExpanded();
        this.allExpanded(state);
        _.each(this.collections(), collection => collection.state.collapsed(!state));
    }

    onCollectionCollapse(collection, collectionState) {
        if (collection.front !== this) {
            return;
        }
        var differentState = _.find(this.collections(), collection => collection.state.collapsed() !== collectionState);
        if (!differentState) {
            this.allExpanded(!collectionState);
        }
    }

    refreshCollections(period) {
        var length = this.collections().length || 1;
        this.setIntervals.push(setInterval(() => {
            this.collections().forEach((list, index) => {
                this.setTimeouts.push(setTimeout(() => list.refresh(), index * period / length)); // stagger requests
            });
        }, period));
    }

    refreshRelativeTimes(period) {
        this.setIntervals.push(setInterval(() => {
            this.collections().forEach(list => list.refreshRelativeTimes());
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
        _.each(this.collections(), function(collection) {
            collection.closeAllArticles();
            collection.populate();
        });
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

    newItemValidator() {}

    dispose() {
        super.dispose();
        _.each(this.setIntervals, clearInterval);
        _.each(this.setTimeouts, clearTimeout);
    }
}

function isAuthorized (baseModel, frontId) {
    var permissions = baseModel.permissions() || {};
    return (permissions.fronts || {})[frontId] !== false;
}
