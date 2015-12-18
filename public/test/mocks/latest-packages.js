import Mock from 'mock/generic-mock';

class LatestPackages extends Mock {
    constructor() {
        super('/story-packages/latest');
    }

    handle(req, data) {
        return {
            results: data
        };
    }
}

export default LatestPackages;
