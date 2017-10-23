import React from 'react';

export function Item({ id }) {
    function onDragStart(ev) {
        ev.dataTransfer.setData("application/json", JSON.stringify({ id }));
    }

    return <div className="item" draggable="true" onDragStart={onDragStart}>
        {id}
    </div>;
}

// export class Item extends React.Component {
//     constructor(props) {
//         super(props);
//         this.state = { dragging: false };
//     }

//     onDragStart = (ev) => {
//         const { id } = this.props;
        
        
//         this.setState({ dragging: true });
//     }

//     onDragEnd = (ev) => {
//         // this.setState({ dragging: false });
//     }

//     render() {
//         const { id, hideOnDrag } = this.props;
//         const { dragging } = this.state;

//         const clazz = `item ${hideOnDrag && dragging ? "item--hidden" : ""}`;

        
//     }
// }