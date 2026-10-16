import { Component, signal, computed, HostListener, ViewChild, ElementRef, AfterViewInit, OnDestroy, Inject, PLATFORM_ID, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { isPlatformBrowser } from '@angular/common';

import { AuthService } from './services/auth.service';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';

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

export interface InfinityParticle {
  normX: number;
  normY: number;
  ox: number;
  oy: number;
  x: number;
  y: number;
  vx: number;
  vy: number;
  r: number;
  colorTier: number;
  isCore: boolean;
}


@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.html',
  styleUrl: './app.scss'
})
export class App implements AfterViewInit, OnDestroy {
  private readonly authService = inject(AuthService);
  private readonly http = inject(HttpClient);

  constructor(
    @Inject(PLATFORM_ID) private platformId: object
  ) { }
  @ViewChild('aboutCanvas') aboutCanvasRef!: ElementRef<HTMLCanvasElement>;
  @ViewChild('profileContainerRef') profileContainerRef!: ElementRef<HTMLElement>;
  @ViewChild('infinityCanvasRef') infinityCanvasRef?: ElementRef<HTMLCanvasElement>;
  @ViewChild('infinityContainerRef') infinityContainerRef?: ElementRef<HTMLElement>;

  // Authentication state exposed from AuthService
  readonly isAuthenticated = this.authService.isAuthenticated;
  readonly currentUser = this.authService.currentUser;

  // Profile avatar initial letter (uppercase)
  readonly userInitial = computed(() => {
    const user = this.currentUser();
    const name = user?.name || user?.email || 'U';
    return name.trim().charAt(0).toUpperCase();
  });

  // Profile dropdown menu state
  showProfileDropdown = signal<boolean>(false);

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

  // Login Modal State
  isLoginModalOpen = signal<boolean>(false);
  loginEmail = signal<string>('');
  loginPassword = signal<string>('');
  loginName = signal<string>('');
  loginConfirmPassword = signal<string>('');
  showPassword = signal<boolean>(false);
  isSignUpMode = signal<boolean>(false);
  rememberMe = signal<boolean>(false);

  // Validation Error Signals
  emailError = signal<string>('');
  passwordError = signal<string>('');
  nameError = signal<string>('');
  confirmPasswordError = signal<string>('');

  // Authentication request state
  isSubmitting = signal<boolean>(false);
  authError = signal<string>('');
  authSuccess = signal<string>('');

  // Contact Section Form State
  contactFirstName = signal<string>('');
  contactLastName = signal<string>('');
  contactEmail = signal<string>('');
  contactPhone = signal<string>('');
  contactMessage = signal<string>('');
  contactSubmitting = signal<boolean>(false);
  contactSubmitSuccess = signal<string>('');
  contactSubmitError = signal<string>('');

  // ===== Testimonials & CTA Section State =====
  activeTestimonialIndex = signal<number>(2);
  readonly testimonialDots = [0, 1, 2, 3, 4, 5];

  readonly testimonials = signal([
    {
      id: 0,
      quote: "The combination of connected devices and a clear monitoring dashboard offers a smarter approach to understanding inverter operation. Essential information becomes easier to access and interpret.",
      userName: "IoT System User",
      userRole: "Connected Power Monitoring",
      userImage: "assets/user3.jpg"
    },
    {
      id: 1,
      quote: "HYKON's intelligent monitoring interface makes it easier to understand my inverter's performance. I can check important system parameters in one place and stay informed about my power backup system.",
      userName: "Residential User",
      userRole: "Home Power Backup",
      userImage: "assets/user1.jpg"
    },
    {
      id: 2,
      quote: "Having key inverter readings available through a single dashboard provides a clearer view of system status. It helps make power monitoring more convenient and organized.",
      userName: "Business User",
      userRole: "Commercial Power Management",
      userImage: "assets/user2.jpg"
    },
    {
      id: 3,
      quote: "The combination of connected devices and a clear monitoring dashboard offers a smarter approach to understanding inverter operation. Essential information becomes easier to access and interpret.",
      userName: "IoT System User",
      userRole: "Connected Power Monitoring",
      userImage: "assets/user3.jpg"
    },
    {
      id: 4,
      quote: "HYKON's intelligent monitoring interface makes it easier to understand my inverter's performance. I can check important system parameters in one place and stay informed about my power backup system.",
      userName: "Residential User",
      userRole: "Home Power Backup",
      userImage: "assets/user4.jpg"
    },
    {
      id: 5,
      quote: "Having key inverter readings available through a single dashboard provides a clearer view of system status. It helps make power monitoring more convenient and organized.",
      userName: "Business User",
      userRole: "Commercial Power Management",
      userImage: "assets/user5.jpg"
    }
  ]);

  readonly visibleTestimonials = computed(() => {
    const list = this.testimonials();
    const active = this.activeTestimonialIndex();
    const len = list.length;
    const leftIdx = (active - 1 + len) % len;
    const centerIdx = active;
    const rightIdx = (active + 1) % len;
    return [list[leftIdx], list[centerIdx], list[rightIdx]];
  });

