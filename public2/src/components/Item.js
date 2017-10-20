import React from 'react';

export class Item extends React.Component {
    onDragStart = (ev) => {
        const { id } = this.props;
        
        ev.dataTransfer.setData("application/json", JSON.stringify({ id }));
    }

    render() {
        return <div className="item" draggable="true" onDragStart={this.onDragStart}>
            {this.props.id}
        </div>;
    }
}