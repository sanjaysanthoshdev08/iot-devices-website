import {
  Component, EventEmitter, Input, OnChanges, OnDestroy,
  Output, PLATFORM_ID, inject, signal,
} from '@angular/core';
import { CommonModule, isPlatformBrowser } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription, timeout } from 'rxjs';

import {
  DeviceTelemetryService,
  TelemetryAnalysis,
  TelemetryExtreme,
} from '../../services/device-telemetry.service';

type Preset = 'today' | 'yesterday' | 'week' | 'month' | 'custom';
const DAY = 86400000;
const IST = 330 * 60000;

export function istInput(instant: number): string {
  return new Date(instant + IST).toISOString().slice(0, 16);
}

export function parseIstInput(value: string): number {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}$/.test(value)) return NaN;
  const instant = Date.parse(value + ':00+05:30');
  return Number.isFinite(instant) && istInput(instant) === value
    ? instant : NaN;
}

export function presetPeriod(
  preset: Exclude<Preset, 'custom'>,
  now = Date.now()
): { from: string; to: string } {
  const local = new Date(now + IST);
  const year = local.getUTCFullYear();
  const month = local.getUTCMonth();
  const day = local.getUTCDate();
  const midnight = Date.UTC(year, month, day) - IST;

  let from = midnight;
  let to = midnight + DAY;

  if (preset === 'yesterday') {
    from -= DAY;
    to -= DAY;
  } else if (preset === 'week') {
    from -= 6 * DAY;
  } else if (preset === 'month') {
    from = Date.UTC(year, month, 1) - IST;
    to = Date.UTC(year, month + 1, 1) - IST;
  }

  return { from: istInput(from), to: istInput(to) };
}

