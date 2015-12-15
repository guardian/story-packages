import Extension from 'models/extension';
import {request} from 'modules/authed-ajax';
import CONST from 'constants/defaults';
import mediator from 'utils/mediator';

export default class extends Extension {
    constructor(baseModel) {
        super(baseModel);
        this.baseModel = baseModel;
        this.fetchPackages();
        this.pollPackages();
        this.pollingId;
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
            mediator.emit('packages:alert', 'Failed to fetch latest packages');
        });
    }

    pollPackages() {
        this.pollingId = setInterval(() => this.fetchPackages(), CONST.packagesPollMs);
    };

    dispose() {
        clearInterval(this.pollingId);
        this.pollingId = null;
    };

}

