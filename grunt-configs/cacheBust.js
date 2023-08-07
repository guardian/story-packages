module.exports = function() {
    return {
        static: {
            options: {
                baseDir: 'public/story-packages/bundles/',
                assets: ['*.js'],
                deleteOriginals: true,
                jsonOutput: true,
                jsonOutputFilename: 'assets-map.json'
            },
            src: ['index.html']
        }
    };
};
