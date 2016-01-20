import ko from 'knockout';
import $ from 'jquery';
import _ from 'underscore';
import mediator from 'utils/mediator';
import Layout from 'models/layout';
import Router from 'modules/router';
import verticalLayout from 'views/templates/vertical_layout.scala.html!text';
import mainLayout from 'views/templates/main.scala.html!text';
import inject from 'test/utils/inject';
import * as wait from 'test/utils/wait';
import fakePushState from 'test/utils/push-state';

describe('Layout', function () {
    var CONST_TRANSITION = 10;
    beforeEach(function () {
        this.ko = inject(`<div id="_test_container_layout">
            <span class="save-layout" data-bind="click: layout.save.bind(layout)">Save</span>
            <span class="cancel-layout" data-bind="click: layout.cancel.bind(layout)">Cancel</span>
            ${verticalLayout}
            ${mainLayout}
        </div>`);

        var handlers = {
            packages: function () {},
            latest: function () {}
        },
        location = {
            pathname: '/',
            search: ''
        },
        history = {
            pushState: function () {}
        };

        spyOn(handlers, 'packages');
        spyOn(handlers, 'latest');
        spyOn(history, 'pushState').and.callFake(fakePushState.bind(location));
        this.router = new Router(handlers, location, history);
        this.widget = [{
            title: 'Story Package',
            layoutType: 'content',
            widget: 'mock-story-package-widget'
        }, {
            title: 'Latest',
            layoutType: 'latest',
            widget: 'mock-latest-widget'
        }, {
            title: 'Packages',
            layoutType: 'packages',
            widget: 'mock-packages-widget'
        }];
        this.layout = new Layout(this.router, this.widget);
        this.layout.CONST.addColumnTransition = CONST_TRANSITION;
        this.layout.init();
    });
    afterEach(function () {
        this.layout.dispose();
        this.ko.dispose();
    });
    function click (selector) {
        return new Promise(resolve => {
            $(selector).click();
            setTimeout(resolve, CONST_TRANSITION + 10);
        });
    }
    function navigateTo (router, search) {
        return new Promise(resolve => {
            router.location.search = search;
            router.onpopstate();
            setTimeout(resolve, 10);
        });
    }
    function columnsInDOM () {
        return _.map($('.mock-widget'), widget => {
            return _.filter(widget.classList, className => className !== 'mock-widget')[0];
        });
    }

    it('changes the workspace', function (done) {
        var layout = this.layout, saved, current;

        this.ko.apply({ layout }, true)
        // wait for the second widget to load
        .then(() => wait.ms(100))
        .then(() => {
            expect(layout.configVisible()).toBe(false);
            expect(layout.configVisible()).toBe(false);
            expect($('.configPane', this.ko.container).is(':visible')).toBe(false);
            expect(columnsInDOM()).toEqual(['latest', 'content', 'packages']);

            layout.toggleConfigVisible();
            expect(layout.configVisible()).toBe(true);
            expect($('.configPane', this.ko.container).is(':visible')).toBe(true);

            saved = layout.savedState.columns();
            current = layout.currentState.columns();
            expect(saved.length).toBe(3);
            expect(current.length).toBe(3);

            // Add an extra column in the middle
            return click('.fa-plus-circle:nth(0)');
        })
        .then(() => {
            // Plus button clones the source column
            expect(layout.savedState.columns().length).toBe(3);
            expect(layout.savedState.columns()).toBe(saved);
            expect(layout.currentState.columns().length).toBe(4);
            expect($('.configPane').length).toBe(4);
            expect(columnsInDOM()).toEqual(['latest', 'latest', 'content', 'packages']);

            // Cancel the workspace change
            return click('.cancel-layout');
        })
        .then(() => {
            expect(layout.savedState.columns().length).toBe(3);
            expect(layout.savedState.columns()).toBe(saved);
            expect(layout.currentState.columns().length).toBe(3);
            expect($('.configPane').length).toBe(3);
            expect(columnsInDOM()).toEqual(['latest', 'content', 'packages']);

            // Add another column
            return click('.fa-plus-circle:nth(1)');
        })
        .then(() => {
            expect(layout.savedState.columns()).toBe(saved);
            expect(layout.currentState.columns().length).toBe(4);
            expect($('.configPane').length).toBe(4);
            expect(columnsInDOM()).toEqual(['latest', 'content', 'content', 'packages']);

            // Change the type of a column
            return click('.configPane:nth(2) .checkbox-latest');
        })
        .then(() => {
            expect(columnsInDOM()).toEqual(['latest', 'content', 'latest', 'packages']);

            return click('.save-layout');
        })
        .then(() => {
            expect(this.router.location.search).toBe('?layout=latest,content,latest,packages');

            return layout.toggleConfigVisible();
        })
        .then(() => {
            expect(columnsInDOM()).toEqual(['latest', 'content', 'latest', 'packages']);

            // Navigate back to the previous layout
            return navigateTo(this.router, '?layout=content:banana,latest');
        })
        .then(() => {
            expect(columnsInDOM()).toEqual(['content', 'latest']);
            expect($('.mock-widget.content').text()).toBe('banana');

            layout.currentState.columns()[0].setConfig('apple');
        })
        .then(() => {
            expect(this.router.location.search).toBe('?layout=content:apple,latest');

            return click('.fa-minus-circle:nth(1)');
        })
        .then(() => {
            expect(layout.savedState.columns().length).toBe(2);
            expect(layout.currentState.columns().length).toBe(1);
            expect($('.configPane').length).toBe(1);
            expect(columnsInDOM()).toEqual(['content']);
            expect($('.mock-widget.content').text()).toBe('apple');
        })
        .then(done)
        .catch(done.fail);
    });
});

ko.components.register('mock-story-package-widget', {
    viewModel: {
        createViewModel: (params) => {
            mediator.emit('widget:load');
            return params;
        }
    },
    template: '<div class="mock-widget content" data-bind="text: column.config"></div>'
});
ko.components.register('mock-latest-widget', {
    viewModel: {
        createViewModel: (params) => {
            mediator.emit('widget:load');
            return params;
        }
    },
    template: '<div class="mock-widget latest"></div>'
});
ko.components.register('mock-packages-widget', {
    viewModel: {
        createViewModel: (params) => {
            mediator.emit('widget:load');
            return params;
        }
    },
    template: '<div class="mock-widget packages"></div>'
});
