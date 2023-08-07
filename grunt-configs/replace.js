module.exports = function() {
    return {
        static: {
            options: {
                patterns: [{
                    match: 'jspm_packages/npm/font-awesome',
                    replacement: '/assets/jspm_packages/npm/font-awesome'
                }],
                usePrefix: false
            },
            files: [{
                expand: true,
                src: '*.js',
                cwd: 'public/story-packages/bundles',
                dest: 'public/story-packages/bundles/'
            }]
        }
    };
};
