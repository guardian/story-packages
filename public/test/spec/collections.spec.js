import $ from 'jquery';
import Page from 'test/utils/page';
import * as wait from 'test/utils/wait';

describe('Collections', function () {
    beforeEach(function (done) {
        this.testPage = new Page('/editorial?layout=latest,content:story-2', {}, done);
    });
    afterEach(function (done) {
        this.testPage.dispose(done);
    });

    it('/edits', function (done) {
        var testPage = this.testPage;

        insertInEmptyGroup()
        .then(insertAfterAnArticle)
        .then(insertOnTopOfTheList)
        .then(insertMetadataOnTopOfTheList)
        .then(moveFirstItemBelow)
        .then(removeItemFromGroup)
        .then(copyPasteAboveArticle)
        .then(done)
        .catch(done.fail);

        function insertInEmptyGroup () {
            return testPage.actions.edit(() => {
                return testPage.regions.latest().trail(1).dropTo(
                    testPage.regions.story().linking()
                );
            })
            .assertRequest(request => {
                expect(request.url).toEqual('/edits');
                expect(request.data.type).toEqual('Update');
                expect(request.data.update.after).toEqual(false);
                expect(request.data.update.draft).toEqual(false);
                expect(request.data.update.live).toEqual(true);
                expect(request.data.update.id).toEqual('story-2');
                expect(request.data.update.item).toEqual('internal-code/page/1');
                expect(request.data.update.itemMeta).toEqual({ group: '0' });
            })
            .respondWith({
                'story-2': {
                    live: [{
                        id: 'internal-code/page/1',
                        meta: {
                            group: 3
                        }
                    }]
                }
            })
            .done;
        }

        function insertAfterAnArticle () {
            return testPage.actions.edit(() => {
                return testPage.regions.latest().trail(2).dropTo(
                    testPage.regions.story().linking()
                );
            })
            .assertRequest(request => {
                expect(request.url).toEqual('/edits');
                expect(request.data.type).toEqual('Update');
                expect(request.data.update.after).toEqual(true);
                expect(request.data.update.draft).toEqual(false);
                expect(request.data.update.live).toEqual(true);
                expect(request.data.update.id).toEqual('story-2');
                expect(request.data.update.item).toEqual('internal-code/page/2');
                expect(request.data.update.itemMeta).toEqual({ group: '0' });
                expect(request.data.update.position).toEqual('internal-code/page/1');
            })
            .respondWith({
                'story-2': {
                    live: [{
                        id: 'internal-code/page/1'
                    }, {
                        id: 'internal-code/page/2'
                    }]
                }
            })
            .done;
        }

        function insertOnTopOfTheList () {
            return testPage.actions.edit(() => {
                return testPage.regions.latest().trail(3).dropTo(
                    testPage.regions.story().linking().trail(1)
                );
            })
            .assertRequest(request => {
                expect(request.url).toEqual('/edits');
                expect(request.data.type).toEqual('Update');
                expect(request.data.update.after).toEqual(false);
                expect(request.data.update.draft).toEqual(false);
                expect(request.data.update.live).toEqual(true);
                expect(request.data.update.id).toEqual('story-2');
                expect(request.data.update.item).toEqual('internal-code/page/3');
                expect(request.data.update.itemMeta).toEqual({ group: '0' });
                expect(request.data.update.position).toEqual('internal-code/page/1');
            })
            .respondWith({
                'story-2': {
                    live: [{
                        id: 'internal-code/page/3'
                    }, {
                        id: 'internal-code/page/1'
                    }, {
                        id: 'internal-code/page/2'
                    }]
                }
            })
            .done;
        }

        function insertMetadataOnTopOfTheList () {
            return testPage.actions.edit(() => {
                return testPage.regions.story().linking().trail(1).open()
                .then(trail => trail.toggleMetadata('showQuotedHeadline'))
                .then(trail => trail.save());
            })
            .assertRequest(request => {
                expect(request.url).toEqual('/edits');
                expect(request.data.type).toEqual('Update');
                expect('after' in request.data.update).toEqual(false);
                expect(request.data.update.draft).toEqual(false);
                expect(request.data.update.live).toEqual(true);
                expect(request.data.update.id).toEqual('story-2');
                expect(request.data.update.item).toEqual('internal-code/page/3');
                expect(request.data.update.itemMeta.showQuotedHeadline).toEqual(true);
                expect(request.data.update.position).toEqual('internal-code/page/3');
            })
            .respondWith({
                'story-2': {
                    live: [{
                        id: 'internal-code/page/3',
                        meta: {
                            showQuotedHeadline: true
                        }
                    }, {
                        id: 'internal-code/page/1'
                    }, {
                        id: 'internal-code/page/2'
                    }]
                }
            })
            .done;
        }

        function moveFirstItemBelow () {
            return testPage.actions.edit(() => {
                var storyPackage = testPage.regions.story();
                return storyPackage.linking().trail(1).dropTo(
                    storyPackage.linking().trail(3)
                );
            })
            .assertRequest(request => {
                expect(request.url).toEqual('/edits');
                expect(request.data.type).toEqual('Update');
                expect(request.data.update.after).toEqual(false);
                expect(request.data.update.draft).toEqual(false);
                expect(request.data.update.live).toEqual(true);
                expect(request.data.update.id).toEqual('story-2');
                expect(request.data.update.item).toEqual('internal-code/page/3');
                expect(request.data.update.itemMeta.showQuotedHeadline).toEqual(true);
                expect(request.data.update.position).toEqual('internal-code/page/2');
            })
            .respondWith({
                'story-2': {
                    live: [{
                        id: 'internal-code/page/1'
                    }, {
                        id: 'internal-code/page/3',
                        meta: {
                            showQuotedHeadline: true
                        }
                    }, {
                        id: 'internal-code/page/2'
                    }]
                }
            })
            .done;
        }

        function removeItemFromGroup () {
            return testPage.actions.edit(() => {
                return testPage.regions.story().linking().trail(2).remove();
            })
            .assertRequest(request => {
                expect(request.url).toEqual('/edits');
                expect(request.data.type).toEqual('Remove');
                expect(request.data.remove.draft).toEqual(false);
                expect(request.data.remove.live).toEqual(true);
                expect(request.data.remove.id).toEqual('story-2');
                expect(request.data.remove.item).toEqual('internal-code/page/3');
            })
            .respondWith({
                'story-2': {
                    live: [{
                        id: 'internal-code/page/1'
                    }, {
                        id: 'internal-code/page/2'
                    }]
                }
            })
            .done;
        }

        function copyPasteAboveArticle () {
            return testPage.actions.edit(() => {
                const regions = testPage.regions;
                return regions.latest().trail(5).copy()
                .then(() => regions.story().linking().trail(1).paste());
            })
            .assertRequest(request => {
                expect(request.url).toBe('/edits');
                expect(request.data.type).toBe('Update');
                expect(request.data.update).toEqual({
                    after: false,
                    live: true,
                    draft: false,
                    id: 'story-2',
                    item: 'internal-code/page/5',
                    position: 'internal-code/page/1',
                    itemMeta: { group: '0' }
                });
            })
            .respondWith({
                'story-2': {
                    live: [{
                        id: 'internal-code/page/5'
                    }, {
                        id: 'internal-code/page/1'
                    }, {
                        id: 'internal-code/page/2'
                    }]
                }
            })
            .done;
        }
    });

    it('copy to clipboard', function (done) {
        this.testPage.regions.latest().trail(5).copyToClipboard()
        .then(() => wait.ms(100))
        .then(() => {
            expect(this.testPage.regions.clipboard().trail(1).fieldText('headline')).toBe('Nothing happened for once');
        })
        .then(done)
        .catch(done.fail);
    });

    //Skipping due to issue with build flakyness
    xit('closes without saving', function (done) {
        this.testPage.regions.story().linking().trail(1).open()
        .then(trail => trail.type('headline', 'different'))
        .then(trail => trail.close())
        .then(() => {
            const trail = this.testPage.regions.story().linking().trail(1);
            expect(trail.fieldText('headline')).toBe('I won the elections');
            expect($('.editor', trail.dom).is(':visible')).toBe(false);
        })
        .then(done)
        .catch(done.fail);
    });
});
