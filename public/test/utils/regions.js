import alert from 'test/utils/regions/alert';
import clipboard from 'test/utils/regions/clipboard';
import front from 'test/utils/regions/front';
import story from 'test/utils/regions/story';
import latest from 'test/utils/regions/latest';
import packages from 'test/utils/regions/packages';
import breakingNewsModal from 'test/utils/regions/breaking-news-modal';

export default function install () {
    return {
        latest,
        front,
        story,
        clipboard,
        alert,
        breakingNewsModal,
        packages
    };
}
