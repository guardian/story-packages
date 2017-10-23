import React from 'react';

import { Container } from './Container';

function insert(ix, value, items) {
    const ret = items.slice();

    ret[ix] = value;

    if(items[ix] !== null) {
        for(let i = ix; i < items.length; i++) {
            if(items[i] === null)
                break;

            ret[i + 1] = items[i];
        }
    }

    return ret.slice(0, items.length);
}

export class Package extends React.Component {
    constructor(props) {
        super(props);

        this.state = { items: props.items };
    }

    componentWillReceiveProps(nextProps) {
        this.setState({ items: nextProps.items });
    }

    onDragEnter = (ix) => {
        const items = insert(ix, null, this.props.items);
        this.setState({ items });
    }

    onDragLeave = (ix) => {
        const { items } = this.props;
        this.setState({ items });
    }

    onDrop = (ix, newItem) => {
        const existingIx = this.props.items.findIndex(item => item ? item.id === newItem.id : false);
        let items = this.props.items.slice();

        if(existingIx !== -1) {
            items[existingIx] = null;
        }

        items = insert(ix, newItem, items);
        this.props.updateFn(items);
    }

    renderContainer = ([item, ix]) => {
        return <Container
            key={ix}
            ix={ix}
            item={item}
            onDragEnter={this.onDragEnter}
            onDragLeave={this.onDragLeave}
            onDrop={this.onDrop}
        />;
    };

    render() {
        const indexedItems = this.state.items.map((item, ix) => [item, ix]);

        const included = indexedItems.slice(0, this.props.size);
        const linked = indexedItems.slice(this.props.size);

        return <div>
            <div className="container-grid">
                {included.map(this.renderContainer)}
            </div>

            <hr />
            <div className="container-grid">
                {linked.map(this.renderContainer)}
            </div>
        </div>;
    }
}