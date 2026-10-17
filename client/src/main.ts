import './style.css';
import { GameStore } from './game/store';
import { Hud } from './hud/hud';
import { StationMap } from './map/stationMap';
import { Connection } from './net/connection';

const store = new GameStore(200);

function byId(id: string): HTMLElement {
  const e = document.getElementById(id);
  if (e === null) throw new Error(`missing #${id} in index.html`);
  return e;
}

const map = new StationMap();
await map.mount(byId('map'));

const connection = new Connection(Connection.defaultUrl(), {
  onMessage(message) {
    switch (message.type) {
      case 'welcome':
        store.reset();
        store.setSnapshotRate(message.snapshotHz);
        hud.setPaused(false);
        hud.showNotice(`Session ${message.session.slice(0, 8)} · seed ${message.seed}`);
        break;
      case 'snapshot':
        store.apply(message, performance.now());
        break;
      case 'paused':
        hud.setPaused(message.paused);
        break;
      case 'rejected':
        hud.showNotice(`Rejected: ${message.reason}`);
        break;
      case 'error':
        hud.showNotice(`Server: ${message.reason}`);
        break;
    }
  },
  onStatus(status) {
    hud.setStatus(status);
  },
});

const hud = new Hud(byId('hud'), byId('panel'), (action) => {
  if (!connection.send(action)) hud.showNotice('Not connected');
});

map.ticker.add((ticker) => {
  const view = store.sample(performance.now());
  map.render(view, ticker.deltaMS);
  hud.render(view);
});

connection.connect();

// Dev only, until the counter panel lands (slice 5b): nodezero.send({ type: 'apply_counter', counter: 'prometheus' })
if (import.meta.env.DEV) {
  Object.assign(window, { nodezero: { send: (a: Parameters<Connection['send']>[0]) => connection.send(a) } });
}
