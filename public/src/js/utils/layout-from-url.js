import _ from 'underscore';
import parseQueryParams from 'utils/parse-query-params';

function get (override) {
    var columns = [{ 'type': 'latest' }, { 'type': 'content' }, { 'type': 'packages' }],
        queryParams = _.isObject(override) ? _.clone(override) : parseQueryParams(override),
        configFromURL = queryParams.layout;

    if (configFromURL) {
        columns = _.map(configFromURL.split(','), function (column) {
            if (!column) {
                return {
                    type: 'content'
                };
            }

            var parts = column.split(':');
            return {
                type: parts[0],
                config: parts[1]
            };
        });
    } else if (queryParams.storyPackage) {
        columns = [{ 'type': 'latest' }, {
            'type': 'content',
            'config': queryParams.storyPackage
        }, { 'type': 'packages' }];
    }

    return columns;
}

function serialize (layout) {
    return _.map(layout, function (column) {
        if ('config' in column) {
            return column.type + ':' + column.config;
        } else {
            return column.type;
        }
    }).join(',');
}

export {
    get,
    serialize
};
