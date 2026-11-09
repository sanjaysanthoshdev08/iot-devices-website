/**
 * Smooth (eased) mouse-wheel / trackpad scrolling for a single scroll container.
 *
 * It only changes HOW scrollTop moves; scrollTop itself is still the source of truth, so all
 * existing scroll-linked code (nav highlighting, frame scrubbing, nav-click animations) keeps working.
 * Touch, keyboard and scrollbar dragging stay native. Nested scrollable areas keep native scrolling.
 */
export interface SmoothWheelHandle {
  destroy(): void;
}

export function enableSmoothWheel(scroller: HTMLElement, ease = 0.11): SmoothWheelHandle {
  const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)');
  let target = scroller.scrollTop;
  let lastSet = scroller.scrollTop;
  let raf = 0;

  const maxScroll = () => Math.max(0, scroller.scrollHeight - scroller.clientHeight);

  // True when an inner element under the pointer can scroll itself in the wheel direction.
  const innerCanScroll = (start: EventTarget | null, dy: number): boolean => {
    let el = start as HTMLElement | null;
    while (el && el !== scroller && el instanceof HTMLElement) {
      const oy = getComputedStyle(el).overflowY;
      if ((oy === 'auto' || oy === 'scroll') && el.scrollHeight > el.clientHeight + 1) {
        const atTop = el.scrollTop <= 0;
        const atBottom = el.scrollTop + el.clientHeight >= el.scrollHeight - 1;
        if ((dy < 0 && !atTop) || (dy > 0 && !atBottom)) return true;
      }
      el = el.parentElement;
    }
    return false;
  };

  const step = () => {
    // Someone else (nav-click animation, scrollTo, scrollbar drag) moved the scroller: follow it.
    if (Math.abs(scroller.scrollTop - lastSet) > 1.5) {
      target = scroller.scrollTop;
      lastSet = scroller.scrollTop;
      raf = 0;
      return;
    }
    const diff = target - scroller.scrollTop;
    if (Math.abs(diff) < 0.4) {
      scroller.scrollTop = target;
      lastSet = scroller.scrollTop;
      raf = 0;
      return;
    }
    scroller.scrollTop += diff * ease;
    lastSet = scroller.scrollTop;
    raf = requestAnimationFrame(step);
  };

  const onWheel = (e: WheelEvent) => {
    if (e.ctrlKey || e.defaultPrevented || reduce?.matches) return; // pinch-zoom / reduced motion
    if (Math.abs(e.deltaX) > Math.abs(e.deltaY)) return; // horizontal gesture
    if (innerCanScroll(e.target, e.deltaY)) return;

    let dy = e.deltaY;
    if (e.deltaMode === 1) dy *= 16;
    else if (e.deltaMode === 2) dy *= scroller.clientHeight;

    // If another animation moved the scroller since our last frame, start from where it is now.
    if (!raf && Math.abs(scroller.scrollTop - lastSet) > 1.5) {
      target = scroller.scrollTop;
    }
    e.preventDefault();
    target = Math.min(Math.max(target + dy, 0), maxScroll());
    if (!raf) {
      lastSet = scroller.scrollTop;
      raf = requestAnimationFrame(step);
    }
  };

  scroller.addEventListener('wheel', onWheel, { passive: false });

  return {
    destroy() {
      scroller.removeEventListener('wheel', onWheel);
      if (raf) cancelAnimationFrame(raf);
    }
  };
}
