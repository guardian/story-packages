'use strict';
/* global module: false, process: false */
module.exports = function (grunt) {

    require('time-grunt')(grunt);

    var options = {
        singleRun: grunt.option('single-run') !== false,
        color: grunt.option('color') !== false
    };

    // Load config and plugins (using jit-grunt)
    require('load-grunt-config')(grunt, {
        configPath: require('path').join(process.cwd(), 'grunt-configs'),
        data: options
    });

    // Default task
    grunt.registerTask('default', function () {
        grunt.task.run(['validate', 'test', 'bundle']);
    });

    /**
     * Validate tasks
     */
    grunt.registerTask('validate', ['clean','eslint']);

    /**
     * Test tasks
     */
    grunt.registerTask('test', function() {
        if (options.singleRun === false) {
            grunt.config.set('karma.options.singleRun', false);
            grunt.config.set('karma.options.autoWatch', true);
        }

        grunt.task.run('karma');
    });

    /**
     * Compile tasks
     */
    grunt.registerTask('bundle', function () {
        grunt.task.run(['clean', 'shell:packages', 'replace', 'cacheBust']);
    });
};
