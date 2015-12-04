define([
    'knockout',
    'underscore',
    'jquery',
    'modules/vars',
    'utils/as-observable-props',
    'utils/human-time',
    'utils/mediator',
    'utils/populate-observables',
    'utils/report-errors',
    'modules/authed-ajax',
    'modules/modal-dialog',
    'models/group',
    'models/collections/article',
    'modules/content-api'
], function(
    ko,
    _,
    $,
    vars,
    asObservableProps,
    humanTime,
    mediator,
    populateObservables,
    reportErrors,
    authedAjax,
    modalDialog,
    Group,
    Article,
    contentApi
) {
    modalDialog = modalDialog.default;
    asObservableProps = asObservableProps.default;
    populateObservables = populateObservables.default;
    mediator = mediator.default;
    humanTime = humanTime.default;
    Group = Group.default;
    reportErrors = reportErrors.default;

    function Collection(opts) {

        if (!opts || !opts.id) { return; }

        this.id = opts.id;

        this.front = opts.front;

        this.raw = undefined;

        this.groups = this.createGroups(opts.groups);

        this.alsoOn = opts.alsoOn || [];

        this.dom = undefined;
        var onDomLoadResolve;
        var onDomLoad = new Promise(function (resolve) {
            onDomLoadResolve = resolve;
        });
        this.registerElement = function (element) {
            this.dom = element;
            onDomLoadResolve();
        };

        this.visibleStories = null;
        this.visibleCount = ko.observable({});

        // properties from the config, about this collection
        this.configMeta   = asObservableProps([
            'type',
            'displayName',
            'hideShowMore',
            'uneditable']);
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
            'collapsed',
            'hasDraft',
            'pending',
            'editingConfig',
            'count',
            'timeAgo',
            'alsoOnVisible',
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
        this.loaded = this.load().then(function () { return onDomLoad; });
    }

    Collection.prototype.setPending = function(asPending) {
        var self = this;

        if (asPending) {
            this.state.pending(true);
        } else {
            setTimeout(function() {
                self.state.pending(false);
            });
        }
    };

    Collection.prototype.isPending = function() {
        return !!this.state.pending();
    };

    Collection.prototype.createGroups = function(groupNames) {
        var self = this;

        return _.map(_.isArray(groupNames) ? groupNames : [undefined], function(name, index) {
            return new Group({
                index: index,
                name: name,
                parent: self,
                parentType: 'Collection',
                omitItem: self.drop.bind(self),
                front: self.front
            });
        }).reverse(); // because groupNames is assumed to be in ascending order of importance, yet should render in descending order
    };

    Collection.prototype.toggleCollapsed = function() {
        var collapsed = !this.state.collapsed();
        this.state.collapsed(collapsed);
        this.closeAllArticles();
        mediator.emit('collection:collapse', this, collapsed);
    };

    Collection.prototype.toggleEditingConfig = function() {
        this.state.editingConfig(!this.state.editingConfig());
    };

    Collection.prototype.reset = function() {
        this.closeAllArticles();
        this.state.editingConfig(false);
        this.load();
    };

    Collection.prototype.publishDraft = function() {
        this.processDraft(true);
    };

    Collection.prototype.discardDraft = function() {
        this.processDraft(false);
    };

    Collection.prototype.processDraft = function(goLive) {
        var self = this;

        this.state.hasDraft(false);
        this.setPending(true);
        this.closeAllArticles();

        authedAjax.request({
            type: 'post',
            url: vars.CONST.apiBase + '/collection/' + (goLive ? 'publish' : 'discard') + '/' + this.id
        })
        .then(function() {
            return self.load();
        })
        .catch(function () {
            reportErrors(new Error('POST request while processing draft failed'));
        });
    };

    Collection.prototype.drop = function(item) {
        var mode = this.front.mode();
        this.setPending(true);

        authedAjax.updateCollections({
            remove: {
                collection: this,
                item:       item.id(),
                mode:       mode
            }
        })
        .catch(function () {});
    };

    Collection.prototype.load = function(opts) {
        var self = this;

        opts = opts || {};

        return authedAjax.request({
            url: vars.CONST.apiBase + '/collection/' + this.id
        })
        .then(function(raw) {
            if (opts.isRefresh && self.isPending()) { return; }
            if (!raw) { return; }

            // We need to wait for the populate
            return new Promise(function (resolve) {
                self.state.hasConcurrentEdits(false);

                self.populate(raw, resolve);

                populateObservables(self.collectionMeta, raw);

                self.collectionMeta.updatedBy(raw.updatedEmail === vars.model.identity.email ? 'you' : raw.updatedBy);

                self.state.timeAgo(self.getTimeAgo(raw.lastUpdated));
            });
        })
        .catch(function (ex) {
            // Network errors should be ignored
            if (ex instanceof Error) {
                reportErrors(ex);
            }
        })
        .then(function() {
            self.setPending(false);
        });
    };

    Collection.prototype.hasOpenArticles = function() {
        return _.some(this.groups, function(group) {
            return _.some(group.items(), function(article) { return article.state.isOpen(); });
        });
    };

    Collection.prototype.isHistoryEnabled = function () {
        return this.front.mode() !== 'treats' && this.history().length;
    };

    Collection.prototype.replaceArticle = function(articleId) {
        var self = this;
        var collectionList = this.front.getCollectionList(this.raw);
        var group;
        var articleIndex;
        var article;

        _.find(collectionList, function(item) {
            if (item.id === articleId) {
                group = _.find(self.groups, function(g) {
                    return (parseInt((item.meta || {}).group, 10) || 0) === g.index;
                });
                article = new Article(_.extend(item, {
                    group: group
                }));

                return true;
            }
        });

        var articleIndex = _.findIndex(group.items(), function(item) {
            return (item.id() === articleId);
        });

        group.items.splice(articleIndex, 1, article);
        this.decorate();
    };

    Collection.prototype.populate = function(rawCollection, callback) {
        callback = callback || function () {};
        var self = this,
            list,
            loading = [];

        this.raw = rawCollection || this.raw;

        if (this.raw) {
            this.state.hasDraft(_.isArray(this.raw.draft));

            if (this.hasOpenArticles()) {
                this.state.hasConcurrentEdits(this.raw.updatedEmail !== vars.model.identity.email && this.state.lastUpdated());

            } else if (!rawCollection || this.raw.lastUpdated !== this.state.lastUpdated()) {
                list = this.front.getCollectionList(this.raw);

                _.each(this.groups, function(group) {
                    group.items.removeAll();
                });

                _.each(list, function(item) {
                    var group = _.find(self.groups, function(g) {
                        return (parseInt((item.meta || {}).group, 10) || 0) === g.index;
                    }) || self.groups[0];
                    var article = new Article(_.extend(item, {
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
        Promise.all(loading).then(function () {
            mediator.emit('collection:populate', self);
            callback();
        })
        .catch(callback);
    };

    Collection.prototype.populateHistory = function(list) {
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
    };

    Collection.prototype.eachArticle = function (fn) {
        _.each(this.groups, function(group) {
            _.each(group.items(), function(item) {
                fn(item, group);
            });
        });
    };

    Collection.prototype.contains = function (article) {
        return _.some(this.groups, function (group) {
            return _.some(group.items(), function (item) {
                return item === article;
            });
        });
    };

    Collection.prototype.closeAllArticles = function() {
        this.eachArticle(function(item) {
            item.close();
        });
    };

    Collection.prototype.decorate = function() {
        var allItems = [],
            done;
        this.eachArticle(function(item) {
            allItems.push(item);
        });
        done = contentApi.decorateItems(allItems);
        contentApi.decorateItems(this.history());

        return done;
    };

    Collection.prototype.refresh = function() {
        if (this.isPending()) { return; }

        this.load({
            isRefresh: true
        });
    };

    Collection.prototype.refreshRelativeTimes = function() {
        this.eachArticle(function(item) {
            item.setRelativeTimes();
        });
    };

    Collection.prototype.getTimeAgo = function(date) {
        return date ? humanTime(date) : '';
    };

    Collection.prototype.alsoOnToggle = function () {
        this.state.alsoOnVisible(!this.state.alsoOnVisible());
    };

    Collection.prototype.dispose = function () {
        this.groups.forEach(function (group) {
            group.dispose();
        });
        if (this.visibleStories) {
            this.visibleStories.dispose();
        }
    };

    return Collection;
});
