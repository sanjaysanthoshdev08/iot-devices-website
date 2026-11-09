import {
  Component,
  ElementRef,
  HostListener,
  Input,
  OnDestroy,
  PLATFORM_ID,
  ViewChild,
  ViewEncapsulation,
  inject,
  signal
} from '@angular/core';
import { NgClass, isPlatformBrowser } from '@angular/common';

let nextId = 0;

/**
 * Reusable responsive header menu.
 *
 * Inputs : brand (text) + class names for the brand / links / extra wrappers.
 * Slots  : [links]  -> page links, [extra] -> account actions.
 *
 * Wide screens (> 700px): every wrapper is `display: contents`, so the projected
 * links and actions stay normal items of the host header layout and the hamburger is hidden.
 * Small screens (<= 700px): brand + circle hamburger bar, with a full-width animated drop-down panel.
 */
@Component({
  selector: 'app-nav-menu',
  standalone: true,
  imports: [NgClass],
  encapsulation: ViewEncapsulation.None,
  styleUrl: './nav-menu.component.scss',
  template: `
    <div class="nm-brand" [ngClass]="brandClass" [class.nm-brand-faded]="open()">
      <span class="nm-brand-text">{{ brand }}</span>
    </div>

    <button
      #toggleBtn
      type="button"
      class="nm-toggle"
      [class.nm-toggle-open]="open()"
      [attr.aria-label]="open() ? 'Close menu' : 'Open menu'"
      [attr.aria-expanded]="open()"
      [attr.aria-controls]="panelId"
      (click)="toggle()"
    >
      <span class="nm-toggle-lines" aria-hidden="true"><span></span><span></span><span></span></span>
    </button>

    <div #panel class="nm-panel" [class.nm-panel-open]="open()" [id]="panelId" (click)="onPanelClick($event)">
      <div class="nm-brand nm-brand-panel" aria-hidden="true">
        <span class="nm-brand-text">{{ brand }}</span>
      </div>
      <div class="nm-links" [ngClass]="linksClass"><ng-content select="[links]"></ng-content></div>
      <div class="nm-extra" [ngClass]="extraClass"><ng-content select="[extra]"></ng-content></div>
    </div>
  `
})
export class NavMenu implements OnDestroy {
  @Input() brand = '';
  @Input() brandClass = '';
  @Input() linksClass = '';
  @Input() extraClass = '';

  @ViewChild('panel', { static: true }) panelRef!: ElementRef<HTMLElement>;
  @ViewChild('toggleBtn', { static: true }) toggleRef!: ElementRef<HTMLElement>;

  readonly open = signal(false);
  readonly panelId = `nm-panel-${nextId++}`;

  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  // Capture-phase scroll listener (scroll events do not bubble) so the menu closes when the page scrolls.
  private readonly onScroll = () => this.close();

  constructor() {
    if (this.isBrowser) {
      document.addEventListener('scroll', this.onScroll, true);
    }
  }

  ngOnDestroy(): void {
    if (this.isBrowser) {
      document.removeEventListener('scroll', this.onScroll, true);
    }
  }

  toggle(): void {
    this.open.update(v => !v);
  }

  close(): void {
    if (this.open()) {
      this.open.set(false);
    }
  }

  /** Close when a link or action is chosen (but not for toggles such as the profile avatar). */
  onPanelClick(event: MouseEvent): void {
    const target = event.target as HTMLElement | null;
    const control = target?.closest('a, button');
    if (control && !control.hasAttribute('aria-haspopup')) {
      this.close();
    }
  }

  @HostListener('document:keydown.escape')
  onEscape(): void {
    if (this.open()) {
      this.close();
      this.toggleRef.nativeElement.focus();
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    if (!this.open()) return;
    const target = event.target as Node;
    if (!this.panelRef.nativeElement.contains(target) && !this.toggleRef.nativeElement.contains(target)) {
      this.close();
    }
  }

  @HostListener('window:resize')
  onResize(): void {
    if (this.isBrowser && window.innerWidth > 700) {
      this.close();
    }
  }
}
