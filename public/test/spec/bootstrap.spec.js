import {CONST} from 'modules/vars';
import Bootstrap from 'modules/bootstrap';
import {scope} from 'test/utils/mockjax';

describe('Bootstrap', function () {
    var ajax,
        originalSetTimeout = window.setTimeout,
        objects = generateMockObjects(),
        tick = function (ms) {
            jasmine.clock().tick(ms);
            return new Promise(function (resolve) {
                originalSetTimeout(function () {
                    resolve();
                }, 10);
            });
        };

    beforeEach(function () {
        jasmine.clock().install();
        ajax = scope();
    });
    afterEach(function () {
        jasmine.clock().uninstall();
        ajax.clear();
    });

    it('loads all endpoints correctly', function (done) {
        ajax.apply(null, objects['ajax-success-mock-one']);

        var bootstrap = new Bootstrap(),
            success = jasmine.createSpy('success'),
            fail = jasmine.createSpy('fail'),
            every = jasmine.createSpy('every'),
            one = jasmine.createSpy('one'),
            two = jasmine.createSpy('two');

        bootstrap
            .onload(success)
            .onfail(fail)
            .every(every);

        tick(100)
        .then(() => {

            expect(fail).not.toHaveBeenCalled();
            expect(success).toHaveBeenCalled();
            expect(success.calls.first().args).toEqual([objects['expected-object-one']]);
            expect(every).toHaveBeenCalled();
            expect(every.calls.first().args).toEqual([objects['expected-object-one']]);

            ajax.clear();
            // Change the config in the meantime
            ajax.apply(null, objects['ajax-success-mock-two']);

            return tick(CONST.configSettingsPollMs);
        })
        .then(() => {
            expect(every).toHaveBeenCalledTimes(2);
            expect(every.calls.argsFor(1)).toEqual([objects['expected-object-two']]);
            // get callbacks should not be called again
            expect(success).toHaveBeenCalledTimes(1);

            bootstrap
                .every(one)
                .every(two);

            return tick(CONST.configSettingsPollMs);
        })
        .then(() => {
            expect(one.calls.argsFor(0)).toEqual([objects['expected-object-two']]);
            expect(two.calls.argsFor(0)).toEqual([objects['expected-object-two']]);
            expect(success).toHaveBeenCalledTimes(1);

            // dispose the bootstrap, check that 'every' is not called anymore
            bootstrap.dispose();
            return tick(5 * CONST.configSettingsPollMs);
        })
        .then(() => {
            expect(every).toHaveBeenCalledTimes(3);
        })
        .then(done)
        .catch(done.fail);
    });

    it('fails on network error', function (done) {
        ajax.apply(null, objects['ajax-network-error']);

        var bootstrap = new Bootstrap(),
            success = jasmine.createSpy('success'),
            fail = jasmine.createSpy('fail');

        bootstrap
            .onload(success)
            .onfail(fail);

        tick(100).then(() => {
            expect(fail).toHaveBeenCalled();
            expect(fail.calls.first().args[0]).toMatch(/defaults is invalid/);
            expect(success).not.toHaveBeenCalled();
        })
        .then(done)
        .catch(done.fail);
    });

    it('fails in the every callback', function (done) {
        ajax.apply(null, objects['ajax-success-mock-one']);

        var bootstrap = new Bootstrap(),
            fail = jasmine.createSpy('fail'),
            every = jasmine.createSpy('every');

        bootstrap.every(every, fail);

        tick(100).then(function () {
            expect(fail).not.toHaveBeenCalled();
            expect(every).toHaveBeenCalled();
            expect(every.calls.first().args).toEqual([objects['expected-object-one']]);

            ajax.clear();
            ajax.apply(null, objects['ajax-network-error']);

            return tick(CONST.configSettingsPollMs);
        })
        .then(function () {
            expect(every).toHaveBeenCalledTimes(1);
            expect(fail).toHaveBeenCalledTimes(1);
        })
        .then(done)
        .catch(done.fail);
    });
});

function generateMockObjects () {
    var objects = {};

    objects['ajax-success-mock-one'] = [{
        url: '/defaults',
        responseText: {
            email: 'yours'
        }
    }];

    objects['expected-object-one'] = {
        defaults: {
            email: 'yours'
        }
    };

    objects['ajax-success-mock-two'] = [{
        url: '/defaults',
        responseText: {
            email: 'yours'
        }
    }];

    objects['expected-object-two'] = {
        defaults: {
            email: 'yours'
        }
    };

    objects['ajax-fail-validation'] = [{
        url: '/defaults',
        responseText: {
            email: 'yours'
        }
    }];

    objects['ajax-network-error'] = [{
        url: '/defaults',
        status: 404
    }];

    return objects;
}
