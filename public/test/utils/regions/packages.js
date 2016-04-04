import {type} from 'test/utils/dom-nodes';
import $ from 'jquery';
import textInside from 'test/utils/text-inside';
import * as wait from 'test/utils/wait';

class Package {
    constructor(dom) {
        this.dom = dom;
    }

    createNewPackage(name) {
        $(this.dom.querySelector('.package-create')).click();
        type($('.createPackageName_input', this.dom), name);
        $(this.dom.querySelector('.createPackageSave')).click();
        return wait.event('package:created');
    }

    search(searchTerm) {
        type($('.packagesSearch_input', this.dom), searchTerm);
        const pendingSearch = wait.event('package:searched');
        return wait.ms(10).then(() => {
            return { search: pendingSearch };
        });
    }

    getPendingSearchMessage() {
        return textInside('.searchingPackages');
    }

    getSearchResultSize() {
        return $(this.dom.querySelector('.packagesResults')).children().length;
    }

    getSearchResultTitle(index) {
        const elem = $('.packagesResults .element__headline')[index];
        return textInside(elem);
    }

    editPackage(index) {
        return $($(this.dom.querySelector('.edit-package'))[index]).click();
    }

    deletePackage(index) {
        $($(this.dom.querySelector('.delete-package'))[index]).click();
        return wait.ms(200)
        .then(() => {
            $('.sendAlert').click();
            return wait.event('delete:package');
        });
    }

    renamePackage(index, newName) {
        return type($('.package-editor textarea', this.dom)[index], newName);
    }

    savePackage(index) {
        $($(this.dom.querySelector('.package--done'))[index]).click();
        return wait.event('update:package');
    }

    viewPackage(index){
        const elem = $(this.dom.querySelector('.packagesResults .article'))[index];
        return $(elem).click();
    }
}

export default function (number = 1) {
    var dom = document.querySelectorAll('.packages-manage')[number - 1].parentNode;
    return new Package(dom);
}
