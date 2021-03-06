import ko from 'knockout';
import _ from 'underscore';
import {CONST} from 'modules/vars';
import BaseClass from 'models/base-class';
import Layout from 'models/layout';
import * as widgets from 'models/widgets';
import copiedArticle from 'modules/copied-article';
import Droppable from 'modules/droppable';
import modalDialog from 'modules/modal-dialog';
import message from 'widgets/message';
import priorityFromUrl from 'utils/priority-from-url';

var droppableSym = Symbol();

export default class BaseModel extends BaseClass {
    constructor(enabledWidgets, extensions, router, res) {
        super();

        var layout = new Layout(router, enabledWidgets, this);

        this.title = ko.observable('story packages');
        this.layout = layout;
        this.extensions = ko.observableArray(extensions || []);
        this.modalDialog = modalDialog;
        this.message = message;
        this.state = ko.observable();
        this.latestPackages = ko.observableArray();
        this.switches = ko.observable();
        this.permissions = ko.observable();
        this.pending = ko.observable(true);
        this.isMainActionVisible = ko.observable(false);
        this.priority = priorityFromUrl(router.location.pathname);
        this.fullPriority = this.priority || CONST.defaultPriority;
        this.liveFrontend = CONST.environmentUrlBase[res.defaults.env] || ('http://' + CONST.mainDomain + '/');
        this.identity = {
            email: res.defaults.email,
            avatarUrl: res.defaults.avatarUrl
        };

        this.update(res);

        this[droppableSym] = new Droppable();
        copiedArticle.flush();
        widgets.register();

        this.loaded = waitFor(this, layout, extensions).then(() => {
            this.pending(false);
            return this;
        });
    }

    chooseLayout() {
        this.layout.toggleConfigVisible();
        this.isMainActionVisible(false);
    }

    saveLayout() {
        this.layout.save();
    }

    cancelLayout() {
        this.layout.cancel();
    }

    update(res) {
        if (!_.isEqual(this.switches(), res.defaults.switches)) {
            this.switches(res.defaults.switches);
        }
        if (!_.isEqual(this.permissions(), res.defaults.acl)) {
            this.permissions(res.defaults.acl);
        }
        // State must be changed last
        this.state(res);
    }

    dispose() {
        super.dispose();
        ko.cleanNode(window.document.body);
        this[droppableSym].dispose();
        this.layout.dispose();
    }
}

function waitFor (model, layout, extensions) {
    var extensionsLoadedInDom,
        extensionsClassesLoaded,
        extensionsLoadedPromise = extensions.length ? new Promise(resolve => {
            extensionsLoadedInDom = _.after(extensions.length, resolve);
        }): Promise.resolve(),
        extensionsClassesPromise = extensions.length ? new Promise(resolve => {
            extensionsClassesLoaded = _.after(extensions.length, resolve);
        }): Promise.resolve();
    model.registerExtension = () => {
        extensionsLoadedInDom();
    };
    model.extensionCreated = () => {
        extensionsClassesLoaded();
    };
    return extensionsLoadedPromise
    .then(() => extensionsClassesPromise)
    .then(() => layout.init());
}
