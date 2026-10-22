import './style.css';
import { loadLanguage, saveLanguage, type ContentLanguage } from './game/language';
import { Orders } from './game/orders';
import { Review } from './game/postmortem';
import { firingNodes } from './game/alerts';
import { shieldAlert } from './game/shield';
import { GameStore } from './game/store';
import { ActionPanel } from './hud/actionPanel';
import { AlertBanner } from './hud/alertBanner';
import { AlertPanel } from './hud/alertPanel';
import { ChapterDialog } from './hud/chapterDialog';
import { CodexPanel } from './hud/codexPanel';
import { CounterPanel } from './hud/counterPanel';
import { Hud } from './hud/hud';
import { PostmortemDialog } from './hud/postmortemDialog';
import { ShieldAlerts } from './hud/shieldAlerts';
import { StationMap } from './map/stationMap';
import { Connection } from './net/connection';
import { Texts } from './net/i18n';
import { SessionResource } from './net/sessionResource';
import { parseCatalog, parseCodex, type ClientAction } from './protocol/messages';

function byId(id: string): HTMLElement {
  const e = document.getElementById(id);
  if (e === null) throw new Error(`missing #${id} in index.html`);
  return e;
}

const store = new GameStore(200);
const orders = new Orders();
const review = new Review();
const texts = new Texts();
const map = new StationMap();
await map.mount(byId('map'));

let session: string | undefined;
let tickHz = 10;
let insightsKey = '';
let codexKey = '';
let language: ContentLanguage = loadLanguage();

const connection = new Connection(Connection.defaultUrl(), {
  onMessage(message) {
    switch (message.type) {
      case 'welcome':
        session = message.session;
        tickHz = message.tickHz;
        insightsKey = '';
        codexKey = '';
        store.reset();
        store.setSnapshotRate(message.snapshotHz);
        orders.clear();
        review.reset();
        panel.reset();
        actionPanel.reset();
        alertBanner.reset();
        codexPanel.reset();
        catalog.load(session, language);
        hud.setPaused(false);
        hud.showNotice(`Session ${message.session.slice(0, 8)} · seed ${message.seed}`);
        break;
      case 'snapshot': {
        const before = store.current();
        const view = store.apply(message, performance.now());
        // Threshold toasts and the collapse notice (decision AD); compared on raw views, not blended ones.
        const alert = shieldAlert(before, view);
        if (alert !== null) shieldAlerts.show(alert, view.wave, performance.now());
        orders.confirm(view.counters);
        // New insights can reveal counters (decision W): ask the server for this session's catalog again.
        const key = view.insights.join('|');
        if (key !== insightsKey && session !== undefined) {
          insightsKey = key;
          catalog.load(session, language);
        }
        // A purchase or an answered postmortem opened codex entries: fetch their texts.
        const opened = view.codex.join('|');
        if (opened !== codexKey && session !== undefined) {
          codexKey = opened;
          codex.load(session, language);
        }
        break;
      }
      case 'paused':
        hud.setPaused(message.paused);
        break;
      case 'rejected':
        // A refused purchase shows on its card; anything else goes to the notice line.
        if (message.action.type === 'answer_postmortem') review.rejected();
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
}, () => tickHz, send);
const actionPanel = new ActionPanel(hud.actionsHost, (action, option) => {
  const message: ClientAction = { type: 'run_action', action };
  if (option !== undefined) message.option = option;
  send(message);
}, () => tickHz);
const catalog = new SessionResource('catalog', parseCatalog, (c) => {
  panel.setCatalog(c);
  actionPanel.setCatalog(c);
  hud.setCrewMax(c.crewPerShift);
}, (e: unknown) => {
  console.error(e);
  hud.showNotice('Could not load the module catalog');
});
const codexPanel = new CodexPanel(hud.codexHost);
const codex = new SessionResource('codex', parseCodex, (c) => codexPanel.show(c), (e: unknown) => {
  console.error(e);
  hud.showNotice('Could not load the codex');
});

const postmortem = new PostmortemDialog(review, texts, (wave, option) => {
  if (send({ type: 'answer_postmortem', option })) review.sent(wave, option);
});
const alertPanel = new AlertPanel(hud.alertsHost, send);
const alertBanner = new AlertBanner();
const shieldAlerts = new ShieldAlerts(() => document.querySelector<HTMLElement>('.hud .shield'));
const chapter = new ChapterDialog();

map.ticker.add((ticker) => {
  const now = performance.now();
  const view = store.sample(now);
  map.render(view, ticker.deltaMS, firingNodes(store.current()?.alerts ?? []));
  hud.render(view, view === undefined || !orders.hasPending() ? undefined : orders.projectedCredits(view));
  panel.render(store.current());
  actionPanel.render(store.current());
  alertPanel.render(store.current());
  alertBanner.render(store.current()?.alerts ?? []);
  const latest = store.current();
  // The collapse notice comes first; the summary waits until the last postmortem is closed.
  postmortem.render(shieldAlerts.blocking(now) ? null : latest?.postmortem ?? null);
  if (!postmortem.isOpen()) chapter.render(latest?.chapter ?? null, latest?.results ?? []);
});

hud.setContentLanguage(language);
postmortem.setLanguage(language);

// Content language (slice 6c): reload the texts, then everything the server resolves by language.
hud.onLanguageChange((next) => {
  language = next;
  saveLanguage(next);
  hud.setContentLanguage(next);
  texts.load(next)
    .then(() => postmortem.setLanguage(next))
    .catch((e: unknown) => {
      console.error(e);
      hud.showNotice('Could not load texts');
    });
  if (session !== undefined) {
    catalog.load(session, next);
    codex.load(session, next);
  }
});

texts.load(language).catch((e: unknown) => {
  console.error(e);
  hud.showNotice('Could not load texts');
}).finally(() => connection.connect());

// Dev only: send raw actions from the console, e.g. nodezero.send({ type: 'start_wave', wave: 2 })
if (import.meta.env.DEV) {
  Object.assign(window, { nodezero: { send } });
}