@Component({
  selector: 'app-telemetry-analysis',
  standalone: true,
  imports: [CommonModule, FormsModule],
  template: `
    <section class="analysis-panel" aria-label="Telemetry period analysis">
      <h3>Period analysis</h3>
      <p>
        Find the highest and lowest recorded measurements.
        All dates and times below use IST (UTC+05:30).
      </p>

      <div class="analysis-controls">
        <label>
          Period
          <select [ngModel]="preset" (ngModelChange)="choosePreset($event)">
            <option value="today">Today</option>
            <option value="yesterday">Yesterday</option>
            <option value="week">Last 7 days</option>
            <option value="month">This month</option>
            <option value="custom">Custom dates</option>
          </select>
        </label>

        <label>
          From, inclusive (IST)
          <input type="datetime-local" [(ngModel)]="fromInput"
            (ngModelChange)="customChanged()" />
        </label>

        <label>
          Until, exclusive (IST)
          <input type="datetime-local" [(ngModel)]="toInput"
            (ngModelChange)="customChanged()" />
        </label>

        <label>
          Source
          <select [(ngModel)]="source" (ngModelChange)="invalidate()">
            <option value="simulated">Simulated data</option>
            <option value="device">Device readings</option>
          </select>
        </label>

        <button type="button" (click)="apply()">Apply / Refresh</button>
      </div>

      <p *ngIf="loading()" role="status">Loading period analysis...</p>

      <div *ngIf="error()" role="alert">
        <p class="analysis-error">{{ error() }}</p>
      </div>

      <ng-container *ngIf="result() as data">
        <p class="analysis-range">
          {{ data.from | date:'dd MMM yyyy, HH:mm':'+0530' }}
          to {{ data.to | date:'dd MMM yyyy, HH:mm':'+0530' }} IST
          · {{ data.simulated ? 'Simulated data' : 'Device readings' }}
          · {{ data.sampleCount }} stored samples
        </p>

        <p *ngIf="data.sampleCount === 0" role="status">
          No readings for this period and source.
        </p>

        <div class="analysis-cards" *ngIf="data.sampleCount > 0">
          <article *ngFor="let metric of data.metrics">
            <h4>{{ metric.label }}</h4>
            <div class="extremes">
              <div>
                <span>Lowest</span>
                <strong>{{ value(metric.minimum?.value) }} {{ metric.unit }}</strong>
                <small>{{ occurrence(metric.minimum) }}</small>
              </div>
              <div>
                <span>Highest</span>
                <strong>{{ value(metric.maximum?.value) }} {{ metric.unit }}</strong>
                <small>{{ occurrence(metric.maximum) }}</small>
              </div>
            </div>
            <small>{{ metric.validSampleCount }} samples with this measurement</small>
          </article>
        </div>

        <div class="analysis-scroll" tabindex="0"
          role="region" aria-label="Readings within selected period"
          *ngIf="data.history.content.length > 0">
          <table>
            <caption>Readings within this period — received time in IST</caption>
            <thead>
              <tr>
                <th scope="col">Received (IST)</th>
                <th scope="col">Mode</th>
                <th scope="col">Load W</th>
                <th scope="col">Battery V</th>
                <th scope="col">Battery %</th>
                <th scope="col">Grid V</th>
                <th scope="col">Grid W</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let row of data.history.content">
                <td>{{ row.receivedAt | date:'dd MMM yyyy, HH:mm:ss':'+0530' }}</td>
                <td>{{ row.operatingMode }}</td>
                <td>{{ value(row.load.outputPowerW) }}</td>
                <td>{{ value(row.battery.voltageV) }}</td>
                <td>{{ value(row.battery.socPercent) }}</td>
                <td>{{ value(row.grid.voltageV) }}</td>
                <td>{{ value(row.grid.inputPowerW) }}</td>
              </tr>
            </tbody>
          </table>
        </div>

        <div class="analysis-pagination" *ngIf="data.history.totalPages > 0">
          <button type="button" class="analysis-previous"
            [disabled]="loading() || data.history.first"
            (click)="loadPage(data.history.number - 1)">Previous</button>
          <span>
            Page {{ data.history.number + 1 }} of {{ data.history.totalPages }}
          </span>
          <button type="button" class="analysis-next"
            [disabled]="loading() || data.history.last"
            (click)="loadPage(data.history.number + 1)">Next</button>
        </div>
      </ng-container>

      <p class="analysis-note">
        Summaries cover the full selected period, not only the displayed page.
        — means unavailable. Equal extremes show the first occurrence.
        Times use backend reception; brief peaks between samples may not be captured.
        The original complete history remains below.
      </p>
    </section>
  `,
  styles: [`
    :host { display:block; margin-bottom:24px; }
    .analysis-panel {
      background:#141820; border:1px solid #303743;
      border-radius:16px; padding:20px; color:#e5e7eb;
    }
    h3 { margin:0; font-size:18px; font-weight:600; }
    h4 { margin:0 0 16px; font-size:14px; font-weight:600; }
    p, small, label, button, select, input { font-size:13px; }
    p { color:#aeb8c7; line-height:1.6; }
    .analysis-controls {
      display:flex; gap:12px; flex-wrap:wrap; align-items:flex-end;
      margin:18px 0;
    }
    label { display:flex; flex-direction:column; gap:7px; color:#bdc8d8; }
    input, select, button {
      padding:9px 11px; background:#202938; color:#edf2f7;
      border:1px solid #475569; border-radius:8px; font-family:inherit;
    }
    input { color-scheme:dark; }
    button { cursor:pointer; }
    button:disabled { opacity:.45; cursor:default; }
    :is(button,input,select,.analysis-scroll):focus-visible {
      outline:2px solid #60a5fa; outline-offset:3px;
    }
    .analysis-cards {
      display:grid; grid-template-columns:repeat(auto-fit,minmax(250px,1fr));
      gap:12px; margin:18px 0;
    }
    article {
      padding:16px; background:#1b2230;
      border:1px solid #354054; border-radius:12px;
    }
    .extremes { display:grid; grid-template-columns:1fr 1fr; gap:14px; }
    .extremes div { display:flex; flex-direction:column; gap:8px; }
    .extremes span, small { color:#aeb8c7; }
    strong { font-size:19px; font-variant-numeric:tabular-nums; }
    article > small { display:block; margin-top:16px; }
    .analysis-scroll { overflow-x:auto; }
    table { width:100%; border-collapse:collapse; font-size:12px; }
    th, td {
      padding:11px; text-align:left; white-space:nowrap;
      border-bottom:1px solid #303743;
    }
    caption { text-align:left; padding:10px 0; color:#aeb8c7; }
    .analysis-pagination {
      display:flex; justify-content:space-between; align-items:center;
      gap:12px; margin-top:16px; font-size:13px;
    }
    .analysis-error { color:#fca5a5; }
    .analysis-note { margin-bottom:0; }
  `],
})
export class TelemetryAnalysisComponent implements OnChanges, OnDestroy {
  @Input({ required: true }) deviceId!: number;
  @Output() readonly accessDenied = new EventEmitter<number>();

  private readonly api = inject(DeviceTelemetryService);
  private readonly platformId = inject(PLATFORM_ID);
  private request: Subscription | null = null;
  private generation = 0;
  private destroyed = false;
  private selection: { from: string; to: string; simulated: boolean } | null = null;

  preset: Preset = 'today';
  source = 'simulated';
  fromInput = '';
  toInput = '';

  readonly result = signal<TelemetryAnalysis | null>(null);
  readonly loading = signal(false);
  readonly error = signal<string | null>(null);

  ngOnChanges(): void {
    if (!this.fromInput) {
      Object.assign(this, {
        fromInput: presetPeriod('today').from,
        toInput: presetPeriod('today').to,
      });
    }
    this.apply();
  }

  ngOnDestroy(): void {
    this.destroyed = true;
    this.invalidate();
  }

