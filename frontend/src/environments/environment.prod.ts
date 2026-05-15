export const environment = {
  production: true,
  
  // Authentication & Fallback Settings
  localAuthFallback: false,

  // MS-User Service (profiles, onboarding persistence) - Route through Gateway
  userApiUrl: 'http://localhost:8887/api/v1',
  
  /** M4 profile optimization service. */
  profileOptimizationApiUrl: 'http://localhost:8887/MS-PROFILE/api',

  // MS-Assessment Configuration - Route through Gateway
  assessmentApiUrl: 'http://localhost:8887/api/v1/assessment',
  assessmentAdminApiKey: 'dev-assessment-admin',

  // MS-Roadmap Service Configuration - Route through Gateway
  roadmapApiUrl: 'http://localhost:8887/msroadmap/api',
  roadmapWsUrl: 'ws://localhost:8887/msroadmap/ws',

  // Alternative API URL structure (for backward compatibility)
  apiUrl: 'http://localhost:8887/msroadmap/api',

  // Legacy API URLs object (maintained for compatibility)
  apiUrls: {
    roadmap: 'http://localhost:8887/msroadmap/api',
    assessment: 'http://localhost:8887/api/v1/assessment'
  },

  // Development User Configuration
  devProfileUserUuid: '00000000-0000-4000-8000-000000000001',

  // Admin Panel Access (dev only)
  openAdminPanelInDev: false,
};
