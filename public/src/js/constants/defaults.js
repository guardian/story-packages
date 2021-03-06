/* eslint key-spacing: 0 */
export default {
    editions: ['uk', 'us', 'au'],

    environmentUrlBase: {
        'prod': 'http://theguardian.com/',
        'code': 'http://m.code.dev-theguardian.com/'
    },

    headlineLength: 200,
    restrictedHeadlineLength: 90,

    restrictHeadlinesOn: [],

    detectPendingChangesInClipboard: 4000,

    defaultPriority: 'editorial',

    filterTypes: {
        section: { display: 'in section:', param: 'section', path: 'sections', placeholder: 'e.g. news' },
        tag:     { display: 'with tag:',   param: 'tag',     path: 'tags',     placeholder: 'e.g. sport/triathlon' }
    },

    searchPageSize:        50,

    capiBatchSize:         10,

    maxSlideshowImages:    5,

    collectionsPollMs:     10000,
    latestArticlesPollMs:  30000,
    configSettingsPollMs:  30000,
    cacheExpiryMs:         60000,
    pubTimeRefreshMs:      30000,
    searchDebounceMs:        300,
    packagesPollMs:      3600000,
    failsBeforeError:          2,

    highFrequencyPaths:    ['uk', 'us', 'au', 'uk/sport', 'us/sport', 'au/sport'],

    mainDomain:            'www.theguardian.com',
    mainDomainShort:       'theguardian.com',

    apiBase:               '',
    apiSearchBase:         '/api/preview',
    apiLiveBase:           '/api/live',
    apiSearchParams:       [
        'show-elements=video',
        'show-tags=all',
        'show-fields=' + [
            'internalPageCode',
            'isLive',
            'firstPublicationDate',
            'scheduledPublicationDate',
            'headline',
            'trailText',
            'byline',
            'thumbnail',
            'secureThumbnail',
            'liveBloggingNow',
            'membershipAccess'
        ].join(',')
    ].join('&'),

    draggableTypes: {
        configCollection: 'config-collection'
    },


    frontendApiBase:       '/frontend',

    reauthPath:            '/login/status',
    reauthInterval:        60000 * 10, // 10 minutes
    reauthTimeout:         60000,

    imageCdnDomain:        '.guim.co.uk',
    imageCdnDomainExpr:    /^https?:\/\/(.*)\.guim\.co\.uk\//,
    imgIXDomainExpr:       /^https?:\/\/i\.guim\.co\.uk\/img\/static\//,
    staticImageCdnDomain:  'https://static.guim.co.uk/',
    previewBase:           'http://preview.gutools.co.uk',
    viewerHost:            'viewer.gutools.co.uk',

    latestSnapPrefix:      'Latest from ',

    ophanBase:             'http://dashboard.ophan.co.uk/summary',
    ophanFrontBase:        'http://dashboard.ophan.co.uk/info-front?path=',

    internalPagePrefix:    'internal-code/page/',

    sparksBatchQueue:      15
};
