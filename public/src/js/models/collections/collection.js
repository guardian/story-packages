import ko from 'knockout';
import _ from 'underscore';
import BaseClass from 'models/base-class';
import Article from 'models/collections/article';
import Group from 'models/group';
import * as authedAjax from 'modules/authed-ajax';
import * as contentApi from 'modules/content-api';
import * as vars from 'modules/vars';
import asObservableProps from 'utils/as-observable-props';
import deepGet from 'utils/deep-get';
import humanTime from 'utils/human-time';
import mediator from 'utils/mediator';
import populateObservables from 'utils/populate-observables';
import reportErrors from 'utils/report-errors';

export default class Collection extends BaseClass {
    constructor(opts = {}) {
        super();

        if (!opts.id) { return; }

        this.id = opts.id;

        this.front = opts.front;

        this.raw = undefined;

        this.groups = this.createGroups(opts.groups);

        var onDomLoadResolve;
        var onDomLoad = new Promise(function (resolve) {
            onDomLoadResolve = resolve;
        });
        this.registerElement = function () {
            onDomLoadResolve();
        };

        // properties from the config, about this collection
        this.configMeta   = asObservableProps(['displayName']);
        populateObservables(this.configMeta, opts);

        // properties from the collection itself
        this.collectionMeta = asObservableProps([
            'displayName',
            'href',
            'lastUpdated',
            'updatedBy',
            'updatedEmail']);

        this.state  = asObservableProps([
            'lastUpdated',
            'hasConcurrentEdits',
            'hasDraft',
            'pending',
            'count',
            'timeAgo',
            'hasExtraActions',
            'isHistoryOpen']);

        this.itemDefaults = _.reduce({
            showTags: 'showKickerTag',
            showSections: 'showKickerSection'
        }, function(defaults, val, key) {
            if (_.has(opts, key)) {
                defaults = defaults || {};
                defaults[val] = opts[key];
            }
            return defaults;
        }, undefined);

        this.history = ko.observableArray();
        this.state.isHistoryOpen(false);

        this.setPending(true);
        this.loaded = this.load().then(() => onDomLoad);
    }

    setPending(asPending) {
        if (asPending) {
            this.state.pending(true);
        } else {
            setTimeout(() => this.state.pending(false));
        }
    }

    isPending() {
        return !!this.state.pending();
    }

    createGroups(groupNames) {
        return _.map(_.isArray(groupNames) ? groupNames : [undefined], (name, index) =>
            new Group({
                index: index,
                name: name,
                parent: this,
                parentType: 'Collection',
                omitItem: this.drop.bind(this),
                front: this.front
            })
        ).reverse(); // because groupNames is assumed to be in ascending order of importance, yet should render in descending order
    }

    reset() {
        this.closeAllArticles();
        this.load();
    }

    drop(item) {
        const mode = this.front.mode();
        this.setPending(true);

        authedAjax.updateCollections({
            remove: {
                collection: this,
                item:       item.id(),
                mode:       mode
            }
        })
        .catch(() => {});
    }

    load(opts = {}) {
        return authedAjax.request({
            url: vars.CONST.apiBase + '/collection/' + this.id
        })
        .then(raw => {
            if (opts.isRefresh && this.isPending()) { return; }
            if (!raw) { return; }

            this.state.hasConcurrentEdits(false);

            // We need to wait for the populate
            const wait = this.populate(raw);

            populateObservables(this.collectionMeta, raw);

            this.collectionMeta.updatedBy(raw.updatedEmail === deepGet(vars, '.model.identity.email') ? 'you' : raw.updatedBy);

            this.state.timeAgo(this.getTimeAgo(raw.lastUpdated));

            return wait;
        })
        .catch(ex => {
            // Network errors should be ignored
            if (ex instanceof Error) {
                reportErrors(ex);
            }
        })
        .then(() => {
            this.setPending(false);
        });
    }

    hasOpenArticles() {
        return _.some(this.groups, group =>
            _.some(group.items(), article => article.state.isOpen())
        );
    }

    isHistoryEnabled() {
        return this.history().length;
    }

    replaceArticle(articleId) {
        const collectionList = this.front.getCollectionList(this.raw);

        const previousArticle = _.find(collectionList, item => item.id === articleId);
        if (previousArticle) {
            const previousArticleGroupIndex = parseInt((previousArticle.meta || {}).group, 10) || 0;
            const group = _.find(this.groups, group => group.index === previousArticleGroupIndex);
            const articleIndex = _.findIndex(group.items(), item => item.id() === articleId);

            const newArticle = new Article(_.extend({}, previousArticle, {
                group: group
            }));

            group.items.splice(articleIndex, 1, newArticle);
            this.decorate();
        }
    }

    populate(rawCollection) {
        this.raw = rawCollection || this.raw;

        const loading = [];
        if (this.raw) {
            this.state.hasDraft(_.isArray(this.raw.draft));

            if (this.hasOpenArticles()) {
                this.state.hasConcurrentEdits(this.raw.updatedEmail !== deepGet(vars, '.model.identity.email') && this.state.lastUpdated());

            } else if (!rawCollection || this.raw.lastUpdated !== this.state.lastUpdated()) {
                const list = this.front.getCollectionList(this.raw);

                _.each(this.groups, group => group.items.removeAll());

                _.each(list, item => {
                    const itemGroupIndex = parseInt((item.meta || {}).group, 10) || 0;
                    const group = _.find(this.groups, g => itemGroupIndex === g.index) || this.groups[0];
                    const article = new Article(_.extend({}, item, {
                        group: group
                    }));

                    group.items.push(article);
                });

                this.populateHistory(this.raw.previously);
                this.state.lastUpdated(this.raw.lastUpdated);
                this.state.count(list.length);
                loading.push(this.decorate());
            }
        }

        this.setPending(false);
        Promise.all(loading)
            .then(() => mediator.emit('collection:populate', this))
            .catch(() => {});
    }

    populateHistory(list) {
        if (!list || list.length === 0) {
            return;
        }
        this.state.hasExtraActions(true);

        list = list.slice(0, this.front.maxArticlesInHistory);
        this.history(_.map(list, function (opts) {
            return new Article(_.extend(opts, {
                uneditable: true
            }));
        }, this));
    }

    eachArticle(fn) {
        _.each(this.groups, group => {
            _.each(group.items(), item => {
                fn(item, group);
            });
        });
    }

    contains(article) {
        return _.some(this.groups, group =>
            _.some(group.items(), item => item === article)
        );
    }

    closeAllArticles() {
        this.eachArticle(item => item.close());
    }

    decorate() {
        const allItems = [];
        this.eachArticle(item => allItems.push(item));

        const done = contentApi.decorateItems(allItems);
        contentApi.decorateItems(this.history());

        return done;
    }

    refresh() {
        if (this.isPending()) { return; }

        this.load({ isRefresh: true });
    }

    refreshRelativeTimes() {
        this.eachArticle(item => item.setRelativeTimes());
    }

    getTimeAgo(date) {
        return date ? humanTime(date) : '';
    }

    dispose() {
        this.groups.forEach(group => group.dispose());
    }
}
