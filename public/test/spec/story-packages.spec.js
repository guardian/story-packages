import * as mockjax from 'test/utils/mockjax';
import Page from 'test/utils/page';

describe('StoryPackages', function () {

    beforeEach(function (done) {
        this.scope = mockjax.scope();
        this.scope({
            url: '/story-packages/create',
            status: 200,
            responseText: {
                'id': 'story-4',
                'name': 'package'
            }
        },
        {
            url: '/story-packages/search/package',
            status: 200,
            responseText: [
                {
                    'id': 'story-4',
                    'name': 'package'
                },
                {
                    'id': 'story-3',
                    'name': 'New package'
                }
            ]
        },
        {
            url: '/story-packages/search/story-2',
            status: 200,
            responseText: [
                {
                    'id': 'story-2',
                    'name': 'Second package'
                }
            ]
        },
        {
            url: '/story-packages/edit/story-3',
            status: 200,
            responseText: {
                'id': 'story-3',
                'name': 'new name'
            }

        },
        {
            url: '/story-package/story-3',
            status: 200
        },
        {
            url: '/story-packages/search/new',
            status: 200,
            responseText: [
                {
                    'id': 'story-3',
                    'name': 'New package'
                }
            ]
        });

        this.testPage = new Page('/editorial?layout=latest,content:story-2,packages', {}, done);
    });
    afterEach(function (done) {
        this.scope.clear();
        this.testPage.dispose(done);
    });

    it('creates a new package', function (done) {
        var testPage = this.testPage;
        const packageName = 'package';
        return testPage.regions.packages().createNewPackage(packageName)
        .then(() => {
            expect(testPage.regions.story().getSelectedPackageName()).toBe(packageName);
            expect(testPage.regions.story().getPackageInSelectorText('story-4')).toBe(packageName);
            return;
        })
        .then(done)
        .catch(done.fail);
    });

    it('searches for packages', function (done) {
        var testPage = this.testPage;

        testPage.regions.packages().search('package')
        .then((waiting) => {
            expect(testPage.regions.packages().getPendingSearchMessage()).toBe('Searching...');
            return waiting.search;
        })
        .then(() => {
            expect(testPage.regions.packages().getSearchResultSize()).toBe(2);
            expect(testPage.regions.packages().getSearchResultTitle(0)).toBe('package');
            expect(testPage.regions.packages().getSearchResultTitle(1)).toBe('New package');
            return;
        })
        .then(done)
        .catch(done.fail);
    });

    it('views a package', function (done) {
        var testPage = this.testPage;

        testPage.regions.packages().search('New')
        .then(waiting => waiting.search)
        .then(() => {
            testPage.regions.packages().viewPackage(0);
            expect(testPage.regions.story().getSelectedPackageName()).toBe('New package');
            return;
        })
        .then(done)
        .catch(done.fail);
    });

    it('manages latest packages', function (done) {
        var testPage = this.testPage;
        testPage.regions.story().selectPackage(0);
        testPage.regions.story().manageSelectedPackage()
        .then(() => {
            expect(testPage.regions.packages().getSearchResultSize()).toBe(1);
            expect(testPage.regions.packages().getSearchResultTitle(0)).toBe('Second package');
        })
        .then(done)
        .catch(done.fail);
    });

    it('renames a package', function (done) {
        var testPage = this.testPage;
        const newName = 'new name';

        testPage.regions.packages().search('New')
        .then(waiting => waiting.search)
        .then(() => {
            testPage.regions.packages().editPackage(0);
            testPage.regions.packages().renamePackage(0, newName);
            return testPage.regions.packages().savePackage(0);

        })
        .then(() => {
            expect(testPage.regions.packages().getSearchResultTitle(0)).toBe(newName);
            expect(testPage.regions.story().getPackageInSelectorText('story-3')).toBe(newName);
            return;
        })
        .then(done)
        .catch(done.fail);
    });

    it('deletes a package', function (done) {
        var testPage = this.testPage;

        testPage.regions.packages().search('New')
        .then(waiting => waiting.search)
        .then(() => {
            return testPage.regions.packages().deletePackage(0);
        })
        .then(() => {
            expect(testPage.regions.packages().getSearchResultSize()).toBe(0);
            expect(testPage.regions.story().getPackageInSelector('story-3').length).toBe(0);
            return;
        })
        .then(done)
        .catch(done.fail);
    });
});

