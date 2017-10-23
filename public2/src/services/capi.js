const ROOT = "https://internal.content.guardianapis.com";

function getTone(item) {
    const tag = item.tags.find(({ type }) => type === "tone");

    if(tag) {
        return tag.webTitle;
    }

    return "";
}

export function getLatestItems() {
    return fetch(`${ROOT}/search?page-size=20&order-by=newest&show-fields=thumbnail,internalComposerCode,lastModified&show-tags=all`)
        .then(r => r.json())
        .then(({ response }) => {
            const { results } = response;
            
            return results.map(result => {
                return {
                    id: result.fields.internalComposerCode,
                    path: result.id,
                    title: result.webTitle,
                    tone: getTone(result),
                    thumbnail: result.fields.thumbnail,
                    webPublicationDate: result.webPublicationDate,
                    lastModified: result.fields.lastModified
                }
            });
        });
}

export function getPackages(search) {
    return fetch(`${ROOT}/packages?q=${encodeURIComponent(search)}`)
        .then(r => r.json())
        .then(({ response }) => {
            const { results } = response;

            return results.map(({ packageId, packageName, lastModified }) => {
                return {
                    id: packageId,
                    name: packageName,
                    lastModified
                }
            });
        });
}