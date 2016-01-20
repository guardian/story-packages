export function thumbnail() {
    const meta = this.meta,
        fields = this.fields,
        state = this.state;

    if (meta.imageReplace() && meta.imageSrc()) {
        return meta.imageSrc();
    } else if (meta.imageCutoutReplace()) {
        return meta.imageCutoutSrc() || state.imageCutoutSrcFromCapi() || fields.secureThumbnail() || fields.thumbnail();
    } else {
        return fields.secureThumbnail() || fields.thumbnail();
    }
}
