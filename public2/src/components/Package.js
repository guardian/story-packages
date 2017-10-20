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
        this.setState({ items: items });
    }

    onDrop = (ix, id) => {
        const items = insert(ix, { id }, this.props.items);
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
            <h3>Package</h3>
            <div className="container-grid">
                {included.map(this.renderContainer)}
            </div>

            <h3>Linked</h3>
            <div className="container-grid">
                {linked.map(this.renderContainer)}
            </div>
        </div>;
    }
}