import $ from 'jquery';
import mockjax from 'jquery-mockjax';

mockjax($, window);
$.mockjaxSettings.logging = false;
$.mockjaxSettings.responseTime = 50;

export default {
    defaults: {
        env: 'test',
        editions: ['uk', 'us', 'au'],
        email: 'someone@theguardian.com',
        avatarUrl: '',
        lowFrequency: 60,
        highFrequency: 2,
        standardFrequency: 5,
        fixedContainers: [{ 'name':'fixed/test' }],
        dynamicContainers: [{ 'name':'dynamic/test' }],
        switches: {
            'story-packages-disable': false,
            'facia-tool-draft-content': true
        }
    }
};
