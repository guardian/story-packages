import React from 'react';

import { Item } from './Item';

function handleDragOver(ev) {
    ev.preventDefault();
    ev.dataTransfer.dropEffect = "move";
}

function handleDrop(fn, ix) {
    return (ev) => {
        ev.preventDefault();
        
        const { id } = JSON.parse(ev.dataTransfer.getData("application/json"));
        fn(ix, id);
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
        {item ? <Item {...item} /> : false}
    </div>;
}