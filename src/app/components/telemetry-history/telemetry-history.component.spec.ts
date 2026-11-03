import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PLATFORM_ID } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { TelemetryHistoryComponent } from './telemetry-history.component';

describe('TelemetryHistoryComponent', () => {
  let fixture: ComponentFixture<TelemetryHistoryComponent>;
  let component: TelemetryHistoryComponent;
  let http: HttpTestingController;

  function reading(deviceId = 1, readingId = 1) {
    return {
      readingId, deviceId, schemaVersion: 1,
      bootId: 'd40189cd-e749-433a-881d-362d78196750',
      sequence: readingId,
      measuredAt: null,
      receivedAt: '2026-11-03T07:00:00Z',
      uptimeMs: 5000,
      simulated: true,
      operatingMode: 'BATTERY',
      battery: { voltageV: 51.2, currentA: -10, socPercent: 0 },
      grid: {
        present: true, voltageV: 230,
        frequencyHz: 50, inputPowerW: -100,
      },
      load: {
        voltageV: 230, currentA: null,
        outputPowerW: 600, loadPercent: null,
      },
    };
  }

  function page(number = 0, deviceId = 1, total = 1) {
    const count = Math.max(0, Math.min(10, total - number * 10));
    return {
      success: true,
      message: 'OK',
      data: {
        content: Array.from({ length: count }, (_, index) =>
          reading(deviceId, number * 10 + index + 1)),
        number,
        size: 10,
        totalElements: total,
        totalPages: Math.ceil(total / 10),
        first: number === 0,
        last: (number + 1) * 10 >= total,
      },
    };
  }

  function request(deviceId = 1, number = 0) {
    const result = http.expectOne(req =>
      req.url === `http://localhost:8080/api/v1/devices/${deviceId}/telemetry/history`
    );
    expect(result.request.params.get('page')).toBe(String(number));
    expect(result.request.params.get('size')).toBe('10');
    return result;
  }

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [TelemetryHistoryComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: PLATFORM_ID, useValue: 'browser' },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TelemetryHistoryComponent);
    component = fixture.componentInstance;
    http = TestBed.inject(HttpTestingController);
    fixture.componentRef.setInput('deviceId', 1);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.destroy();
    http.verify({ ignoreCancelled: true });
  });

  it('renders stored values with null, zero, signs and simulation labels intact', () => {
    request().flush(page());
    fixture.detectChanges();

    const cells = fixture.nativeElement.querySelectorAll('tbody td');
    expect(cells[2].textContent.trim()).toBe('51.2');
    expect(cells[3].textContent.trim()).toBe('-10');
    expect(cells[4].textContent.trim()).toBe('0');
    expect(cells[7].textContent.trim()).toBe('-100');
    expect(cells[9].textContent.trim()).toBe('—');
    expect(cells[11].textContent.trim()).toBe('—');
    expect(cells[12].textContent).toContain('Simulated data');
  });

  it('navigates next and previous pages', () => {
    request().flush(page(0, 1, 11));
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.history-next').click();

    request(1, 1).flush(page(1, 1, 11));
    fixture.detectChanges();
    expect(component.history()?.number).toBe(1);
    expect(fixture.nativeElement.querySelector('.history-next').disabled).toBe(true);

    fixture.nativeElement.querySelector('.history-previous').click();
    request(1, 0).flush(page(0, 1, 11));
    fixture.detectChanges();

    expect(component.history()?.number).toBe(0);
    expect(fixture.nativeElement.querySelector('.history-previous').disabled).toBe(true);
  });

  it('refreshes from page zero', () => {
    request().flush(page(0, 1, 11));
    component.loadPage(1);
    request(1, 1).flush(page(1, 1, 11));
    fixture.detectChanges();

    fixture.nativeElement.querySelector('.history-refresh').click();
    request(1, 0).flush(page(0, 1, 11));

    expect(component.history()?.number).toBe(0);
  });

  it('cancels an old request and clears history when the device changes', () => {
    request().flush(page());
    component.loadPage(0);
    const oldRequest = request();

    fixture.componentRef.setInput('deviceId', 2);
    fixture.detectChanges();

    expect(oldRequest.cancelled).toBe(true);
    expect(component.history()).toBeNull();

    request(2).flush(page(0, 2));
    expect(component.history()?.content[0].deviceId).toBe(2);
  });

  it('cancels a pending request when destroyed', () => {
    const pending = request();
    fixture.destroy();
    expect(pending.cancelled).toBe(true);
    expect(component.history()).toBeNull();
  });

  it.each([401, 403, 404])(
    'clears rows and reports access rejection for HTTP %s',
    status => {
      const rejected: number[] = [];
      component.accessDenied.subscribe(value => rejected.push(value));

      request().flush(
        { success: false },
        { status, statusText: 'Access rejected' }
      );
      fixture.detectChanges();

      expect(component.history()).toBeNull();
      expect(component.accessBlocked()).toBe(true);
      expect(rejected).toEqual([status]);
      expect(fixture.nativeElement.querySelector('tbody')).toBeNull();
    }
  );

  it('offers retry after a temporary failure without fabricated rows', () => {
    request().flush(
      { success: false },
      { status: 500, statusText: 'Server error' }
    );
    fixture.detectChanges();

    expect(component.history()).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Try again');

    component.loadPage(component.requestedPage());
    request().flush(page());

    expect(component.error()).toBeNull();
    expect(component.history()?.content).toHaveLength(1);
  });

  it('shows an empty state for a device with no stored readings', () => {
    request().flush(page(0, 1, 0));
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('No stored readings');
    expect(fixture.nativeElement.querySelector('table')).toBeNull();
  });

  it('rejects readings belonging to a different device', () => {
    request().flush(page(0, 2));
    fixture.detectChanges();

    expect(component.history()).toBeNull();
    expect(component.error()).not.toBeNull();
    expect(fixture.nativeElement.querySelector('tbody')).toBeNull();
  });
});