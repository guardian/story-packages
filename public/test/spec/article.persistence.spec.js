import Article from 'models/collections/article';
import Collection from 'models/collections/collection';
import persistence from 'models/collections/persistence';
import Group from 'models/group';
import MockCollections from 'mock/collection';
import * as authedAjax from 'modules/authed-ajax';
import * as contentApi from 'modules/content-api';

describe('Article Persistence', function () {
    beforeEach(function () {
        this.mock = new MockCollections();
    });
    afterEach(function () {
        this.mock.dispose();
    });

    it('ignores saving in the clipboard', function (done) {
        const clipboard = new Group({
            parentType: 'Clipboard',
            keepCopy: true,
            front: null
        });

        const article = new Article({
            group: clipboard
        });

        persistence.article.save(article)
        .then(result => {
            expect(result).toBe(false);
        })
        .then(() => {
            clipboard.dispose();
            article.dispose();
        })
        .then(done)
        .catch(done.fail);
    });

    //Skipping due to issue with build flakyness
    xit('updates the parent collection', function (done) {
        let request;
        spyOn(authedAjax, 'updateCollections').and.callFake(actualRequest => {
            request = actualRequest;
            return Promise.resolve();
        });

        loadMockCollection({
            id: 'banana'
        }, this.mock).then(({collection, article}) => {
            return persistence.article.save(article)
            .then(() => {
                expect(request).toEqual({
                    update: {
                        collection: collection,
                        item: 'banana',
                        position: 'banana',
                        itemMeta: {
                            group: '0'
                        },
                        mode: 'live'
                    }
                });
            })
            .then(() => {
                collection.dispose();
                article.dispose();
            });
        })
        .then(done)
        .catch(done.fail);
    });
});

function loadMockCollection (articleDefinition, mock) {
    spyOn(contentApi, 'decorateItems').and.callFake(() => Promise.resolve());
    mock.set({
        'fruits': {
            live: [articleDefinition],
            lastUpdated: (new Date()).toISOString()
        }
    });
    const collection = new Collection({
        id: 'fruits',
        type: 'fixed/slow',
        front: {
            getCollectionList: raw => raw.live,
            confirmSendingAlert: () => false,
            mode: () => 'live'
        }
    });
    collection.registerElement(null);

    return collection.loaded.then(() => {
        expect(collection.groups.length).toBe(1);
        const group = collection.groups[0];

        expect(group.items().length).toBe(1);
        const article = group.items()[0];

        return {collection, article};
    });
}
