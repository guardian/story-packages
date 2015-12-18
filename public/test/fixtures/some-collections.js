import dates from 'test/fixtures/dates';

export default {
    'story-1': {
        lastUpdated: dates.yesterday.toISOString(),
        live: [{
            id: 'internal-code/page/1',
            frontPublicationDate: dates.yesterday.getTime()
        }],
        updatedBy: 'Test'
    }
};
