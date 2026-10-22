import type { Review, ReviewStage } from '../game/postmortem';
import type { Texts } from '../net/i18n';
import type { Postmortem } from '../protocol/messages';

/**
 * The postmortem as a modal dialog (slice 6a). While the question is open it cannot be dismissed:
 * the next wave waits for the answer anyway (decision Z). After the answer it shows the chosen and
 * the correct root cause; closing it hands the station back to the player. Blameless: a wrong answer
 * only explains, it never scolds.
 */
export class PostmortemDialog {
  private readonly dialog = document.createElement('dialog');
  private key = '';

  constructor(private readonly review: Review, private readonly texts: Texts,
    private readonly answer: (wave: number, option: string) => void) {
    this.dialog.className = 'postmortem';
    this.dialog.setAttribute('aria-labelledby', 'postmortem-title');
    // Escape would close a modal dialog; an open question must be answered first.
    this.dialog.addEventListener('cancel', (e) => {
      if (this.key.startsWith('question') || this.key.startsWith('sent')) e.preventDefault();
    });
    // Browsers ignore preventDefault on a close request without recent user activation (a dialog
    // opened by a snapshot has none), so Escape can still close it: forget the rendered state and
    // the next frame opens it again while the question is unanswered.
    this.dialog.addEventListener('close', () => {
      this.key = '';
    });
    document.body.append(this.dialog);
  }

  /** Content language changed: tag the dialog and rebuild it with the newly loaded texts. */
  setLanguage(language: string): void {
    this.dialog.lang = language;
    this.key = '';
  }

  isOpen(): boolean {
    return this.dialog.open;
  }

  render(pm: Postmortem | null): void {
    const stage = this.review.stage(pm);
    const key = `${stage.kind}|${pm?.wave ?? ''}|${stage.kind === 'sent' ? stage.option : ''}`;
    if (key === this.key) return;
    this.key = key;
    if (pm === null || stage.kind === 'none') {
      if (this.dialog.open) this.dialog.close();
      return;
    }
    this.dialog.replaceChildren(...this.content(pm, stage));
    if (!this.dialog.open) this.dialog.showModal();
  }

  private content(pm: Postmortem, stage: ReviewStage): HTMLElement[] {
    const title = element('h2', `Postmortem · wave ${pm.wave}`);
    title.id = 'postmortem-title';
    const question = element('p', this.texts.t(pm.questionKey));
    question.className = 'question';
    const list = element('div', '');
    list.className = 'options';
    list.setAttribute('role', 'group');

    for (const option of pm.options) {
      const button = element('button', this.texts.t(option.key)) as HTMLButtonElement;
      button.type = 'button';
      if (stage.kind === 'question') {
        button.onclick = () => this.answer(pm.wave, option.id);
      } else {
        button.disabled = true;
      }
      if (stage.kind === 'sent' && stage.option === option.id) button.dataset['mark'] = 'chosen';
      if (stage.kind === 'result') {
        if (option.id === stage.correct) button.dataset['mark'] = 'correct';
        else if (option.id === stage.chosen) button.dataset['mark'] = 'wrong';
      }
      list.append(button);
    }

    const footer = element('div', '');
    footer.className = 'footer';
    if (stage.kind === 'question') {
      footer.append(element('p', 'Blameless review: what was the root cause? One answer; the right one earns a bonus.'));
    } else if (stage.kind === 'sent') {
      footer.append(element('p', 'Sending…'));
    } else if (stage.kind === 'result') {
      footer.append(element('p', stage.right
        ? 'Right root cause. Bonus credited.'
        : 'Not this time. The highlighted answer was the root cause; the codex explains why.'));
      const close = element('button', 'Back to the station') as HTMLButtonElement;
      close.type = 'button';
      close.onclick = () => {
        this.review.close(pm.wave);
        this.render(pm);
      };
      footer.append(close);
    }
    return [title, question, list, footer];
  }
}

function element(tag: string, text: string): HTMLElement {
  const e = document.createElement(tag);
  if (text) e.textContent = text;
  return e;
}
