import dates from 'test/fixtures/dates';

var all = {
    'story-1': {
        name: 'First package',
        id: 'story-1',
        lastModify: dates.yesterday.toString(),
        lastModifyBy: 'test@user',
        lastModifyByName: 'Test',
        isHidden: false
    },
    'story-2': {
        name: 'Second package',
        id: 'story-2',
        lastModify: dates.yesterday.toString(),
        lastModifyBy: 'test@user',
        lastModifyByName: 'Test',
        isHidden: false
    },
    'story-3': {
        name: 'New package',
        id: 'story-3',
        lastModify: dates.yesterday.toString(),
        lastModifyBy: 'test@user',
        lastModifyByName: 'Test',
        isHidden: false
    }
};

var latest = Object.keys(all).map(key => {
    var storyPackage = all[key];
    return {
        packageId: storyPackage.id,
        packageName: storyPackage.name,
    };
});

export default {
    all, latest
};
