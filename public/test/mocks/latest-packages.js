import Mock from 'mock/generic-mock';

class LatestPackages extends Mock {
    constructor() {
        super('/story-packages/latest');
    }

    handle(req, data) {
        return {
            response: {
                results: data
            }
        };
    }
}

export default LatestPackages;