  prevTestimonial() {
    this.activeTestimonialIndex.update(idx => (idx > 0 ? idx - 1 : 5));
  }

  nextTestimonial() {
    this.activeTestimonialIndex.update(idx => (idx < 5 ? idx + 1 : 0));
  }

  setTestimonialIndex(index: number) {
    this.activeTestimonialIndex.set(index);
  }

  onExploreDashboard() {
    this.setActiveNav(2);
  }

  onDiscoverFeatures() {
    this.setActiveNav(3);
  }

  scrollToTop() {
    const scroller = document.querySelector('.viewport-wrapper');
    if (scroller) {
      scroller.scrollTo({ top: 0, behavior: 'smooth' });
    } else {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  openLoginModal(isSignUp: boolean = false) {
    this.clearErrors();
    this.isSignUpMode.set(isSignUp);
    this.isLoginModalOpen.set(true);


  }

  openSignUpModal() {
    this.openLoginModal(true);
  }

  closeLoginModal() {
    this.isLoginModalOpen.set(false);
    this.clearErrors();
  }

  toggleProfileDropdown(event?: Event) {
    if (event) {
      event.stopPropagation();
    }
    this.showProfileDropdown.update(v => !v);
  }

  closeProfileDropdown() {
    this.showProfileDropdown.set(false);
  }

  onLogout() {
    this.closeProfileDropdown();
    this.authService.logout().subscribe({
      next: () => {
        this.redirectToHome();
      },
      error: () => {
        this.authService.clearSession();
        this.redirectToHome();
      }
    });
  }

  redirectToHome() {
    this.setActiveNav(0);
    this.isNavbarHidden.set(false);
    if (isPlatformBrowser(this.platformId)) {
      const scroller = document.querySelector('.viewport-wrapper') as HTMLElement;
      if (scroller) {
        scroller.scrollTo({ top: 0, behavior: 'smooth' });
      }
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent) {
    if (this.showProfileDropdown()) {
      const target = event.target as Node;
      if (this.profileContainerRef?.nativeElement && !this.profileContainerRef.nativeElement.contains(target)) {
        this.closeProfileDropdown();
      }
    }
  }

  @HostListener('document:keydown.escape')
  onEscapeKey() {
    if (this.showProfileDropdown()) {
      this.closeProfileDropdown();
    }
    if (this.isLoginModalOpen()) {
      this.closeLoginModal();
    }
  }

  togglePasswordVisibility() {
    this.showPassword.update(v => !v);
  }

  toggleAuthMode() {
    this.isSignUpMode.update(v => !v);
    this.clearErrors();
  }

  clearErrors() {
    this.emailError.set('');
    this.passwordError.set('');
    this.nameError.set('');
    this.confirmPasswordError.set('');
  }

  
  onLoginSubmit(event: Event) {
    event.preventDefault();
    this.clearErrors();

    let hasError = false;

    if (this.isSignUpMode()) {
      // Signup validation
      if (!this.loginName().trim()) {
        this.nameError.set('Name is required');
        hasError = true;
      }

      if (!this.loginEmail().trim()) {
        this.emailError.set('Email is required');
        hasError = true;
      } else if (!this.loginEmail().includes('@')) {
        this.emailError.set('Please enter a valid email address');
        hasError = true;
      }

      if (!this.loginPassword()) {
        this.passwordError.set('Password is required');
        hasError = true;
      }

      if (this.loginPassword() !== this.loginConfirmPassword()) {
        this.confirmPasswordError.set('Passwords do not match');
        hasError = true;
      }

      if (hasError) return;

      // Connect Signup to the Spring Boot backend
      this.isSubmitting.set(true);
      this.authError.set('');
      this.authSuccess.set('');

      this.authService.register({
        name: this.loginName().trim(),
        email: this.loginEmail().trim().toLowerCase(),
        password: this.loginPassword(),
        confirmPassword: this.loginConfirmPassword()
      }).subscribe({
        
        
        next: (response) => {
          this.isSubmitting.set(false);

          // Clear Signup fields after successful registration.
          this.loginName.set('');
          this.loginEmail.set('');
          this.loginPassword.set('');
          this.loginConfirmPassword.set('');

          // Switch from Signup mode to Login mode.
          this.isSignUpMode.set(false);

          // Clear previous validation errors.
          this.clearErrors();

          // Show registration success message.
          this.authSuccess.set(
            response.message ||
            'Registration successful. You can now log in.'
          );
        },

        error: (error: HttpErrorResponse) => {
          this.isSubmitting.set(false);

          const body = error.error;
          const errors = body?.errors;

          if (errors?.name) {
            this.nameError.set(errors.name);
          }

          if (errors?.email) {
            this.emailError.set(errors.email);
          }

          if (errors?.password) {
            this.passwordError.set(errors.password);
          }

          if (errors?.confirmPassword) {
            this.confirmPasswordError.set(errors.confirmPassword);
          }

          this.authError.set(
            errors
              ? ''
              : body?.message || 'Registration failed. Please try again.'
          );
        }
      });

    } else {
      // Login validation
      if (!this.loginEmail().trim()) {
        this.emailError.set('Email is required');
        hasError = true;
      } else if (!this.loginEmail().includes('@')) {
        this.emailError.set('Please enter a valid email address');
        hasError = true;
      }

      if (!this.loginPassword()) {
        this.passwordError.set('Password is required');
        hasError = true;
      }

      if (hasError) return;

      // Connect Login to the Spring Boot backend
      this.isSubmitting.set(true);
      this.authError.set('');
      this.authSuccess.set('');

      this.authService.login({
        email: this.loginEmail().trim().toLowerCase(),
        password: this.loginPassword()
      }).subscribe({
        next: (response) => {
this.isSubmitting.set(false);
this.authSuccess.set(response.message || 'Login successful.');
this.loginPassword.set('');

// Close the Login modal after successful login.
this.closeLoginModal();

          // Return to the Home page.
          this.redirectToHome();
        },
        error: (error: HttpErrorResponse) => {
          this.isSubmitting.set(false);

          const body = error.error;
          const errors = body?.errors;

          if (errors?.email) {
            this.emailError.set(errors.email);
          }

          if (errors?.password) {
            this.passwordError.set(errors.password);
          }

          this.authError.set(
            errors
              ? ''
              : body?.message || 'Login failed. Please try again.'
          );
        }
      });
    }
  }

  // Transition state when navigating via navbar click (locks active link & bypasses canvas scrub)
  isNavClickScrolling = signal<boolean>(false);

  // Scroll animation frames
  private readonly TOTAL_FRAMES = 240;
  private frames: HTMLImageElement[] = [];
  private currentFrame = 0;
  private animationFrameId: number | null = null;
  private resizeListener: (() => void) | null = null;

  // About Us text overlay signals (driven by scroll progress)
  aboutTextOpacity = signal<number>(1);
  aboutTextTranslateY = signal<number>(0);
  aboutTextScale = signal<number>(1);

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

    // Hide navbar once the user has scrolled past the home hero section
    const homeEl = document.querySelector('.home-hero-section') as HTMLElement;
    const homeHeight = homeEl ? homeEl.offsetHeight : (target.clientHeight || window.innerHeight);
    this.isNavbarHidden.set(target.scrollTop >= homeHeight * 0.6);

    // Only update active nav link on manual mouse scroll (not during nav click animation)
    if (!this.isNavClickScrolling()) {
      const scrollTop = target.scrollTop;
      const aboutEl = document.getElementById('about-section');
      const productEl = document.getElementById('product-section');
      const featuresEl = document.getElementById('features-section');
      const contactEl = document.getElementById('contact-section');

      const viewH = target.clientHeight || window.innerHeight;
      const aboutTop = aboutEl ? aboutEl.offsetTop : viewH;
      const productTop = productEl ? productEl.offsetTop : viewH * 2;
      const featuresTop = featuresEl ? featuresEl.offsetTop : viewH * 3;
      const contactTop = contactEl ? contactEl.offsetTop : viewH * 4;

      let activeIndex = 0;
      if (scrollTop >= contactTop - viewH * 0.4) {
        activeIndex = 4; // Contact
      } else if (scrollTop >= featuresTop - viewH * 0.4) {
        activeIndex = 3; // Features
      } else if (scrollTop >= productTop - viewH * 0.4) {
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
      this.updateFeaturesScale();
      this.initInfinityParticleSystem();
      this.resizeListener = () => {
        this.drawFrame(this.currentFrame);
        this.updateFeaturesScale();
      };
      window.addEventListener('resize', this.resizeListener);
    }
  }

  updateFeaturesScale() {
    if (!isPlatformBrowser(this.platformId)) return;
    const container = document.getElementById('features-section');
    if (container) {
      const w = container.clientWidth || window.innerWidth;
      const h = container.clientHeight || window.innerHeight;
      const scale = Math.min(w / 1376, h / 768);
      document.documentElement.style.setProperty('--features-scale', String(scale));
    }
  }

  ngOnDestroy() {
    if (this.animationFrameId) {
      cancelAnimationFrame(this.animationFrameId);
    }
    if (this.resizeListener) {
      window.removeEventListener('resize', this.resizeListener);
    }
    this.destroyInfinityParticleSystem();
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

    const rect = canvas.getBoundingClientRect();
    const dpr = window.devicePixelRatio || 1;

    const targetW = Math.round(rect.width * dpr);
    const targetH = Math.round(rect.height * dpr);

    if (canvas.width !== targetW || canvas.height !== targetH) {
      canvas.width = targetW;
      canvas.height = targetH;
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }

    ctx.imageSmoothingEnabled = true;
    (ctx as any).imageSmoothingQuality = 'high';

    const cssW = rect.width;
    const cssH = rect.height;
    const scale = Math.max(cssW / img.naturalWidth, cssH / img.naturalHeight);
    const drawW = img.naturalWidth * scale;
    const drawH = img.naturalHeight * scale;
    const ox = (cssW - drawW) / 2;
    const oy = (cssH - drawH) / 2;

    ctx.clearRect(0, 0, cssW, cssH);
    ctx.drawImage(img, ox, oy, drawW, drawH);
  }

  private updateCanvasFrame(scroller: HTMLElement) {
    if (!isPlatformBrowser(this.platformId)) return;
    if (this.isNavClickScrolling()) return;
    const section = document.getElementById('about-section');
    if (!section) return;

    const sectionTop = section.offsetTop;
    const sectionHeight = section.scrollHeight;
    const viewH = scroller.clientHeight;
    const scrollTop = scroller.scrollTop;

    const start = sectionTop;
    const end = sectionTop + sectionHeight - viewH;
    const progress = Math.min(Math.max((scrollTop - start) / (end - start), 0), 1);

    // REVERSED: start at last frame (hands close + text visible) and play backwards
    // so scrolling DOWN moves hands apart and fades text out.
    const reversedProgress = 1 - progress;
    const frameIndex = Math.min(
      Math.floor(reversedProgress * (this.TOTAL_FRAMES - 1)),
      this.TOTAL_FRAMES - 1
    );

    if (frameIndex !== this.currentFrame) {
      this.currentFrame = frameIndex;
      if (this.animationFrameId) cancelAnimationFrame(this.animationFrameId);
      this.animationFrameId = requestAnimationFrame(() => this.drawFrame(frameIndex));
    }

    // REVERSED text overlay:
    // Text is FULLY visible at scroll start (progress=0) and fades OUT as user scrolls down.
    const TEXT_VISIBLE_END = 0.30;  // text starts fading at 30% scroll
    const TEXT_FADE_END = 0.50;  // text fully gone by 50% scroll

    let opacity = 0;
    let translateY = 0;
    let scale = 1;

    if (progress < TEXT_VISIBLE_END) {
      // Fully visible at the start
      opacity = 1;
      translateY = 0;
      scale = 1;
    } else if (progress <= TEXT_FADE_END) {
      const t = (progress - TEXT_VISIBLE_END) / (TEXT_FADE_END - TEXT_VISIBLE_END);
      const ease = t < 0.5 ? 2 * t * t : -1 + (4 - 2 * t) * t;
      opacity = 1 - ease;
      translateY = -30 * ease;
      scale = 1 - 0.06 * ease;
    } else {
      opacity = 0;
      translateY = -30;
      scale = 0.94;
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

    if (index === 2) {
      // Products clicked: smooth flow to Products section
      this.isNavClickScrolling.set(true);

      const productEl = document.getElementById('product-section');
      if (!productEl) return;

      const startScroll = scroller.scrollTop;
      const targetScroll = productEl.offsetTop;
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
          // Finished flow, lock active link to Products
          this.updateActiveNavIndex(2);
          this.isNavClickScrolling.set(false);
          this.updateCanvasFrame(scroller);
        }
      };

      requestAnimationFrame(animateScroll);
      return;
    } else if (index === 3) {
      // Features clicked: smooth flow to Features section
      this.isNavClickScrolling.set(true);

      const featuresEl = document.getElementById('features-section');
      if (!featuresEl) return;

      const startScroll = scroller.scrollTop;
      const targetScroll = featuresEl.offsetTop;
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
          // Finished flow, lock active link to Features
          this.updateActiveNavIndex(3);
          this.isNavClickScrolling.set(false);
          this.updateCanvasFrame(scroller);
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
          this.updateCanvasFrame(scroller);
        }
      };

      requestAnimationFrame(animateScroll);
    } else if (index === 1) {
      // About clicked
      const aboutEl = document.getElementById('about-section');
      if (!aboutEl) return;

      // Ensure About Us text and initial canvas frame are processed immediately when clicked
      this.aboutTextOpacity.set(1);
      this.aboutTextTranslateY.set(0);
      this.aboutTextScale.set(1);
      if (this.TOTAL_FRAMES > 0) {
        this.currentFrame = this.TOTAL_FRAMES - 1;
        this.drawFrame(this.TOTAL_FRAMES - 1);
      }

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
          this.updateCanvasFrame(scroller);
        }
      };

