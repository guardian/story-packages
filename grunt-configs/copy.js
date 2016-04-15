module.exports = function() {
    return {
        static: {
            files: [{
                expand: true,
                src: ['**'],
                cwd: 'tmp/bundles',
                dest: 'tmp/riffraff/packages/static-story-packages/'
            }]
        },
        debian: {
            files: [{
                expand: true,
                src: ['story-packages*.deb'],
                cwd: 'target/',
                dest: 'tmp/riffraff/packages/story-packages/'
            }]
        },
        deploy: {
            files: [{
                expand: true,
                src: ['deploy.json'],
                cwd: 'conf/',
                dest: 'tmp/riffraff/'
            }]
        }
    };
};
