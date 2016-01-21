import group from 'test/utils/regions/group';

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

    pacakgeSelector() {
        return this.dom.querySelector('.front-selector');
    }
}

export default function (number = 1) {
    var dom = document.querySelectorAll('.collection-container')[number - 1].parentNode;
    return new Story(dom);
}
