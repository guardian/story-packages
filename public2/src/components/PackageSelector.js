import React from 'react';
import moment from 'moment';

function PackageLink({ name, lastModified }) {
    const modifiedDiff = moment.duration(moment(lastModified).diff(moment())).humanize();

    return <li className="package-list__item">
        <div>{name}</div>
        <div className="package-list__timestamp">{modifiedDiff}</div>
    </li>;
}

export class PackageSelector extends React.Component {
    constructor(props) {
        super(props);
    }

    onChange = (e) => {
        this.props.updateSearchText(e.target.value);
    }

    render() {
        return <div className="package-selector">
            <input
                type="text"
                placeholder="Search for package"
                onChange={this.onChange}
                value={this.props.searchText}
            />
            <ul className="package-list">
                {this.props.searchResults.map(p => <PackageLink {...p} />)}
            </ul>
        </div>;
    }
}