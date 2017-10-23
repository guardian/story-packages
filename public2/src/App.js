import React, { Component } from 'react';
import logo from './logo.svg';
import './App.css';

import { Item } from './components/Item';
import { ItemSearch } from './components/ItemSearch';
import { ContainerGrid } from './components/ContainerGrid';
import { Package } from './components/Package';

function testItems(size) {
  const ret = [];

  for(let i = 0; i < size; i++) {
    ret.push({ id: i });
  }

  return ret;
}

function fill(size, value) {
  const ret = [];

  for(let i = 0; i < size; i++) {
    ret.push(value);
  }

  return ret;
}

const PACKAGE_SIZE = 9;

class App extends Component {
  constructor(props) {
    super(props);
    this.state = {
      items: testItems(15),
      packageItems: fill(PACKAGE_SIZE + 1, null)
    };
  }

  setPackageItems = (items) => {
    // Need a placeholder at the end so people can drag into linked
    const lastSlotFilled = items[items.length - 1] !== null;
    const packageItems = lastSlotFilled ? items.concat([null]) : items;

    this.setState({ packageItems });
  }

  getSearchItems() {
    return this.state.items.map(item => {
      const inUse = this.state.packageItems.some(i => i == null ? false : i.id === item.id);
      return Object.assign({}, item, { inUse });
    });
  }

  render() {
    const items = Object.values(this.state.items);

    return <div className="app">
      <ItemSearch items={this.getSearchItems()} />
      <div className="right">
        <Package
          size={PACKAGE_SIZE}
          items={this.state.packageItems}
          updateFn={this.setPackageItems}
        />
      </div>
    </div>;
  }
}

export default App;
