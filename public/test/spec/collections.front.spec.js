import ko from 'knockout';
import _ from 'underscore';
import $ from 'jquery';
import * as vars from 'modules/vars';
import mediator from 'utils/mediator';
import dates from 'test/fixtures/dates';
import inject from 'test/utils/inject';
import * as mockjax from 'test/utils/mockjax';
import * as wait from 'test/utils/wait';

describe('Front', function () {
    beforeEach(function () {
        this.scope = mockjax.scope();
        var fronts = {
            uk: {
                id: 'uk',
                collections: ['one', 'non-existing', 'shared', 'uneditable']
            },
            au: {
                id: 'au',
                collections: ['shared', 'two']
            }
        };
        this.model = {
            identity: { email: 'fabio.crisci@theguardian.com' },
            frontsList: ko.observableArray(_.values(fronts)),
            frontsMap: ko.observable(fronts),
            testColumn: {
                config: ko.observable(),
                setConfig: () => {},
                baseModel: this.model
            },
            switches: ko.observable({}),
            permissions: ko.observable({}),
            isPasteActive: ko.observable(false),
            state: ko.observable({
                config: {
                    collections: {
                        'one': { displayName: 'Fruits', type: 'long' },
                        'two': { displayName: 'Vegetables', type: 'short' },
                        'shared': { displayName: 'Spices', type: 'long' },
                        'uneditable': { uneditable: true }
                    }
                },
                defaults: { env: 'test' }
            })
        };
        this.ko = inject('<fronts-widget params="column: testColumn, baseModel: $root"></fronts-widget>');
        this.loadFront = (model = {}, columnConfig) => {
            _.extend(model, this.model);
            return new Promise(resolve => {
                model.testColumn.registerMainWidget = widget => setTimeout(() => widget.loaded.then(resolve), 10);
                model.testColumn.config(columnConfig);
                vars.setModel(model);
                this.ko.apply(model);
                this.model = model;
            });
        };
        spyOn(this.model.testColumn, 'setConfig');
    });
    afterEach(function () {
        this.scope.clear();
        this.ko.dispose();
    });

    it('load a front from the select and toggle collection visibility', function (done) {
        this.scope({
            url: '/collection/one',
            status: 404
        }, {
            url: '/collection/shared',
            status: 404
        });

        this.loadFront()
        .then(() => {
            var options = [];
            $('.select--front option').each((i, element) => options.push($(element).text()));
            expect(options).toEqual(['choose a story package...', 'uk', 'au']);

            // Select one front
            $('.select--front')[0].selectedIndex = 1;
            $('.select--front').change();
        })
        .then(() => {
            expect(this.model.testColumn.setConfig).toHaveBeenCalled();

            expect($('collection-widget').length).toBe(2);
            // Collections are still loading
            return wait.event('front:loaded');
        })
        .then(() => {
            expect($('collection-widget:nth(0) .title').text()).toBe('Fruits');
            expect($('collection-widget:nth(1) .title').text()).toBe('Spices');

            return wait.ms(50);
        })
        .then(() => {
            expect($('.collapse-expand-all').hasClass('expanded')).toBe(true);
            $('.list-header__collapser:nth(0)').click();

            // Wait for the presser action
            return wait.ms(50);
        })
        .then(() => {
            expect($('.collapse-expand-all').hasClass('expanded')).toBe(true);
            expect($('.list-header:nth(0)').hasClass('collapsed')).toBe(true);
            $('.list-header__collapser:nth(1)').click();
        })
        .then(() => {
            expect($('.collapse-expand-all').hasClass('expanded')).toBe(false);
            expect($('.list-header:nth(0)').hasClass('collapsed')).toBe(true);
            expect($('.list-header:nth(1)').hasClass('collapsed')).toBe(true);

            $('.collapse-expand-all').click();
        })
        .then(() => {
            expect($('.collapse-expand-all').hasClass('expanded')).toBe(true);
            expect($('.list-header:nth(0)').hasClass('collapsed')).toBe(false);
            expect($('.list-header:nth(1)').hasClass('collapsed')).toBe(false);

            $('.collapse-expand-all').click();
        })
        .then(() => {
            expect($('.collapse-expand-all').hasClass('expanded')).toBe(false);
            expect($('.list-header:nth(0)').hasClass('collapsed')).toBe(true);
            expect($('.list-header:nth(1)').hasClass('collapsed')).toBe(true);

            $('.list-header__collapser:nth(1)').click();
        })
        .then(() => {
            expect($('.collapse-expand-all').hasClass('expanded')).toBe(false);
            expect($('.list-header:nth(0)').hasClass('collapsed')).toBe(true);
            expect($('.list-header:nth(1)').hasClass('collapsed')).toBe(false);
        })
        .then(done)
        .catch(done.fail);
    });
});
