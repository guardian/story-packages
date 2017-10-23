import React from 'react';

import { Item } from './Item';

export function ItemSearch({ items }) {
    return <div>
        {items.map((item, ix) => {
            const { inUse } = item;

            return <div key={ix} className={`container ${inUse ? "container--collapsed" : ""}`}>
                <Item draggable={!inUse} item={item} />
            </div>;
        })}
    </div>;
}