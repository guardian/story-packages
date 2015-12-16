import Mock from 'mock/generic-mock';

class StoryPackage extends Mock {
    constructor() {
        super(/story-package\/(.+)/, ['story']);
    }

    handle(req, data) {
        return data[req.urlParams.story];
    }
}

export default StoryPackage;
