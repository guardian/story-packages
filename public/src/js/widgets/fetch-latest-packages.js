import Extension from 'models/extension';
import {request} from 'modules/authed-ajax';
import CONST from 'constants/defaults';

export default class extends Extension {
    constructor(baseModel) {
        super(baseModel);
        this.baseModel = baseModel;
        this.fetchPackages();
        this.pollPackages();
    }

    fetchPackages() {
        var that = this;
        return request({
            url: CONST.apiBase + '/story-packages/latest'
        })
        .then(response => {
            that.baseModel.latestPackages(response.results);
        })
        .catch(function () {
            throw new Error('latest packages endpoint is invalid or unavailable');
        });
    }

    pollPackages() {
        setInterval(() => this.fetchPackages(), CONST.packagesPollMs);
    };

}

