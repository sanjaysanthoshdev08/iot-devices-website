import { Component, signal, computed, HostListener, ViewChild, ElementRef, AfterViewInit, OnDestroy, Inject, PLATFORM_ID } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { isPlatformBrowser } from '@angular/common';

export interface HykonBranch {
  id: string;
  unitName: string;
  place: string;
  state: string;
  address: string;
  phone: string;
  isHeadquarters?: boolean;
  status: string;
}

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements AfterViewInit, OnDestroy {
  constructor(@Inject(PLATFORM_ID) private platformId: object) {}
  @ViewChild('aboutCanvas') aboutCanvasRef!: ElementRef<HTMLCanvasElement>;

  // Navigation links
  navLinks = signal([
    { label: 'Home', active: true },
    { label: 'About', active: false },
    { label: 'Product', active: false },
    { label: 'Features', active: false },
    { label: 'Contact', active: false }
  ]);

  // Product image lightbox modal
  isProductModalOpen = signal<boolean>(false);

  openProductModal() {
    this.isProductModalOpen.set(true);
  }

  closeProductModal() {
    this.isProductModalOpen.set(false);
  }

  // Scroll animation frames
  private readonly TOTAL_FRAMES = 240;
  private frames: HTMLImageElement[] = [];
  private currentFrame = 0;
  private animationFrameId: number | null = null;
  private scrollListener: (() => void) | null = null;
  private resizeListener: (() => void) | null = null;

  // About Us text overlay signals (driven by scroll progress)
  aboutTextOpacity    = signal<number>(0);
  aboutTextTranslateY = signal<number>(40);
  aboutTextScale      = signal<number>(0.92);

  // Avatars list
  avatars = [
    { src: 'assets/user1.jpg', name: 'Alex M.' },
    { src: 'assets/user2.jpg', name: 'Sarah K.' },
    { src: 'assets/user3.jpg', name: 'Elena R.' },
    { src: 'assets/user4.jpg', name: 'Maya L.' },
    { src: 'assets/user5.jpg', name: 'Jessica T.' }
  ];

  // Hykon India Ltd Branches & Units in India
  hykonBranches: HykonBranch[] = [
    {
      id: 'kochi-kinfra',
      unitName: 'Hykon India Ltd - Kochi Unit',
      place: 'Kakkanad Kinfra',
      state: 'Kochi, Kerala',
      address: 'Kinfra Export Promotion Industrial Park, Kakkanad, Kochi - 682030',
      phone: '+91 484 241 3300',
      status: 'Active Unit'
    },
    {
      id: 'hq-thrissur',
      unitName: 'Hykon India Ltd - Headquarters',
      place: 'East Fort',
      state: 'Thrissur, Kerala',
      address: 'Hykon Tower, East Fort, Thrissur - 680005',
      phone: '+91 487 244 1414',
      isHeadquarters: true,
      status: 'Headquarters'
    },
    {
      id: 'bengaluru-unit',
      unitName: 'Hykon India Ltd - Bengaluru Branch',
      place: 'Indiranagar',
      state: 'Bengaluru, Karnataka',
      address: '#102, 100 Feet Road, Indiranagar, Bengaluru - 560038',
      phone: '+91 80 2520 8900',
      status: 'Active Branch'
    },
    {
      id: 'chennai-unit',
      unitName: 'Hykon India Ltd - Chennai Branch',
      place: 'Guindy Mount Road',
      state: 'Chennai, Tamil Nadu',
      address: 'Old No. 120, Mount Road, Guindy, Chennai - 600032',
      phone: '+91 44 2234 5020',
      status: 'Active Branch'
    },
    {
      id: 'coimbatore-unit',
      unitName: 'Hykon India Ltd - Coimbatore Unit',
      place: 'Peelamedu',
      state: 'Coimbatore, Tamil Nadu',
      address: '1432, Avinashi Road, Peelamedu, Coimbatore - 641004',
      phone: '+91 422 257 8890',
      status: 'Active Unit'
    },
    {
      id: 'trivandrum-unit',
      unitName: 'Hykon India Ltd - Trivandrum Branch',
      place: 'Pattom',
      state: 'Trivandrum, Kerala',
      address: 'TC 4/1250, Kowdiar Road, Pattom, Trivandrum - 695004',
      phone: '+91 471 244 8833',
      status: 'Active Branch'
    },
    {
      id: 'hyderabad-unit',
      unitName: 'Hykon India Ltd - Hyderabad Branch',
      place: 'Jubilee Hills',
      state: 'Hyderabad, Telangana',
      address: 'Plot No. 44, Cyber Hills, Jubilee Hills, Hyderabad - 500033',
      phone: '+91 40 2355 7711',
      status: 'Active Branch'
    }
  ];

  selectedBranchId = signal<string>('kochi-kinfra');
  showBranchDropdown = signal<boolean>(false);
  
  // Side boxes pop-up animation state on scroll
  boxesVisible = signal<boolean>(false);

  // Hero Title interactive motion signals
  titleMouseX = signal<number>(0);
  titleMouseY = signal<number>(0);
  titleTiltX = signal<number>(0);
  titleTiltY = signal<number>(0);

  onHeroTitleMouseMove(event: MouseEvent) {
    const target = event.currentTarget as HTMLElement;
    if (!target) return;
    const rect = target.getBoundingClientRect();
    const x = event.clientX - rect.left;
    const y = event.clientY - rect.top;
    const centerX = rect.width / 2;
    const centerY = rect.height / 2;

    const tiltY = ((x - centerX) / centerX) * 5;
    const tiltX = -((y - centerY) / centerY) * 4;

    this.titleMouseX.set(Math.round(x));
    this.titleMouseY.set(Math.round(y));
    this.titleTiltX.set(Number(tiltX.toFixed(2)));
    this.titleTiltY.set(Number(tiltY.toFixed(2)));
  }

  onHeroTitleMouseLeave() {
    this.titleTiltX.set(0);
    this.titleTiltY.set(0);
  }

  selectedBranch = computed(() =>
    this.hykonBranches.find(b => b.id === this.selectedBranchId()) || this.hykonBranches[0]
  );

  @HostListener('window:scroll', [])
  onWindowScroll() {
    this.checkScrollPosition();
  }

  onElementScroll(event: Event) {
    const target = event.target as HTMLElement;
    if (target && target.scrollTop > 30) {
      this.boxesVisible.set(true);
    }
    // Drive canvas animation via viewport-wrapper scroll
    this.updateCanvasFrame(target);
  }

  checkScrollPosition() {
    const scrollPos = window.scrollY || document.documentElement.scrollTop || document.body.scrollTop || 0;
    if (scrollPos > 30) {
      this.boxesVisible.set(true);
    }
  }

  // ---- Scroll-driven Canvas Animation ----

  ngAfterViewInit() {
    if (isPlatformBrowser(this.platformId)) {
      this.preloadFrames();
      // Redraw at correct DPR on resize / zoom change
      this.resizeListener = () => this.drawFrame(this.currentFrame);
      window.addEventListener('resize', this.resizeListener);
    }
  }

  ngOnDestroy() {
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
    }
    if (this.resizeListener) {
      window.removeEventListener('resize', this.resizeListener);
    }
  }

  private preloadFrames() {
    let loaded = 0;
    for (let i = 1; i <= this.TOTAL_FRAMES; i++) {
      const img = new Image();
      const num = String(i).padStart(3, '0');
      img.src = `assets/ezgif-frame-${num}.jpg`;
      img.onload = () => {
        loaded++;
        if (loaded === 1) {
          // Draw first frame as soon as it's ready
          this.drawFrame(0);
        }
      };
      this.frames[i - 1] = img;
    }
  }

  private drawFrame(index: number) {
    if (!this.aboutCanvasRef) return;
    const canvas = this.aboutCanvasRef.nativeElement;
    const ctx = canvas.getContext('2d', { alpha: false });
    if (!ctx) return;
    const img = this.frames[index];
    if (!img || !img.complete || img.naturalWidth === 0) return;

    const rect   = canvas.getBoundingClientRect();
    const dpr    = window.devicePixelRatio || 1;

    // Set the canvas backing buffer to native screen resolution
    const targetW = Math.round(rect.width  * dpr);
    const targetH = Math.round(rect.height * dpr);

    if (canvas.width !== targetW || canvas.height !== targetH) {
      canvas.width  = targetW;
      canvas.height = targetH;
      // Scale all draw calls so 1 CSS pixel = dpr backing pixels
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    // Highest-quality image smoothing
    ctx.imageSmoothingEnabled = true;
    (ctx as any).imageSmoothingQuality = 'high';

    // Cover-fill: centre image, preserving aspect ratio
    const cssW  = rect.width;
    const cssH  = rect.height;
    const scale = Math.max(cssW / img.naturalWidth, cssH / img.naturalHeight);
    const drawW = img.naturalWidth  * scale;
    const drawH = img.naturalHeight * scale;
    const ox    = (cssW - drawW) / 2;
    const oy    = (cssH - drawH) / 2;

    ctx.clearRect(0, 0, cssW, cssH);
    ctx.drawImage(img, ox, oy, drawW, drawH);
  }

  private isNavClickScrolling = false;

  private updateCanvasFrame(scroller: HTMLElement) {
    if (!isPlatformBrowser(this.platformId)) return;
    if (this.isNavClickScrolling) return;
    const section = document.getElementById('about-section');
    if (!section) return;

    const sectionTop    = section.offsetTop;
    const sectionHeight = section.scrollHeight;
    const viewH         = scroller.clientHeight;
    const scrollTop     = scroller.scrollTop;

    // Scroll range: from when the section enters view to when it exits
    const start = sectionTop;
    const end   = sectionTop + sectionHeight - viewH;
    const progress = Math.min(Math.max((scrollTop - start) / (end - start), 0), 1);

    const frameIndex = Math.min(
      Math.floor(progress * (this.TOTAL_FRAMES - 1)),
      this.TOTAL_FRAMES - 1
    );

    if (frameIndex !== this.currentFrame) {
      this.currentFrame = frameIndex;
      if (this.animationFrameId) cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = requestAnimationFrame(() => this.drawFrame(frameIndex));
    }

    // ---- Drive About Us text overlay ----
    // Text is fully visible from progress 0.28 → 0.72, fades in/out on the edges
    const TEXT_IN_START  = 0.28;  // start fading in
    const TEXT_IN_END    = 0.40;  // fully visible
    const TEXT_OUT_START = 0.65;  // start fading out
    const TEXT_OUT_END   = 0.78;  // fully gone

    let opacity = 0;
    let translateY = 40;
    let scale = 0.92;

    if (progress >= TEXT_IN_START && progress <= TEXT_OUT_END) {
      if (progress < TEXT_IN_END) {
        // Fade in
        const t = (progress - TEXT_IN_START) / (TEXT_IN_END - TEXT_IN_START);
        const ease = t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t; // ease-in-out quad
        opacity    = ease;
        translateY = 40 * (1 - ease);
        scale      = 0.92 + 0.08 * ease;
      } else if (progress > TEXT_OUT_START) {
        // Fade out
        const t = (progress - TEXT_OUT_START) / (TEXT_OUT_END - TEXT_OUT_START);
        const ease = t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
        opacity    = 1 - ease;
        translateY = -30 * ease;
        scale      = 1 - 0.06 * ease;
      } else {
        // Fully visible plateau
        opacity    = 1;
        translateY = 0;
        scale      = 1;
      }
    }

    this.aboutTextOpacity.set(Math.max(0, Math.min(1, opacity)));
    this.aboutTextTranslateY.set(translateY);
    this.aboutTextScale.set(scale);
  }

  setActiveNav(index: number) {
    this.navLinks.update(links =>
      links.map((link, i) => ({ ...link, active: i === index }))
    );

    const scroller = document.querySelector('.viewport-wrapper') as HTMLElement;
    if (!scroller) return;

    let targetEl: HTMLElement | null = null;

    if (index === 0) {
      targetEl = document.querySelector('.home-hero-section');
    } else if (index === 1) {
      targetEl = document.getElementById('about-section');
    } else if (index === 2 || index === 3) {
      targetEl = document.getElementById('product-section');
    } else if (index === 4) {
      targetEl = document.getElementById('contact-section') || document.querySelector('.card-transfer-widget');
    }

    if (!targetEl) return;

    // Temporarily collapse 800vh About section so scroll flows fast & smooth (450ms) without 800vh animation delay
    scroller.classList.add('nav-scrolling');
    this.isNavClickScrolling = true;

    const startY = scroller.scrollTop;
    const targetY = targetEl.offsetTop;

    this.animateNavScroll(scroller, targetEl, startY, targetY, 450);
  }

  private animateNavScroll(
    scroller: HTMLElement,
    targetEl: HTMLElement,
    startY: number,
    targetY: number,
    duration: number = 450
  ) {
    const diffY = targetY - startY;

    if (Math.abs(diffY) < 5) {
      scroller.scrollTop = targetY;
      this.finishNavScroll(scroller, targetEl);
      return;
    }

    const startTime = performance.now();
    const easeInOutCubic = (t: number) =>
      t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

    const step = (currentTime: number) => {
      const elapsed = currentTime - startTime;
      const progress = Math.min(elapsed / duration, 1);
      const easedProgress = easeInOutCubic(progress);

      scroller.scrollTop = startY + diffY * easedProgress;

      if (progress < 1) {
        requestAnimationFrame(step);
      } else {
        this.finishNavScroll(scroller, targetEl);
      }
    };

    requestAnimationFrame(step);
  }

  private finishNavScroll(scroller: HTMLElement, targetEl: HTMLElement) {
    scroller.classList.remove('nav-scrolling');
    // Set final position to target element's full offsetTop while isNavClickScrolling is still true
    scroller.scrollTop = targetEl.offsetTop;

    // Reset isNavClickScrolling after scroll event has settled so canvas frame animation never triggers
    setTimeout(() => {
      this.isNavClickScrolling = false;
    }, 150);
  }

  toggleBranchDropdown() {
    this.showBranchDropdown.update(v => !v);
  }

  selectBranch(id: string) {
    this.selectedBranchId.set(id);
    this.showBranchDropdown.set(false);
  }

  onContactBranch() {
    const branch = this.selectedBranch();
    alert(`Connecting to ${branch.unitName} (${branch.place})...`);
  }
}
