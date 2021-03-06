export default Object.freeze([
    {
        key: 'headline',
        editable: true,
        ifState: 'enableContentOverrides',
        label: 'headline',
        type: 'text',
        maxLength: 120
    },
    {
        key: 'trailText',
        editable: true,
        ifState: 'enableContentOverrides',
        label: 'trail text',
        type: 'text'
    },
    {
        key: 'byline',
        editable: true,
        ifState: 'enableContentOverrides',
        visibleWhen: 'showByline',
        label: 'byline',
        type: 'text'
    },
    {
        key: 'customKicker',
        editable: true,
        visibleWhen: 'showKickerCustom',
        label: 'custom kicker',
        type: 'text'
    },
    {
        key: 'href',
        label: 'special link URL',
        type: 'text'
    },
    {
        key: 'imageSrc',
        editable: true,
        dropImage: true,
        visibleWhen: 'imageReplace',
        label: 'replacement image URL',
        validator: {
            fn: 'validateImage',
            params: {
                src: 'imageSrc',
                width: 'imageSrcWidth',
                height: 'imageSrcHeight',
                origin: 'imageSrcOrigin',
                options: {
                    maxWidth: 1000,
                    minWidth: 400,
                    widthAspectRatio: 5,
                    heightAspectRatio: 3
                }
            }
        },
        type: 'text'
    },
    {
        key: 'imageSrcWidth',
        visibleWhen: 'imageReplace',
        label: 'replacement image width',
        type: 'text'
    },
    {
        key: 'imageSrcHeight',
        visibleWhen: 'imageReplace',
        label: 'replacement image height',
        type: 'text'
    },
    {
        key: 'imageSrcOrigin',
        visibleWhen: 'imageReplace',
        label: 'replacement image origin',
        type: 'text'
    },
    {
        key: 'imageCutoutSrc',
        editable: true,
        dropImage: true,
        visibleWhen: 'imageCutoutReplace',
        label: 'replacement cutout image URL',
        validator: {
            fn: 'validateImage',
            params: {
                src: 'imageCutoutSrc',
                width: 'imageCutoutSrcWidth',
                height: 'imageCutoutSrcHeight',
                origin: 'imageCutoutSrcOrigin',
                options: {
                    maxWidth: 1000,
                    minWidth: 400
                }
            }
        },
        type: 'text'
    },
    {
        key: 'imageCutoutSrcWidth',
        visibleWhen: 'imageCutoutReplace',
        label: 'replacement cutout image width',
        type: 'text'
    },
    {
        key: 'imageCutoutSrcHeight',
        visibleWhen: 'imageCutoutReplace',
        label: 'replacement cutout image height',
        type: 'text'
    },
    {
        key: 'imageCutoutSrcOrigin',
        visibleWhen: 'imageCutoutReplace',
        label: 'replacement cutout image origin',
        type: 'text'
    },
    {
        key: 'showLivePlayable',
        editable: true,
        ifState: 'isLiveBlog',
        label: 'show updates',
        type: 'boolean'
    },
    {
        key: 'showMainVideo',
        editable: true,
        ifState: 'hasMainVideo',
        singleton: 'images',
        label: 'show video',
        type: 'boolean'
    },
    {
        key: 'showBoostedHeadline',
        editable: true,
        label: 'large headline',
        type: 'boolean'
    },
    {
        key: 'showQuotedHeadline',
        editable: true,
        label: 'quote headline',
        type: 'boolean'
    },
    {
        key: 'showByline',
        editable: true,
        label: 'byline',
        type: 'boolean'
    },
    {
        key: 'imageCutoutReplace',
        editable: true,
        singleton: 'images',
        label: 'cutout image',
        type: 'boolean'
    },
    {
        key: 'imageReplace',
        editable: true,
        singleton: 'images',
        label: 'replace image',
        omitIfNo: 'imageSrc',
        type: 'boolean'
    },
    {
        key: 'imageHide',
        editable: true,
        singleton: 'images',
        label: 'hide image',
        type: 'boolean'
    },
    {
        key: 'showKickerTag',
        editable: true,
        singleton: 'kicker',
        label: 'kicker',
        labelState: 'primaryTag',
        type: 'boolean'
    },
    {
        key: 'showKickerSection',
        editable: true,
        singleton: 'kicker',
        label: 'kicker',
        labelState: 'sectionName',
        type: 'boolean'
    },
    {
        key: 'showKickerCustom',
        editable: true,
        singleton: 'kicker',
        label: 'custom kicker',
        labelMeta: 'customKicker',
        type: 'boolean'
    },
    {
        key: 'snapUri',
        label: 'snap target',
        type: 'text'
    },
    {
        key: 'snapType',
        label: 'snap type',
        type: 'text'
    },
    {
        key: 'snapCss',
        label: 'snap class',
        type: 'text'
    }
]);
