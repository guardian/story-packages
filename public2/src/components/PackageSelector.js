import React from 'react';
import moment from 'moment';

function PackageLink({ name, lastModified, highlighted }) {
    const modifiedDiff = moment.duration(moment(lastModified).diff(moment())).humanize();

    return <li className={`package-list__item ${highlighted ? "package-list__item--highlighted" : ""}`}>
        <div>{name}</div>
        <div className="package-list__timestamp">{modifiedDiff}</div>
    </li>;
}

export class PackageSelector extends React.Component {
    constructor(props) {
        super(props);
        this.state = { highlighted: 0 };
    }

    componentWillReceiveProps() {
        this.setState({ highlighted: 0 });
    }

    onSelect = () => {
        console.log(this.props.searchResults[this.state.highlighted].id);
    }

    onChange = (e) => {
        this.props.updateSearchText(e.target.value);
    }

    onKeyDown = ({ key }) => {
        switch(key) {
            case "ArrowDown": {
                const highlighted = (this.state.highlighted + 1) % this.props.searchResults.length;
                this.setState({ highlighted });

                break;
            }

            case "ArrowUp": {
                let highlighted = this.state.highlighted - 1;
                highlighted = highlighted < 0 ? this.props.searchResults.length - 1 : highlighted;

                this.setState({ highlighted });
                break;
            }

            case "Enter":
                this.onSelect();
                break;

            default:
                return;
        }
    }

    render() {
        return <div className="package-selector">
            <input
                type="text"
                placeholder="Search for package"
                onKeyDown={this.onKeyDown}
                onChange={this.onChange}
                value={this.props.searchText}
            />
            <ul className="package-list">
                {this.props.searchResults.map((p, ix) =>
                    <div key={p.id} onMouseOver={() => this.setState({ highlighted: ix })} onClick={this.onSelect}>
                        <PackageLink key={p.id} highlighted={ix === this.state.highlighted} {...p} />
                    </div>
                )}
            </ul>
        </div>;
    }
}