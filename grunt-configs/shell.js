module.exports = function() {
    return {
        packages: {
            command: 'jspm bundle ' + [
                'setup', 'main',
                'story-packages/widgets/columns/story-package',
                'story-packages/widgets/columns/latest',
                'story-packages/widgets/columns/packages',
                'story-packages/widgets/collection',
                'story-packages/widgets/clipboard',
                'story-packages/widgets/columns/fronts-standalone-clipboard',
                'story-packages/widgets/autocomplete',
                'story-packages/widgets/copy-paste-articles',
                'story-packages/widgets/fetch-latest-packages',
                'story-packages/widgets/display-alerts',
                'story-packages/widgets/sparklines-trails'
            ].join(' + ') + ' tmp/bundles/packages.js'
        }
    };
};
