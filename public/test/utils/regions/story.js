import group from 'test/utils/regions/group';
import $ from 'jquery';
import textInside from 'test/utils/text-inside';
import * as wait from 'test/utils/wait';

class Story {
    constructor(dom) {
        this.dom = dom;
    }

    linking() {
        return group(2, this.dom, this);
    }

    included() {
        return group(1, this.dom, this);
    }

    getPackageInSelector(id) {
        return $('.front-selector option[value=' + id + ']', this.dom);
    }

    getPackageInSelectorText(id) {
        return textInside(this.getPackageInSelector(id));
    }

    getSelectedPackageName() {
        return textInside('.front-selector option:selected');
    }

    selectPackage(index) {
        const selectorIndex = index + 1;
        const selector = $('.front-selector option:eq('+ selectorIndex +')');
        selector.prop('selected', true);
        return $('.front-actions', this.dom).click();
    }

    manageSelectedPackage() {
        $('.package-management', this.dom).click();
        return wait.event('manage:package');
    }

};

export default function (number = 1) {
    var dom = document.querySelectorAll('.collection-container')[number - 1].parentNode;
    return new Story(dom);
}