      requestAnimationFrame(animateScroll);
    } else if (index === 4) {
      // Contact clicked: smooth scroll to Contact section
      const contactEl = document.getElementById('contact-section');
      if (!contactEl) return;

      this.isNavClickScrolling.set(true);
      const startScroll = scroller.scrollTop;
      const targetScroll = contactEl.offsetTop;
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
          this.updateActiveNavIndex(4);
          this.isNavClickScrolling.set(false);
          this.updateCanvasFrame(scroller);
        }
      };

      requestAnimationFrame(animateScroll);
    }
  }

  onExploreFeatures() {
    this.setActiveNav(3);
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

  onContactSubmit(event: Event) {
    event.preventDefault();
    if (this.contactSubmitting()) return;

    const firstName = this.contactFirstName().trim();
    const lastName = this.contactLastName().trim();
    const email = this.contactEmail().trim();
    const message = this.contactMessage().trim();

    if (!firstName || !lastName || !email || !message) {
      this.contactSubmitError.set('Please fill in all required fields.');
      return;
    }

    this.contactSubmitting.set(true);
    this.contactSubmitError.set('');
    this.contactSubmitSuccess.set('');

    // Simulate form submission (no backend endpoint yet — extend as needed)
    setTimeout(() => {
      this.contactSubmitting.set(false);
      this.contactSubmitSuccess.set('Thank you! Your message has been sent successfully.');
      this.contactFirstName.set('');
      this.contactLastName.set('');
      this.contactEmail.set('');
      this.contactPhone.set('');
      this.contactMessage.set('');
    }, 1200);
  }

  // =========================================================================
  // INFINITY NATURAL STRAND & PARTICLE ENGINE (16:9)
  // Preserves 100% visual fidelity of 'a page after the features page.png'
  // with natural organic strand flexing, line rippling, and spreading particles.
  // =========================================================================

  private infinityGl: WebGLRenderingContext | null = null;
  private infinity2dCtx: CanvasRenderingContext2D | null = null;
  private infinityImage: HTMLImageElement | null = null;
  private infinityTexture: WebGLTexture | null = null;
  private infinityProgram: WebGLProgram | null = null;
  private infinityQuadBuffer: WebGLBuffer | null = null;

  // Mask grid to detect if mouse is over the blue infinity strands
  private infinityMaskCols = 160;
  private infinityMaskRows = 90;
  private infinitySymbolMask: Uint8Array = new Uint8Array(0);

  // Impulse wave system for natural strand flexing & line movement
  private readonly MAX_IMPULSES = 16;
  private infinityImpulses: {
    x: number;
    y: number;
    dirX: number;
    dirY: number;
    radius: number;
    age: number;
    life: number;
  }[] = [];
  private infinityNextImpulseIdx = 0;

  // Floating strand spark particles
  private infinitySparkParticles: {
    normX: number;
    normY: number;
    ox: number;
    oy: number;
    x: number;
    y: number;
    vx: number;
    vy: number;
    r: number;
    alpha: number;
    active: boolean;
  }[] = [];

  private infinityCanvasWidth = 0;
  private infinityCanvasHeight = 0;
  private infinityAnimId: number | null = null;
  private infinityResizeObserver: ResizeObserver | null = null;
  private infinityMouseInside = false;
  private infinityMouseNormX = -1;
  private infinityMouseNormY = -1;
  private infinityPrevMouseNormX = -1;
  private infinityPrevMouseNormY = -1;
  private infinityMouseVx = 0;
  private infinityMouseVy = 0;
  private infinityLastFrameTime = 0;
  private prefersReducedMotion = false;
  private infinityReducedMotionQuery?: MediaQueryList;
  private infinityMediaListener?: (e: MediaQueryListEvent) => void;

  private initInfinityParticleSystem() {
    if (!isPlatformBrowser(this.platformId)) return;

    if (window.matchMedia) {
      this.infinityReducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
      this.prefersReducedMotion = this.infinityReducedMotionQuery.matches;
      this.infinityMediaListener = (e: MediaQueryListEvent) => {
        this.prefersReducedMotion = e.matches;
      };
      this.infinityReducedMotionQuery.addEventListener('change', this.infinityMediaListener);
    }

    // Initialize circular impulse pool
    for (let i = 0; i < this.MAX_IMPULSES; i++) {
      this.infinityImpulses.push({
        x: 0,
        y: 0,
        dirX: 0,
        dirY: 0,
        radius: 0.16,
        age: 999,
        life: 0
      });
    }

    const img = new Image();
    img.src = encodeURI('assets/a page after the features page.png');
    img.onload = () => {
      this.infinityImage = img;
      this.setupNaturalStrandEngine(img);
    };
    img.onerror = () => {
      const fallback = new Image();
      fallback.src = 'assets/a%20page%20after%20the%20features%20page.png';
      fallback.onload = () => {
        this.infinityImage = fallback;
        this.setupNaturalStrandEngine(fallback);
      };
    };
  }

  private setupNaturalStrandEngine(img: HTMLImageElement) {
    if (!this.infinityCanvasRef || !this.infinityContainerRef) return;
    const canvas = this.infinityCanvasRef.nativeElement;
    const container = this.infinityContainerRef.nativeElement;

    // 1. Build mask from the image to pinpoint exact strand/particle areas
    this.buildStrandMask(img);

    // 2. Initialize WebGL for fluid strand line wave dynamics
    const gl = canvas.getContext('webgl', { antialias: true, alpha: false, depth: false }) ||
               canvas.getContext('experimental-webgl');

    if (gl) {
      this.infinityGl = gl as WebGLRenderingContext;
      this.initNaturalStrandWebGL(gl as WebGLRenderingContext, img);
    } else {
      this.infinity2dCtx = canvas.getContext('2d');
    }

    const updateDimensions = () => {
      const rect = container.getBoundingClientRect();
      const dpr = Math.min(window.devicePixelRatio || 1, 2);
      const w = Math.round(rect.width);
      const h = Math.round(rect.height);

      if (w === 0 || h === 0) return;

      this.infinityCanvasWidth = w;
      this.infinityCanvasHeight = h;

      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);

      if (this.infinityGl) {
        this.infinityGl.viewport(0, 0, canvas.width, canvas.height);
      } else if (this.infinity2dCtx) {
        this.infinity2dCtx.setTransform(dpr, 0, 0, dpr, 0, 0);
      }

      // Update spark anchor coordinates
      for (let i = 0; i < this.infinitySparkParticles.length; i++) {
        const p = this.infinitySparkParticles[i];
        p.ox = p.normX * w;
        p.oy = p.normY * h;
        if (!p.active) {
          p.x = p.ox;
          p.y = p.oy;
        }
      }

      this.renderNaturalStrandFrame(0.016);
    };

    updateDimensions();

    this.infinityResizeObserver = new ResizeObserver(() => {
      updateDimensions();
    });
    this.infinityResizeObserver.observe(container);

    this.infinityLastFrameTime = performance.now();
    this.startNaturalStrandAnimationLoop();
  }

  private buildStrandMask(img: HTMLImageElement) {
    const cols = this.infinityMaskCols;
    const rows = this.infinityMaskRows;

    const off = document.createElement('canvas');
    off.width = cols;
    off.height = rows;
    const offCtx = off.getContext('2d', { willReadFrequently: true });
    if (!offCtx) return;

    offCtx.drawImage(img, 0, 0, cols, rows);
    const data = offCtx.getImageData(0, 0, cols, rows).data;

    this.infinitySymbolMask = new Uint8Array(cols * rows);
    const sparks: typeof this.infinitySparkParticles = [];

    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        const idx = (r * cols + c) * 4;
        const red = data[idx];
        const green = data[idx + 1];
        const blue = data[idx + 2];
        const brightness = red * 0.299 + green * 0.587 + blue * 0.114;

        if (blue > 42 && brightness > 30) {
          this.infinitySymbolMask[r * cols + c] = 1;

          // Collect bright spark nodes along the strands
          if (brightness > 130 && Math.random() < 0.22) {
            sparks.push({
              normX: c / cols,
              normY: r / rows,
              ox: 0,
              oy: 0,
              x: 0,
              y: 0,
              vx: 0,
              vy: 0,
              r: 1.2 + Math.random() * 1.6,
              alpha: 0.65 + Math.random() * 0.35,
              active: false
            });
          }
        } else {
          this.infinitySymbolMask[r * cols + c] = 0;
        }
      }
    }

    this.infinitySparkParticles = sparks;
  }

  private initNaturalStrandWebGL(gl: WebGLRenderingContext, img: HTMLImageElement) {
    // Vertex shader: Full-screen quad passing UV coordinates
    const vsSource = `
      attribute vec2 a_position;
      varying vec2 v_uv;
      void main() {
        v_uv = (a_position + 1.0) * 0.5;
        // Flip Y to match natural texture image orientation
        v_uv.y = 1.0 - v_uv.y;
        gl_Position = vec4(a_position, 0.0, 1.0);
      }
    `;

    // Fragment shader: Natural strand flexing, line wave oscillation & particle flare
    const fsSource = `
      precision mediump float;
      uniform sampler2D u_image;
      uniform float u_aspect;
      uniform vec2 u_impulsePos[16];
      uniform vec2 u_impulseDir[16];
      uniform float u_impulseRadius[16];
      uniform float u_impulseAge[16];
      uniform float u_impulseLife[16];
      varying vec2 v_uv;

      void main() {
        vec2 totalDisp = vec2(0.0);

        for (int i = 0; i < 16; i++) {
          if (u_impulseLife[i] > 0.0) {
            vec2 d = v_uv - u_impulsePos[i];
            d.x *= u_aspect;
            float dist = length(d);
            float r = u_impulseRadius[i];

            if (dist < r) {
              float normDist = dist / r;
              // Smooth cubic falloff across the strand zone
              float falloff = (1.0 - normDist * normDist);
              falloff = falloff * falloff;

              float age = u_impulseAge[i];
              // Damped harmonic oscillation simulating organic strand flexibility
              float decay = exp(-age * 4.6);
              float wave = sin(age * 13.5 - normDist * 3.6);

              vec2 dir = u_impulseDir[i];
              // Subtle perpendicular curl to simulate physical strand curvature
              vec2 curl = vec2(-dir.y, dir.x) * 0.24;

              totalDisp += (dir + curl) * falloff * wave * decay * 0.024;
            }
          }
        }

        // Sample the exact reference image with organic displaced UVs
        vec2 sampleUV = clamp(v_uv - totalDisp, vec2(0.001), vec2(0.999));
        vec4 color = texture2D(u_image, sampleUV);

        // Organic strand brightness flare when agitated by the cursor
        float dispMag = length(totalDisp);
        if (dispMag > 0.0004) {
          float blueStrandMask = smoothstep(0.12, 0.45, color.b);
          color.rgb += vec3(0.0, 0.35, 0.75) * dispMag * 10.0 * blueStrandMask;
        }

        gl_FragColor = color;
      }
    `;

    const vertShader = this.compileStrandShader(gl, gl.VERTEX_SHADER, vsSource);
    const fragShader = this.compileStrandShader(gl, gl.FRAGMENT_SHADER, fsSource);
    if (!vertShader || !fragShader) return;

    const program = gl.createProgram();
    if (!program) return;
    gl.attachShader(program, vertShader);
    gl.attachShader(program, fragShader);
    gl.linkProgram(program);
    this.infinityProgram = program;

    // Full-screen quad buffer
    const quadVerts = new Float32Array([
      -1, -1,
       1, -1,
      -1,  1,
      -1,  1,
       1, -1,
       1,  1
    ]);
    this.infinityQuadBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, this.infinityQuadBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, quadVerts, gl.STATIC_DRAW);

    // Upload exact reference image texture
    this.infinityTexture = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, this.infinityTexture);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
  }

  private compileStrandShader(gl: WebGLRenderingContext, type: number, source: string): WebGLShader | null {
    const shader = gl.createShader(type);
    if (!shader) return null;
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      console.warn('Shader compile failed:', gl.getShaderInfoLog(shader));
      gl.deleteShader(shader);
      return null;
    }
    return shader;
  }

  onInfinityMouseMove(event: MouseEvent) {
    if (!this.infinityCanvasRef) return;
    const canvas = this.infinityCanvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    const curNormX = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width));
    const curNormY = Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height));

    if (!this.infinityMouseInside) {
      this.infinityMouseInside = true;
      this.infinityPrevMouseNormX = curNormX;
      this.infinityPrevMouseNormY = curNormY;
    }

    const deltaX = curNormX - this.infinityPrevMouseNormX;
    const deltaY = curNormY - this.infinityPrevMouseNormY;
    this.infinityPrevMouseNormX = curNormX;
    this.infinityPrevMouseNormY = curNormY;

    this.infinityMouseNormX = curNormX;
    this.infinityMouseNormY = curNormY;

    this.infinityMouseVx = this.infinityMouseVx * 0.5 + deltaX * 0.5;
    this.infinityMouseVy = this.infinityMouseVy * 0.5 + deltaY * 0.5;

    const speed = Math.hypot(this.infinityMouseVx, this.infinityMouseVy);

    // Check if mouse is directly over the blue infinity strands/symbol
    const maskC = Math.floor(curNormX * this.infinityMaskCols);
    const maskR = Math.floor(curNormY * this.infinityMaskRows);
    const maskIdx = maskR * this.infinityMaskCols + maskC;
    const isOverStrands = this.infinitySymbolMask[maskIdx] === 1;

    // Only create organic impulses if over the blue infinity symbol
    if (isOverStrands && (speed > 0.002 || !this.hasRecentImpulseAt(curNormX, curNormY))) {
      this.spawnStrandImpulse(curNormX, curNormY, this.infinityMouseVx, this.infinityMouseVy);
    }
  }

  private hasRecentImpulseAt(nx: number, ny: number): boolean {
    for (let i = 0; i < this.MAX_IMPULSES; i++) {
      const imp = this.infinityImpulses[i];
      if (imp.life > 0 && imp.age < 0.25) {
        const d = Math.hypot(imp.x - nx, imp.y - ny);
        if (d < 0.04) return true;
      }
    }
    return false;
  }

  private spawnStrandImpulse(nx: number, ny: number, vx: number, vy: number) {
    if (this.prefersReducedMotion) return;

    let dirX = vx;
    let dirY = vy;
    const len = Math.hypot(dirX, dirY);

    if (len > 0.0001) {
      const clampedLen = Math.min(len, 0.045);
      dirX = (dirX / len) * (clampedLen / 0.045);
      dirY = (dirY / len) * (clampedLen / 0.045);
    } else {
      // Subtle ambient nudge if hovering over strands
      dirX = (Math.random() - 0.5) * 0.005;
      dirY = (Math.random() - 0.5) * 0.005;
    }

    const imp = this.infinityImpulses[this.infinityNextImpulseIdx];
    imp.x = nx;
    imp.y = ny;
    imp.dirX = dirX;
    imp.dirY = dirY;
    imp.radius = 0.16; // localized radius
    imp.age = 0.0;
    imp.life = 0.75; // decays over 0.75s

    this.infinityNextImpulseIdx = (this.infinityNextImpulseIdx + 1) % this.MAX_IMPULSES;
  }

  onInfinityMouseEnter(event: MouseEvent) {
    if (!this.infinityCanvasRef) return;
    const canvas = this.infinityCanvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    const curNormX = Math.max(0, Math.min(1, (event.clientX - rect.left) / rect.width));
    const curNormY = Math.max(0, Math.min(1, (event.clientY - rect.top) / rect.height));

    this.infinityMouseInside = true;
    this.infinityMouseNormX = curNormX;
    this.infinityMouseNormY = curNormY;
    this.infinityPrevMouseNormX = curNormX;
    this.infinityPrevMouseNormY = curNormY;
    this.infinityMouseVx = 0;
    this.infinityMouseVy = 0;
  }

  onInfinityMouseLeave() {
    this.infinityMouseInside = false;
    this.infinityMouseNormX = -1;
    this.infinityMouseNormY = -1;
    this.infinityMouseVx = 0;
    this.infinityMouseVy = 0;
  }

  private startNaturalStrandAnimationLoop() {
    if (this.infinityAnimId) {
      cancelAnimationFrame(this.infinityAnimId);
    }

    const loop = (now: number) => {
      const dt = Math.min((now - this.infinityLastFrameTime) / 1000, 0.04);
      this.infinityLastFrameTime = now;

      this.renderNaturalStrandFrame(dt);
      this.infinityAnimId = requestAnimationFrame(loop);
    };

    this.infinityAnimId = requestAnimationFrame(loop);
  }

  private renderNaturalStrandFrame(dt: number) {
    // 1. Age and update impulses
    const posArr = new Float32Array(this.MAX_IMPULSES * 2);
    const dirArr = new Float32Array(this.MAX_IMPULSES * 2);
    const radiusArr = new Float32Array(this.MAX_IMPULSES);
    const ageArr = new Float32Array(this.MAX_IMPULSES);
    const lifeArr = new Float32Array(this.MAX_IMPULSES);

    for (let i = 0; i < this.MAX_IMPULSES; i++) {
      const imp = this.infinityImpulses[i];
      if (imp.life > 0) {
        imp.age += dt;
        if (imp.age >= imp.life) {
          imp.life = 0;
        }
      }

      posArr[i * 2] = imp.x;
      posArr[i * 2 + 1] = imp.y;
      dirArr[i * 2] = imp.dirX;
      dirArr[i * 2 + 1] = imp.dirY;
      radiusArr[i] = imp.radius;
      ageArr[i] = imp.age;
      lifeArr[i] = imp.life;
    }

    // 2. Render WebGL natural strand deflection shader
    if (this.infinityGl && this.infinityProgram && this.infinityImage) {
      const gl = this.infinityGl;
      gl.useProgram(this.infinityProgram);

      gl.bindBuffer(gl.ARRAY_BUFFER, this.infinityQuadBuffer);
      const aPos = gl.getAttribLocation(this.infinityProgram, 'a_position');
      gl.enableVertexAttribArray(aPos);
      gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 0, 0);

      gl.activeTexture(gl.TEXTURE0);
      gl.bindTexture(gl.TEXTURE_2D, this.infinityTexture);
      gl.uniform1i(gl.getUniformLocation(this.infinityProgram, 'u_image'), 0);

      gl.uniform1f(gl.getUniformLocation(this.infinityProgram, 'u_aspect'), 16.0 / 9.0);

      gl.uniform2fv(gl.getUniformLocation(this.infinityProgram, 'u_impulsePos'), posArr);
      gl.uniform2fv(gl.getUniformLocation(this.infinityProgram, 'u_impulseDir'), dirArr);
      gl.uniform1fv(gl.getUniformLocation(this.infinityProgram, 'u_impulseRadius'), radiusArr);
      gl.uniform1fv(gl.getUniformLocation(this.infinityProgram, 'u_impulseAge'), ageArr);
      gl.uniform1fv(gl.getUniformLocation(this.infinityProgram, 'u_impulseLife'), lifeArr);

      gl.drawArrays(gl.TRIANGLES, 0, 6);
    } else if (this.infinity2dCtx && this.infinityImage && this.infinityCanvasWidth > 0) {
      // 2D canvas fallback: direct original image rendering
      this.infinity2dCtx.drawImage(this.infinityImage, 0, 0, this.infinityCanvasWidth, this.infinityCanvasHeight);
    }

    this.infinityMouseVx *= 0.7;
    this.infinityMouseVy *= 0.7;
  }

  private destroyInfinityParticleSystem() {
    if (this.infinityAnimId) {
      cancelAnimationFrame(this.infinityAnimId);
      this.infinityAnimId = null;
    }
    if (this.infinityResizeObserver) {
      this.infinityResizeObserver.disconnect();
      this.infinityResizeObserver = null;
    }
    if (this.infinityReducedMotionQuery && this.infinityMediaListener) {
      this.infinityReducedMotionQuery.removeEventListener('change', this.infinityMediaListener);
    }
    if (this.infinityGl) {
      if (this.infinityTexture) this.infinityGl.deleteTexture(this.infinityTexture);
      if (this.infinityProgram) this.infinityGl.deleteProgram(this.infinityProgram);
      if (this.infinityQuadBuffer) this.infinityGl.deleteBuffer(this.infinityQuadBuffer);
      this.infinityGl = null;
    }
  }
}




