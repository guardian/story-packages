import React from 'react';

import { Item } from './Item';

function handleDragOver(ev) {
    ev.preventDefault();
    ev.dataTransfer.dropEffect = "move";
}

function handleDrop(fn, ix) {
    return (ev) => {
        ev.preventDefault();
        
        const { item } = JSON.parse(ev.dataTransfer.getData("application/json"));
        fn(ix, item);
    }
}

function handleEvent(fn, ix) {
    return (ev) => {
        ev.preventDefault();
        fn(ix);
    }
}

export function Container({ ix, item, onDragEnter, onDragLeave, onDrop }) {
    return <div
        className="container"
        onDragOver={handleDragOver}
        onDragEnter={handleEvent(onDragEnter, ix)}
        onDragLeave={handleEvent(onDragLeave, ix)}
        onDrop={handleDrop(onDrop, ix)}
    >
        {item ? <Item draggable={true} item={item} /> : false}
    </div>;
}