  invalidate(): void {
    this.generation++;
    this.request?.unsubscribe();
    this.request = null;
    this.selection = null;
    this.result.set(null);
    this.loading.set(false);
    this.error.set(null);
  }

  customChanged(): void {
    this.preset = 'custom';
    this.invalidate();
  }

  choosePreset(preset: Preset): void {
    this.preset = preset;
    this.invalidate();
    if (preset === 'custom') return;
    const period = presetPeriod(preset);
    this.fromInput = period.from;
    this.toInput = period.to;
    this.apply();
  }

  apply(): void {
    this.invalidate();
    const from = parseIstInput(this.fromInput);
    const to = parseIstInput(this.toInput);

    if (!Number.isFinite(from) || !Number.isFinite(to)
        || from >= to || to - from > 366 * DAY
        || !['simulated', 'device'].includes(this.source)) {
      this.error.set('Choose a valid start and end, up to 366 days apart.');
      return;
    }

    this.selection = {
      from: new Date(from).toISOString(),
      to: new Date(to).toISOString(),
      simulated: this.source === 'simulated',
    };
    this.loadPage(0);
  }

  loadPage(page: number): void {
    const selection = this.selection;
    if (this.destroyed || !isPlatformBrowser(this.platformId) || !selection) return;

    const generation = ++this.generation;
    this.request?.unsubscribe();
    this.result.set(null);
    this.error.set(null);

    if (!Number.isSafeInteger(this.deviceId) || this.deviceId <= 0
        || !Number.isSafeInteger(page) || page < 0) {
      this.loading.set(false);
      this.error.set('Invalid analysis request.');
      return;
    }

    const deviceId = this.deviceId;
    this.loading.set(true);
    const current = () => !this.destroyed
      && this.generation === generation && this.deviceId === deviceId;

    this.request = this.api.analysis(
      deviceId, selection.from, selection.to, selection.simulated, page, 10
    ).pipe(timeout({ first: 10000 })).subscribe({
      next: response => {
        if (!current()) return;
        this.loading.set(false);
        const data = response.data;
        const start = Date.parse(selection.from);
        const end = Date.parse(selection.to);
        const inPeriod = (value: string) =>
          Date.parse(value) >= start && Date.parse(value) < end;
        const extremeValid = (value: TelemetryExtreme | null) =>
          value === null || (value && Number.isFinite(value.value)
            && inPeriod(value.receivedAt)
            && Number.isSafeInteger(value.readingId) && value.readingId > 0);

        if (!response.success || !data || data.deviceId !== deviceId
            || Date.parse(data.from) !== start || Date.parse(data.to) !== end
            || data.simulated !== selection.simulated
            || data.timeBasis !== 'RECEIVED_AT'
            || !Number.isSafeInteger(data.sampleCount) || data.sampleCount < 0
            || !Array.isArray(data.metrics)
            || !data.metrics.every(metric =>
              metric && typeof metric.label === 'string'
              && typeof metric.unit === 'string'
              && Number.isSafeInteger(metric.validSampleCount)
              && metric.validSampleCount >= 0
              && metric.validSampleCount <= data.sampleCount
              && extremeValid(metric.minimum) && extremeValid(metric.maximum)
              && (metric.validSampleCount === 0
                ? metric.minimum === null && metric.maximum === null
                : metric.minimum !== null && metric.maximum !== null))
            || !data.history || data.history.number !== page
            || data.history.size !== 10
            || data.history.totalElements !== data.sampleCount
            || !Number.isSafeInteger(data.history.totalPages)
            || data.history.totalPages < 0
            || !Array.isArray(data.history.content)
            || !data.history.content.every(row =>
              row && row.deviceId === deviceId
              && row.simulated === selection.simulated
              && inPeriod(row.receivedAt) && row.battery && row.grid && row.load)) {
          this.error.set('Unable to display the analysis response.');
          return;
        }

        this.result.set(data);
      },
      error: (error: unknown) => {
        if (!current()) return;
        this.result.set(null);
        this.loading.set(false);
        const status = error instanceof HttpErrorResponse ? error.status : 0;

        if ([401, 403, 404].includes(status)) {
          this.selection = null;
          this.error.set('Analysis access is no longer available.');
          this.accessDenied.emit(status);
        } else {
          this.error.set('Unable to load analysis. Use Apply / Refresh to retry.');
        }
      },
    });
  }

  value(value: number | null | undefined): string {
    return typeof value === 'number' && Number.isFinite(value)
      ? value.toLocaleString('en-IN', { maximumFractionDigits: 2 }) : '—';
  }

  occurrence(extreme: TelemetryExtreme | null): string {
    return extreme
      ? new Intl.DateTimeFormat('en-IN', {
          timeZone: 'Asia/Kolkata', year: 'numeric', month: 'short',
          day: '2-digit', hour: '2-digit', minute: '2-digit', second: '2-digit',
        }).format(new Date(extreme.receivedAt)) + ' IST'
      : 'No measurement';
  }
}