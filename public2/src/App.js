import React, { Component } from 'react';
import logo from './logo.svg';
import './App.css';

import { Item } from './components/Item';
import { ContainerGrid } from './components/ContainerGrid';

function item(id) {
  return { id };
}

class App extends Component {
  constructor(props) {
    super(props);
    this.state = {
      items: { 1: item(1), 2: item(2), 3: item(3), 4: item(4), 5: item(5) },
      included: [null, null, null, null, null, null, null, null, null]
    };
  }

  render() {
    const items = Object.values(this.state.items);

    return <div className="app">
      <div>
        <h3>Items</h3>
        {items.map((item, ix) =>
          <div key={ix} className="container">
            <Item {...item} />
          </div>
        )}
      </div>
      <div className="right">
        <h3>Package</h3>
        <ContainerGrid
          items={this.state.included}
          updateFn={(included) => this.setState({ included })}
        />
      </div>
    </div>;
  }
}

export default App;
