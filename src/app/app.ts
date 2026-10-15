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
    { label: 'Features', active: false }
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

      const viewH = target.clientHeight || window.innerHeight;
      const aboutTop = aboutEl ? aboutEl.offsetTop : viewH;
      const productTop = productEl ? productEl.offsetTop : viewH * 2;
      const featuresTop = featuresEl ? featuresEl.offsetTop : viewH * 3;

      let activeIndex = 0;
      if (scrollTop >= featuresTop - viewH * 0.4) {
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
      // Smooth scroll to infinity section if index 4 requested
      const infinityEl = document.getElementById('infinity-section');
      if (!infinityEl) return;

      this.isNavClickScrolling.set(true);
      const startScroll = scroller.scrollTop;
      const targetScroll = infinityEl.offsetTop;
      const distance = targetScroll - startScroll;
      const duration = 1800;
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
          this.isNavClickScrolling.set(false);
        }
      };

      requestAnimationFrame(animateScroll);
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
  // INFINITY PARTICLE SYSTEM (16:9 INTERACTIVE WEBGL / CANVAS)
  // Preserves 100% pixel fidelity of 'a page after the features page.png'
  // with localized particle & strand shift in the cursor's direction.
  // =========================================================================

  private infinityGl: WebGLRenderingContext | null = null;
  private infinity2dCtx: CanvasRenderingContext2D | null = null;
  private infinityImage: HTMLImageElement | null = null;
  private infinityTexture: WebGLTexture | null = null;
  private infinityProgram: WebGLProgram | null = null;
  private infinityPointsProgram: WebGLProgram | null = null;

  private infinityPosBuffer: WebGLBuffer | null = null;
  private infinityTexBuffer: WebGLBuffer | null = null;
  private infinityIndexBuffer: WebGLBuffer | null = null;
  private infinityPointPosBuffer: WebGLBuffer | null = null;
  private infinityPointColorBuffer: WebGLBuffer | null = null;

  private infinityGridCols = 96;
  private infinityGridRows = 54;
  private infinityNumIndices = 0;
  private infinityVertices: Float32Array = new Float32Array(0);
  private infinityOrigVertices: Float32Array = new Float32Array(0);
  private infinityVelocities: Float32Array = new Float32Array(0);
  private infinityIsSymbol: Uint8Array = new Uint8Array(0);

  // Floating spark particles
  private infinitySparks: { origX: number; origY: number; x: number; y: number; vx: number; vy: number; r: number; alpha: number }[] = [];

  private infinityCanvasWidth = 0;
  private infinityCanvasHeight = 0;
  private infinityAnimId: number | null = null;
  private infinityResizeObserver: ResizeObserver | null = null;
  private infinityMouseInside = false;
  private infinityMouseClipX = 999;
  private infinityMouseClipY = 999;
  private infinityPrevMouseClipX = 999;
  private infinityPrevMouseClipY = 999;
  private infinityMouseVx = 0;
  private infinityMouseVy = 0;
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

    const img = new Image();
    img.src = encodeURI('assets/a page after the features page.png');
    img.onload = () => {
      this.infinityImage = img;
      this.setupInfinityEngine(img);
    };
    img.onerror = () => {
      const fallback = new Image();
      fallback.src = 'assets/a%20page%20after%20the%20features%20page.png';
      fallback.onload = () => {
        this.infinityImage = fallback;
        this.setupInfinityEngine(fallback);
      };
    };
  }

  private setupInfinityEngine(img: HTMLImageElement) {
    if (!this.infinityCanvasRef || !this.infinityContainerRef) return;
    const canvas = this.infinityCanvasRef.nativeElement;
    const container = this.infinityContainerRef.nativeElement;

    // Detect symbol mask from image so only blue particles & strands move
    this.extractSymbolMask(img);

    // Try WebGL for hardware-accelerated fluid particle mesh
    const gl = canvas.getContext('webgl', { antialias: true, alpha: false, depth: false }) ||
               canvas.getContext('experimental-webgl');

    if (gl) {
      this.infinityGl = gl as WebGLRenderingContext;
      this.initWebGL(gl as WebGLRenderingContext, img);
    } else {
      // 2D fallback
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

      this.renderInfinityFrame();
    };

    updateDimensions();

    this.infinityResizeObserver = new ResizeObserver(() => {
      updateDimensions();
    });
    this.infinityResizeObserver.observe(container);

    this.startInfinityAnimationLoop();
  }

  private extractSymbolMask(img: HTMLImageElement) {
    const cols = this.infinityGridCols;
    const rows = this.infinityGridRows;
    const numVerts = (cols + 1) * (rows + 1);

    const off = document.createElement('canvas');
    off.width = cols + 1;
    off.height = rows + 1;
    const offCtx = off.getContext('2d', { willReadFrequently: true });
    if (!offCtx) return;

    offCtx.drawImage(img, 0, 0, cols + 1, rows + 1);
    const data = offCtx.getImageData(0, 0, cols + 1, rows + 1).data;

    this.infinityIsSymbol = new Uint8Array(numVerts);
    this.infinityVertices = new Float32Array(numVerts * 2);
    this.infinityOrigVertices = new Float32Array(numVerts * 2);
    this.infinityVelocities = new Float32Array(numVerts * 2);

    const sparks: { origX: number; origY: number; x: number; y: number; vx: number; vy: number; r: number; alpha: number }[] = [];

    for (let r = 0; r <= rows; r++) {
      for (let c = 0; c <= cols; c++) {
        const i = r * (cols + 1) + c;
        const u = c / cols;
        const v = r / rows;

        // Clip space [-1, 1]
        const clipX = u * 2 - 1;
        const clipY = 1 - v * 2;

        this.infinityOrigVertices[i * 2] = clipX;
        this.infinityOrigVertices[i * 2 + 1] = clipY;
        this.infinityVertices[i * 2] = clipX;
        this.infinityVertices[i * 2 + 1] = clipY;
        this.infinityVelocities[i * 2] = 0;
        this.infinityVelocities[i * 2 + 1] = 0;

        const pixelIdx = (r * (cols + 1) + c) * 4;
        const red = data[pixelIdx];
        const green = data[pixelIdx + 1];
        const blue = data[pixelIdx + 2];
        const brightness = red * 0.299 + green * 0.587 + blue * 0.114;

        // Mask: mark vertices that belong to the blue glowing infinity symbol
        if (blue > 45 && brightness > 35) {
          this.infinityIsSymbol[i] = 1;

          // Populate spark points at bright nodes
          if (brightness > 130 && Math.random() < 0.28) {
            sparks.push({
              origX: clipX,
              origY: clipY,
              x: clipX,
              y: clipY,
              vx: 0,
              vy: 0,
              r: 1.5 + Math.random() * 2.0,
              alpha: 0.6 + Math.random() * 0.4
            });
          }
        } else {
          this.infinityIsSymbol[i] = 0;
        }
      }
    }

    this.infinitySparks = sparks;
  }

  private initWebGL(gl: WebGLRenderingContext, img: HTMLImageElement) {
    // 1. Shaders for the textured deformable mesh
    const vsSource = `
      attribute vec2 a_position;
      attribute vec2 a_texCoord;
      varying vec2 v_texCoord;
      void main() {
        v_texCoord = a_texCoord;
        gl_Position = vec4(a_position, 0.0, 1.0);
      }
    `;

    const fsSource = `
      precision mediump float;
      uniform sampler2D u_image;
      varying vec2 v_texCoord;
      void main() {
        gl_FragColor = texture2D(u_image, v_texCoord);
      }
    `;

    const vertShader = this.compileShader(gl, gl.VERTEX_SHADER, vsSource);
    const fragShader = this.compileShader(gl, gl.FRAGMENT_SHADER, fsSource);
    if (!vertShader || !fragShader) return;

    const program = gl.createProgram();
    if (!program) return;
    gl.attachShader(program, vertShader);
    gl.attachShader(program, fragShader);
    gl.linkProgram(program);
    this.infinityProgram = program;

    // 2. Mesh buffers
    const cols = this.infinityGridCols;
    const rows = this.infinityGridRows;
    const numVerts = (cols + 1) * (rows + 1);

    // Texture coordinates (static UVs)
    const texCoords = new Float32Array(numVerts * 2);
    for (let r = 0; r <= rows; r++) {
      for (let c = 0; c <= cols; c++) {
        const i = r * (cols + 1) + c;
        texCoords[i * 2] = c / cols;
        texCoords[i * 2 + 1] = r / rows;
      }
    }

    this.infinityTexBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, this.infinityTexBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, texCoords, gl.STATIC_DRAW);

    // Vertex positions (dynamic)
    this.infinityPosBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, this.infinityPosBuffer);
    gl.bufferData(gl.ARRAY_BUFFER, this.infinityVertices, gl.DYNAMIC_DRAW);

    // Triangles index buffer
    const indices: number[] = [];
    for (let r = 0; r < rows; r++) {
      for (let c = 0; c < cols; c++) {
        const i00 = r * (cols + 1) + c;
        const i10 = i00 + 1;
        const i01 = (r + 1) * (cols + 1) + c;
        const i11 = i01 + 1;

        indices.push(i00, i01, i10);
        indices.push(i10, i01, i11);
      }
    }
    this.infinityNumIndices = indices.length;
    this.infinityIndexBuffer = gl.createBuffer();
    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, this.infinityIndexBuffer);
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, new Uint16Array(indices), gl.STATIC_DRAW);

    // 3. Texture creation
    this.infinityTexture = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, this.infinityTexture);
    gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.LINEAR);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);

    // 4. Point sparks shader for interactive glowing particle depth
    const ptVs = `
      attribute vec2 a_pointPosition;
      attribute float a_pointSize;
      attribute vec4 a_pointColor;
      varying vec4 v_color;
      void main() {
        v_color = a_pointColor;
        gl_Position = vec4(a_pointPosition, 0.0, 1.0);
        gl_PointSize = a_pointSize;
      }
    `;

    const ptFs = `
      precision mediump float;
      varying vec4 v_color;
      void main() {
        vec2 coord = gl_PointCoord - vec2(0.5);
        float dist = length(coord);
        if (dist > 0.5) discard;
        float alpha = smoothstep(0.5, 0.0, dist) * v_color.a;
        gl_FragColor = vec4(v_color.rgb, alpha);
      }
    `;

    const ptVShader = this.compileShader(gl, gl.VERTEX_SHADER, ptVs);
    const ptFShader = this.compileShader(gl, gl.FRAGMENT_SHADER, ptFs);
    if (ptVShader && ptFShader) {
      const ptProg = gl.createProgram();
      if (ptProg) {
        gl.attachShader(ptProg, ptVShader);
        gl.attachShader(ptProg, ptFShader);
        gl.linkProgram(ptProg);
        this.infinityPointsProgram = ptProg;
        this.infinityPointPosBuffer = gl.createBuffer();
        this.infinityPointColorBuffer = gl.createBuffer();
      }
    }
  }

  private compileShader(gl: WebGLRenderingContext, type: number, source: string): WebGLShader | null {
    const shader = gl.createShader(type);
    if (!shader) return null;
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
      console.warn('WebGL shader compile error:', gl.getShaderInfoLog(shader));
      gl.deleteShader(shader);
      return null;
    }
    return shader;
  }

  onInfinityMouseMove(event: MouseEvent) {
    if (!this.infinityCanvasRef) return;
    const canvas = this.infinityCanvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();

    const curClipX = (event.clientX - rect.left) / rect.width * 2 - 1;
    const curClipY = 1 - (event.clientY - rect.top) / rect.height * 2;

    if (!this.infinityMouseInside) {
      this.infinityMouseInside = true;
      this.infinityPrevMouseClipX = curClipX;
      this.infinityPrevMouseClipY = curClipY;
    }

    const deltaX = curClipX - this.infinityPrevMouseClipX;
    const deltaY = curClipY - this.infinityPrevMouseClipY;
    this.infinityPrevMouseClipX = curClipX;
    this.infinityPrevMouseClipY = curClipY;

    this.infinityMouseClipX = curClipX;
    this.infinityMouseClipY = curClipY;

    // Smooth cursor movement velocity
    this.infinityMouseVx = this.infinityMouseVx * 0.5 + deltaX * 0.5;
    this.infinityMouseVy = this.infinityMouseVy * 0.5 + deltaY * 0.5;

    const speed = Math.hypot(this.infinityMouseVx, this.infinityMouseVy);
    const maxSpeed = 0.08;
    if (speed > maxSpeed) {
      this.infinityMouseVx = (this.infinityMouseVx / speed) * maxSpeed;
      this.infinityMouseVy = (this.infinityMouseVy / speed) * maxSpeed;
    }
  }

  onInfinityMouseEnter(event: MouseEvent) {
    if (!this.infinityCanvasRef) return;
    const canvas = this.infinityCanvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    const curClipX = (event.clientX - rect.left) / rect.width * 2 - 1;
    const curClipY = 1 - (event.clientY - rect.top) / rect.height * 2;

    this.infinityMouseInside = true;
    this.infinityMouseClipX = curClipX;
    this.infinityMouseClipY = curClipY;
    this.infinityPrevMouseClipX = curClipX;
    this.infinityPrevMouseClipY = curClipY;
    this.infinityMouseVx = 0;
    this.infinityMouseVy = 0;
  }

  onInfinityMouseLeave() {
    this.infinityMouseInside = false;
    this.infinityMouseVx = 0;
    this.infinityMouseVy = 0;
  }

  private startInfinityAnimationLoop() {
    if (this.infinityAnimId) {
      cancelAnimationFrame(this.infinityAnimId);
    }

    const loop = () => {
      this.renderInfinityFrame();
      this.infinityAnimId = requestAnimationFrame(loop);
    };

    this.infinityAnimId = requestAnimationFrame(loop);
  }

  private renderInfinityFrame() {
    if (this.infinityGl) {
      this.renderWebGLFrame();
    } else if (this.infinity2dCtx) {
      this.render2dFrame();
    }
  }

  private renderWebGLFrame() {
    const gl = this.infinityGl;
    if (!gl || !this.infinityProgram || !this.infinityImage) return;

    const cols = this.infinityGridCols;
    const rows = this.infinityGridRows;
    const numVerts = (cols + 1) * (rows + 1);

    // Physics constants
    const radius = 0.28; // Interaction radius in clip space
    const radiusSq = radius * radius;
    const springK = 0.075;
    const damping = 0.82;
    const shiftStrength = 0.26;
    const maxShift = 0.042; // Bounded localized shift preserving infinity shape

    const isMouseActive = this.infinityMouseInside && !this.prefersReducedMotion;
    const mx = this.infinityMouseClipX;
    const my = this.infinityMouseClipY;
    const mvx = this.infinityMouseVx;
    const mvy = this.infinityMouseVy;

    // 1. Update vertex physics
    for (let i = 0; i < numVerts; i++) {
      const idx2 = i * 2;
      const ox = this.infinityOrigVertices[idx2];
      const oy = this.infinityOrigVertices[idx2 + 1];

      // Only shift nearby vertices that are on the infinity symbol
      if (isMouseActive && this.infinityIsSymbol[i] === 1) {
        const dx = mx - ox;
        // Account for 16:9 aspect ratio so influence zone is circular
        const dy = (my - oy) * (16 / 9);
        const distSq = dx * dx + dy * dy;

        if (distSq < radiusSq) {
          const dist = Math.sqrt(distSq);
          const t = 1 - (dist / radius);
          const falloff = t * t * (3 - 2 * t); // Smoothstep falloff

          // Subtle localized shift in cursor's direction
          this.infinityVelocities[idx2] += mvx * falloff * shiftStrength;
          this.infinityVelocities[idx2 + 1] += mvy * falloff * shiftStrength;

          // Gentle ambient nudge if hovering
          const hoverSpeed = Math.hypot(mvx, mvy);
          if (hoverSpeed < 0.004) {
            const nudge = 0.0016 * falloff;
            this.infinityVelocities[idx2] += (dx < 0 ? -1 : 1) * nudge;
            this.infinityVelocities[idx2 + 1] += (dy < 0 ? -1 : 1) * nudge;
          }
        }
      }

      // Spring physics smoothly returning to anchor position
      const hx = ox - this.infinityVertices[idx2];
      const hy = oy - this.infinityVertices[idx2 + 1];
      this.infinityVelocities[idx2] += hx * springK;
      this.infinityVelocities[idx2 + 1] += hy * springK;
      this.infinityVelocities[idx2] *= damping;
      this.infinityVelocities[idx2 + 1] *= damping;

      this.infinityVertices[idx2] += this.infinityVelocities[idx2];
      this.infinityVertices[idx2 + 1] += this.infinityVelocities[idx2 + 1];

      // Bounded displacement clamp keeping infinity symbol structure completely intact
      const offX = this.infinityVertices[idx2] - ox;
      const offY = this.infinityVertices[idx2 + 1] - oy;
      const currentShift = Math.hypot(offX, offY);
      if (currentShift > maxShift) {
        const scale = maxShift / currentShift;
        this.infinityVertices[idx2] = ox + offX * scale;
        this.infinityVertices[idx2 + 1] = oy + offY * scale;
      }

      // Snap to rest when near home
      if (Math.abs(hx) < 0.0004 && Math.abs(hy) < 0.0004 &&
          Math.hypot(this.infinityVelocities[idx2], this.infinityVelocities[idx2 + 1]) < 0.00015) {
        this.infinityVertices[idx2] = ox;
        this.infinityVertices[idx2 + 1] = oy;
        this.infinityVelocities[idx2] = 0;
        this.infinityVelocities[idx2 + 1] = 0;
      }
    }

    // 2. Update spark particles
    const ptPositions: number[] = [];
    const ptColors: number[] = [];

    for (let s = 0; s < this.infinitySparks.length; s++) {
      const sp = this.infinitySparks[s];

      if (isMouseActive) {
        const dx = mx - sp.origX;
        const dy = (my - sp.origY) * (16 / 9);
        const distSq = dx * dx + dy * dy;

        if (distSq < radiusSq) {
          const dist = Math.sqrt(distSq);
          const t = 1 - (dist / radius);
          const falloff = t * t * (3 - 2 * t);

          sp.vx += mvx * falloff * (shiftStrength * 1.3);
          sp.vy += mvy * falloff * (shiftStrength * 1.3);
        }
      }

      const hx = sp.origX - sp.x;
      const hy = sp.origY - sp.y;
      sp.vx += hx * (springK * 0.9);
      sp.vy += hy * (springK * 0.9);
      sp.vx *= damping;
      sp.vy *= damping;

      sp.x += sp.vx;
      sp.y += sp.vy;

      // Only draw sparks that are active / moving
      const sparkShift = Math.hypot(sp.x - sp.origX, sp.y - sp.origY);
      if (sparkShift > 0.002) {
        ptPositions.push(sp.x, sp.y);
        const intensity = Math.min(1, sparkShift * 25);
        ptColors.push(0.0, 0.9, 1.0, sp.alpha * intensity);
      }
    }

    this.infinityMouseVx *= 0.7;
    this.infinityMouseVy *= 0.7;

    // 3. Render textured deformable mesh (100% exact visual fidelity of the reference image)
    gl.clearColor(0.01, 0.02, 0.06, 1.0);
    gl.clear(gl.COLOR_BUFFER_BIT);

    gl.useProgram(this.infinityProgram);

    // Upload updated vertex positions
    gl.bindBuffer(gl.ARRAY_BUFFER, this.infinityPosBuffer);
    gl.bufferSubData(gl.ARRAY_BUFFER, 0, this.infinityVertices);

    const aPos = gl.getAttribLocation(this.infinityProgram, 'a_position');
    gl.enableVertexAttribArray(aPos);
    gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 0, 0);

    const aTex = gl.getAttribLocation(this.infinityProgram, 'a_texCoord');
    gl.bindBuffer(gl.ARRAY_BUFFER, this.infinityTexBuffer);
    gl.enableVertexAttribArray(aTex);
    gl.vertexAttribPointer(aTex, 2, gl.FLOAT, false, 0, 0);

    gl.activeTexture(gl.TEXTURE0);
    gl.bindTexture(gl.TEXTURE_2D, this.infinityTexture);
    gl.uniform1i(gl.getUniformLocation(this.infinityProgram, 'u_image'), 0);

    gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, this.infinityIndexBuffer);
    gl.drawElements(gl.TRIANGLES, this.infinityNumIndices, gl.UNSIGNED_SHORT, 0);

    // 4. Render additive glowing spark particles
    if (this.infinityPointsProgram && ptPositions.length > 0) {
      gl.enable(gl.BLEND);
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE);

      gl.useProgram(this.infinityPointsProgram);

      gl.bindBuffer(gl.ARRAY_BUFFER, this.infinityPointPosBuffer);
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(ptPositions), gl.DYNAMIC_DRAW);
      const aPtPos = gl.getAttribLocation(this.infinityPointsProgram, 'a_pointPosition');
      gl.enableVertexAttribArray(aPtPos);
      gl.vertexAttribPointer(aPtPos, 2, gl.FLOAT, false, 0, 0);

      gl.bindBuffer(gl.ARRAY_BUFFER, this.infinityPointColorBuffer);
      gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(ptColors), gl.DYNAMIC_DRAW);
      const aPtCol = gl.getAttribLocation(this.infinityPointsProgram, 'a_pointColor');
      gl.enableVertexAttribArray(aPtCol);
      gl.vertexAttribPointer(aPtCol, 4, gl.FLOAT, false, 0, 0);

      gl.vertexAttrib1f(gl.getAttribLocation(this.infinityPointsProgram, 'a_pointSize'), 5.0 * (window.devicePixelRatio || 1));

      gl.drawArrays(gl.POINTS, 0, ptPositions.length / 2);

      gl.disable(gl.BLEND);
    }
  }

  private render2dFrame() {
    const ctx = this.infinity2dCtx;
    if (!ctx || !this.infinityImage || this.infinityCanvasWidth === 0) return;

    const w = this.infinityCanvasWidth;
    const h = this.infinityCanvasHeight;

    // Draw the reference image at full resolution
    ctx.drawImage(this.infinityImage, 0, 0, w, h);

    // If mouse is active, render interactive subtle cyan particles
    if (this.infinityMouseInside && !this.prefersReducedMotion) {
      const mx = (this.infinityMouseClipX + 1) * 0.5 * w;
      const my = (1 - this.infinityMouseClipY) * 0.5 * h;
      const radius = Math.min(w, h) * 0.16;

      ctx.save();
      ctx.globalCompositeOperation = 'lighter';
      const grad = ctx.createRadialGradient(mx, my, 0, mx, my, radius);
      grad.addColorStop(0, 'rgba(0, 240, 255, 0.15)');
      grad.addColorStop(0.5, 'rgba(0, 180, 255, 0.05)');
      grad.addColorStop(1, 'rgba(0, 120, 255, 0)');
      ctx.fillStyle = grad;
      ctx.beginPath();
      ctx.arc(mx, my, radius, 0, Math.PI * 2);
      ctx.fill();
      ctx.restore();
    }
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
      if (this.infinityPointsProgram) this.infinityGl.deleteProgram(this.infinityPointsProgram);
      if (this.infinityPosBuffer) this.infinityGl.deleteBuffer(this.infinityPosBuffer);
      if (this.infinityTexBuffer) this.infinityGl.deleteBuffer(this.infinityTexBuffer);
      if (this.infinityIndexBuffer) this.infinityGl.deleteBuffer(this.infinityIndexBuffer);
      this.infinityGl = null;
    }
  }
}


