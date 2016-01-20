import Page from 'test/utils/page';
import * as mockjax from 'test/utils/mockjax';
import * as wait from 'test/utils/wait';
import 'widgets/trail-editor.html!text';

describe('Alternate Drag', function () {
    beforeEach(function (done) {
        this.scope = mockjax.scope();
        this.testPage = new Page('/editorial?layout=latest,content:story-1', {}, done);
    });
    afterEach(function (done) {
        this.testPage.dispose(done);
    });

    it('replace article', function (done) {
        var mockScope = this.scope, testPage = this.testPage;
        openFirstArticle()
        .then(trail => trail.toggleMetadata('showBoostedHeadline'))
        .then(saveArticle)
        .then(openFirstArticle)
        .then(alternateDrag)
        .then(expectItemSwapped)
        .then(deleteEntireArticle)
        .then(done)
        .catch(done.fail);


        function openFirstArticle () {
            return testPage.regions.front().collection(1).group(1).trail(1).open();
        }
        function saveArticle (trail) {
            return testPage.actions.edit(() => {
                return trail.save();
            })
            .assertRequest(request => {
                expect(request.url).toBe('/edits');
                expect(request.data).toEqual({
                    type: 'Update',
                    update: {
                        live: true,
                        draft: false,
                        id: 'story-1',
                        item: 'internal-code/page/1',
                        position: 'internal-code/page/1',
                        itemMeta: {
                            showBoostedHeadline: true
                        }
                    }
                });
            })
            .respondWith({
                'story-1': {
                    live: [{
                        id: 'internal-code/page/1',
                        meta: {
                            showBoostedHeadline: true
                        }
                    }]
                }
            })
            .done;
        }
        function alternateDrag (trail) {
            // This action is making to consecutive requests
            var requestIndex = 0, requests = [], responses = [{
                'story-1': {
                    lastUpdated: (new Date()).toISOString(),
                    live: [{
                        id: 'internal-code/page/4',
                            meta: {
                                showBoostedHeadline: true
                            }
                    }, {
                        id: 'internal-code/page/1',
                            meta: {
                                showBoostedHeadline: true
                            }
                    }]
                }
            }, {
                'story-1': {
                    lastUpdated: (new Date()).toISOString() + 10,
                    live: [{
                        id: 'internal-code/page/4',
                            meta: {
                                showBoostedHeadline: true
                            }
                    }]
                }
            }];
            mockScope({
                url: '/edits',
                method: 'post',
                response: function (req) {
                    requests.push(JSON.parse(req.data));
                    this.responseText = responses[requestIndex];
                    testPage.mocks.mockCollections.set(this.responseText);
                    requestIndex += 1;
                }
            });

            return new Promise(resolve => {
                testPage.regions.latest().trail(4).dropTo(
                    trail.innerDroppable(),
                    true
                )
                .then(() => wait.ms(10))
                .then(() => {
                    expect(requests.length).toBe(2);
                    expect(requests[0]).toEqual({
                        type: 'Update',
                        update: {
                            live: true,
                            draft: false,
                            id: 'story-1',
                            item: 'internal-code/page/4',
                            position: 'internal-code/page/1',
                            after: false,
                            itemMeta: {
                                showBoostedHeadline: true
                            }
                        }
                    });
                    expect(requests[1]).toEqual({
                        type: 'Remove',
                        remove: {
                            live: true,
                            draft: false,
                            id: 'story-1',
                            item: 'internal-code/page/1'
                        }
                    });

                    resolve();
                });
            });
        }
        function expectItemSwapped () {
            mockScope.clear();
            const trail = testPage.regions.front().collection(1).group(1).trail(1);
            expect(trail.fieldText('headline')).toBe('Santa Claus is a real thing');
        }
        function deleteEntireArticle () {
            const trail = testPage.regions.front().collection(1).group(1).trail(1);
            return testPage.actions.edit(() => trail.remove())
            .assertRequest(request => {
                expect(request.url).toBe('/edits');
                expect(request.data).toEqual({
                    type: 'Remove',
                    remove: {
                        live: true,
                        draft: false,
                        id: 'story-1',
                        item: 'internal-code/page/4'
                    }
                });
                expect(testPage.regions.front().collection(1).group(1).isEmpty()).toBe(true);
            })
            .respondWith({
                'story-1': { live: [] }
            })
            .done;
        }
    });
});
