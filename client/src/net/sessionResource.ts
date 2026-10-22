/**
 * A per-session HTTP resource such as the counter catalog (slice 5b) or the codex (slice 6b).
 * The server answers with an ETag and `Cache-Control: private, no-cache`, so the browser revalidates
 * and an unchanged body is a 304. Responses can overtake each other; only the newest request's answer
 * is delivered.
 */
export class SessionResource<T> {
  private sequence = 0;

  constructor(
    private readonly name: string,
    private readonly parse: (body: unknown) => T,
    private readonly onData: (data: T) => void,
    private readonly onError: (e: unknown) => void,
  ) {}

  load(session: string, language = 'en'): void {
    const request = ++this.sequence;
    fetch(`/api/sessions/${encodeURIComponent(session)}/${this.name}?lang=${language}`)
      .then((response) => {
        if (!response.ok) throw new Error(`${this.name}: HTTP ${response.status}`);
        return response.json() as Promise<unknown>;
      })
      .then((body) => {
        if (request === this.sequence) this.onData(this.parse(body));
      })
      .catch(this.onError);
  }
}
