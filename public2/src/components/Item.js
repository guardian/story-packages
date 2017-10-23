import React from 'react';
import moment from 'moment';

export function Item({ item, draggable }) {
    function onDragStart(ev) {
        ev.dataTransfer.setData("application/json", JSON.stringify({ item }));
    }

    const now = moment();
    const publishTime = moment(item.webPublicationDate);
    const modifiedTime = moment(item.lastModified);

    const publishDiff = moment.duration(publishTime.diff(now)).humanize();
    const modifiedDiff = moment.duration(modifiedTime.diff(now)).humanize();
    
    return <div className="item" draggable={draggable} onDragStart={onDragStart}>
        <img className="item__thumbnail" src={item.thumbnail} height="50px" />
        <div className="item__data">
            <div className="item__headline">{item.title}</div>
            <div className="item__metadata">
                <div className="item__tone">{item.tone}</div>
                <div>
                    <span className="item__timestamp">{publishDiff}</span>
                    <span className="item__timestamp item__timestamp__modified">{modifiedDiff}</span>
                </div>
            </div>
        </div>
    </div>;
}