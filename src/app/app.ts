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
  // INFINITY PARTICLE SYSTEM (16:9 SPREAD PARTICLES ENGINE)
  // Preserves 100% pixel-perfect fidelity of 'a page after the features page.png'
  // with particles that spread and disperse individually instead of floating.
  // =========================================================================

  private infinityCanvasCtx: CanvasRenderingContext2D | null = null;
  private infinityTempCanvas: HTMLCanvasElement | null = null;
  private infinityTempCtx: CanvasRenderingContext2D | null = null;
  private infinityImage: HTMLImageElement | null = null;

  private infinitySpreadParticles: {
    normX: number;
    normY: number;
    ox: number;
    oy: number;
    x: number;
    y: number;
    vx: number;
    vy: number;
    r: number;
    color: string;
    isCore: boolean;
    scatterAngle: number;
    scatterSpeed: number;
    active: boolean;
  }[] = [];

  private infinityCanvasWidth = 0;
  private infinityCanvasHeight = 0;
  private infinityAnimId: number | null = null;
  private infinityResizeObserver: ResizeObserver | null = null;
  private infinityMouseInside = false;
  private infinityMouseX = -9999;
  private infinityMouseY = -9999;
  private infinityPrevMouseX = -9999;
  private infinityPrevMouseY = -9999;
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
      this.setupInfinitySpreadEngine(img);
    };
    img.onerror = () => {
      const fallback = new Image();
      fallback.src = 'assets/a%20page%20after%20the%20features%20page.png';
      fallback.onload = () => {
        this.infinityImage = fallback;
        this.setupInfinitySpreadEngine(fallback);
      };
    };
  }

  private setupInfinitySpreadEngine(img: HTMLImageElement) {
    if (!this.infinityCanvasRef || !this.infinityContainerRef) return;
    const canvas = this.infinityCanvasRef.nativeElement;
    const container = this.infinityContainerRef.nativeElement;

    this.infinityCanvasCtx = canvas.getContext('2d');
    if (!this.infinityCanvasCtx) return;

    this.extractSpreadParticles(img);

    this.infinityTempCanvas = document.createElement('canvas');
    this.infinityTempCtx = this.infinityTempCanvas.getContext('2d');

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
      this.infinityCanvasCtx!.setTransform(dpr, 0, 0, dpr, 0, 0);

      if (this.infinityTempCanvas && this.infinityTempCtx) {
        this.infinityTempCanvas.width = canvas.width;
        this.infinityTempCanvas.height = canvas.height;
        this.infinityTempCtx.setTransform(dpr, 0, 0, dpr, 0, 0);
      }

      // Update particle anchor coordinates
      for (let i = 0; i < this.infinitySpreadParticles.length; i++) {
        const p = this.infinitySpreadParticles[i];
        p.ox = p.normX * w;
        p.oy = p.normY * h;
        if (!p.active) {
          p.x = p.ox;
          p.y = p.oy;
        }
      }

      this.renderInfinitySpreadFrame();
    };

    updateDimensions();

    this.infinityResizeObserver = new ResizeObserver(() => {
      updateDimensions();
    });
    this.infinityResizeObserver.observe(container);

    this.startInfinityAnimationLoop();
  }

  private extractSpreadParticles(img: HTMLImageElement) {
    // 16:9 sampling resolution
    const sampleW = 1024;
    const sampleH = 576;

    const off = document.createElement('canvas');
    off.width = sampleW;
    off.height = sampleH;
    const offCtx = off.getContext('2d', { willReadFrequently: true });
    if (!offCtx) return;

    offCtx.drawImage(img, 0, 0, sampleW, sampleH);
    const data = offCtx.getImageData(0, 0, sampleW, sampleH).data;

    const particles: typeof this.infinitySpreadParticles = [];

    // Dense particle grid across the infinity symbol
    for (let y = 0; y < sampleH; y += 2) {
      for (let x = 0; x < sampleW; x += 2) {
        const idx = (y * sampleW + x) * 4;
        const r = data[idx];
        const g = data[idx + 1];
        const b = data[idx + 2];
        const brightness = r * 0.299 + g * 0.587 + b * 0.114;

        // Filter: only capture the blue glowing infinity symbol
        if (b < 40 && brightness < 30) continue;

        let radius = 1.0;
        let isCore = false;
        let alpha = 0.85;

        if (brightness >= 150) {
          radius = 1.6 + Math.random() * 1.4;
          isCore = true;
          alpha = 1.0;
        } else if (brightness >= 90) {
          radius = 1.1 + Math.random() * 0.8;
          isCore = brightness > 125;
          alpha = 0.9;
        } else if (brightness >= 45) {
          if (x % 3 !== 0 || y % 3 !== 0) continue;
          radius = 0.8 + Math.random() * 0.6;
          alpha = 0.75;
        } else {
          if (x % 4 !== 0 || y % 4 !== 0) continue;
          radius = 0.6 + Math.random() * 0.4;
          alpha = 0.6;
        }

        const colorStr = `rgba(${r}, ${g}, ${b}, ${alpha.toFixed(2)})`;
        const angleOffset = (Math.random() - 0.5) * 0.7; // slight angular dispersal
        const speedFactor = 0.75 + Math.random() * 0.5;

        particles.push({
          normX: x / sampleW,
          normY: y / sampleH,
          ox: 0,
          oy: 0,
          x: 0,
          y: 0,
          vx: 0,
          vy: 0,
          r: radius,
          color: colorStr,
          isCore,
          scatterAngle: angleOffset,
          scatterSpeed: speedFactor,
          active: false
        });
      }
    }

    this.infinitySpreadParticles = particles;
  }

  onInfinityMouseMove(event: MouseEvent) {
    if (!this.infinityCanvasRef) return;
    const canvas = this.infinityCanvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    const curX = event.clientX - rect.left;
    const curY = event.clientY - rect.top;

    if (!this.infinityMouseInside) {
      this.infinityMouseInside = true;
      this.infinityPrevMouseX = curX;
      this.infinityPrevMouseY = curY;
    }

    const deltaX = curX - this.infinityPrevMouseX;
    const deltaY = curY - this.infinityPrevMouseY;
    this.infinityPrevMouseX = curX;
    this.infinityPrevMouseY = curY;

    this.infinityMouseX = curX;
    this.infinityMouseY = curY;

    this.infinityMouseVx = this.infinityMouseVx * 0.5 + deltaX * 0.5;
    this.infinityMouseVy = this.infinityMouseVy * 0.5 + deltaY * 0.5;

    const speed = Math.hypot(this.infinityMouseVx, this.infinityMouseVy);
    const maxSpeed = 20;
    if (speed > maxSpeed) {
      this.infinityMouseVx = (this.infinityMouseVx / speed) * maxSpeed;
      this.infinityMouseVy = (this.infinityMouseVy / speed) * maxSpeed;
    }
  }

  onInfinityMouseEnter(event: MouseEvent) {
    if (!this.infinityCanvasRef) return;
    const canvas = this.infinityCanvasRef.nativeElement;
    const rect = canvas.getBoundingClientRect();
    const curX = event.clientX - rect.left;
    const curY = event.clientY - rect.top;
    this.infinityMouseInside = true;
    this.infinityMouseX = curX;
    this.infinityMouseY = curY;
    this.infinityPrevMouseX = curX;
    this.infinityPrevMouseY = curY;
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
      this.renderInfinitySpreadFrame();
      this.infinityAnimId = requestAnimationFrame(loop);
    };

    this.infinityAnimId = requestAnimationFrame(loop);
  }

  private renderInfinitySpreadFrame() {
    const ctx = this.infinityCanvasCtx;
    if (!ctx || !this.infinityImage || this.infinityCanvasWidth === 0) return;

    const w = this.infinityCanvasWidth;
    const h = this.infinityCanvasHeight;

    const isMouseActive = this.infinityMouseInside && !this.prefersReducedMotion;
    const mx = this.infinityMouseX;
    const my = this.infinityMouseY;
    const mvx = this.infinityMouseVx;
    const mvy = this.infinityMouseVy;

    // Interaction zone radius
    const radius = Math.min(w, h) * 0.17;
    const radiusSq = radius * radius;
    const springK = 0.082;
    const damping = 0.81;

    let anyParticleActive = false;

    // 1. If mouse is completely outside and no particles are moving, draw 100% exact static image
    if (!isMouseActive) {
      let activeCount = 0;
      for (let i = 0; i < this.infinitySpreadParticles.length; i++) {
        if (this.infinitySpreadParticles[i].active) {
          activeCount++;
          break;
        }
      }
      if (activeCount === 0) {
        ctx.drawImage(this.infinityImage, 0, 0, w, h);
        return;
      }
    }

    // 2. Prepare masked base image in tempCanvas:
    // Erase the localized circular interaction zone under the cursor so the solid image dissolves into spreading particles
    if (this.infinityTempCanvas && this.infinityTempCtx && isMouseActive) {
      const tCtx = this.infinityTempCtx;
      tCtx.clearRect(0, 0, w, h);
      tCtx.drawImage(this.infinityImage, 0, 0, w, h);

      tCtx.globalCompositeOperation = 'destination-out';
      const grad = tCtx.createRadialGradient(mx, my, 0, mx, my, radius);
      grad.addColorStop(0, 'rgba(0, 0, 0, 1)');
      grad.addColorStop(0.65, 'rgba(0, 0, 0, 0.85)');
      grad.addColorStop(1, 'rgba(0, 0, 0, 0)');
      tCtx.fillStyle = grad;
      tCtx.beginPath();
      tCtx.arc(mx, my, radius, 0, Math.PI * 2);
      tCtx.fill();
      tCtx.globalCompositeOperation = 'source-over';

      // Draw background and masked base image
      ctx.fillStyle = '#020610';
      ctx.fillRect(0, 0, w, h);
      ctx.drawImage(this.infinityTempCanvas, 0, 0, w, h);
    } else {
      ctx.fillStyle = '#020610';
      ctx.fillRect(0, 0, w, h);
      ctx.drawImage(this.infinityImage, 0, 0, w, h);
    }

    // 3. Update & render particles that spread and scatter in the cursor's interaction area
    const activeParticlesToDraw: typeof this.infinitySpreadParticles = [];
    const hoverSpeed = Math.hypot(mvx, mvy);

    for (let i = 0; i < this.infinitySpreadParticles.length; i++) {
      const p = this.infinitySpreadParticles[i];

      const dx = mx - p.ox;
      const dy = my - p.oy;
      const distSq = dx * dx + dy * dy;

      if (isMouseActive && distSq < radiusSq) {
        const dist = Math.sqrt(distSq);
        const t = 1 - (dist / radius);
        const falloff = t * t * (3 - 2 * t); // smoothstep falloff

        // A. Shift in cursor's movement direction
        const shiftX = mvx * falloff * 0.28 * p.scatterSpeed;
        const shiftY = mvy * falloff * 0.28 * p.scatterSpeed;

        // B. Radial dispersal (particles spread outward from cursor instead of floating)
        const angle = Math.atan2(p.oy - my, p.ox - mx) + p.scatterAngle;
        const spreadStrength = (hoverSpeed < 0.5 ? 1.5 : 0.8) * falloff * p.scatterSpeed;
        const spreadX = Math.cos(angle) * spreadStrength;
        const spreadY = Math.sin(angle) * spreadStrength;

        p.vx += shiftX + spreadX;
        p.vy += shiftY + spreadY;
        p.active = true;
      }

      // Spring physics smoothly returning particle to anchor position
      if (p.active) {
        const hx = p.ox - p.x;
        const hy = p.oy - p.y;
        p.vx += hx * springK;
        p.vy += hy * springK;
        p.vx *= damping;
        p.vy *= damping;

        p.x += p.vx;
        p.y += p.vy;

        // Displacement clamp keeping infinity symbol structure completely intact
        const offX = p.x - p.ox;
        const offY = p.y - p.oy;
        const currentShift = Math.hypot(offX, offY);
        const maxShift = 26;
        if (currentShift > maxShift) {
          const scale = maxShift / currentShift;
          p.x = p.ox + offX * scale;
          p.y = p.oy + offY * scale;
        }

        // Snap to rest when near anchor
        if (Math.abs(hx) < 0.08 && Math.abs(hy) < 0.08 && Math.hypot(p.vx, p.vy) < 0.02) {
          p.x = p.ox;
          p.y = p.oy;
          p.vx = 0;
          p.vy = 0;
          p.active = false;
        } else {
          anyParticleActive = true;
          activeParticlesToDraw.push(p);
        }
      } else if (isMouseActive && distSq < radiusSq * 1.2) {
        // Also draw resting particles within the masked circle so there is zero gap
        p.x = p.ox;
        p.y = p.oy;
        activeParticlesToDraw.push(p);
      }
    }

    this.infinityMouseVx *= 0.7;
    this.infinityMouseVy *= 0.7;

    // 4. Draw spreading particles
    for (let i = 0; i < activeParticlesToDraw.length; i++) {
      const p = activeParticlesToDraw[i];
      ctx.fillStyle = p.color;
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
      ctx.fill();
    }

    // 5. Glowing aura for active core spark nodes
    ctx.save();
    ctx.globalCompositeOperation = 'lighter';
    for (let i = 0; i < activeParticlesToDraw.length; i++) {
      const p = activeParticlesToDraw[i];
      if (p.isCore && p.active) {
        const glowR = p.r * 2.8;
        const grad = ctx.createRadialGradient(p.x, p.y, 0, p.x, p.y, glowR);
        grad.addColorStop(0, 'rgba(0, 240, 255, 0.5)');
        grad.addColorStop(0.5, 'rgba(0, 180, 255, 0.15)');
        grad.addColorStop(1, 'rgba(0, 120, 255, 0)');
        ctx.fillStyle = grad;
        ctx.beginPath();
        ctx.arc(p.x, p.y, glowR, 0, Math.PI * 2);
        ctx.fill();
      }
    }
    ctx.restore();
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
    this.infinityTempCanvas = null;
    this.infinityTempCtx = null;
  }
}



