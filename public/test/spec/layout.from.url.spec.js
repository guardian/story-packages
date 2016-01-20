import * as fromURL from 'utils/layout-from-url';

describe('utils/layout-from-url', function () {
    it('gets the default configuration', function () {
        expect(fromURL.get('test')).toEqual([
            { 'type': 'latest' },
            { 'type': 'content' },
            { 'type': 'packages' }
        ]);
    });

    it('gets the default configuration by path', function () {
        expect(fromURL.get('test', 'config')).toEqual([
            { 'type': 'latest' },
            { 'type': 'content' },
            { 'type': 'packages' }
        ]);
    });

    it('gets the configuration form URL', function () {
        expect(fromURL.get('test?layout=content,content:banana,treats:apple')).toEqual([
            {
                'type': 'content',
                'config': undefined
            }, {
                'type': 'content',
                'config': 'banana'
            }, {
                'type': 'treats',
                'config': 'apple'
            }
        ]);
    });

    it('gets an empty configuration', function () {
        expect(fromURL.get('test?layout=,,any')).toEqual([
            { 'type': 'content' },
            { 'type': 'content' },
            {
                'type': 'any',
                'config': undefined
            }
        ]);
    });

    it('ignores unknown parameters', function () {
        expect(fromURL.get('test?other=stuff')).toEqual([
            { 'type': 'latest' },
            { 'type': 'content' },
            { 'type': 'packages' }
        ]);
    });

    it('respects the content parameter', function () {
        expect(fromURL.get('test?storyPackage=banana')).toEqual([
            { 'type': 'latest' },
            {
                'type': 'content',
                'config': 'banana'
            },
            { 'type': 'packages' }
        ]);
    });

    it('gives priority to layout over front', function () {
        expect(fromURL.get('test?front=banana&layout=front:apple')).toEqual([
            {
                'type': 'front',
                'config': 'apple'
            }
        ]);
    });

    it('serializes a layout', function () {
        expect(fromURL.serialize([{
            type: 'latest',
            ignore: 'please'
        }, {
            type: 'content',
            config: 'banana'
        }])).toBe('latest,content:banana');
    });

    it('extract from an object', function () {
        expect(fromURL.get({
            storyPackage: 'banana'
        })).toEqual([{
            type: 'latest'
        }, {
            type: 'content',
            config: 'banana'
        }, {
            type: 'packages'
        }]);
    });
});
