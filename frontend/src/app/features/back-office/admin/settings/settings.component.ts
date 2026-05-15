import { Component, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { LUCIDE_ICONS } from '../../../../shared/lucide-icons';
import { Router } from '@angular/router';
import { UserManagementService } from '../../service/user-management.service';
import { AutheService } from '../../../front-office/auth/authe.service';
import { FaceRecognitionService } from '../../../front-office/auth/setup-face-recognition/face-recognition.service';
import { ThemeMode, ThemePalette, ThemeService } from '../../../../shared/services/theme.service';

/* ══════════ INTERFACES ══════════ */

interface SettingsCategory {
  id: string;
  label: string;
  icon: string;
  group: number;
  danger?: boolean;
  dirty?: boolean;
}

interface PlatformIdentity {
  name: string;
  url: string;
  supportEmail: string;
  contactEmail: string;
  defaultLanguage: string;
  defaultTimezone: string;
}

interface MaintenanceConfig {
  enabled: boolean;
  message: string;
  estimatedEnd: string;
}

interface LocalizationConfig {
  dateFormat: string;
  currency: string;
  availableLanguages: string[];
}

interface FeaturesConfig {
  faceRecognitionEnabled: boolean;
}

interface Toast {
  message: string;
  type: 'success' | 'error';
  visible: boolean;
}

@Component({
  selector: 'app-settings',
  standalone: true,
  imports: [CommonModule, FormsModule, LUCIDE_ICONS],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent implements OnDestroy {

  /* ══════════ LEFT NAV CATEGORIES ══════════ */
  categories: SettingsCategory[] = [
    { id: 'appearance',     label: 'Appearance & Theme',         icon: 'palette',           group: 1 },
    { id: 'general',        label: 'General',                    icon: 'settings',          group: 1 },
    { id: 'branding',       label: 'Branding',                   icon: 'image',             group: 1 },
    { id: 'ai',             label: 'AI Configuration',           icon: 'brain-circuit',     group: 2 },
    { id: 'email',          label: 'Email & Notifications',      icon: 'mail',              group: 2 },
    { id: 'auth',           label: 'Authentication & Security',  icon: 'lock',              group: 2 },
    { id: 'billing',        label: 'Subscription & Billing',     icon: 'credit-card',       group: 3 },
    { id: 'features',       label: 'Feature Flags',              icon: 'flag',              group: 3 },
    { id: 'integrations',   label: 'Integrations',               icon: 'code',              group: 3 },
    { id: 'audit',          label: 'Audit Log',                  icon: 'file-text',         group: 4 },
    { id: 'danger',         label: 'Danger Zone',                icon: 'triangle-alert',    group: 4, danger: true },
  ];

  activeCategory = 'general';
  themePalette: ThemePalette;
  themeMode: ThemeMode;
  readonly themePalettes: ThemePalette[];

  /* ══════════ GENERAL — PLATFORM IDENTITY ══════════ */
  identity: PlatformIdentity = {
    name: 'SmartHire AI',
    url: 'smarthire.io',
    supportEmail: 'support@smarthire.io',
    contactEmail: 'contact@smarthire.io',
    defaultLanguage: 'en',
    defaultTimezone: 'UTC+1'
  };
  identitySnapshot = '';

  languageOptions = [
    { value: 'en', label: 'English (EN)' },
    { value: 'fr', label: 'French (FR)' },
    { value: 'de', label: 'German (DE)' },
    { value: 'es', label: 'Spanish (ES)' },
    { value: 'ar', label: 'Arabic (AR)' },
    { value: 'zh', label: 'Chinese (ZH)' },
    { value: 'ja', label: 'Japanese (JA)' },
    { value: 'pt', label: 'Portuguese (PT)' },
  ];

  timezoneOptions = [
    'UTC-12 — Baker Island',
    'UTC-8 — Pacific Time (US)',
    'UTC-7 — Mountain Time (US)',
    'UTC-6 — Central Time (US)',
    'UTC-5 — Eastern Time (US)',
    'UTC-3 — Buenos Aires',
    'UTC+0 — London / Lisbon',
    'UTC+1 — Central European Time',
    'UTC+2 — Eastern European Time',
    'UTC+3 — Moscow / Istanbul',
    'UTC+5:30 — India Standard Time',
    'UTC+8 — Singapore / Perth',
    'UTC+9 — Tokyo / Seoul',
    'UTC+10 — Sydney / Melbourne',
  ];

  /* ══════════ GENERAL — MAINTENANCE MODE ══════════ */
  maintenance: MaintenanceConfig = {
    enabled: false,
    message: 'We\\u2019re upgrading SmartHire \\u2014 back in 2 hours.',
    estimatedEnd: '2026-03-01T18:00'
  };
  maintenanceSnapshot = '';
  showMaintenanceModal = false;
  maintenanceConfirmText = '';

  /* ══════════ GENERAL — LOCALIZATION ══════════ */
  localization: LocalizationConfig = {
    dateFormat: 'DD/MM/YYYY',
    currency: 'EUR',
    availableLanguages: ['English', 'French']
  };
  localizationSnapshot = '';

  dateFormatOptions = ['DD/MM/YYYY', 'MM/DD/YYYY', 'YYYY-MM-DD'];
  currencyOptions = [
    { value: 'EUR', label: 'EUR — Euro (€)' },
    { value: 'USD', label: 'USD — US Dollar ($)' },
    { value: 'GBP', label: 'GBP — British Pound (£)' },
    { value: 'CHF', label: 'CHF — Swiss Franc' },
    { value: 'CAD', label: 'CAD — Canadian Dollar (C$)' },
    { value: 'AUD', label: 'AUD — Australian Dollar (A$)' },
    { value: 'JPY', label: 'JPY — Japanese Yen (¥)' },
  ];

  allLanguages = ['English', 'French', 'German', 'Spanish', 'Arabic', 'Chinese', 'Japanese', 'Portuguese', 'Italian', 'Dutch', 'Korean', 'Russian'];
  newLanguageInput = '';
  showLanguageDropdown = false;

  /* ══════════ DIRTY / SAVE STATE ══════════ */
  dirtyCategories = new Set<string>();
  toast: Toast = { message: '', type: 'success', visible: false };
  private toastTimeout: any;
  /* Features config */
  features: FeaturesConfig = {
    faceRecognitionEnabled: true
  };
  featuresSnapshot = '';

  /* ══════════ LIFECYCLE ══════════ */
  constructor(private readonly themeService: ThemeService,private router: Router, private userManagementService: UserManagementService, private setupFaceService: FaceRecognitionService) {

    this.themePalette = this.themeService.palette;
    this.themeMode = this.themeService.mode;
    this.themePalettes = this.themeService.palettes;
    this.snapshotAll();
  }

  private userId:any;
ngOnInit(): void {
  this.userId = localStorage.getItem('userId') || sessionStorage.getItem('userId');

  this.userManagementService.isMfaEnabled(this.userId).subscribe((isEnabled) => {
    console.log('Face recognition enabled:', isEnabled);

    this.features.faceRecognitionEnabled = isEnabled;
  });
}

  ngOnDestroy(): void {
    if (this.toastTimeout) clearTimeout(this.toastTimeout);
  }




  setupFaceRecognition(): void {
    if ( ! this.features.faceRecognitionEnabled) {
      this.features.faceRecognitionEnabled = !this.features.faceRecognitionEnabled; 
      this.checkDirty();
      this.router.navigate(["/setup-face-recognition"])
    }else{
      this.setupFaceService.disableFaceRecognition(this.userId).subscribe({
        next: () => {
          this.features.faceRecognitionEnabled = !this.features.faceRecognitionEnabled;
          this.checkDirty();
          this.showToast('Face recognition disabled successfully', 'success');
        }
      });

    }
    
  }

  /* ══════════ SNAPSHOT HELPERS ══════════ */
  private snapshotAll(): void {
    this.identitySnapshot = JSON.stringify(this.identity);
    this.maintenanceSnapshot = JSON.stringify(this.maintenance);
    this.localizationSnapshot = JSON.stringify(this.localization);
    this.featuresSnapshot = JSON.stringify(this.features);
  }

  private snapshotCategory(cat: string): void {
    if (cat === 'general') {
      this.identitySnapshot = JSON.stringify(this.identity);
      this.maintenanceSnapshot = JSON.stringify(this.maintenance);
      this.localizationSnapshot = JSON.stringify(this.localization);
    } else if (cat === 'features') {
      this.featuresSnapshot = JSON.stringify(this.features);
    }
  }

  /* ══════════ DIRTY CHECK ══════════ */
  checkDirty(): void {
    const identityDirty = JSON.stringify(this.identity) !== this.identitySnapshot;
    const maintenanceDirty = JSON.stringify(this.maintenance) !== this.maintenanceSnapshot;
    const localizationDirty = JSON.stringify(this.localization) !== this.localizationSnapshot;
    const featuresDirty = JSON.stringify(this.features) !== this.featuresSnapshot;

    if (identityDirty || maintenanceDirty || localizationDirty) {
      this.dirtyCategories.add('general');
    } else {
      this.dirtyCategories.delete('general');
    }
    if (featuresDirty) {
      this.dirtyCategories.add('features');
    } else {
      this.dirtyCategories.delete('features');
    }
    this.syncCategoryDirtyFlags();
  }

  private syncCategoryDirtyFlags(): void {
    this.categories.forEach(c => c.dirty = this.dirtyCategories.has(c.id));
  }

  get isCurrentDirty(): boolean {
    return this.dirtyCategories.has(this.activeCategory);
  }

  /* ══════════ SAVE / DISCARD ══════════ */
  saveCategory(): void {
    this.snapshotCategory(this.activeCategory);
    this.dirtyCategories.delete(this.activeCategory);
    this.syncCategoryDirtyFlags();
    this.showToast('Settings saved successfully', 'success');
  }

  discardCategory(): void {
    if (this.activeCategory === 'general') {
      this.identity = JSON.parse(this.identitySnapshot);
      this.maintenance = JSON.parse(this.maintenanceSnapshot);
      this.localization = JSON.parse(this.localizationSnapshot);
    } else if (this.activeCategory === 'features') {
      this.features = JSON.parse(this.featuresSnapshot);
    }
    this.dirtyCategories.delete(this.activeCategory);
    this.syncCategoryDirtyFlags();
  }

  /* ══════════ TOAST ══════════ */
  showToast(message: string, type: 'success' | 'error'): void {
    this.toast = { message, type, visible: true };
    if (this.toastTimeout) clearTimeout(this.toastTimeout);
    this.toastTimeout = setTimeout(() => this.toast.visible = false, 3500);
  }

  /* ══════════ CATEGORY NAV ══════════ */
  selectCategory(id: string): void {
    this.activeCategory = id;
  }

  selectThemePalette(palette: ThemePalette): void {
    this.themeService.setTheme(palette);
    this.themePalette = palette;
  }

  selectThemeMode(mode: ThemeMode): void {
    this.themeService.setMode(mode);
    this.themeMode = mode;
  }

  formatPaletteName(palette: ThemePalette): string {
    return palette.split('-').map((part) => part.charAt(0).toUpperCase() + part.slice(1)).join(' ');
  }

  shouldShowDivider(index: number): boolean {
    if (index === 0) return false;
    return this.categories[index].group !== this.categories[index - 1].group;
  }

  /* ══════════ MAINTENANCE MODE ══════════ */
  onMaintenanceToggle(): void {
    if (!this.maintenance.enabled) {
      // Turning ON — show confirmation modal
      this.showMaintenanceModal = true;
      this.maintenanceConfirmText = '';
    } else {
      // Turning OFF — just disable
      this.maintenance.enabled = false;
      this.checkDirty();
    }
  }

  confirmMaintenance(): void {
    this.maintenance.enabled = true;
    this.showMaintenanceModal = false;
    this.maintenanceConfirmText = '';
    this.checkDirty();
  }

  cancelMaintenance(): void {
    this.showMaintenanceModal = false;
    this.maintenanceConfirmText = '';
  }

  get maintenanceConfirmValid(): boolean {
    return this.maintenanceConfirmText === 'MAINTENANCE';
  }

  /* ══════════ LANGUAGE TAGS ══════════ */
  get filteredLanguageOptions(): string[] {
    const current = new Set(this.localization.availableLanguages.map(l => l.toLowerCase()));
    return this.allLanguages.filter(l =>
      !current.has(l.toLowerCase()) &&
      l.toLowerCase().includes(this.newLanguageInput.toLowerCase())
    );
  }

  addLanguage(lang: string): void {
    if (!this.localization.availableLanguages.includes(lang)) {
      this.localization.availableLanguages.push(lang);
      this.newLanguageInput = '';
      this.showLanguageDropdown = false;
      this.checkDirty();
    }
  }

  removeLanguage(index: number): void {
    this.localization.availableLanguages.splice(index, 1);
    this.checkDirty();
  }

  onLanguageInputFocus(): void {
    this.showLanguageDropdown = true;
  }

  onLanguageInputBlur(): void {
    // Delay to allow click on dropdown items
    setTimeout(() => this.showLanguageDropdown = false, 200);
  }

 
}
