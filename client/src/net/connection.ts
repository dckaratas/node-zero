import { encodeAction, parseServerMessage, ProtocolError, type ClientAction, type ServerMessage } from '../protocol/messages';

export type ConnectionStatus = 'connecting' | 'open' | 'closed';

export interface ConnectionHandlers {
  onMessage(message: ServerMessage): void;
  onStatus(status: ConnectionStatus): void;
}

/**
 * The game socket. Sessions live in server memory and are not resumable yet (ADR-0015), so a dropped
 * connection reconnects into a new session; the welcome message tells the UI to start over.
 * Reconnect backs off 1 s → 10 s so a restarting server is not hammered.
 */
export class Connection {
  private socket: WebSocket | undefined;
  private retryMs = 1000;
  private stopped = false;

  constructor(private readonly url: string, private readonly handlers: ConnectionHandlers) {}

  static defaultUrl(): string {
    const scheme = location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${location.host}/ws/game`;
  }

  connect(): void {
    this.stopped = false;
    this.handlers.onStatus('connecting');
    const socket = new WebSocket(this.url);
    this.socket = socket;
    socket.onopen = () => {
      this.retryMs = 1000;
      this.handlers.onStatus('open');
    };
    socket.onmessage = (event: MessageEvent<string>) => {
      try {
        this.handlers.onMessage(parseServerMessage(JSON.parse(event.data)));
      } catch (e) {
        // A contract break is a bug: make it loud in the console, keep the game running.
        if (e instanceof ProtocolError || e instanceof SyntaxError) console.error('bad server message', e, event.data);
        else throw e;
      }
    };
    socket.onclose = () => {
      this.handlers.onStatus('closed');
      if (this.stopped) return;
      setTimeout(() => this.connect(), this.retryMs);
      this.retryMs = Math.min(10_000, this.retryMs * 2);
    };
  }

  send(action: ClientAction): boolean {
    if (this.socket?.readyState !== WebSocket.OPEN) return false;
    this.socket.send(encodeAction(action));
    return true;
  }

  close(): void {
    this.stopped = true;
    this.socket?.close();
  }
}
