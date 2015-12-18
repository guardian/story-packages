import Page from 'test/utils/page';

describe('Collections', function () {
    beforeEach(function (done) {
        this.testPage = new Page('/editorial?layout=latest,front:story-1', {}, done);
    });
    afterEach(function (done) {
        this.testPage.dispose(done);
    });

    it('displays the correct timing', function () {
        expect(this.testPage.regions.front().collection(1).lastModified()).toMatch('1 day ago by Test');
    });
});
