import ko from 'knockout';
import _ from 'underscore';
import ColumnWidget from 'widgets/column-widget';
import Front from 'models/config/front';
import mediator from 'utils/mediator';
import modalDialog from 'modules/modal-dialog';

export default class Package extends ColumnWidget {

    constructor(params, element) {
        super(params, element);
        this.allPackages;
        this.searchedPackages = ko.observableArray();
        this.searchTerm = ko.observable('');
        this.populateAllPackages(this.baseModel.state());
        this.subscribeOn(this.baseModel.state, this.populateAllPackages);
        this.subscribeOn(this.searchTerm, this.search);
        this.creatingPackage = ko.observable(false);
        this.displayName = ko.observable();
    };

    populateAllPackages(state) {
        this.allPackages = _.values(state.config.collections).map(collection => {
            return {
                displayName: collection.displayName,
                searchTerm: collection.displayName.toLowerCase()
            };
        });
    };

    search() {
        var lowerCaseSearchTerm = this.searchTerm().toLowerCase().match(/\S+/g);
        this.searchedPackages(
            _.filter(this.allPackages, existingPackage => {
                return existingPackage.searchTerm.indexOf(lowerCaseSearchTerm) !== -1;
            })
        );
    }

    createPackage() {
        this.creatingPackage(!this.creatingPackage());
    }

    displayPackage(chosenPackage) {
        mediator.emit('find:package', chosenPackage.displayName);
    }

    savePackage() {
        var front = new Front({
            priority: this.baseModel.priority,
            isHidden: this.baseModel.priority === 'training',
            id: this.displayName()
        });
        var newPackage = front.createCollection();
        newPackage.meta.type = 'story-package';
        newPackage.meta.displayName = this.displayName();

        var after = () => {
            this.creatingPackage(false);
            this.displayName(null);
            mediator.emit('find:package', newPackage.meta.displayName);
        };
        return newPackage.save().then(after).catch(after);
    }

    displayRemoveModal(storyPackage) {
        return modalDialog.confirm({
            name: 'confirm_package_delete',
            data: {
                packageName: storyPackage.displayName
            }
        })
        .then(() => removePackage(storyPackage))
        .catch(() => {
            return;
        });

    }
}

function removePackage(storyPackage) {
    // TODO
    return;
};

