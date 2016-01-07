import Article from 'models/collections/article';
import serialize from 'utils/serialize-article-meta';
import * as contentApi from 'modules/content-api';

describe('Serialize Article Meta', function () {
    beforeEach(function () {
        spyOn(contentApi, 'decorateItems');
    });

    it('trim and sanitize strings', function () {
        const article = new Article({
            meta: {
                headline: '   <script>\nalert("banana")\n</script>  Headline   with \t\n spaces  '
            }
        });

        expect(serialize(article)).toEqual({
            headline: 'Headline with spaces'
        });
    });

    it('ignores values equal to their default', function () {
        const article = new Article({
            meta: {
                showQuotedHeadline: true,
                headline: 'meta defaults'
            },
            webUrl: 'something',
            fields: {
                headline: 'headline from CAPI'
            },
            frontsMeta: {
                defaults: {
                    showQuotedHeadline: true
                }
            },
            group: {}
        }, true);

        expect(serialize(article)).toEqual({
            headline: 'meta defaults'
        });
    });

    it('ignores values equal to the overridden field', function () {
        const article = new Article({
            meta: {
                trailText: 'trail text',
                headline: 'same trail text as CAPI'
            },
            webUrl: 'something',
            fields: {
                headline: 'headline from CAPI',
                trailText: 'trail text'
            }
        }, true);

        expect(serialize(article)).toEqual({
            headline: 'same trail text as CAPI'
        });
    });

    it('converts number to strings', function () {
        const article = new Article({
            meta: {
                imageSrcWidth: 100
            }
        });

        expect(serialize(article)).toEqual({
            imageSrcWidth: '100'
        });
    });

    it('serializes supporting links', function () {
        const article = new Article({
            group: {},
            meta: {
                supporting: [{
                    id: 'first'
                }, {
                    id: 'second'
                }]
            }
        });

        expect(serialize(article)).toEqual({
            supporting: [{ id: 'first' }, { id: 'second' }]
        });
    });

    it('ignore empty meta data', function () {
        const article = new Article({
            meta: {
                headline: 'headline from CAPI'
            },
            webUrl: 'anywhere',
            fields: {
                headline: 'headline from CAPI'
            }
        }, true);

        expect(serialize(article)).toBeUndefined();
    });
});
