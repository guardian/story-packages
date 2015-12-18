import MockCollections from 'mock/collection';
import MockSearch from 'mock/search';
import MockLatestPackages from 'mock/latest-packages';
import MockStoryPackage from 'mock/story-package';

export default function install ({
    latestPackages = {},
    fixCollections = {},
    fixArticles = {},
    storyPackages = {}
} = {}) {
    var all = {
        mockCollections: new MockCollections(),
        mockSearch: new MockSearch(),
        mockLatestPackages: new MockLatestPackages(),
        mockStoryPackage: new MockStoryPackage()
    };

    all.mockCollections.set(fixCollections);
    all.mockSearch.set(fixArticles.articlesData);
    all.mockSearch.latest(fixArticles.allArticles);
    all.mockLatestPackages.set(latestPackages);
    all.mockStoryPackage.set(storyPackages);

    return Object.assign(all, {
        dispose() {
            Object.keys(all).filter(name => name.indexOf('mock') === 0)
                .forEach(name => all[name].dispose());
        }
    });
}
