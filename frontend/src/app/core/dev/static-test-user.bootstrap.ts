import { environment } from '../../../environments/environment';

const USER_KEY = 'user';
const LOCAL_USER_KEY = 'smarthire_local_user';
const LOCAL_PROFILE_KEY = 'smarthire_local_profile';
const PROFILE_UUID_KEY = 'smarthire_profile_user_uuid';
const DEMO_MODE_KEY = 'smarthire_local_demo_mode';

const STATIC_USER_ID = '1';

/**
 * Seeds a deterministic local test user/profile when running in local fallback mode.
 * This keeps roadmap/profile pages usable even when external auth/profile services are down.
 */
export function ensureStaticTestUserProfile(): void {
  if (!environment.localAuthFallback || typeof window === 'undefined') {
    return;
  }

  const storage = window.localStorage;

  if (storage.getItem(USER_KEY)) {
    return;
  }

  const sessionUser = {
    id: STATIC_USER_ID,
    email: 'roadmap.tester@smarthire.local',
    name: 'Roadmap Tester',
    role: 'user',
  };

  storage.setItem(USER_KEY, JSON.stringify(sessionUser));
  storage.setItem('userId', STATIC_USER_ID);
  storage.setItem('user_id', STATIC_USER_ID);
  storage.setItem('uid', STATIC_USER_ID);

  storage.setItem(
    LOCAL_USER_KEY,
    JSON.stringify({
      firstName: 'Roadmap',
      lastName: 'Tester',
      email: sessionUser.email,
    })
  );

  storage.setItem(PROFILE_UUID_KEY, environment.devProfileUserUuid || STATIC_USER_ID);
  storage.setItem(DEMO_MODE_KEY, '1');

  storage.setItem(
    LOCAL_PROFILE_KEY,
    JSON.stringify({
      userId: environment.devProfileUserUuid || STATIC_USER_ID,
      firstName: 'Roadmap',
      lastName: 'Tester',
      email: sessionUser.email,
      headline: 'Static local profile for roadmap testing',
      location: 'Tunis',
      githubUrl: '',
      linkedinUrl: '',
      avatarUrl: '',
      onboardingJson: JSON.stringify({
        completedAt: new Date().toISOString(),
        situation: 'Testing local roadmap flow',
        careerPath: 'Roadmap QA',
        preferencesOnly: true,
      }),
    })
  );
}
