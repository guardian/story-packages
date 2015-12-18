import $ from 'jquery';
import mockjax from 'jquery-mockjax';

mockjax($, window);
$.mockjaxSettings.logging = false;
$.mockjaxSettings.responseTime = 50;

export default $.mockjax;

export function scope () {
    var ids = [];

    var addMocks = function (...mocks) {
        mocks.forEach(function (mock) {
            ids.push($.mockjax(mock));
        });
    };

    addMocks.clear = function () {
        ids.forEach(function (id) {
            $.mockjax.clear(id);
        });
    };

    return addMocks;
}
