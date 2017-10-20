import React from 'react';

import { Item } from './Item';

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

export class ContainerGrid extends React.Component {
    constructor(props) {
        super(props);
        this.state = {
            items: props.items
        };
    }

    componentWillReceiveProps(nextProps) {
        this.setState({ items: nextProps.items });
    }

    onDragOver= (ev) => {
        ev.preventDefault();
        ev.dataTransfer.dropEffect = "move";
    }

    onDragEnter = (ix) => {
        return (ev) => {
            ev.preventDefault();

            const items = insert(ix, null, this.props.items);
            this.setState({ items });
        };
    }

    onDragLeave = (ix) => {
        return (ev) => {
            ev.preventDefault();

            const { items } = this.props;
            this.setState({ items: items });
        }
    }

    onDrop = (ix) => {
        return (ev) => {
            ev.preventDefault();

            const data = JSON.parse(ev.dataTransfer.getData("application/json"));
            const items = insert(ix, { id: data.id }, this.props.items);

            this.props.updateFn(items);
        };
    }

    render() {
        return <div className="container-grid">
            {this.state.items.map((item, ix) => {
                return <div
                    key={ix}
                    className="container"
                    onDragOver={this.onDragOver}
                    onDragEnter={this.onDragEnter(ix)}
                    onDragLeave={this.onDragLeave(ix)}
                    onDrop={this.onDrop(ix)}
                >
                    {item ? <Item {...item} /> : false}
                </div>;
            })}
        </div>;
    }
}