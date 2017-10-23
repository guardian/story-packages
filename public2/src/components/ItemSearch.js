import React from 'react';

import { Item } from './Item';

export function ItemSearch({ items }) {
    return <div>
        <h3>Items</h3>
        {items.map(({ id, inUse }, ix) =>
            <div key={ix} className={`container ${inUse ? "container--collapsed" : ""}`}>
                <Item id={id} draggable={!inUse} />
            </div>
        )}
    </div>;
}