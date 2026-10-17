import './style.css';
import { Orders } from './game/orders';
import { GameStore } from './game/store';
import { CounterPanel } from './hud/counterPanel';
import { Hud } from './hud/hud';
import { StationMap } from './map/stationMap';
import { CatalogLoader } from './net/catalog';
import { Connection } from './net/connection';
import type { ClientAction } from './protocol/messages';

function byId(id: string): HTMLElement {
  const e = document.getElementById(id);
  if (e === null) throw new Error(`missing #${id} in index.html`);
  return e;
}

const store = new GameStore(200);
const orders = new Orders();
const map = new StationMap();
await map.mount(byId('map'));

let session: string | undefined;
let tickHz = 10;
let insightsKey = '';

const connection = new Connection(Connection.defaultUrl(), {
  onMessage(message) {
    switch (message.type) {
      case 'welcome':
        session = message.session;
        tickHz = message.tickHz;
        insightsKey = '';
        store.reset();
        store.setSnapshotRate(message.snapshotHz);
        orders.clear();
        panel.reset();
        catalog.load(session);
        hud.setPaused(false);
        hud.showNotice(`Session ${message.session.slice(0, 8)} · seed ${message.seed}`);
        break;
      case 'snapshot': {
        const view = store.apply(message, performance.now());
        orders.confirm(view.counters);
        // New insights can reveal counters (decision W): ask the server for this session's catalog again.
        const key = view.insights.join('|');
        if (key !== insightsKey && session !== undefined) {
          insightsKey = key;
          catalog.load(session);
        }
        break;
      }
      case 'paused':
        hud.setPaused(message.paused);
        break;
      case 'rejected':
        // A refused purchase shows on its card; anything else goes to the notice line.
        if (!orders.reject(message.action, message.reason)) hud.showNotice(`Rejected: ${message.reason}`);
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

function send(action: ClientAction): boolean {
  if (connection.send(action)) return true;
  hud.showNotice('Not connected');
  return false;
}

const hud = new Hud(byId('hud'), byId('panel'), send);
const panel = new CounterPanel(hud.countersHost, orders, (item, option) => {
  const action: ClientAction = { type: 'apply_counter', counter: item.id };
  if (option !== undefined) action.option = option;
  if (send(action)) orders.place(item);
}, () => tickHz);
const catalog = new CatalogLoader((c) => panel.setCatalog(c), (e: unknown) => {
  console.error(e);
  hud.showNotice('Could not load the module catalog');
});

map.ticker.add((ticker) => {
  const view = store.sample(performance.now());
  map.render(view, ticker.deltaMS);
  hud.render(view, view === undefined || !orders.hasPending() ? undefined : orders.projectedCredits(view));
  panel.render(store.current());
});

connection.connect();

// Dev only: send raw actions from the console, e.g. nodezero.send({ type: 'start_wave', wave: 2 })
if (import.meta.env.DEV) {
  Object.assign(window, { nodezero: { send } });
}
