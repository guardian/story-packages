import Extension from 'models/extension';
import {request} from 'modules/authed-ajax';
import CONST from 'constants/defaults';
import mediator from 'utils/mediator';

export default class extends Extension {
    constructor(baseModel) {
        super(baseModel);
        this.baseModel = baseModel;
        this.loaded = this.fetchPackages();
        this.pollPackages();
        this.pollingId;
    }

    fetchPackages() {
        return request({
            url: CONST.apiBase + '/story-packages/latest',
            data: {
                isHidden: this.baseModel.priority === 'training'
            }
        })
        .then(result => {
            return this.baseModel.latestPackages(result.response.results.map(result => {
                return {
                    name: result.packageName,
                    lastModify: result.lastModified,
                    id: result.packageId
                };
            }));
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
        super.dispose();
    };
}
