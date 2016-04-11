import ko from 'knockout';
import _ from 'underscore';
import $ from 'jquery';
import numeral from 'numeral';
import {request} from 'modules/authed-ajax';
import * as vars from 'modules/vars';
import Highcharts from 'utils/highcharts';
import mediator from 'utils/mediator';
import parseQueryParams from 'utils/parse-query-params';
import urlAbsPath from 'utils/url-abs-path';

let subscribedPackages = [];
let pollingId;

function goodEnoughSeries (totalHits, series) {
    return series && series.length && totalHits >= 10;
}

function createSparklikes (element, totalHits, series) {
    const lineWidth = Math.min(Math.ceil(totalHits / 2000), 4);

    return new Highcharts.Chart($.extend(true, Highcharts.CONFIG_DEFAULTS.sparklines, {
        chart: {
            renderTo: element
        },
        title: {
            text: numeral(totalHits).format(',')
        },
        plotOptions: {
            series: {
                lineWidth: lineWidth
            }
        },
        series: series
    }));
}

function getWebUrl (article) {
    const url = urlAbsPath(article.props.webUrl());
    if (url) {
        return '/' + url;
    }
}

function showSparklinesInArticle (element, article) {
    const front = article.front || {};
    const storyPackage = front.collection ? front.collection() : null;
    const webUrl = getWebUrl(article);
    const $element = $(element);
    const chart = $element.data('sparklines');

    if (!storyPackage || !storyPackage.sparklines || !webUrl) {
        return;
    }

    const data = storyPackage.sparklines.data()[webUrl] || {};
    const series = data.series;

    if (chart) {
        // dispose the chart even if there's no series because the new update means
        // there's no data for it. Don't show stale data
        chart.destroy();
        $element.removeData('sparklines');
    }

    if (!goodEnoughSeries(data.totalHits, series)) {
        return;
    }
    const newChart = createSparklikes(element, data.totalHits, _.map(series, function (value) {
        return {
            name: value.name,
            data: _.map(value.data, point => point.count)
        };
    }));
    $element.data('sparklines', newChart);
    return newChart;
}

ko.bindingHandlers.sparklines = {
    init: function (element, valueAccessor, allBindings, viewModel, bindingContext) {
        showSparklinesInArticle(element, bindingContext.$data);

        ko.utils.domNodeDisposal.addDisposeCallback(element, function () {
            const $element = $(element),
                chart = $element.data('sparklines');
            if (chart) {
                chart.destroy();
                $element.removeData('sparklines');
            }
        });
    },
    update: function (element, valueAccessor, allBindings, viewModel, bindingContext) {
        showSparklinesInArticle(element, bindingContext.$data);
    }
};

function isEnabled () {
    const disabledFromSwitch = vars.model.switches()['facia-tool-sparklines'] === false,
          enabledFromParam = parseQueryParams().sparklines === 'please';

    return !disabledFromSwitch || enabledFromParam;
}

function allWebUrls (storyPackage) {
    const all = [];
    storyPackage.eachArticle(article => {
        const webUrl = getWebUrl(article);
        if (webUrl) {
            all.push(webUrl);
        }
    });
    return all;
}

function serializeParams (articles) {
    const params = articles.map(path => 'path=' + path);
    params.push('hours=24');
    params.push('interval=60');

    return params.join('&');
}

function reduceRequest (memo, articles) {
    return request({
        url: '/ophan/histogram?' + serializeParams(articles)
    })
    .then(data => {
        _.each(data, content => {
            memo[content.path] = content;
        });

        return memo;
    })
    .catch(function () {
        // Ignore errors from Ophan
        return {};
    });
}

function getHistogram (articles) {
    let chain = Promise.resolve({});

    // Allow max articles in one request or the GET request is too big
    const maxArticles = vars.CONST.sparksBatchQueue;
    _.each(_.range(0, articles.length, maxArticles), limit => {
        chain = chain.then(memo => reduceRequest(
            memo,
            articles.slice(limit, Math.min(limit + maxArticles, articles.length))
        ));
    });

    return chain;
}

function differential (collection) {
    const front = collection.front;
    const storyPackage = front.collection ? front.collection() : null;
    const sparklines = storyPackage ? storyPackage.sparklines : null;

    if (!sparklines || !sparklines.resolved) {
        return;
    }

    const data = sparklines.data();
    const newArticles = [];
    collection.eachArticle(article => {
        const webUrl = getWebUrl(article);
        if (webUrl && !data[webUrl]) {
            newArticles.push(webUrl);
        }
    });

    if (newArticles.length) {
        const storyPackageId = front.front();

        sparklines.resolved = false;
        sparklines.promise = getHistogram(newArticles)
        .then(newData => {
            if (storyPackageId === front.front()) {
                _.each(newArticles, webUrl => {
                    data[webUrl] = newData[webUrl];
                });
                sparklines.data(data);
                sparklines.resolved = true;
                return data;
            }
        });

        return front.sparklines.promise;
    }
}

function loadSparklinesForPackage (storyPackage) {
    if (!isEnabled()) {
        return;
    }

    if (!storyPackage.sparklines) {
        storyPackage.sparklines = {
            data: ko.observable({}),
            resolved: false
        };
    }

    storyPackage.sparklines.resolved = false;
    storyPackage.sparklines.promise = storyPackage.loaded
    .then(() => {
        return getHistogram(
            allWebUrls(storyPackage)
        ).then(data => {
            storyPackage.sparklines.data(data);
            storyPackage.sparklines.resolved = true;
            return data;
        });
    });
}

function startPolling () {
    if (!pollingId) {
        const period = vars.CONST.sparksRefreshMs || 60000;
        pollingId = setInterval(() => {
            _.each(subscribedPackages, storyPackage => {
                loadSparklinesForPackage(storyPackage, true);
            });
        }, period);
    }
}

function stopPolling () {
    if (pollingId) {
        clearInterval(pollingId);
        pollingId = null;
    }
}

function subscribe (widget) {
    if (subscribedPackages.length === 0) {
        startPolling();
        mediator.on('collection:populate', differential);
    }
    subscribedPackages.push(widget);
    loadSparklinesForPackage(widget);
}

function unsubscribe (widget) {
    subscribedPackages = _.without(subscribedPackages, widget);
    if (subscribedPackages.length === 0) {
        stopPolling();
        mediator.off('collection:populate', differential);
    }
}

export {
    subscribe,
    unsubscribe,
    isEnabled
};
