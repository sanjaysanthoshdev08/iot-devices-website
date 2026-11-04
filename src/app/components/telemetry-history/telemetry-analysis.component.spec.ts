import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PLATFORM_ID } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController, provideHttpClientTesting, TestRequest,
} from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import {
  TelemetryAnalysisComponent, parseIstInput, presetPeriod,
} from './telemetry-analysis.component';

describe('IST analysis periods', () => {
  const now = Date.parse('2026-11-04T00:00:00Z');

  it('uses Indian midnight rather than browser timezone', () => {
    expect(presetPeriod('today', now)).toEqual({
      from: '2026-11-04T00:00', to: '2026-11-05T00:00',
    });
    expect(new Date(parseIstInput('2026-11-04T00:00')).toISOString())
      .toBe('2026-11-03T18:30:00.000Z');
  });

  it('handles yesterday, seven calendar days and month boundaries', () => {
    expect(presetPeriod('yesterday', now).from).toBe('2026-11-03T00:00');
    expect(presetPeriod('week', now).from).toBe('2026-10-29T00:00');
    expect(presetPeriod('month', now)).toEqual({
      from: '2026-11-01T00:00', to: '2026-12-01T00:00',
    });
  });

  it('rejects invalid dates instead of rolling into another month', () => {
    expect(Number.isNaN(parseIstInput('2026-02-30T12:00'))).toBe(true);
    expect(Number.isNaN(parseIstInput(''))).toBe(true);
  });
});

describe('TelemetryAnalysisComponent', () => {
  let fixture: ComponentFixture<TelemetryAnalysisComponent>;
  let component: TelemetryAnalysisComponent;
  let http: HttpTestingController;

  function request(id = 1): TestRequest {
    return http.expectOne(req =>
      req.url === `http://localhost:8080/api/v1/devices/${id}/telemetry/analysis`);
  }

  function response(req: TestRequest, id = 1) {
    const from = req.request.params.get('from')!;
    const to = req.request.params.get('to')!;
    return {
      success: true, message: 'OK',
      data: {
        deviceId: id, from, to,
        simulated: req.request.params.get('simulated') === 'true',
        timeBasis: 'RECEIVED_AT',
        sampleCount: 1,
        metrics: [{
          metric: 'loadOutputPowerW', label: 'Load output power', unit: 'W',
          validSampleCount: 1,
          minimum: { value: 0, receivedAt: from, measuredAt: null, readingId: 1 },
          maximum: { value: 900, receivedAt: from, measuredAt: null, readingId: 2 },
        }],
        history: {
          content: [], number: Number(req.request.params.get('page')),
          size: 10, totalElements: 1, totalPages: 1, first: true, last: true,
        },
      },
    };
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TelemetryAnalysisComponent],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: PLATFORM_ID, useValue: 'browser' },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TelemetryAnalysisComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('deviceId', 1);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    http.verify({ ignoreCancelled: true });
  });

  it('requests explicit dates and source, and renders recorded zero and peak time', () => {
    const req = request();
    expect(req.request.params.get('simulated')).toBe('true');
    expect(req.request.params.get('size')).toBe('10');
    req.flush(response(req));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('900 W');
    expect(fixture.nativeElement.textContent).toContain('0 W');
    expect(fixture.nativeElement.textContent).toContain('IST');
  });

  it('cancels the previous device request and rejects mismatched responses', () => {
    const old = request();
    fixture.componentRef.setInput('deviceId', 2);
    fixture.detectChanges();
    expect(old.cancelled).toBe(true);
    const next = request(2);
    next.flush(response(next, 1));
    expect(component.result()).toBeNull();
    expect(component.error()).not.toBeNull();
  });

  it('cancels displayed analysis when inputs change and selects device readings explicitly', () => {
    const old = request();
    component.source = 'device';
    component.invalidate();
    expect(old.cancelled).toBe(true);
    component.apply();

    const next = request();
    expect(next.request.params.get('simulated')).toBe('false');
    next.flush(response(next));
    expect(component.result()?.simulated).toBe(false);
  });

  it('rejects an invalid period without another HTTP request', () => {
    const old = request();
    component.fromInput = '2026-11-05T00:00';
    component.toInput = '2026-11-04T00:00';
    component.apply();
    expect(old.cancelled).toBe(true);
    expect(component.error()).toContain('valid start and end');
    http.expectNone(req => req.url.endsWith('/telemetry/analysis'));
  });

  it.each([401, 403, 404])('reports HTTP %s access rejection', status => {
    const rejected: number[] = [];
    component.accessDenied.subscribe(value => rejected.push(value));
    request().flush({}, { status, statusText: 'Rejected' });
    expect(component.result()).toBeNull();
    expect(rejected).toEqual([status]);
  });

  it('cancels pending analysis on destruction', () => {
    const pending = request();
    fixture.destroy();
    expect(pending.cancelled).toBe(true);
    expect(component.result()).toBeNull();
  });
});