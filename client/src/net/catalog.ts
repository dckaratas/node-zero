import { parseCatalog, type Catalog } from '../protocol/messages';

/**
 * Loads the session's counter catalog (slice 5b, decision V). The server answers with an ETag and
 * `Cache-Control: private, no-cache`, so the browser revalidates and an unchanged catalog is a 304.
 * Responses can overtake each other; only the newest request's answer is delivered.
 */
export class CatalogLoader {
  private sequence = 0;

  constructor(private readonly onCatalog: (catalog: Catalog) => void, private readonly onError: (e: unknown) => void) {}

  load(session: string, language = 'en'): void {
    const request = ++this.sequence;
    fetch(`/api/sessions/${encodeURIComponent(session)}/catalog?lang=${language}`)
      .then((response) => {
        if (!response.ok) throw new Error(`catalog: HTTP ${response.status}`);
        return response.json() as Promise<unknown>;
      })
      .then((body) => {
        if (request === this.sequence) this.onCatalog(parseCatalog(body));
      })
      .catch(this.onError);
  }
}
