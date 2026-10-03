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

  // Navigation links with active state
  navLinks = signal([
    { label: 'Home', active: true },
    { label: 'About', active: false },
    { label: 'Products', active: false },
    { label: 'Features', active: false },
    { label: 'Contact', active: false }
  ]);

  // Navbar visibility
  isNavbarHidden = signal<boolean>(false);

  // Transition state when navigating via navbar click (locks active link & bypasses canvas scrub)
  isNavClickScrolling = signal<boolean>(false);

  // Scroll animation frames
  private readonly TOTAL_FRAMES = 240;
  private frames: HTMLImageElement[] = [];
  private currentFrame = 0;
  private animationFrameId: number | null = null;
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

    // Only update active nav link on manual mouse scroll (not during nav click animation)
    if (!this.isNavClickScrolling()) {
      const scrollTop = target.scrollTop;
      const aboutEl = document.getElementById('about-section');
      const productEl = document.getElementById('product-section');

      const viewH = target.clientHeight || window.innerHeight;
      const aboutTop = aboutEl ? aboutEl.offsetTop : viewH;
      const productTop = productEl ? productEl.offsetTop : viewH * 2;

      let activeIndex = 0;
      if (scrollTop >= productTop - viewH * 0.4) {
        activeIndex = 2; // Products
      } else if (scrollTop >= aboutTop - viewH * 0.4) {
        activeIndex = 1; // About
      } else {
        activeIndex = 0; // Home
      }

      this.updateActiveNavIndex(activeIndex);
      this.updateCanvasFrame(target);
    }
  }

  private updateActiveNavIndex(index: number) {
    const current = this.navLinks();
    if (current[index] && !current[index].active) {
      this.navLinks.update(links =>
        links.map((link, i) => ({ ...link, active: i === index }))
      );
    }
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

    const targetW = Math.round(rect.width  * dpr);
    const targetH = Math.round(rect.height * dpr);

    if (canvas.width !== targetW || canvas.height !== targetH) {
      canvas.width  = targetW;
      canvas.height = targetH;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    ctx.imageSmoothingEnabled = true;
    (ctx as any).imageSmoothingQuality = 'high';

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

  private updateCanvasFrame(scroller: HTMLElement) {
    if (!isPlatformBrowser(this.platformId)) return;
    if (this.isNavClickScrolling()) return;
    const section = document.getElementById('about-section');
    if (!section) return;

    const sectionTop    = section.offsetTop;
    const sectionHeight = section.scrollHeight;
    const viewH         = scroller.clientHeight;
    const scrollTop     = scroller.scrollTop;

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

    // Drive About Us text overlay
    const TEXT_IN_START  = 0.28;
    const TEXT_IN_END    = 0.40;
    const TEXT_OUT_START = 0.65;
    const TEXT_OUT_END   = 0.78;

    let opacity = 0;
    let translateY = 40;
    let scale = 0.92;

    if (progress >= TEXT_IN_START && progress <= TEXT_OUT_END) {
      if (progress < TEXT_IN_END) {
        const t = (progress - TEXT_IN_START) / (TEXT_IN_END - TEXT_IN_START);
        const ease = t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
        opacity    = ease;
        translateY = 40 * (1 - ease);
        scale      = 0.92 + 0.08 * ease;
      } else if (progress > TEXT_OUT_START) {
        const t = (progress - TEXT_OUT_START) / (TEXT_OUT_END - TEXT_OUT_START);
        const ease = t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
        opacity    = 1 - ease;
        translateY = -30 * ease;
        scale      = 1 - 0.06 * ease;
      } else {
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
    // Lock underline immediately to clicked nav item
    this.updateActiveNavIndex(index);

    const scroller = document.querySelector('.viewport-wrapper') as HTMLElement;
    if (!scroller) return;

    if (index === 2 || index === 3) {
      // Products clicked: smooth, continuous, slower flow down: Home -> About -> Products
      this.isNavClickScrolling.set(true);

      const productEl = document.getElementById('product-section');
      if (!productEl) return;

      const startScroll = scroller.scrollTop;
      const targetScroll = productEl.offsetTop;
      const distance = targetScroll - startScroll;
      // Duration set to 3600ms (3.6 seconds) for an ultra-smooth, slower, luxurious flow
      const duration = 3600;
      let startTime: number | null = null;

      const easeInOutCubic = (t: number) =>
        t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

      const animateScroll = (timestamp: number) => {
        if (!startTime) startTime = timestamp;
        const elapsed = timestamp - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const easedProgress = easeInOutCubic(progress);

        scroller.scrollTop = startScroll + distance * easedProgress;

        if (progress < 1) {
          requestAnimationFrame(animateScroll);
        } else {
          // Finished flow, lock active link to Products
          this.updateActiveNavIndex(2);
          this.isNavClickScrolling.set(false);
        }
      };

      requestAnimationFrame(animateScroll);
      return;
    }

    if (index === 0) {
      // Home clicked
      this.isNavClickScrolling.set(true);
      const startScroll = scroller.scrollTop;
      const targetScroll = 0;
      const distance = targetScroll - startScroll;
      const duration = 2200;
      let startTime: number | null = null;

      const easeInOutCubic = (t: number) =>
        t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

      const animateScroll = (timestamp: number) => {
        if (!startTime) startTime = timestamp;
        const elapsed = timestamp - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const easedProgress = easeInOutCubic(progress);

        scroller.scrollTop = startScroll + distance * easedProgress;

        if (progress < 1) {
          requestAnimationFrame(animateScroll);
        } else {
          this.updateActiveNavIndex(0);
          this.isNavClickScrolling.set(false);
        }
      };

      requestAnimationFrame(animateScroll);
    } else if (index === 1) {
      // About clicked
      const aboutEl = document.getElementById('about-section');
      if (!aboutEl) return;
      this.isNavClickScrolling.set(true);
      const startScroll = scroller.scrollTop;
      const targetScroll = aboutEl.offsetTop;
      const distance = targetScroll - startScroll;
      const duration = 2200;
      let startTime: number | null = null;

      const easeInOutCubic = (t: number) =>
        t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;

      const animateScroll = (timestamp: number) => {
        if (!startTime) startTime = timestamp;
        const elapsed = timestamp - startTime;
        const progress = Math.min(elapsed / duration, 1);
        const easedProgress = easeInOutCubic(progress);

        scroller.scrollTop = startScroll + distance * easedProgress;

        if (progress < 1) {
          requestAnimationFrame(animateScroll);
        } else {
          this.updateActiveNavIndex(1);
          this.isNavClickScrolling.set(false);
        }
      };

      requestAnimationFrame(animateScroll);
    } else if (index === 4) {
      const contactEl = document.getElementById('contact-section') || document.querySelector('.card-transfer-widget');
      contactEl?.scrollIntoView({ behavior: 'smooth' });
    }
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
